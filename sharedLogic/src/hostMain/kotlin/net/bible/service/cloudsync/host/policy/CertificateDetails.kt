package net.bible.service.cloudsync.host

import kotlin.time.Instant

internal data class CertificateDetails(val issuer: String, val notBefore: Long, val notAfter: Long)

/** Reads only the public X.509 issuer/validity fields; never participates in trust evaluation. */
internal fun certificateDetails(der: ByteArray): CertificateDetails? = try {
    val certificate = DerReader(der).read(0x30).reader()
    val tbs = certificate.read(0x30).reader()
    if (tbs.peek() == 0xa0) tbs.read(0xa0)
    tbs.read(0x02) // serial
    tbs.read(0x30) // signature algorithm
    val issuer = tbs.read(0x30).reader()
    val names = mutableListOf<String>()
    while (issuer.hasNext()) {
        val rdn = issuer.read(0x31).reader()
        while (rdn.hasNext()) {
            val attribute = rdn.read(0x30).reader()
            val oid = attribute.read(0x06).bytes
            val value = attribute.read()
            val key = when (oid.toList()) {
                listOf<Byte>(0x55, 0x04, 0x03) -> "CN"
                listOf<Byte>(0x55, 0x04, 0x06) -> "C"
                listOf<Byte>(0x55, 0x04, 0x07) -> "L"
                listOf<Byte>(0x55, 0x04, 0x08) -> "ST"
                listOf<Byte>(0x55, 0x04, 0x0a) -> "O"
                listOf<Byte>(0x55, 0x04, 0x0b) -> "OU"
                else -> "OID:" + oid.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
            }
            val text = when (value.tag) {
                0x0c, 0x13, 0x16 -> value.bytes.decodeToString(throwOnInvalidSequence = true)
                0x14 -> value.bytes.map { (it.toInt() and 255).toChar() }.joinToString("")
                0x1e -> {
                    require(value.bytes.size % 2 == 0)
                    value.bytes.toList().chunked(2).map { ((it[0].toInt() and 255) * 256 + (it[1].toInt() and 255)).toChar() }.joinToString("")
                }
                else -> error("Unsupported X.509 name encoding")
            }
            names += "$key=$text"
        }
    }
    require(names.isNotEmpty())
    val validity = tbs.read(0x30).reader()
    val from = validity.read().epochMs()
    val until = validity.read().epochMs()
    require(!validity.hasNext() && until >= from)
    CertificateDetails(names.joinToString(", "), from, until)
} catch (_: IllegalArgumentException) { null }
  catch (_: IllegalStateException) { null }

private data class DerValue(val tag: Int, val bytes: ByteArray) {
    fun reader() = DerReader(bytes)
    fun epochMs(): Long {
        require(tag == 0x17 || tag == 0x18)
        val time = bytes.decodeToString(throwOnInvalidSequence = true)
        val generalized = if (tag == 0x17) {
            require(time.length == 13)
            val year = time.take(2).toInt()
            (if (year >= 50) "19" else "20") + time
        } else time
        require(generalized.length == 15 && generalized.last() == 'Z')
        fun part(start: Int, end: Int) = generalized.substring(start, end)
        return Instant.parse("${part(0, 4)}-${part(4, 6)}-${part(6, 8)}T${part(8, 10)}:${part(10, 12)}:${part(12, 14)}Z").toEpochMilliseconds()
    }
}

private class DerReader(private val bytes: ByteArray) {
    private var position = 0
    fun hasNext() = position < bytes.size
    fun peek(): Int { require(hasNext()); return bytes[position].toInt() and 255 }
    fun read(expected: Int? = null): DerValue {
        val tag = peek(); position++
        require(expected == null || expected == tag)
        require(position < bytes.size)
        var size = bytes[position++].toInt() and 255
        if (size >= 128) {
            val count = size and 127
            require(count in 1..4 && count <= bytes.size - position)
            var length = 0L
            repeat(count) { length = length * 256 + (bytes[position++].toInt() and 255) }
            require(length <= Int.MAX_VALUE)
            size = length.toInt()
        }
        require(size <= bytes.size - position)
        return DerValue(tag, bytes.copyOfRange(position, position + size)).also { position += size }
    }
}
