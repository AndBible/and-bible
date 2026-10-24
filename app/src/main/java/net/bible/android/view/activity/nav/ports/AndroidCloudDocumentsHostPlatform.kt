package net.bible.android.view.activity.nav.ports

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import net.bible.android.activity.R
import net.bible.sharedui.app.download.CloudDocumentsHostPlatform
import net.bible.sharedui.app.download.CloudDocumentsOverflowItem

/** [CloudDocumentsHostPlatform]: the `R.drawable` painters classic's overflow menu used. */
class AndroidCloudDocumentsHostPlatform : CloudDocumentsHostPlatform {
    @Composable
    override fun OverflowIcon(item: CloudDocumentsOverflowItem) {
        when (item) {
            CloudDocumentsOverflowItem.SYNC_NOW -> Icon(painterResource(R.drawable.ic_sync_white_24dp), contentDescription = null)
            CloudDocumentsOverflowItem.RESCAN -> Icon(painterResource(R.drawable.ic_baseline_refresh_24), contentDescription = null)
            CloudDocumentsOverflowItem.HELP -> Icon(painterResource(R.drawable.ic_help_white_24dp), contentDescription = null)
        }
    }
}
