package net.bible.sharedui.app.download

import androidx.compose.runtime.Composable
import okio.Path
import org.crosswire.jsword.book.Book

/** The rows of the Download screen's overflow menu that carry a platform icon. */
enum class DownloadOverflowItem { ERRORS, INSTALL_ZIP, CUSTOM_REPOSITORIES, CLOUD_DOCUMENTS }

/**
 * The platform half of the download destination: what [DownloadHost] cannot do in common code because it needs the
 * Activity or `:app`-only code. A host-scoped port, built on first use (see `HostPorts.downloadPlatform`).
 */
interface DownloadHostPlatform {
    /**
     * Copies a finished download over its target, keeping the free-space check (Android: `FileManager.copyFile`).
     * Answers false when the copy did not happen.
     */
    fun copyFile(from: Path, to: Path): Boolean

    /** The document About dialog for a catalogue (not necessarily installed) [book] (`CommonUtils.showAbout`). */
    suspend fun showAbout(book: Book)

    /** Asks for the cipher key until the user gives up or [book] unlocks; true when it unlocked. */
    suspend fun unlock(book: Book): Boolean

    /** The "upgrading this document may orphan its bookmarks" confirmation; true to go on. */
    suspend fun confirmUpgradeWithBookmarks(): Boolean

    /** The icon of an overflow row (Android: the `R.drawable` painters classic used). */
    @Composable
    fun OverflowIcon(item: DownloadOverflowItem)
}
