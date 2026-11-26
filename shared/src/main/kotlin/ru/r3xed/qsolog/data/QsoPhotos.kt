package ru.r3xed.qsolog.data

import java.io.File

/**
 * The station's photo kept with a contact on the device it was logged on: a file named by the contact's UID in [dir]
 * (the app's own folder). It is not in the log, in ADIF, in backups or in the table sync, so on another device the
 * same contact shows no photo.
 */
class QsoPhotos(private val dir: File) {
    private fun name(uid: String) = uid.filter { it.isLetterOrDigit() || it == '-' }.takeIf { it.isNotBlank() }

    /** The photo of the contact [uid], when this device has one. */
    fun file(uid: String): File? = name(uid)?.let { File(dir, "$it.jpg") }?.takeIf { it.isFile && it.length() > 0 }

    /** Keeps [bytes] (as downloaded: JPEG, PNG or GIF) for the contact [uid]. */
    fun save(uid: String, bytes: ByteArray) {
        val n = name(uid) ?: return
        if (bytes.isEmpty()) return
        dir.mkdirs()
        val tmp = File(dir, "$n.tmp")
        tmp.writeBytes(bytes)
        tmp.renameTo(File(dir, "$n.jpg")) || run { File(dir, "$n.jpg").writeBytes(bytes); tmp.delete() }
    }

    fun delete(uid: String) {
        name(uid)?.let { File(dir, "$it.jpg").delete() }
    }

    companion object {
        /** "png", "gif" or "jpg" by the file's first bytes (the photos are kept as .jpg whatever they are). */
        fun extension(b: ByteArray): String = when {
            b.size > 3 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() && b[2] == 'N'.code.toByte() -> "png"
            b.size > 3 && b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() && b[2] == 'F'.code.toByte() -> "gif"
            b.size > 11 && b[8] == 'W'.code.toByte() && b[9] == 'E'.code.toByte() && b[10] == 'B'.code.toByte() -> "webp"
            else -> "jpg"
        }

        /** The photo at [url] (http or https), or null; at most 5 MB. */
        fun download(url: String, agent: String = "QSO-LOG"): ByteArray? = try {
            val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 8_000; c.readTimeout = 12_000
            c.setRequestProperty("User-Agent", agent)
            try {
                if (c.responseCode !in 200..299) null
                else c.inputStream.use { s -> readAtMost(s, 5_000_001) }.takeIf { it.size in 1..5_000_000 }
            } finally {
                c.disconnect()
            }
        } catch (e: Exception) {
            null
        }

        /** Up to [limit] bytes of [s]. InputStream.readNBytes is only on Android 13+, so it is read by hand. */
        internal fun readAtMost(s: java.io.InputStream, limit: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            while (out.size() < limit) {
                val n = s.read(buf, 0, minOf(buf.size, limit - out.size()))
                if (n < 0) break
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }
}
