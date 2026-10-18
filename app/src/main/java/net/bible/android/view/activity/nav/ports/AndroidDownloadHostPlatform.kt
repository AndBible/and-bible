package net.bible.android.view.activity.nav.ports

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import net.bible.android.activity.R
import net.bible.service.common.CommonUtils
import net.bible.service.common.FileManager
import net.bible.sharedui.app.download.DownloadHostPlatform
import net.bible.sharedui.app.download.DownloadOverflowItem
import okio.Path
import org.crosswire.jsword.book.Book

/**
 * [DownloadHostPlatform] over the nav host: the download destination's Activity-bound and `:app`-only seams, moved
 * here unchanged from `NavHostComposeActivity`'s download baggage.
 */
class AndroidDownloadHostPlatform(private val activity: AppCompatActivity) : DownloadHostPlatform {
    override fun copyFile(from: Path, to: Path): Boolean = FileManager.copyFile(from.toFile(), to.toFile())

    override suspend fun showAbout(book: Book) = CommonUtils.showAbout(activity, book)

    override suspend fun unlock(book: Book): Boolean = CommonUtils.unlockDocument(activity, book)

    override suspend fun confirmUpgradeWithBookmarks(): Boolean = CommonUtils.documentUpgradeConfirmation(activity)

    @Composable
    override fun OverflowIcon(item: DownloadOverflowItem) {
        when (item) {
            DownloadOverflowItem.ERRORS -> Icon(painterResource(R.drawable.ic_error_outline_black_24dp), contentDescription = null)
            DownloadOverflowItem.INSTALL_ZIP -> Icon(painterResource(R.drawable.ic_unarchive_white_24dp), contentDescription = null)
            // Icons.Filled.Dns (a stack of servers) rather than the "Install zip" unarchive glyph this row used to share.
            DownloadOverflowItem.CUSTOM_REPOSITORIES -> Icon(Icons.Filled.Dns, contentDescription = null)
            DownloadOverflowItem.CLOUD_DOCUMENTS -> Icon(painterResource(R.drawable.ic_syncdb_24dp), contentDescription = null)
        }
    }
}
