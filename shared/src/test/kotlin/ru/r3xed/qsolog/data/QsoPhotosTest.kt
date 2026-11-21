package ru.r3xed.qsolog.data

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class QsoPhotosTest {
    @Test
    fun keptByUidOnly() {
        val dir = Files.createTempDirectory("photos").toFile()
        val p = QsoPhotos(dir)
        val uid = "3f2b1c9e-0d4a-4c1e-9b7a-1a2b3c4d5e6f"
        assertNull(p.file(uid))
        p.save(uid, byteArrayOf(1, 2, 3))
        assertContentEquals(byteArrayOf(1, 2, 3), p.file(uid)!!.readBytes())
        // Another contact, a blank or a path-like UID: nothing.
        assertNull(p.file("other"))
        assertNull(p.file(""))
        p.save("../x", byteArrayOf(9))
        assertNull(dir.parentFile.resolve("x.jpg").takeIf { it.exists() })
        p.delete(uid)
        assertNull(p.file(uid))
    }

    @Test
    fun readAtMostStopsAtLimit() {
        val data = ByteArray(40_000) { it.toByte() }
        assertContentEquals(data.copyOf(20_001), QsoPhotos.readAtMost(data.inputStream(), 20_001))
        assertContentEquals(data, QsoPhotos.readAtMost(data.inputStream(), 5_000_001))
        assertContentEquals(ByteArray(0), QsoPhotos.readAtMost(ByteArray(0).inputStream(), 10))
    }
}
