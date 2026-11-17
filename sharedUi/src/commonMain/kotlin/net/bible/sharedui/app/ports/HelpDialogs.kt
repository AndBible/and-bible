@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app.ports

@kotlin.native.HiddenFromObjC
interface HelpDialogs {
    fun show(topic: HelpTopic)

    /** The localized text of a help string resource [key] (`help_nav_text`...); the key itself where a host has no table. */
    fun tipText(key: String): String = key

    /** The app version name for the Help screen's version line; empty where unknown. */
    fun versionName(): String = ""

    /** The app's UI language tag, for language-aware video links. */
    fun appLanguage(): String = "en"
}

/** The help topics lifted call sites use; each lifting task adds its constants. */
@kotlin.native.HiddenFromObjC
enum class HelpTopic {
    /** The reading-progress screen's overflow help. */
    READING_PROGRESS,

    /** The workspace selector's help (`help_workspaces_title`). */
    WORKSPACES,

    /** The cloud documents screen's overflow help (`help_document_sync_text`). */
    DOCUMENT_SYNC,

    /** The AI connection settings overflow help (`help_ai_connection_text`). */
    AI_CONNECTION,

    /** The drawer's "Help & tips": all help items and the version line. */
    HELP_AND_TIPS,

    /** The drawer's licence dialog (license.html). */
    LICENCE,
}
