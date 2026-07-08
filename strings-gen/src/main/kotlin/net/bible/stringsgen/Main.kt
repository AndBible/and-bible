package net.bible.stringsgen

import java.io.File

/**
 * The :strings-gen ENTRY POINT. Reads the REAL project files, runs the parsers + emitter, and writes
 * the two PURE-KOTLIN iOS holder files (`StringsData.kt` + `GeneratedStrings.kt`) into the `--out` dir
 * under the `net.bible.sharedui.strings` package.
 *
 * Invoked by the `:sharedUi:generateIosStrings` Gradle task before any iOS Kotlin compile; its output
 * dir is an `iosMain` source root, so the iOS compile then type-checks the generated holder against
 * the REAL `Strings` interface for all locales.
 *
 * Args (all required, order-independent):
 *   --res <app/src/main/res>                              the Android resource dir (scanned for values* /strings.xml)
 *   --android-strings <…/strings/AndroidStrings.kt>       the R.string-backed mapping (key recovery)
 *   --interface <…/strings/Strings.kt>                    the Strings interface (member set + signatures)
 *   --out <dir>                                           where the two generated .kt files are written
 *
 * JVM-only build-time code (file IO + the JVM-only parsers); its OUTPUT is what crosses into commonMain.
 */
private const val PACKAGE_DIR = "net/bible/sharedui/strings"

fun main(args: Array<String>) {
    val opts = parseArgs(args)
    val resDir = File(opts.getValue("res"))
    val androidStringsFile = File(opts.getValue("android-strings"))
    val interfaceFile = File(opts.getValue("interface"))
    val outDir = File(opts.getValue("out"))

    require(resDir.isDirectory) { "--res is not a directory: $resDir" }
    require(androidStringsFile.isFile) { "--android-strings is not a file: $androidStringsFile" }
    require(interfaceFile.isFile) { "--interface is not a file: $interfaceFile" }

    // 1. Parse every values* /strings.xml into localeTag -> (key -> value). Skip non-locale
    //    configuration qualifiers (screen-size / density / UI-mode / API-version) and any values*
    //    dir without a strings.xml. AndBible has BOTH `values/` and `values-en/` (both map to "en"),
    //    so MERGE rather than replace: `values-en` overrides `values` for the shared "en" tag.
    val localeToKeyValues = LinkedHashMap<String, Map<String, String>>()
    val localeToPlurals = LinkedHashMap<String, Map<String, Map<String, String>>>()
    val localeToArrays = LinkedHashMap<String, Map<String, List<String>>>()
    val dirs = (resDir.listFiles() ?: emptyArray())
        .filter { it.isDirectory && it.name.startsWith("values") }
        .sortedBy { it.name }
    for (dir in dirs) {
        if (isNonLocaleQualifier(dir.name)) continue
        val xml = File(dir, "strings.xml")
        if (!xml.isFile) continue
        val tag = qualifierToTag(dir.name)
        val text = xml.readText()
        // Merge (values-en overrides values for the shared "en" tag; both map to "en") — see [mergeLocale].
        localeToKeyValues[tag] = mergeLocale(localeToKeyValues[tag], parseStringsXml(text))
        localeToPlurals[tag] = (localeToPlurals[tag].orEmpty()) + parsePluralsXml(text)
        localeToArrays[tag] = (localeToArrays[tag].orEmpty()) + parseStringArraysXml(text)
    }

    // 2. Recover each member's R.string key from AndroidStrings.kt.
    val mapping = parseAndroidStringsMapping(androidStringsFile.readText())
    val mappingByName = mapping.associateBy { it.name }

    // 3. Read the Strings interface member set + signatures.
    val interfaceMembers = parseStringsInterface(interfaceFile.readText())

    // 4. Emit + write both files (creating the package dir tree under --out).
    val pkgDir = File(outDir, PACKAGE_DIR)
    pkgDir.mkdirs()
    File(pkgDir, "StringsData.kt").writeText(emitStringsData(localeToKeyValues, localeToPlurals, localeToArrays))
    File(pkgDir, "GeneratedStrings.kt").writeText(emitGeneratedStrings(interfaceMembers, mappingByName))

    // 5. Summary + loud surfacing of any coverage gap (interface member with no mapping, or a mapped
    //    R.string key absent from the base `en` map — a dropped/missing string that would silently
    //    fall back to the key at runtime).
    val baseKeys = localeToKeyValues["en"]?.keys ?: emptySet()
    val basePluralKeys = localeToPlurals["en"]?.keys ?: emptySet()
    val baseArrayKeys = localeToArrays["en"]?.keys ?: emptySet()
    val missingInMapping = interfaceMembers.map { it.name }.filter { it !in mappingByName }
    val missingBaseKeys = LinkedHashSet<String>()
    for (member in interfaceMembers) {
        val sm = mappingByName[member.name] ?: continue
        when (sm.kind) {
            MemberKind.VAL, MemberKind.FORMAT_FUN -> if (sm.key !in baseKeys) missingBaseKeys += sm.key
            MemberKind.PLURAL_FUN -> if (sm.key !in basePluralKeys) missingBaseKeys += sm.key
            MemberKind.ARRAY_VAL -> if (sm.key !in baseArrayKeys) missingBaseKeys += sm.key
        }
    }

    println(
        "generateIosStrings: ${localeToKeyValues.size} locales, " +
            "${interfaceMembers.size} interface members, ${mapping.size} mapped overrides " +
            "(base 'en' has ${baseKeys.size} keys) -> ${pkgDir.absolutePath}"
    )
    if (missingInMapping.isNotEmpty()) {
        System.err.println(
            "generateIosStrings WARNING: ${missingInMapping.size} interface member(s) have NO " +
                "AndroidStrings.kt mapping (the emitter would have errored on these): $missingInMapping"
        )
    }
    if (missingBaseKeys.isNotEmpty()) {
        System.err.println(
            "generateIosStrings WARNING: ${missingBaseKeys.size} mapped R.string key(s) missing from " +
                "the base 'en' strings.xml (runtime falls back to the key): $missingBaseKeys"
        )
    }
}

/** Parses `--name value` flag pairs into a map keyed by the flag name (leading `--` stripped). */
private fun parseArgs(args: Array<String>): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    var i = 0
    while (i < args.size) {
        val a = args[i]
        require(a.startsWith("--")) { "Expected a --flag, got: $a" }
        require(i + 1 < args.size) { "Missing value for flag: $a" }
        out[a.removePrefix("--")] = args[i + 1]
        i += 2
    }
    for (required in listOf("res", "android-strings", "interface", "out")) {
        require(required in out) { "Missing required --$required argument" }
    }
    return out
}
