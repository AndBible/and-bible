package net.bible.sharedui.app.download

import androidx.compose.runtime.Composable

/** The rows of the Cloud documents screen's overflow menu that carry a platform icon. */
enum class CloudDocumentsOverflowItem { SYNC_NOW, RESCAN, HELP }

/** The platform half of the cloud documents destination: its overflow icons (Android: `R.drawable` painters). */
interface CloudDocumentsHostPlatform {
    @Composable
    fun OverflowIcon(item: CloudDocumentsOverflowItem)
}
