@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app.chooser

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import net.bible.android.control.page.CurrentGeneralBookPage
import net.bible.android.control.page.CurrentMapPage
import net.bible.sharedcore.nav.PassageResult
import net.bible.sharedcore.navigation.DocRow
import net.bible.sharedcore.navigation.GridChoosePassageController
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Key

/** The three rows of the document chooser's overflow menu; the platform supplies each row's icon. */
@kotlin.native.HiddenFromObjC
enum class ChooserOverflowItem { DOWNLOAD, BACKUP, INSTALL_ZIP }

/**
 * The platform half of the chooser cluster: what [ChooserHost] cannot do in common code because it needs the
 * Activity or `:app`-only types. A host-scoped port, built on first use (see `HostPorts.chooserPlatform`).
 */
@kotlin.native.HiddenFromObjC
interface ChooserHostPlatform {
    /** Classic `onBackup()`: the backup-modules intent flow (`BackupControl.backupModulesViaIntent`). */
    fun backupModules()

    /** The general-book chooser's keys (an EPUB offers its table of contents); see `keyChooserKeys`. */
    suspend fun generalBookKeys(page: CurrentGeneralBookPage): List<Key>

    /** The map chooser's keys. */
    fun mapKeys(page: CurrentMapPage): List<Key>

    /**
     * The Book, Chapter, Verse grid's controller (classic `GridChoosePassageComposeActivity.onCreate` in one
     * factory); its host-side logic lives in `:app` (`GridPassageHostSupport`).
     */
    fun gridChoosePassageController(
        scope: CoroutineScope,
        isScripture: Boolean,
        onResult: (PassageResult) -> Unit,
    ): GridChoosePassageController

    /** The Book to [DocRow] mapping, language grouping included (`DocRowMapper`); one mapper per book list. */
    fun docRows(books: List<Book>): List<DocRow>

    /** Classic's `Dialogs.showErrorMsg(R.string.error_occurred, e)`: error dialog with a report button. */
    fun showError(e: Exception)

    /** The icon of an overflow row (Android: the `R.drawable` painters classic used). */
    @Composable
    fun OverflowIcon(item: ChooserOverflowItem)
}
