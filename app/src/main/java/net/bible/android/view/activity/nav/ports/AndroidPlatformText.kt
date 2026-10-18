package net.bible.android.view.activity.nav.ports

import android.content.Context
import android.text.format.Formatter
import net.bible.android.activity.R
import net.bible.sharedui.app.ports.PlatformText
import net.bible.sharedui.app.ports.PluralKey

class AndroidPlatformText(private val context: Context) : PlatformText {
    override fun formatFileSize(bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

    override fun plural(key: PluralKey, count: Int, vararg args: Any): String {
        val id = when (key) {
            PluralKey.CLOUD_DOC_SYNC_NOW_COUNT -> R.plurals.cloud_doc_sync_now_count
            PluralKey.CLOUD_DOC_SYNC_NOW_COUNT_SIZE -> R.plurals.cloud_doc_sync_now_count_size
            PluralKey.CLOUD_DOC_BULK_BLOCK_CONFIRM -> R.plurals.cloud_doc_bulk_block_confirm
            PluralKey.CLOUD_DOC_BULK_REMOVE_CONFIRM -> R.plurals.cloud_doc_bulk_remove_all_confirm
            PluralKey.CLOUD_DOC_BULK_PURGE_CONFIRM -> R.plurals.cloud_doc_bulk_purge_confirm
        }
        return context.resources.getQuantityString(id, count, *args)
    }
}
