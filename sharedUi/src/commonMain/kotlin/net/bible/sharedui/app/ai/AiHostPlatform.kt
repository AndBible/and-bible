@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app.ai

import net.bible.service.llm.agent.RawLlmLog
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedui.app.ports.DocumentRef

/** Sentinel identifying the "Custom..." entry in the AI language picker. */
@kotlin.native.HiddenFromObjC
const val AI_CUSTOM_LANGUAGE_TAG = "\u0000custom"

/**
 * Sends an AI bug report (the log attached, the question and the mail chooser included). Host-scoped: the Android
 * implementation needs the Activity.
 */
@kotlin.native.HiddenFromObjC
interface BugReportPlatform {
    /** Reports the stored raw log record [recordId]; does nothing when the record is gone or its model is not supported. */
    suspend fun reportRecord(recordId: String)

    /** Reports the in-memory log of an active session. */
    suspend fun reportRawLog(rawLog: RawLlmLog)
}

/** The content I/O of the AI prompts CSV export and import. Host-scoped: the Android implementation needs the Activity. */
@kotlin.native.HiddenFromObjC
interface PromptFiles {
    /** The CSV text behind [ref]. @throws IllegalArgumentException when it cannot be opened. */
    suspend fun read(ref: DocumentRef): String

    /** Replaces the content behind [ref] with [text]. @throws IllegalArgumentException when it cannot be opened. */
    suspend fun write(ref: DocumentRef, text: String)

    /**
     * Copies the CSV behind [ref] into the add-on modules directory (under its own display name, `prompts.csv` when it
     * has none), registers it as a prompt book and drops the add-on caches.
     */
    suspend fun installAsAddon(ref: DocumentRef)

    /** The file name the export dialog proposes, stamped with the current time. */
    fun suggestedExportName(): String
}

/** What the AI cluster needs from the platform beyond the shared ports. Host-scoped. */
@kotlin.native.HiddenFromObjC
interface AiHostPlatform {
    /** The language picker's options: the app default, one per supported locale, and a trailing [AI_CUSTOM_LANGUAGE_TAG]. */
    fun languageChoices(): List<SettingsItem.Choice>

    /**
     * [epochMs] as `yyyy-MM-dd HH:mm` in the device locale (the raw log screen's title). Deliberately
     * `SimpleDateFormat(Locale.getDefault())` on Android, for exact parity with the old activity code, and not the
     * `DateTimeFormats.pattern` port; do not simplify it.
     */
    fun formatLogTimestamp(epochMs: Long): String

    /** The error dialog with the report and backup actions (`ErrorReportControl.showErrorDialog`). */
    suspend fun showErrorDialog(message: String, cause: Throwable)
}
