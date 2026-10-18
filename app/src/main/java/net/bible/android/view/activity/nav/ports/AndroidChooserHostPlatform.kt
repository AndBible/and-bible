package net.bible.android.view.activity.nav.ports

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.download.DownloadControl
import net.bible.android.control.navigation.NavigationControl
import net.bible.android.control.page.CurrentGeneralBookPage
import net.bible.android.control.page.CurrentMapPage
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.navigation.DocRowMapper
import net.bible.android.view.activity.navigation.buildGridStep
import net.bible.android.view.activity.navigation.initialGridOptions
import net.bible.android.view.activity.navigation.persistGridOptions
import net.bible.android.view.activity.navigation.pickGridBook
import net.bible.android.view.activity.navigation.pickGridChapter
import net.bible.android.view.activity.navigation.genbookmap.keyChooserKeys
import net.bible.android.platform.AndroidBackupHost
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.nav.PassageResult
import net.bible.sharedcore.navigation.DocRow
import net.bible.sharedcore.navigation.GridChoosePassageController
import net.bible.sharedui.app.chooser.ChooserHostPlatform
import net.bible.sharedui.app.chooser.ChooserOverflowItem
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.koin.java.KoinJavaComponent.get

/**
 * [ChooserHostPlatform] over the nav host: the chooser cluster's Activity-bound and `:app`-only seams, moved here
 * unchanged from `NavHostComposeActivity`'s chooser baggage.
 */
class AndroidChooserHostPlatform(private val activity: NavHostComposeActivity) : ChooserHostPlatform {
    private val backupControl: BackupControl get() = get(BackupControl::class.java)
    private val navigationControl: NavigationControl get() = get(NavigationControl::class.java)
    private val windowControl: WindowControl get() = get(WindowControl::class.java)
    private val downloadControl: DownloadControl get() = get(DownloadControl::class.java)

    override fun backupModules() {
        activity.lifecycleScope.launch { backupControl.backupModulesViaIntent(AndroidBackupHost { activity }) }
    }

    override suspend fun generalBookKeys(page: CurrentGeneralBookPage): List<Key> =
        withContext(Dispatchers.IO) { page.keyChooserKeys() }

    override fun mapKeys(page: CurrentMapPage): List<Key> = page.keyChooserKeys()

    /**
     * Classic `GridChoosePassageComposeActivity.onCreate` (`:52-82`) in one factory. The two step fields classic held
     * as Activity state (`selectedBookNo`, `selectedChapter`) are local `var`s of this call, so they belong to the
     * entry that asked for the controller rather than to the host.
     *
     * **`navigateToVerse` is read from the PREF, not from an argument** (design §6.1.1): the extra's single
     * producer moved to the ref-chooser sheet, so what survives is exactly the fallback both remaining callers
     * already got. The `"title"` extra classic also read is deliberately dropped (no producer anywhere in the tree),
     * so the base title is unconditionally `R.string.choosePassageBookName`.
     */
    override fun gridChoosePassageController(
        scope: CoroutineScope,
        isScripture: Boolean,
        onResult: (PassageResult) -> Unit,
    ): GridChoosePassageController {
        val navigateToVerse = CommonUtils.settings.getBoolean("navigate_to_verse_pref", false)
        var selectedBookNo = 0
        var selectedChapter = 1
        val baseTitle = activity.getString(R.string.choosePassageBookName)
        val workspaceName = activity.hostWindowRepository.name
        return GridChoosePassageController(
            scope = scope,
            initialOptions = initialGridOptions(navigationControl, isScripture),
            buildStep = { step, opts ->
                buildGridStep(
                    step, opts, baseTitle, workspaceName, selectedBookNo, selectedChapter,
                    navigationControl, windowControl,
                )
            },
            onPersistOptions = { persistGridOptions(it, navigationControl) },
            onPickBook = { bookNo ->
                selectedBookNo = bookNo
                pickGridBook(bookNo, navigateToVerse, navigationControl) { selectedChapter = it }
            },
            onPickChapter = { chapter ->
                selectedChapter = chapter
                pickGridChapter(chapter, selectedBookNo, navigateToVerse, navigationControl, windowControl)
            },
            onPickVerse = { verse ->
                Verse(
                    navigationControl.versification,
                    BibleBook.values()[selectedBookNo],
                    selectedChapter,
                    verse,
                ).getOsisID()
            },
            // Classic `finishWithVerse` (`:101-107`) with the Intent removed.
            onFinish = { osisId -> onResult(PassageResult(verse = osisId)) },
        )
    }

    override fun docRows(books: List<Book>): List<DocRow> {
        // The Book -> DocRow mapping (language grouping included) lives in DocRowMapper, shared with the reading
        // view's document quick sheet. One mapper per book list.
        val mapper = DocRowMapper(downloadControl, books)
        return books.map { mapper.toDocRow(it) }
    }

    override fun showError(e: Exception) = Dialogs.showErrorMsg(R.string.error_occurred, e)

    @Composable
    override fun OverflowIcon(item: ChooserOverflowItem) {
        val drawable = when (item) {
            ChooserOverflowItem.DOWNLOAD -> R.drawable.ic_file_download_24dp
            ChooserOverflowItem.BACKUP -> R.drawable.ic_backup_black_24dp
            ChooserOverflowItem.INSTALL_ZIP -> R.drawable.ic_unarchive_white_24dp
        }
        Icon(painterResource(drawable), contentDescription = null)
    }
}
