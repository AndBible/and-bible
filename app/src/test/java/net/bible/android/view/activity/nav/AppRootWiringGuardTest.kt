package net.bible.android.view.activity.nav

import net.bible.android.view.compose.ClassicRemovalScan
import net.bible.test.ProductionSources
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * I2 Stream A Task 2: the single `NavHost` lives in `:sharedUi`'s `AppRoot`, the activity only calls it.
 * A source scan, so it must prove its subject exists before asserting an absence (a scan of a moved
 * file passes vacuously).
 */
class AppRootWiringGuardTest {
    private val code: String by lazy {
        val host = ProductionSources.resolve("src/main/java/net/bible/android/view/activity/nav/NavHostComposeActivity.kt")
        assertTrue(host.exists(), "the nav host source moved; this scan is watching nothing")
        ClassicRemovalScan.codeLinesOf(host.path)
    }

    @Test
    fun activityHasNoNavHostCall() {
        assertTrue(code.contains("setContent"), "scan reads the wrong text: no setContent in the stripped source")
        assertFalse(
            Regex("""(?<![A-Za-z])NavHost\s*\(""").containsMatchIn(code),
            "NavHostComposeActivity composes a NavHost( itself; the NavHost belongs to AppRoot in :sharedUi",
        )
    }

    @Test
    fun activityCallsAppRoot() {
        assertTrue(Regex("""(?<![A-Za-z])AppRoot\s*\(""").containsMatchIn(code), "the activity must call AppRoot(")
    }

    @Test
    fun activityConstructsNoNonReadingNavDeps() {
        // ReadingNavDeps( is allowed: the reading graph is Stream B's slot and stays wired from the activity.
        val deps = listOf(
            "AiNavDeps(", "BookmarkNavDeps(", "ReadingPlanNavDeps(", "SearchNavDeps(", "SettingsNavDeps(",
            "DownloadNavDeps(", "MyDocumentsNavDeps(", "ChooserNavDeps(", "WorkspaceNavDeps(", "BackupNavDeps(",
            "InstallZipNavDeps(", "WelcomeNavDeps(",
        )
        assertTrue(code.contains("ReadingNavDeps("), "scan reads the wrong text: the reading slot's deps are missing")
        val found = deps.filter { Regex("""(?<![A-Za-z])${Regex.escape(it.removeSuffix("("))}\s*\(""").containsMatchIn(code) }
        assertTrue(found.isEmpty(), "NavHostComposeActivity constructs $found; the feature hosts in :sharedUi own them")
    }

    @Test
    fun activityHasNoSessionClasses() {
        val sessions = listOf(
            "class BookmarksSession", "class ManageLabelsSession", "class MyDocumentPagesSession",
            "class MyDocumentsSession", "class DownloadSession", "class CloudDocumentsEntry",
            "class ChooseDictionarySession", "class ChooseDocumentSession",
        )
        val dir = ProductionSources.resolve("src/main/java/net/bible/android/view/activity/nav")
        val files = dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(files.isNotEmpty(), "no .kt files under ${dir.path}; this scan is watching nothing")
        assertTrue(
            files.any { it.name == "NavHostComposeActivity.kt" },
            "the nav package scan does not include the activity; it is watching the wrong directory",
        )
        val found = files.flatMap { f ->
            val text = ClassicRemovalScan.codeLinesOf(f.path)
            sessions.filter { text.contains(it) }.map { "${f.name}: $it" }
        }
        assertTrue(found.isEmpty(), "nav/ declares $found; sessions live in the feature hosts")
    }
}
