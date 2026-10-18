package net.bible.service.legacymigration.schema

import androidx.sqlite.SQLiteConnection
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import net.bible.service.legacymigration.plist.BinaryPlist
import net.bible.service.legacymigration.plist.LegacyFormatException
import net.bible.service.legacymigration.plist.PlistArray
import net.bible.service.legacymigration.plist.PlistDict
import net.bible.service.legacymigration.plist.PlistInt
import net.bible.service.legacymigration.plist.PlistString
import net.bible.service.legacymigration.plist.PlistUid
import net.bible.service.legacymigration.plist.PlistValue
import net.bible.service.legacymigration.testsupport.FixtureDir
import net.bible.service.legacymigration.testsupport.columnNames
import net.bible.service.legacymigration.testsupport.fingerprint
import net.bible.service.legacymigration.testsupport.openStore
import net.bible.service.legacymigration.testsupport.query
import net.bible.service.legacymigration.testsupport.storeFiles
import net.bible.service.legacymigration.testsupport.tableNames

/**
 * Generates the committed Kotlin schema maps (`schema/generated/SchemaMap_<12 hex>.kt` and `schema/KnownSchemaMaps.kt`)
 * from the legacy-store fixtures. It never ships; the reader only sees the generated sources.
 *
 * Per store it reads the compiled model from `Z_MODELCACHE` (raw deflate of an `NSKeyedArchiver` plist), walks the
 * `NSEntityDescription`s and derives each column the way SwiftData/Core Data names it:
 *
 *  - table `Z<ENTITY>`; attribute column `Z<LEAFNAME>` upper-cased; composite (Codable struct) attributes are
 *    flattened depth-first into the owning entity's table, the property path joining the names with `.`;
 *  - a leaf name occurring more than once in one entity gets `1`, `2`, ... appended to the later occurrences, in
 *    depth-first order of the composite elements (first occurrence unsuffixed). Top-level properties are visited
 *    sorted by name (the archive stores them in an unordered dictionary); a collision between two different top-level
 *    properties is rejected as ambiguous, so nothing is guessed;
 *    Note that the table's physical column order is NOT property order: Core Data groups the columns by type, and
 *    within each type keeps model order (top-level properties alphabetical, composite leaves depth-first). That
 *    position is what proves the suffix numbering (see SchemaMapGeneratorTest.suffixedGroupsFollowTheirParentComposite);
 *  - to-one relationships (`NSMaxCount == 1`) are a foreign key column `Z<RELATIONSHIPNAME>`; to-many have no column.
 *
 * Every derived column is checked against `sqlite_master`/`pragma_table_info`; a missing one fails the generation.
 * The blob encoding of binary attributes is taken from the stored bytes (`bplist00` = keyed archive, `[` or `{` = JSON).
 */
object SchemaMapGenerator {
    private const val PACKAGE = "net.bible.service.legacymigration.schema"
    private val META_TABLES = setOf("Z_PRIMARYKEY", "Z_METADATA", "Z_MODELCACHE")

    private class Leaf(val path: String, val topLevel: String, val type: Int, val transformer: String?, val column: String)
    private class Rel(val name: String, val column: String)
    private class ModelEntity(val name: String, val leaves: List<Leaf>, val toOne: List<Rel>)

    /** Returns the generated files keyed by path relative to the `schema` source directory. */
    fun generate(fixtures: List<FixtureDir>): Map<String, String> {
        val models = sortedMapOf<String, List<ModelEntity>>()
        val entityNumbers = mutableMapOf<String, Map<String, Int>>()
        val samples = mutableMapOf<String, MutableSet<String>>() // fingerprint -> "Entity|column|kind"
        for (fixture in fixtures) for (store in fixture.storeFiles()) {
            openStore(store).use { c ->
                val fp = c.fingerprint()
                val model = readModel(c)
                val numbers = c.query("SELECT Z_NAME, Z_ENT FROM Z_PRIMARYKEY") { it.getText(0) to it.getLong(1).toInt() }.toMap()
                verifyAgainstStore(c, model, "${fixture.app}/${fixture.ios}/${fixture.scenario}/${store.name}")
                val known = models[fp]
                if (known == null) {
                    models[fp] = model
                    entityNumbers[fp] = numbers
                } else {
                    check(describe(known) == describe(model) && entityNumbers[fp] == numbers) { "stores with fingerprint $fp differ" }
                }
                sampleBlobs(c, model, samples.getOrPut(fp) { mutableSetOf() })
            }
        }
        val maps = models.map { (fp, model) -> buildMap(fp, model, entityNumbers.getValue(fp), samples.getValue(fp)) }
        val out = sortedMapOf<String, String>()
        for (map in maps) out["generated/${objectName(map)}.kt"] = emit(map)
        out["KnownSchemaMaps.kt"] = emitKnown(maps)
        return out
    }

    /** Writes [files] (as returned by [generate]) below [schemaDir], removing stale generated files. */
    fun writeTo(schemaDir: File, files: Map<String, String>) {
        File(schemaDir, "generated").listFiles().orEmpty().filter { it.name.startsWith("SchemaMap_") }.forEach { it.delete() }
        for ((relative, text) in files) File(schemaDir, relative).also { it.parentFile.mkdirs() }.writeText(text)
    }

    // ---- model reading ----

    private fun readModel(c: SQLiteConnection): List<ModelEntity> {
        val blob = c.query("SELECT Z_CONTENT FROM Z_MODELCACHE") { it.getBlob(0) }.single()
        val raw = InflaterInputStream(ByteArrayInputStream(blob), Inflater(true)).use { it.readBytes() }
        val root = BinaryPlist.parse(raw) as? PlistDict ?: throw LegacyFormatException("model cache is not a dictionary")
        val objects = (root.entries["\$objects"] as? PlistArray)?.items ?: throw LegacyFormatException("model cache has no \$objects")
        fun deref(v: PlistValue?): PlistValue? = if (v is PlistUid) objects[v.index] else v
        fun dict(v: PlistValue?) = deref(v) as? PlistDict
        fun str(v: PlistValue?): String? = (deref(v) as? PlistString)?.value?.takeUnless { it == "\$null" }
        fun className(d: PlistDict) = str(dict(d.entries["\$class"])?.entries?.get("\$classname"))
        fun elements(v: PlistValue?): List<PlistDict> =
            ((dict(v)?.entries?.get("NS.objects") as? PlistArray)?.items ?: emptyList()).mapNotNull { dict(it) }

        fun leavesOf(prop: PlistDict, path: List<String>): List<Pair<List<String>, PlistDict>> {
            val name = str(prop.entries["NSPropertyName"]) ?: throw LegacyFormatException("property without a name")
            return if (className(prop) == "NSCompositeAttributeDescription") {
                elements(prop.entries["NSCompositeElements"]).flatMap { leavesOf(it, path + name) }
            } else {
                listOf((path + name) to prop)
            }
        }

        val entities = objects.filterIsInstance<PlistDict>().filter { className(it) == "NSEntityDescription" }
        return entities.map { entity ->
            val name = str(entity.entries["NSEntityName"]) ?: throw LegacyFormatException("entity without a name")
            val props = elements(entity.entries["NSProperties"]).sortedBy { str(it.entries["NSPropertyName"]) }
            val used = mutableMapOf<String, String>() // base column -> top-level property that first used it
            val counts = mutableMapOf<String, Int>()
            fun columnFor(leafName: String, topLevel: String): String {
                val base = "Z" + leafName.uppercase()
                val owner = used.getOrPut(base) { topLevel }
                val n = counts.getOrElse(base) { 0 }
                counts[base] = n + 1
                if (n > 0 && owner != topLevel) {
                    error("$name: column $base is claimed by top-level properties $owner and $topLevel; order is ambiguous")
                }
                return if (n == 0) base else base + n
            }
            val leaves = mutableListOf<Leaf>()
            val rels = mutableListOf<Rel>()
            for (prop in props) {
                val top = str(prop.entries["NSPropertyName"])!!
                when (className(prop)) {
                    "NSRelationshipDescription" -> {
                        if ((prop.entries["NSMaxCount"] as? PlistInt)?.value == 1L) rels += Rel(top, columnFor(top, top))
                    }
                    "NSAttributeDescription", "NSCompositeAttributeDescription" -> for ((path, leaf) in leavesOf(prop, emptyList())) {
                        val type = (leaf.entries["NSAttributeType"] as? PlistInt)?.value?.toInt()
                            ?: throw LegacyFormatException("$name.$path has no attribute type")
                        leaves += Leaf(
                            path.joinToString("."), top, type,
                            str(leaf.entries["NSValueTransformerName"]), columnFor(path.last(), top),
                        )
                    }
                    else -> throw LegacyFormatException("$name.$top: unsupported property class ${className(prop)}")
                }
            }
            ModelEntity(name, leaves, rels)
        }.sortedBy { it.name }
    }

    private fun describe(model: List<ModelEntity>) = model.map { e ->
        Triple(e.name, e.leaves.map { listOf(it.path, it.type, it.transformer, it.column) }, e.toOne.map { it.name to it.column })
    }

    private fun verifyAgainstStore(c: SQLiteConnection, model: List<ModelEntity>, label: String) {
        val tables = c.tableNames().toSet()
        for (e in model) {
            val table = "Z" + e.name.uppercase()
            check(table in tables) { "$label: table $table of entity ${e.name} not in sqlite_master" }
            val actual = c.columnNames(table).toSet()
            val derived = e.leaves.map { it.column to it.path } + e.toOne.map { it.column to it.name }
            for ((column, what) in derived) {
                check(column in actual) {
                    "$label: entity ${e.name}: derived column $column for $what does not exist; candidates: " +
                        actual.filter { it.startsWith(column.take(4)) }.sorted()
                }
            }
        }
        val entityTables = model.map { "Z" + it.name.uppercase() }.toSet()
        val extra = tables.filter { it.startsWith("Z") && it !in META_TABLES && it !in entityTables }
        check(extra.isEmpty()) { "$label: tables without an entity in the model: $extra" }
    }

    // ---- blob sniffing ----

    private fun sampleBlobs(c: SQLiteConnection, model: List<ModelEntity>, into: MutableSet<String>) {
        for (e in model) for (leaf in e.leaves.filter { it.type == 1000 || it.type == 1800 }) {
            val rows = c.query("SELECT ${leaf.column} FROM Z${e.name.uppercase()} WHERE ${leaf.column} IS NOT NULL") { it.getBlob(0) }
            for (bytes in rows) into += "${e.name}|${leaf.column}|${kindOf(bytes) ?: continue}"
        }
    }

    private fun kindOf(bytes: ByteArray): String? = when {
        bytes.isEmpty() -> null
        bytes.size >= 8 && bytes.decodeToString(0, 8) == "bplist00" -> "KEYED_ARCHIVE"
        bytes[0] == '['.code.toByte() || bytes[0] == '{'.code.toByte() -> "JSON"
        else -> "BYTES"
    }

    // ---- mapping ----

    private fun encodingOf(entity: String, leaf: Leaf, samples: Set<String>): ValueEncoding = when (leaf.type) {
        100, 200, 300 -> ValueEncoding.INTEGER
        500, 600 -> ValueEncoding.REAL
        700 -> ValueEncoding.TEXT
        800 -> ValueEncoding.BOOL
        900 -> ValueEncoding.DATE
        1100 -> ValueEncoding.UUID
        1000, 1800 -> {
            val kinds = samples.filter { it.startsWith("$entity|${leaf.column}|") }.map { it.substringAfterLast('|') }.toSet()
            check(kinds.size <= 1) { "$entity.${leaf.path}: mixed blob encodings $kinds" }
            val sniffed = kinds.singleOrNull()?.let { ValueEncoding.valueOf(it) }
            if (leaf.type == 1800) {
                check(leaf.transformer == "NSSecureUnarchiveFromData") { "$entity.${leaf.path}: unknown transformer ${leaf.transformer}" }
                check(sniffed == null || sniffed == ValueEncoding.KEYED_ARCHIVE) { "$entity.${leaf.path}: transformable stored as $sniffed" }
                ValueEncoding.KEYED_ARCHIVE
            } else {
                // No stored value in any fixture: nothing proves the structure, so the reader must treat it as opaque.
                sniffed ?: ValueEncoding.BYTES
            }
        }
        else -> error("$entity.${leaf.path}: unsupported NSAttributeType ${leaf.type}")
    }

    private fun buildMap(fp: String, model: List<ModelEntity>, numbers: Map<String, Int>, samples: Set<String>) = SchemaMap(
        fp,
        model.associate { e ->
            e.name to EntityMap(
                name = e.name,
                table = "Z" + e.name.uppercase(),
                entityNumber = numbers[e.name] ?: error("no Z_PRIMARYKEY entry for ${e.name}"),
                columns = e.leaves.associate { it.path to ColumnMap(it.column, encodingOf(e.name, it, samples)) },
                toOne = e.toOne.associate { it.name to it.column },
            )
        },
    )

    // ---- Kotlin source emission ----

    private fun objectName(map: SchemaMap) = "SchemaMap_" + map.fingerprint.take(12)

    private fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""

    private fun emit(map: SchemaMap): String = buildString {
        appendLine("// GENERATED by SchemaMapGenerator (jvmTest). DO NOT EDIT; regenerate with REGENERATE=true, see SchemaMapGeneratorTest.")
        appendLine("package $PACKAGE.generated")
        appendLine()
        appendLine("import $PACKAGE.ColumnMap")
        appendLine("import $PACKAGE.EntityMap")
        appendLine("import $PACKAGE.SchemaMap")
        appendLine("import $PACKAGE.ValueEncoding")
        appendLine()
        appendLine("/** Schema map of the SwiftData model with fingerprint `${map.fingerprint}`. */")
        appendLine("internal object ${objectName(map)} {")
        appendLine("    val map: SchemaMap = SchemaMap(")
        appendLine("        fingerprint = ${q(map.fingerprint)},")
        appendLine("        entities = listOf(")
        map.entities.keys.forEach { appendLine("            ${fn(it)}(),") }
        appendLine("        ).associateBy { it.name },")
        appendLine("    )")
        for (e in map.entities.values) {
            appendLine()
            appendLine("    private fun ${fn(e.name)}() = EntityMap(")
            appendLine("        name = ${q(e.name)},")
            appendLine("        table = ${q(e.table)},")
            appendLine("        entityNumber = ${e.entityNumber},")
            appendLine("        columns = mapOf(")
            e.columns.forEach { (path, c) -> appendLine("            ${q(path)} to ColumnMap(${q(c.column)}, ValueEncoding.${c.encoding}),") }
            appendLine("        ),")
            appendLine("        toOne = mapOf(")
            e.toOne.forEach { (name, column) -> appendLine("            ${q(name)} to ${q(column)},") }
            appendLine("        ),")
            appendLine("    )")
        }
        appendLine("}")
    }

    private fun fn(entityName: String) = "entity" + entityName

    private fun emitKnown(maps: List<SchemaMap>): String = buildString {
        appendLine("// GENERATED by SchemaMapGenerator (jvmTest). DO NOT EDIT; regenerate with REGENERATE=true, see SchemaMapGeneratorTest.")
        appendLine("package $PACKAGE")
        appendLine()
        appendLine("import $PACKAGE.generated.*")
        appendLine()
        appendLine("/** Every SwiftData model of the native Swift app this build can read, by [modelFingerprint]. */")
        appendLine("object KnownSchemaMaps {")
        appendLine("    val all: Map<String, SchemaMap> = listOf(")
        maps.sortedBy { it.fingerprint }.forEach { appendLine("        ${objectName(it)}.map,") }
        appendLine("    ).associateBy { it.fingerprint }")
        appendLine()
        appendLine("    fun forFingerprint(fp: String): SchemaMap? = all[fp]")
        appendLine("}")
    }
}
