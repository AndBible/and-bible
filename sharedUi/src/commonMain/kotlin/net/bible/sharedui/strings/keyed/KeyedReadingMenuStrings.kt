/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.strings.keyed

/**
 * The labels of the reading view's overflow ("3-dot") menu and navigation drawer, read from the generated string
 * tables by the resource keys Android's `OptionsMenuStateBuilder` and `DrawerMenuStateBuilder` use. The keys are in
 * [KEYS], pinned against `strings.xml` by `KeyedReadingMenuStringsTest`.
 */
@kotlin.native.HiddenFromObjC
class KeyedReadingMenuStrings(private val t: StringTable) {
    // --- overflow menu ---
    val toggleFullscreen get() = t.text("toggle_fullscreen")
    val nightMode get() = t.text("options_menu_night_mode")
    val switchToWorkspace get() = t.text("switch_to_workspace")
    val tiltToScroll get() = t.text("prefs_tilt_to_scroll_title")
    val reversedSplitMode get() = t.text("reversed_split_mode")
    val windowPinning get() = t.text("window_pinning_menutitle")
    val allTextOptions get() = t.text("all_text_options_window_menutitle")

    // --- drawer ---
    val chooseDocument get() = t.text("chooce_document")
    val search get() = t.text("search")
    val speak get() = t.text("speak")
    val bookmarks get() = t.text("bookmarks")
    val studyPads get() = t.text("studypads")
    val myDocuments get() = t.text("my_documents_title")
    val readingPlan get() = t.text("rdg_plan_title")
    val readingProgress get() = t.text("reading_progress_title")
    val history get() = t.text("history")
    val administration get() = t.text("administration")
    val download get() = t.text("download")
    val backupAndRestore get() = t.text("backup_and_restore")
    val cloudSync get() = t.text("cloud_sync_title")
    val aiSettings get() = t.text("ai_settings")
    val settings get() = t.text("settings")
    val information get() = t.text("information")
    val helpAndTips get() = t.text("help_and_tips")
    val buyDevelopment get() = t.text("buy_development")
    val questions get() = t.text("questions_title")
    val howToContribute get() = t.text("how_to_contribute")
    val appLicence get() = t.text("app_licence_title")
    val contact get() = t.text("contact")
    val tellFriend get() = t.text("tell_friend_title")
    val rateApplication get() = t.text("rate_application")
    val bugReport get() = t.text("send_bug_report_title")
    val appNameMedium get() = t.text("app_name_medium")
    fun versionText(version: String) = t.fmt("version_text", version)

    // --- R3 ---
    val llmActions get() = t.text("llm_actions")

    companion object {
        /** Every resource key this class reads. */
        val KEYS = listOf(
            "toggle_fullscreen", "options_menu_night_mode", "switch_to_workspace", "prefs_tilt_to_scroll_title",
            "reversed_split_mode", "window_pinning_menutitle", "all_text_options_window_menutitle",
            "chooce_document", "search", "speak", "bookmarks", "studypads", "my_documents_title", "rdg_plan_title",
            "reading_progress_title", "history", "administration", "download", "backup_and_restore", "cloud_sync_title",
            "ai_settings", "settings", "information", "help_and_tips", "buy_development", "questions_title",
            "how_to_contribute", "app_licence_title", "contact", "tell_friend_title", "rate_application",
            "send_bug_report_title", "app_name_medium", "version_text",
            // R3
            "llm_actions",
        )
    }
}
