package ru.r3xed.qsolog.data

import java.io.IOException
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * A PKCS#12 (.p12) reader of our own, for when the system's keystore cannot open the file.
 *
 * TQSL built with OpenSSL 3 saves the callsign certificate with PBES2: PBKDF2 (HMAC-SHA256) and AES-256-CBC. The keystore
 * of older Android (Huawei tablets with Android 10 among them) looks the key derivation up by its OID and fails with
 * "1.2.840.113549.1.5.12 SecretKeyFactory not available". Here PBKDF2 is done by hand over the HMAC every Android has.
 * The older files (pbeWithSHAAnd3-KeyTripleDES-CBC, pbeWithSHAAnd40BitRC2-CBC) are read too.
 *
 * Only what LoTW needs: the certificates and the private keys. The file's MAC is not checked: a wrong password shows
 * itself as a padding error of the key or a key that does not parse.
 */
object Pkcs12 {
    class WrongPassword : IOException("wrong password")

    data class Content(val keys: List<Pair<PrivateKey, ByteArray?>>, val certs: List<Pair<X509Certificate, ByteArray?>>)

    /** The keys and certificates of [bytes]; each with its localKeyId, which pairs a key with its certificate. */
    fun read(bytes: ByteArray, password: String): Content {
        val keys = ArrayList<Pair<PrivateKey, ByteArray?>>()
        val certs = ArrayList<Pair<X509Certificate, ByteArray?>>()
        val pfx = Der(bytes).seq()
        pfx.next() // version
        val authSafe = pfx.next().seq()
        if (authSafe.next().oid() != DATA) throw IOException("not a password-protected PKCS#12 file")
        val safes = Der(authSafe.next().explicit().octets()).seq()
        while (safes.more()) {
            val ci = safes.next().seq()
            val type = ci.next().oid()
            val content = ci.next().explicit()
            val safeContents = when (type) {
                DATA -> content.octets()
                ENCRYPTED_DATA -> {
                    val ed = content.seq()
                    ed.next() // version
                    val eci = ed.next().seq()
                    eci.next() // content type
                    val alg = eci.next()
                    val plain = decrypt(alg, eci.next().octets(), password)
                    // Garbage that happened to end in valid padding: the password was wrong.
                    try { bags(plain, password, keys, certs) } catch (e: WrongPassword) { throw e } catch (e: Exception) { throw WrongPassword() }
                    continue
                }
                else -> continue
            }
            bags(safeContents, password, keys, certs)
        }
        return Content(keys, certs)
    }

    private fun bags(safeContents: ByteArray, password: String, keys: MutableList<Pair<PrivateKey, ByteArray?>>, certs: MutableList<Pair<X509Certificate, ByteArray?>>) {
        val list = Der(safeContents).seq()
        while (list.more()) {
            val bag = list.next().seq()
            val id = bag.next().oid()
            val value = bag.next().explicit()
            val localId = if (bag.more()) localKeyId(bag.next()) else null
            when (id) {
                KEY_BAG -> keys += privateKey(value.encoded()) to localId
                SHROUDED_KEY_BAG -> {
                    val epki = value.seq()
                    val alg = epki.next()
                    keys += privateKey(decrypt(alg, epki.next().octets(), password)) to localId
                }
                CERT_BAG -> {
                    val cb = value.seq()
                    if (cb.next().oid() != X509_CERT) continue
                    val der = cb.next().explicit().octets()
                    val cert = CertificateFactory.getInstance("X.509").generateCertificate(der.inputStream()) as X509Certificate
                    certs += cert to localId
                }
            }
        }
    }

    private fun localKeyId(attrs: Der): ByteArray? {
        val set = attrs.seq() // SET OF Attribute: read as a sequence of elements
        while (set.more()) {
            val a = set.next().seq()
            if (a.next().oid() != LOCAL_KEY_ID) continue
            return a.next().seq().next().octets()
        }
        return null
    }

    private fun privateKey(pkcs8: ByteArray): PrivateKey {
        // PrivateKeyInfo: version, AlgorithmIdentifier, key. RSA for TQSL; EC just in case.
        val alg = runCatching { Der(pkcs8).seq().apply { next() }.next().seq().next().oid() }.getOrNull()
            ?: throw WrongPassword()
        val name = if (alg == EC_KEY) "EC" else "RSA"
        return try {
            KeyFactory.getInstance(name).generatePrivate(PKCS8EncodedKeySpec(pkcs8))
        } catch (e: Exception) {
            throw WrongPassword()
        }
    }

    // ---------- decryption ----------

    private fun decrypt(algId: Der, data: ByteArray, password: String): ByteArray {
        val alg = algId.seq()
        val oid = alg.next().oid()
        val params = alg.next()
        return try {
            when (oid) {
                PBES2 -> pbes2(params, data, password)
                PBE_SHA_3DES -> legacy(params, data, password, "DESede", 24)
                PBE_SHA_RC2_40 -> legacy(params, data, password, "RC2", 5)
                PBE_SHA_RC2_128 -> legacy(params, data, password, "RC2", 16)
                else -> throw IOException("unknown encryption $oid")
            }
        } catch (e: javax.crypto.BadPaddingException) {
            throw WrongPassword()
        }
    }

    private fun pbes2(params: Der, data: ByteArray, password: String): ByteArray {
        val p = params.seq()
        val kdf = p.next().seq()
        if (kdf.next().oid() != PBKDF2) throw IOException("unknown key derivation")
        val kp = kdf.next().seq()
        val salt = kp.next().octets()
        val iterations = kp.next().int()
        var keyLength = -1
        var prf = HMAC_SHA1
        while (kp.more()) {
            val e = kp.next()
            if (e.tag == 0x02) keyLength = e.int() else prf = e.seq().next().oid()
        }
        val scheme = p.next().seq()
        val enc = scheme.next().oid()
        val iv = scheme.next().octets()
        val (cipher, len) = when (enc) {
            AES128_CBC -> "AES/CBC/PKCS5Padding" to 16
            AES192_CBC -> "AES/CBC/PKCS5Padding" to 24
            AES256_CBC -> "AES/CBC/PKCS5Padding" to 32
            DES_EDE3_CBC -> "DESede/CBC/PKCS5Padding" to 24
            else -> throw IOException("unknown cipher $enc")
        }
        val mac = when (prf) {
            HMAC_SHA1 -> "HmacSHA1"
            HMAC_SHA224 -> "HmacSHA224"
            HMAC_SHA256 -> "HmacSHA256"
            HMAC_SHA384 -> "HmacSHA384"
            HMAC_SHA512 -> "HmacSHA512"
            else -> throw IOException("unknown PRF $prf")
        }
        // PBES2 in PKCS#12 takes the password as its UTF-8 bytes (OpenSSL does so).
        val key = pbkdf2(mac, password.toByteArray(Charsets.UTF_8), salt, iterations, if (keyLength > 0) keyLength else len)
        val c = Cipher.getInstance(cipher)
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, cipher.substringBefore('/')), IvParameterSpec(iv))
        return c.doFinal(data)
    }

    /** PBKDF2 (RFC 8018, 5.2) over the HMAC [macName]. */
    fun pbkdf2(macName: String, password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
        val mac = Mac.getInstance(macName)
        // An empty password is a valid one, but SecretKeySpec does not take empty bytes.
        mac.init(if (password.isEmpty()) EmptyKey(macName) else SecretKeySpec(password, macName))
        val hLen = mac.macLength
        val out = ByteArray(length)
        var block = 1
        var at = 0
        while (at < length) {
            mac.update(salt)
            mac.update(byteArrayOf((block ushr 24).toByte(), (block ushr 16).toByte(), (block ushr 8).toByte(), block.toByte()))
            var u = mac.doFinal()
            val t = u.copyOf()
            repeat(iterations - 1) {
                u = mac.doFinal(u)
                for (i in t.indices) t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
            }
            System.arraycopy(t, 0, out, at, minOf(hLen, length - at))
            at += hLen
            block++
        }
        return out
    }

    private class EmptyKey(private val alg: String) : javax.crypto.SecretKey {
        override fun getAlgorithm() = alg
        override fun getFormat() = "RAW"
        override fun getEncoded() = ByteArray(0)
    }

    /** The PKCS#12 PBE of the older files: SHA-1 key derivation (RFC 7292, appendix B) and 3DES or RC2 in CBC. */
    private fun legacy(params: Der, data: ByteArray, password: String, cipher: String, keyLen: Int): ByteArray {
        val p = params.seq()
        val salt = p.next().octets()
        val iterations = p.next().int()
        val pw = bmpPassword(password)
        val key = pkcs12Kdf(pw, salt, iterations, 1, keyLen)
        val iv = pkcs12Kdf(pw, salt, iterations, 2, 8)
        val c = Cipher.getInstance("$cipher/CBC/PKCS5Padding")
        if (cipher == "RC2") c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "RC2"), javax.crypto.spec.RC2ParameterSpec(keyLen * 8, iv))
        else c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, cipher), IvParameterSpec(iv))
        return c.doFinal(data)
    }

    private fun bmpPassword(password: String): ByteArray {
        val b = ByteArray(password.length * 2 + 2)
        password.forEachIndexed { i, ch -> b[i * 2] = (ch.code ushr 8).toByte(); b[i * 2 + 1] = ch.code.toByte() }
        return b
    }

    /** RFC 7292, B.2, with SHA-1 (u = 20, v = 64). */
    fun pkcs12Kdf(password: ByteArray, salt: ByteArray, iterations: Int, id: Int, n: Int): ByteArray {
        val u = 20
        val v = 64
        val d = ByteArray(v) { id.toByte() }
        fun fill(src: ByteArray): ByteArray {
            if (src.isEmpty()) return ByteArray(0)
            val len = v * ((src.size + v - 1) / v)
            return ByteArray(len) { src[it % src.size] }
        }
        val i = fill(salt) + fill(password)
        val out = ByteArray(n)
        val md = MessageDigest.getInstance("SHA-1")
        var at = 0
        while (at < n) {
            md.update(d)
            md.update(i)
            var a = md.digest()
            repeat(iterations - 1) { a = md.digest(a) }
            System.arraycopy(a, 0, out, at, minOf(u, n - at))
            at += u
            if (at >= n) break
            val b = ByteArray(v) { a[it % u] }
            // I_j = (I_j + B + 1) mod 2^(v*8)
            for (j in 0 until i.size / v) {
                var carry = 1
                for (k in v - 1 downTo 0) {
                    val sum = (i[j * v + k].toInt() and 0xFF) + (b[k].toInt() and 0xFF) + carry
                    i[j * v + k] = sum.toByte()
                    carry = sum ushr 8
                }
            }
        }
        return out
    }

    // ---------- a small DER reader ----------

    class Der(val raw: ByteArray, private val from: Int = 0, private val to: Int = raw.size) {
        val tag: Int get() = raw[from].toInt() and 0xFF
        private val header: Pair<Int, Int> by lazy {
            var at = from + 1
            var len = raw[at++].toInt() and 0xFF
            if (len == 0x80) return@lazy at to -1 // indefinite length (BER)
            if (len > 0x7F) {
                val n = len and 0x7F
                len = 0
                repeat(n) { len = (len shl 8) or (raw[at++].toInt() and 0xFF) }
            }
            at to len
        }
        private val start get() = header.first
        val end: Int get() = if (header.second < 0) indefiniteEnd() else start + header.second

        private fun indefiniteEnd(): Int {
            var at = start
            while (!(raw[at].toInt() == 0 && raw[at + 1].toInt() == 0)) at = Der(raw, at, to).end
            return at + 2
        }

        /** The whole element: tag, length and content. */
        fun encoded(): ByteArray = raw.copyOfRange(from, end)

        fun content(): ByteArray = raw.copyOfRange(start, if (header.second < 0) end - 2 else end)

        /** The elements inside a SEQUENCE, SET or context tag. */
        fun seq(): Reader = Reader(raw, start, if (header.second < 0) end - 2 else end)

        /** The one element inside an [n] EXPLICIT tag. */
        fun explicit(): Der = seq().next()

        /** OCTET STRING, primitive or (BER) constructed from pieces; also an IMPLICIT [0] of encrypted content. */
        fun octets(): ByteArray {
            if (tag and 0x20 == 0) return content()
            val out = java.io.ByteArrayOutputStream()
            val r = seq()
            while (r.more()) out.write(r.next().octets())
            return out.toByteArray()
        }

        fun int(): Int = content().fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }

        fun oid(): String {
            val c = content()
            val parts = ArrayList<Long>()
            var v = 0L
            for (b in c) {
                v = (v shl 7) or (b.toLong() and 0x7F)
                if (b.toInt() and 0x80 == 0) {
                    if (parts.isEmpty()) {
                        val first = if (v < 80) v / 40 else 2
                        parts += first
                        parts += v - first * 40
                    } else parts += v
                    v = 0
                }
            }
            return parts.joinToString(".")
        }

        class Reader(private val raw: ByteArray, private var at: Int, private val to: Int) {
            fun more() = at < to && !(raw[at].toInt() == 0 && at + 1 < to && raw[at + 1].toInt() == 0)
            fun next(): Der {
                if (!more()) throw IOException("bad PKCS#12 structure")
                val d = Der(raw, at, to)
                at = d.end
                return d
            }
        }
    }

    private const val DATA = "1.2.840.113549.1.7.1"
    private const val ENCRYPTED_DATA = "1.2.840.113549.1.7.6"
    private const val KEY_BAG = "1.2.840.113549.1.12.10.1.1"
    private const val SHROUDED_KEY_BAG = "1.2.840.113549.1.12.10.1.2"
    private const val CERT_BAG = "1.2.840.113549.1.12.10.1.3"
    private const val X509_CERT = "1.2.840.113549.1.9.22.1"
    private const val LOCAL_KEY_ID = "1.2.840.113549.1.9.21"
    private const val EC_KEY = "1.2.840.10045.2.1"
    private const val PBES2 = "1.2.840.113549.1.5.13"
    private const val PBKDF2 = "1.2.840.113549.1.5.12"
    private const val PBE_SHA_3DES = "1.2.840.113549.1.12.1.3"
    private const val PBE_SHA_RC2_128 = "1.2.840.113549.1.12.1.5"
    private const val PBE_SHA_RC2_40 = "1.2.840.113549.1.12.1.6"
    private const val HMAC_SHA1 = "1.2.840.113549.2.7"
    private const val HMAC_SHA224 = "1.2.840.113549.2.8"
    private const val HMAC_SHA256 = "1.2.840.113549.2.9"
    private const val HMAC_SHA384 = "1.2.840.113549.2.10"
    private const val HMAC_SHA512 = "1.2.840.113549.2.11"
    private const val AES128_CBC = "2.16.840.1.101.3.4.1.2"
    private const val AES192_CBC = "2.16.840.1.101.3.4.1.22"
    private const val AES256_CBC = "2.16.840.1.101.3.4.1.42"
    private const val DES_EDE3_CBC = "1.2.840.113549.3.7"
}
