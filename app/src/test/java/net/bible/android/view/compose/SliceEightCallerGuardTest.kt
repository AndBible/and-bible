package net.bible.android.view.compose

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slice 8 §3.5: no production caller reaches the seven screens through their Activities any more --
 * neither `ScreenLauncher.intentFor(…, Screen.X)` for the seven nor the text-settings Activity's own
 * `intentFor`/`intentForColors`/`intentForDetachedWorkspace`. They navigate the graph with a route built
 * from arguments.
 *
 * Excluded by FILE, with the reason: `ScreenLauncher.kt` (the arm table itself), `MainBibleActivity.kt`
 * (dead since T8b, deleted in Task F5) and the seven Activities' own files (deleted in Task F6; the
 * selector's launch of the editor is the Activity PAIR's, whose graph twin already exists).
 *
 * [ALLOWLIST] is a RATCHET: each entry is a not-yet-repointed call site with the task that repoints it.
 * An entry that no longer matches a real offender fails ([everyAllowlistEntryIsStillAnOffender]), so the
 * list can only shrink, and Task B6 leaves it empty.
 */
class SliceEightCallerGuardTest {

    private val screens = listOf(
        "ChooseDocument", "GridChoosePassageBook", "ChooseDictionaryWord", "ChooseGeneralBookKey",
        "ChooseMapKey", "WorkspaceSelector", "TextDisplaySettings",
    )

    private val excludedFiles = setOf(
        "ScreenLauncher.kt", "MainBibleActivity.kt",
        "ChooseDocumentComposeActivity.kt", "GridChoosePassageComposeActivity.kt",
        "ChooseDictionaryWordComposeActivity.kt", "ChooseGeneralBookKeyComposeActivity.kt",
        "ChooseMapKeyComposeActivity.kt", "WorkspaceSelectorComposeActivity.kt",
        "TextDisplaySettingsComposeActivity.kt",
    )

    private val launch = Regex(
        """ScreenLauncher\.intentFor\([^)]*Screen\.(""" + screens.joinToString("|") + """)\b|""" +
            """TextDisplaySettingsComposeActivity\.intentFor(Colors|DetachedWorkspace)?\("""
    )

    /** `File.name:line` of every offending line, comment lines excluded. */
    private fun offenders(): List<String> =
        File("src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name !in excludedFiles }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filterNot { (_, l) -> l.trimStart().let { it.startsWith("//") || it.startsWith("*") || it.startsWith("/*") } }
                    .filter { (_, l) -> launch.containsMatchIn(l) }
                    .map { (i, _) -> "${file.name}:${i + 1}" }
            }
            .toList()

    @Test
    fun theScanSeesTheTree() {
        assertTrue("src/main/java not found", File("src/main/java").isDirectory)
    }

    @Test
    fun noCallerReachesTheSevenScreensThroughTheirActivities() {
        val live = offenders().filterNot { site -> ALLOWLIST.keys.any { site.startsWith(it) } }
        assertEquals(
            "these callers still launch one of the seven screens' Activities. Build the route from its " +
                "arguments and launch NavHostComposeActivity.intentFor(ctx, NavRoutes.x(args)) -- never " +
                "putExtra (NavHostRoutingGuardTest.migratedScreenArgumentIsNeverDroppedByAPutExtra)",
            emptyList<String>(),
            live,
        )
    }

    @Test
    fun everyAllowlistEntryIsStillAnOffender() {
        val stale = ALLOWLIST.keys.filterNot { key -> offenders().any { it.startsWith(key) } }
        assertEquals("these allowlist entries name call sites that are already repointed -- delete them", emptyList<String>(), stale)
    }

    private companion object {
        /** `File.name:` prefix -> the task that repoints it. Keyed by FILE, because lines shift. */
        val ALLOWLIST: Map<String, String> = emptyMap()
    }
}
