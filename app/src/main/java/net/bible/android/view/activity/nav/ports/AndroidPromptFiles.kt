package net.bible.android.view.activity.nav.ports

import android.app.Activity
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.SharedConstants
import net.bible.service.common.AndBibleAddons
import net.bible.service.sword.csvprompt.addCsvPromptBook
import net.bible.sharedui.app.ai.PromptFiles
import net.bible.sharedui.app.ports.DocumentRef
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The AI prompts CSV file I/O through the Activity's content resolver (ported from `NavHostComposeActivity`). */
class AndroidPromptFiles(private val activity: Activity) : PromptFiles {
    private val contentResolver get() = activity.contentResolver

    override suspend fun read(ref: DocumentRef): String = withContext(Dispatchers.IO) {
        contentResolver.openInputStream(Uri.parse(ref.uri))?.use { it.readBytes().decodeToString() }
            ?: throw IllegalArgumentException("Could not open input stream")
    }

    override suspend fun write(ref: DocumentRef, text: String) {
        withContext(Dispatchers.IO) {
            contentResolver.openOutputStream(Uri.parse(ref.uri))?.use { it.write(text.encodeToByteArray()) }
                ?: throw IllegalArgumentException("Could not open output stream")
        }
    }

    override suspend fun installAsAddon(ref: DocumentRef) {
        val uri = Uri.parse(ref.uri)
        val displayName = contentResolver.query(uri, null, null, null, null)?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) it.getString(idx) else null
            } else null
        } ?: "prompts.csv"

        withContext(Dispatchers.IO) {
            val outDir = File(SharedConstants.modulesDir, "prompts")
            outDir.mkdirs()
            val outFile = File(outDir, displayName)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
            addCsvPromptBook(outFile.toOkioPath())
            AndBibleAddons.clearCaches()
        }
    }

    override fun suggestedExportName(): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
        return "ai_prompts_$timestamp.csv"
    }
}
