package net.bible.sharedlogic.text

import okio.ByteString.Companion.toByteString

/** Common equivalent of `java.util.UUID.nameUUIDFromBytes(name).toString()` (UUID version 3, MD5). */
object NameBasedUuid {
    fun v3(name: ByteArray): String {
        val b = name.toByteString().md5().toByteArray()
        b[6] = (b[6].toInt() and 0x0f or 0x30).toByte()
        b[8] = (b[8].toInt() and 0x3f or 0x80).toByte()
        val hex = b.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
            "${hex.substring(16, 20)}-${hex.substring(20)}"
    }
}
