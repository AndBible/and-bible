package net.bible.service.legacymigration.plist

/** A UUID decoded from an archived `NSUUID`, in lowercase canonical form. */
data class LegacyUuid(val value: String)

/** Decoder for the subset of `NSKeyedArchiver` archives that the legacy Core Data store uses. */
object KeyedArchive {
    private const val MAX_DEPTH = 100

    /**
     * Resolves `$top.root` through `$objects`. Returns `Set<Any?>` for NSSet, `List<Any?>` for NSArray,
     * `String`, `Long`, `Double`, `Boolean`, [LegacyUuid] for NSUUID, or `null` for `$null`.
     */
    fun decode(bytes: ByteArray): Any? {
        val root = BinaryPlist.parse(bytes) as? PlistDict ?: throw LegacyFormatException("archive root is not a dict")
        if ((root.entries["\$archiver"] as? PlistString)?.value != "NSKeyedArchiver") {
            throw LegacyFormatException("not an NSKeyedArchiver archive")
        }
        val objects = (root.entries["\$objects"] as? PlistArray)?.items
            ?: throw LegacyFormatException("archive has no \$objects")
        val top = root.entries["\$top"] as? PlistDict ?: throw LegacyFormatException("archive has no \$top")
        val rootUid = top.entries["root"] as? PlistUid ?: throw LegacyFormatException("archive has no \$top.root")
        return resolve(objects, rootUid.index, 0)
    }

    private fun resolve(objects: List<PlistValue>, index: Int, depth: Int): Any? {
        if (depth > MAX_DEPTH) throw LegacyFormatException("archive nested too deeply")
        val obj = objects.getOrNull(index) ?: throw LegacyFormatException("archive object $index out of range")
        return when (obj) {
            is PlistString -> if (obj.value == "\$null") null else obj.value
            is PlistInt -> obj.value
            is PlistReal -> obj.value
            is PlistBool -> obj.value
            is PlistDict -> resolveObject(objects, obj, depth)
            else -> throw LegacyFormatException("unsupported archived value")
        }
    }

    private fun resolveObject(objects: List<PlistValue>, d: PlistDict, depth: Int): Any? {
        val clsUid = d.entries["\$class"] as? PlistUid ?: throw LegacyFormatException("archived object has no \$class")
        val cls = (objects.getOrNull(clsUid.index) as? PlistDict)?.entries?.get("\$classname") as? PlistString
            ?: throw LegacyFormatException("archived object has bad \$class")
        return when (cls.value) {
            "NSSet", "NSMutableSet" -> members(objects, d, depth).toSet()
            "NSArray", "NSMutableArray" -> members(objects, d, depth)
            "NSUUID" -> {
                val raw = (d.entries["NS.uuidbytes"] as? PlistData)?.bytes
                if (raw == null || raw.size != 16) throw LegacyFormatException("bad NSUUID bytes")
                val hex = raw.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
                LegacyUuid("${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}")
            }
            else -> throw LegacyFormatException("unsupported archived class ${cls.value}")
        }
    }

    private fun members(objects: List<PlistValue>, d: PlistDict, depth: Int): List<Any?> {
        val refs = (d.entries["NS.objects"] as? PlistArray)?.items ?: throw LegacyFormatException("missing NS.objects")
        return refs.map {
            val uid = it as? PlistUid ?: throw LegacyFormatException("NS.objects entry is not a UID")
            resolve(objects, uid.index, depth + 1)
        }
    }
}
