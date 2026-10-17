package net.bible.service.common

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication.Companion.application
import okio.source

private const val TAG = "ABBackupManifest"

/** Reads the backup manifest from the ZIP behind [uri]; null when absent or unreadable. */
suspend fun AndBibleBackupManifest.Companion.fromUri(uri: Uri): AndBibleBackupManifest? = withContext(Dispatchers.IO) {
    try {
        val inputStream = application.contentResolver.openInputStream(uri) ?: return@withContext null
        fromZip(inputStream.source())
    } catch (e: Exception) {
        Log.e(TAG, "Error reading backup manifest from URI", e)
        null
    }
}
