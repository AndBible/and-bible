package net.bible.service.legacymigration.testsupport

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.Path
import okio.Path.Companion.toOkioPath

/** One unpacked legacy-store fixture: [root] is a fresh temp directory holding the stores, `sidecar.json` and `oracle/`. */
data class FixtureDir(val root: Path, val app: String, val ios: String, val scenario: String)

private const val FIXTURE_BASE = "/legacymigration/fixtures"

/** Unzips `<app>/<ios>/<scenario>.zip` from the test resources into a fresh temp directory. */
fun fixture(app: String, ios: String, scenario: String): FixtureDir {
    val resource = "$FIXTURE_BASE/$app/$ios/$scenario.zip"
    val stream = FixtureDir::class.java.getResourceAsStream(resource)
        ?: error("fixture resource not found: $resource")
    val dir = Files.createTempDirectory("legacy-fixture-$app-$ios-$scenario-").toFile().also { it.deleteOnExit() }
    val canonicalRoot = dir.canonicalFile
    ZipInputStream(stream).use { zip ->
        generateSequence { zip.nextEntry }.forEach { entry ->
            val target = File(dir, entry.name).canonicalFile
            // Fixture zips are untrusted data: refuse entries that would escape the target directory.
            require(target.path.startsWith(canonicalRoot.path + File.separator)) { "unsafe zip entry ${entry.name}" }
            if (entry.isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile.mkdirs()
                target.outputStream().use { zip.copyTo(it) }
            }
        }
    }
    return FixtureDir(dir.toOkioPath(), app, ios, scenario)
}

/** Every fixture listed in `manifest.json`, each unpacked into its own temp directory. */
fun allFixtures(): List<FixtureDir> {
    val text = FixtureDir::class.java.getResourceAsStream("$FIXTURE_BASE/manifest.json")
        ?.use { it.readBytes().decodeToString() } ?: error("fixture manifest not found")
    val entries: JsonArray = Json.parseToJsonElement(text).jsonArray
    return entries.map { element ->
        val o: JsonObject = element.jsonObject
        fixture(
            o.getValue("app").jsonPrimitive.content,
            o.getValue("ios").jsonPrimitive.content,
            o.getValue("scenario").jsonPrimitive.content,
        )
    }
}

/** The manifest's own fingerprint per `app/ios/scenario` (computed by the Swift oracle; a different algorithm than [net.bible.service.legacymigration.schema.modelFingerprint]). */
fun manifestFingerprints(): Map<String, String> {
    val text = FixtureDir::class.java.getResourceAsStream("$FIXTURE_BASE/manifest.json")!!.use { it.readBytes().decodeToString() }
    return Json.parseToJsonElement(text).jsonArray.associate {
        val o = it.jsonObject
        "${o.getValue("app").jsonPrimitive.content}/${o.getValue("ios").jsonPrimitive.content}/${o.getValue("scenario").jsonPrimitive.content}" to
            o.getValue("fingerprint").jsonPrimitive.content
    }
}
