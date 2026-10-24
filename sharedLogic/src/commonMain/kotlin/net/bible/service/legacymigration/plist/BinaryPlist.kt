package net.bible.service.legacymigration.plist

/** Thrown when legacy-store data (plist, keyed archive, ...) is not in the expected format. */
class LegacyFormatException(message: String) : Exception(message)

/** A decoded property-list value. */
sealed interface PlistValue
data class PlistString(val value: String) : PlistValue
data class PlistInt(val value: Long) : PlistValue
data class PlistReal(val value: Double) : PlistValue
data class PlistBool(val value: Boolean) : PlistValue
class PlistData(val bytes: ByteArray) : PlistValue {
    override fun equals(other: Any?) = other is PlistData && bytes.contentEquals(other.bytes)
    override fun hashCode() = bytes.contentHashCode()
}
data class PlistDate(val secondsSince2001: Double) : PlistValue
data class PlistArray(val items: List<PlistValue>) : PlistValue
data class PlistDict(val entries: Map<String, PlistValue>) : PlistValue
data class PlistUid(val index: Int) : PlistValue
object PlistNull : PlistValue

/** Parser for Apple's `bplist00` binary property-list format (trailer = last 32 bytes; object table; offset table). */
object BinaryPlist {
    private const val MAX_DEPTH = 200

    fun parse(bytes: ByteArray): PlistValue {
        if (bytes.size < 8 + 32) throw LegacyFormatException("binary plist too short")
        if (bytes.decodeToString(0, 8, throwOnInvalidSequence = false) != "bplist00") {
            throw LegacyFormatException("not a bplist00 file")
        }
        val t = bytes.size - 32
        val offsetSize = bytes[t + 6].toInt() and 0xFF
        val refSize = bytes[t + 7].toInt() and 0xFF
        val numObjects = uint(bytes, t + 8, 8)
        val top = uint(bytes, t + 16, 8)
        val tableOffset = uint(bytes, t + 24, 8)
        if (offsetSize !in 1..8 || refSize !in 1..8) throw LegacyFormatException("bad plist trailer sizes")
        // numObjects is validated against the file size first so the products below cannot overflow.
        if (numObjects <= 0 || numObjects > t / offsetSize) throw LegacyFormatException("bad plist object count")
        if (top < 0 || top >= numObjects) throw LegacyFormatException("plist top object out of range")
        if (tableOffset < 8 || tableOffset > t || tableOffset + numObjects * offsetSize > t) {
            throw LegacyFormatException("plist offset table out of range")
        }
        return Reader(bytes, tableOffset.toInt(), offsetSize, refSize, numObjects).read(top, 0)
    }

    private fun uint(b: ByteArray, pos: Int, n: Int): Long {
        if (pos < 0 || pos + n > b.size) throw LegacyFormatException("plist read beyond end")
        var v = 0L
        for (i in 0 until n) v = (v shl 8) or (b[pos + i].toLong() and 0xFF)
        return v
    }

    private class Reader(
        val b: ByteArray, val dataEnd: Int, val offsetSize: Int, val refSize: Int,
        val numObjects: Long,
    ) {
        /** The offset table starts where the object data ends; every payload read is bounded by [dataEnd]. */
        val tableOffset = dataEnd

        private fun u(pos: Int, n: Int): Long {
            if (pos < 0 || n < 0 || pos.toLong() + n > dataEnd) throw LegacyFormatException("plist object beyond data area")
            return uint(b, pos, n)
        }

        private fun ref(pos: Int, depth: Int): PlistValue = read(uint(b, pos, refSize), depth)

        fun read(index: Long, depth: Int): PlistValue {
            if (depth > MAX_DEPTH) throw LegacyFormatException("plist nested too deeply")
            if (index < 0 || index >= numObjects) throw LegacyFormatException("plist object ref out of range")
            val off = uint(b, (tableOffset + index * offsetSize).toInt(), offsetSize)
            if (off < 8 || off >= dataEnd) throw LegacyFormatException("plist object offset out of range")
            var pos = off.toInt()
            val marker = b[pos++].toInt() and 0xFF
            val hi = marker shr 4
            val lo = marker and 0xF
            return when (hi) {
                0x0 -> when (lo) {
                    0x0 -> PlistNull
                    0x8 -> PlistBool(false)
                    0x9 -> PlistBool(true)
                    else -> throw LegacyFormatException("unsupported plist marker $marker")
                }
                0x1 -> {
                    val n = 1 shl lo
                    if (n > 8) throw LegacyFormatException("unsupported plist int width $n")
                    PlistInt(u(pos, n))
                }
                0x2 -> when (lo) {
                    2 -> PlistReal(Float.fromBits(u(pos, 4).toInt()).toDouble())
                    3 -> PlistReal(Double.fromBits(u(pos, 8)))
                    else -> throw LegacyFormatException("unsupported plist real width")
                }
                0x3 -> {
                    if (lo != 3) throw LegacyFormatException("unsupported plist date marker")
                    PlistDate(Double.fromBits(u(pos, 8)))
                }
                0x4 -> {
                    val (len, p) = length(lo, pos)
                    checkRange(p, len.toLong())
                    PlistData(b.copyOfRange(p, p + len))
                }
                0x5 -> {
                    val (len, p) = length(lo, pos)
                    checkRange(p, len.toLong())
                    PlistString(CharArray(len) { (b[p + it].toInt() and 0xFF).toChar() }.concatToString())
                }
                0x6 -> {
                    val (len, p) = length(lo, pos)
                    checkRange(p, len.toLong() * 2)
                    PlistString(CharArray(len) { u(p + it * 2, 2).toInt().toChar() }.concatToString())
                }
                0x8 -> {
                    if (lo + 1 > 4) throw LegacyFormatException("unsupported plist uid width")
                    PlistUid(u(pos, lo + 1).toInt())
                }
                0xA -> {
                    val (len, p) = length(lo, pos)
                    checkRange(p, len.toLong() * refSize)
                    PlistArray(List(len) { ref(p + it * refSize, depth + 1) })
                }
                0xD -> {
                    val (len, p) = length(lo, pos)
                    checkRange(p, len.toLong() * refSize * 2)
                    val map = LinkedHashMap<String, PlistValue>()
                    for (i in 0 until len) {
                        val k = ref(p + i * refSize, depth + 1)
                        val v = ref(p + (len + i) * refSize, depth + 1)
                        if (k !is PlistString) throw LegacyFormatException("plist dict key is not a string")
                        map[k.value] = v
                    }
                    PlistDict(map)
                }
                else -> throw LegacyFormatException("unsupported plist marker $marker")
            }
        }

        /** Decodes the length nibble [lo]; 0xF means an int object follows. Returns (length, position of the payload). */
        private fun length(lo: Int, pos: Int): Pair<Int, Int> {
            if (lo != 0xF) return lo to pos
            if (pos >= dataEnd) throw LegacyFormatException("plist read beyond end")
            val m = b[pos].toInt() and 0xFF
            if (m shr 4 != 0x1) throw LegacyFormatException("bad plist length marker")
            val n = 1 shl (m and 0xF)
            if (n > 4) throw LegacyFormatException("plist length too large")
            val len = u(pos + 1, n)
            if (len > Int.MAX_VALUE / 4) throw LegacyFormatException("plist length too large")
            return len.toInt() to (pos + 1 + n)
        }

        private fun checkRange(p: Int, n: Long) {
            if (n < 0 || p.toLong() + n > dataEnd) throw LegacyFormatException("plist object beyond data area")
        }
    }
}
