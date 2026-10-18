package net.bible.sharedui.app.bookmark

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.view.activity.bookmark.ManageLabelsContract
import net.bible.android.view.activity.bookmark.ManageLabelsMapper
import net.bible.android.view.activity.bookmark.ManageLabelsWrites
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import net.bible.sharedcore.bookmark.LabelItem
import net.bible.sharedcore.bookmark.ManageLabelsController
import net.bible.sharedcore.bookmark.ManageLabelsRow
import net.bible.sharedcore.bookmark.ManageLabelsService
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import sun.misc.Unsafe
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class BookmarkHostTest {
    private val strings = iosStrings("en")
    private val scope = CoroutineScope(Job())
    private val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as Unsafe

    private object NoIcons : LabelIcons {
        override val iconKeys: List<String?> = listOf(null)
        @Composable override fun LabelIcon(name: String?, tint: Color) = Unit
        @Composable override fun ManageLabelIcon(name: String?, tint: Color, studyPadMode: Boolean) = Unit
        @Composable override fun ShareIcon() = Unit
        @Composable override fun SaveIcon() = Unit
        @Composable override fun ActionIcon(icon: BookmarkActionIcon, contentDescription: String?, modifier: Modifier) = Unit
    }

    private object NoPlatform : BookmarkHostPlatform {
        override fun subscribeBookmarkSync(onChanged: () -> Unit): () -> Unit = {}
        override suspend fun exportBookmarksCsv(bookmarks: List<net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes>) = Unit
        override suspend fun importBookmarksCsv() = Unit
        override fun openInstallZip(onResult: () -> Unit) = Unit
        override fun helpReadMoreUrl(studyPad: Boolean): String = "https://example.test/help"
        override fun documentsRecreation() = BookmarkDocumentsRecreation()
    }

    private val shell = object : HostShell {
        override val hostScope: CoroutineScope = scope
        override fun exitHost() = Unit
        override fun setWindowTitle(title: String) = Unit
        override fun recreateHost() = Unit
        override fun openReading() = Unit
        override fun setHistoryRoute(owner: Any, route: String) = Unit
        override fun clearHistoryRoute(owner: Any) = Unit
        override fun goBackInHistory(): Boolean = false
        override fun navigate(route: String) = Unit
    }

    private val emptyService = object : ManageLabelsService {
        override suspend fun assignableLabels(): List<LabelItem> = emptyList()
        override suspend fun unlabeledLabel(): LabelItem =
            LabelItem("u", "Unlabeled", 0, false, true, true, null, BookmarkDisplayStyle.HIGHLIGHT, null, null)
        override fun recentLabelIds(): List<String> = emptyList()
        override suspend fun overriddenLabelStyles(): Map<String, BookmarkDisplayStyle> = emptyMap()
        override fun randomColorArgb(): Int = 0
        override suspend fun searchStudyPadsByContent(text: String): List<ManageLabelsRow.SearchResult> = emptyList()
        override fun styleTagsVisible(): Boolean = false
        override fun setStyleTagsVisible(visible: Boolean) = Unit
    }

    private fun host() = BookmarkHost(
        shell = shell,
        strings = strings,
        icons = NoIcons,
        studyPadExport = object : StudyPadExportPlatform {
            override suspend fun export(
                labels: List<net.bible.android.database.bookmarks.BookmarkEntities.Label>,
                chooseDestination: suspend () -> net.bible.sharedcore.platform.files.SaveOrShare?,
            ) = Unit
        },
        platform = NoPlatform,
        appSettingsProvider = { error("settings must stay lazy") },
        bookmarksServiceProvider = { error("bookmarksService must stay lazy") },
        bookmarkControlProvider = { error("bookmarkControl must stay lazy") },
        speakControlProvider = { error("speakControl must stay lazy") },
        windowControlProvider = { error("windowControl must stay lazy") },
        manageLabelsServiceProvider = { error("manageLabelsService must stay lazy") },
        labelEditServiceProvider = { error("labelEditService must stay lazy") },
        fakeBookFactoryProvider = { error("fakeBookFactory must stay lazy") },
        appScopeProvider = { error("appScope must stay lazy") },
        orderedLauncherProvider = { error("orderedLauncher must stay lazy") },
        userNotifierProvider = { error("userNotifier must stay lazy") },
        restored = null,
    )

    private fun session(): ManageLabelsSession {
        val data = ManageLabelsContract.ManageLabelsData(mode = ManageLabelsContract.Mode.STUDYPAD)
        val control = unsafe.allocateInstance(BookmarkControl::class.java) as BookmarkControl
        val session = ManageLabelsSession(data, mutableMapOf(), ManageLabelsWrites(control, scope))
        session.controller = ManageLabelsController(
            mode = ManageLabelsMapper.toMode(data.mode),
            service = emptyService,
            scope = scope,
            initialSelected = emptySet(),
            initialAutoAssign = emptySet(),
            initialAutoAssignPrimary = null,
            initialBookmarkPrimary = null,
            highlightLabelId = null,
            onEditLabel = {},
            onSelectStudyPad = { _, _ -> },
            onSave = {},
            onReset = {},
        )
        return session
    }

    /**
     * The regression the overlay block's comment describes: the host's overlays compose once, while the session
     * is still null (a host started on `bookmarks`, the label manager reached from there). The help flag must
     * still be observed, so that a session that appears later and a flipped flag open the dialog.
     */
    @Test
    fun manageLabelsOverlaysReadStateFirst() = runComposeUiTest {
        val host = host()
        assertEquals(null, host.manageLabelsSession)
        setContent {
            AbTheme(darkTheme = false) {
                CompositionLocalProvider(LocalStrings provides strings) { host.Overlays() }
            }
        }
        waitForIdle()
        runOnIdle { host.manageLabelsSession = "payload" to session() }
        runOnIdle { host.manageLabelsHelpOpen = true }
        waitForIdle()
        onNodeWithText(strings.studyPadsHelpText, substring = true).assertExists()
    }

    @Test
    fun theHelpDialogStaysClosedUntilTheFlagIsSet() = runComposeUiTest {
        val host = host()
        setContent {
            AbTheme(darkTheme = false) {
                CompositionLocalProvider(LocalStrings provides strings) { host.Overlays() }
            }
        }
        runOnIdle { host.manageLabelsSession = "payload" to session() }
        waitForIdle()
        assertEquals(0, onAllNodesWithText(strings.studyPadsHelpText, substring = true).fetchSemanticsNodes().size)
    }
}
