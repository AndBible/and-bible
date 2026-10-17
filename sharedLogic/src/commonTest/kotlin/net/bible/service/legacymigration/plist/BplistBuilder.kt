package net.bible.service.legacymigration.plist

/** Test-only encoder producing `bplist00` bytes from [PlistValue] trees (one-byte object refs, up to 255 objects). */
internal object BplistBuilder {
    fun build(root: PlistValue, minOffsetSize: Int = 1): ByteArray {
        val encoded = ArrayList<ByteArray>()
        val index = encode(root, encoded)
        val out = ArrayList<Byte>()
        "bplist00".forEach { out.add(it.code.toByte()) }
        val offsets = ArrayList<Long>()
        for (obj in encoded) { offsets.add(out.size.toLong()); obj.forEach { out.add(it) } }
        val tableOffset = out.size.toLong()
        val offsetSize = if (tableOffset > 0xFFFF) maxOf(minOffsetSize, 3) else if (tableOffset > 0xFF) maxOf(minOffsetSize, 2) else minOffsetSize
        for (o in offsets) out.addAll(be(o, offsetSize))
        out.addAll(List(6) { 0 })
        out.add(offsetSize.toByte()); out.add(1)
        out.addAll(be(offsets.size.toLong(), 8)); out.addAll(be(index.toLong(), 8)); out.addAll(be(tableOffset, 8))
        return out.toByteArray()
    }

    fun be(v: Long, n: Int): List<Byte> = List(n) { i -> (v shr (8 * (n - 1 - i))).toByte() }

    private fun header(marker: Int, len: Int): ByteArray =
        if (len < 15) byteArrayOf((marker or len).toByte())
        else byteArrayOf((marker or 0xF).toByte(), 0x10, len.toByte())

    /** Encodes [v] (children first is not required; ids are assigned in append order, root ends up last-appended slot reserved first). */
    private fun encode(v: PlistValue, objs: MutableList<ByteArray>): Int {
        val slot = objs.size
        objs.add(ByteArray(0))
        objs[slot] = when (v) {
            PlistNull -> byteArrayOf(0x00)
            is PlistBool -> byteArrayOf(if (v.value) 0x09 else 0x08)
            is PlistInt -> byteArrayOf(0x13) + be(v.value, 8).toByteArray()
            is PlistReal -> byteArrayOf(0x23) + be(v.value.toRawBits(), 8).toByteArray()
            is PlistDate -> byteArrayOf(0x33) + be(v.secondsSince2001.toRawBits(), 8).toByteArray()
            is PlistData -> header(0x40, v.bytes.size) + v.bytes
            is PlistString -> header(0x50, v.value.length) + v.value.encodeToByteArray()
            is PlistUid -> byteArrayOf(0x80.toByte()) + byteArrayOf(v.index.toByte())
            is PlistArray -> {
                val refs = v.items.map { encode(it, objs).toByte() }
                header(0xA0, refs.size) + refs.toByteArray()
            }
            is PlistDict -> {
                val keys = v.entries.keys.map { encode(PlistString(it), objs).toByte() }
                val vals = v.entries.values.map { encode(it, objs).toByte() }
                header(0xD0, keys.size) + keys.toByteArray() + vals.toByteArray()
            }
        }
        return slot
    }
}
