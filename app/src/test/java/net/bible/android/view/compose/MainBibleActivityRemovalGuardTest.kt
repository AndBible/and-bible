package net.bible.android.view.compose

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Slice 8 §5.3: `MainBibleActivity` is deleted. F1: nothing reaches its former nested types through it.
 * (Task F4 adds the file/manifest/layout assertions.)
 */
class MainBibleActivityRemovalGuardTest {

    private val nested = listOf(
        "SystemInsetsChangedEvent", "KeyIsNull", "AgentLogOffsetsUpdated", "SearchSheetOffsetsUpdated",
        "ImePaddingChanged", "FullScreenEvent", "UpdateRestoreWindowButtons", "ConfigurationChanged",
        "MainBibleAfterRestore", "UpdateMainBibleActivityDocuments", "WORKSPACE_CHANGED",
    )

    @Test
    fun nothingReachesTheReadingEventsThroughMainBibleActivity() {
        val pattern = Regex("""MainBibleActivity\.(""" + nested.joinToString("|") + """)\b""")
        val offenders = listOf(File("src/main/java"), File("src/test/java")).flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }
                .filter { f -> f.readLines().any { l -> !l.trimStart().startsWith("*") && !l.trimStart().startsWith("//") && pattern.containsMatchIn(l) } }
                .map { it.name }.toList()
        }.sorted()
        assertEquals(
            "these files still reach a former MainBibleActivity nested type through the class; they live top " +
                "level in net.bible.android.view.activity.page since slice 8 F1 (same names)",
            emptyList<String>(),
            offenders,
        )
    }

    /**
     * F2: every Robolectric test that built `MainBibleActivity` is rehosted on `NavHostComposeActivity` or deleted
     * with its subject (spec §5.3), so Task F4 can delete the class. Scans CODE only -- comments and string literals
     * are stripped first (because `ReadingHostLauncherGuardTest` legitimately names the class inside a
     * string it scans for, and this file's own regex would otherwise match itself), and this file is skipped.
     */
    @Test
    fun noTestBuildsMainBibleActivity() {
        val builds = Regex("""buildActivity\(\s*MainBibleActivity|MainBibleActivity::class""")
        val offenders = File("src/test/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "MainBibleActivityRemovalGuardTest.kt" }
            .filter { f -> builds.containsMatchIn(kotlinCodeOnly(f.readText())) }
            .map { it.name }.toList().sorted()
        assertEquals(
            "these tests still build the class Task F4 deletes -- rehost or delete per Task F2's table",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun theClassItsLayoutAndItsManifestEntryAreGone() {
        ClassicRemovalScan.assertPathsGone(
            listOf(
                "src/main/java/net/bible/android/view/activity/page/MainBibleActivity.kt",
                "src/main/res/layout/main_bible_view.xml",
            ),
            "MainBibleActivity was unreachable since T8b and is deleted in slice 8 F4",
        )
        ClassicRemovalScan.assertNoManifestNames(
            listOf("net.bible.android.view.activity.page.MainBibleActivity"),
            "a manifest still declares the deleted reading Activity",
        )
    }

    /**
     * `BibleApplication.saveStateTag = "MainBibleActivity"` is a SharedPreferences FILE NAME on users' devices
     * (plan Correction 8); it is the one production literal allowed to spell the name.
     */
    @Test
    fun noProductionCodeNamesTheClass() {
        val offenders = File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }
            .flatMap { f ->
                f.readLines().filterNot { l -> l.trimStart().let { it.startsWith("*") || it.startsWith("//") || it.startsWith("/*") } }
                    .filter { l -> Regex("""\bMainBibleActivity\b""").containsMatchIn(l) }
                    .filterNot { l -> l.contains("saveStateTag = \"MainBibleActivity\"") }
                    .map { "${f.name}: ${it.trim()}" }
            }.toList()
        assertEquals("only BibleApplication's prefs-file literal may name it", emptyList<String>(), offenders)
    }

    @Test
    fun theCodeOnlyScanStripsCommentsAndStringsButKeepsCode() {
        val src = "val a = \"MainBibleActivity::class.java\" // MainBibleActivity::class\n" +
            "/* buildActivity(MainBibleActivity */ val t = \"\"\"MainBibleActivity::class\"\"\"\n" +
            "val c = 'x'; val real = MainBibleActivity::class.java"
        val code = kotlinCodeOnly(src)
        assertEquals(1, Regex("MainBibleActivity").findAll(code).count())
        assertEquals(true, code.contains("val real = MainBibleActivity::class.java"))
    }

    /** [src] with `//` and block comments and all string/char literals removed (literal bodies dropped). */
    private fun kotlinCodeOnly(src: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < src.length) {
            when {
                src.startsWith("//", i) -> { while (i < src.length && src[i] != '\n') i++ }
                src.startsWith("/*", i) -> { val e = src.indexOf("*/", i + 2); i = if (e < 0) src.length else e + 2 }
                src.startsWith("\"\"\"", i) -> { val e = src.indexOf("\"\"\"", i + 3); i = if (e < 0) src.length else e + 3; out.append("\"\"") }
                src[i] == '"' || src[i] == '\'' -> {
                    val q = src[i]; i++
                    while (i < src.length && src[i] != q && src[i] != '\n') { if (src[i] == '\\') i++; i++ }
                    i++; out.append("\"\"")
                }
                else -> { out.append(src[i]); i++ }
            }
        }
        return out.toString()
    }
}
