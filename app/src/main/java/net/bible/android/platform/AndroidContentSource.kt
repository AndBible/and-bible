package net.bible.android.platform

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import net.bible.service.sword.ContentSource

/** Keeps URI handling and cursor ownership on the Android side of the import port. */
class AndroidContentSource(private val resolver: ContentResolver) : ContentSource {
    override fun openInputStream(uri: String) = resolver.openInputStream(Uri.parse(uri))

    override fun displayName(uri: String): String? =
        resolver.query(Uri.parse(uri), null, null, null, null)?.use {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && index >= 0) it.getString(index) else null
        }

    override fun mimeType(uri: String): String? = resolver.getType(Uri.parse(uri))
}
