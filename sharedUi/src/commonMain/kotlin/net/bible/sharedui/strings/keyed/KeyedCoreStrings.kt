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

package net.bible.sharedui.strings.keyed

import net.bible.sharedcore.platform.CoreStrings

/**
 * [CoreStrings] over a [StringTable]: the same resource keys as `AndroidCoreStrings`, so the two cannot drift apart.
 * Misspelt keys are caught by `KeyedStringsTest` (a missing key answers itself).
 */
class KeyedCoreStrings(private val t: StringTable) : CoreStrings {
    override val namelessTitle: String get() = t.text("nameless")
    override fun processingEpub(title: String): String = t.fmt("processing_epub", title)
    override fun creatingIndexFor(name: String): String = t.fmt("creating_index_for", name)
    override fun syncCategoryContents(category: String): String = t.text(when (category) {
        "READINGPLANS" -> "reading_plans_content"
        "BOOKMARKS" -> "bookmarks_contents"
        "WORKSPACES" -> "workspaces_contents"
        "MYDOCUMENTS" -> "my_documents_contents"
        "AI_SETTINGS" -> "ai_settings_sync_contents"
        "PROGRESS" -> "progress_sync_contents"
        else -> throw IllegalArgumentException("Unknown sync category: $category")
    })

    override val syncIntroduction: String get() = t.text("prefs_sync_introduction_summary1")
    override fun syncDriveIntroduction(discrete: Boolean): String =
        t.fmt("prefs_sync_introduction_summary2", t.text(if (discrete) "app_name_calculator" else "app_name_medium"))
    override fun syncAdapterSummary(adapter: String): String = t.fmt("sync_adapter_summary", adapter)
    override fun syncLastUpdated(time: String): String = t.fmt("last_updated", time)
    override fun syncCloudInfo(megabytes: String): String = t.fmt("cloud_info_summary", megabytes)
    override val documentSyncNothing: String get() = t.text("document_sync_enable_dialog_nothing")
    override fun documentSyncUpload(count: Int): String = t.fmt("cloud_doc_summary_upload", count)
    override fun documentSyncUploadSize(count: Int, size: String): String = t.fmt("cloud_doc_summary_upload_size", count, size)
    override fun documentSyncDownload(count: Int): String = t.fmt("cloud_doc_summary_download", count)
    override fun documentSyncDownloadSize(count: Int, size: String): String = t.fmt("cloud_doc_summary_download_size", count, size)
    override val documentSyncWifiWaiting: String get() = t.text("cloud_doc_wifi_waiting")
    override val storageSpaceWarning: String get() = t.text("storage_space_warning")
    override val errorDownloading: String get() = t.text("error_downloading")
    override val journalDescription: String get() = t.text("journal_description")
    override val studypads: String get() = t.text("studypads")
    override val compareDescription: String get() = t.text("compare_description")
    override val compareAbbreviation: String get() = t.text("compare_abbreviation")
    override val memorizeDescription: String get() = t.text("memorize_description")
    override val memorizeAbbreviation: String get() = t.text("memorize_abbreviation")
    override val multiDescription: String get() = t.text("multi_description")
    override val multiAbbreviation: String get() = t.text("multi_abbreviation")
    override val myNotesDescription: String get() = t.text("my_notes_description")
    override val myNotesAbbreviation: String get() = t.text("my_notes_abbreviation")
    override fun httpStatusText(code: Int): String = t.text(when (code) {
        403 -> "http_forbidden"
        404 -> "http_not_found"
        else -> "http_unknown"
    })
    override fun errorDownloadingStatus(url: String, statusText: String, code: Int): String =
        t.fmt("error_downloading_status", url, statusText, code)
    override val labelAll: String get() = t.text("all")
    override val installZipSuccessful: String get() = t.text("install_zip_successfull")
    override val installZipCanceled: String get() = t.text("install_zip_canceled")
    override val invalidModule: String get() = t.text("invalid_module")
    override val sqliteInvalidFile: String get() = t.text("sqlite_invalid_file")
    override val aiUnknownPrompt: String get() = t.text("ai_unknown_prompt")
    override val errorPageTooLarge: String get() = t.text("error_page_too_large")
    override fun workspaceNumber(n: Int): String = t.fmt("workspace_number", n)
    override val errorOccurred: String get() = t.text("error_occurred")
    override val errorNoContent: String get() = t.text("error_no_content")
    override fun errorKeyNotInDocument(key: String, initials: String): String =
        t.fmt("error_key_not_in_document2", key, initials)
    override val verseShareAdvertise: String get() =
        t.fmt("verse_share_advertise", t.text("app_name_long"))
    override fun somethingWithParenthesis(a: String, b: String): String = t.fmt("something_with_parenthesis", a, b)
    override fun readingPlanDay(day: String): String = t.fmt("rdg_plan_day", day)
    override val sortByAlphabetical: String get() = t.text("sort_by_alphabetical")
    override val sortByBibleBook: String get() = t.text("sort_by_bible_book")
    override fun documentNotInstalled(initials: String): String = t.fmt("document_not_installed", initials)
    override val noIndexedBibleWithStrongsRef: String get() = t.text("no_indexed_bible_with_strongs_ref")
    override val wordNotFoundInDictionaries: String get() = t.text("word_not_found_in_dictionaries")
    override val speakNoBooksAvailable: String get() = t.text("speak_no_books_available")
    override val speakGeneralError: String get() = t.text("speak_general_error")
    override val verseRangeModeDisabled: String get() = t.text("verse_range_mode_disabled")
    override val rewind: String get() = t.text("rewind")
    override val forward: String get() = t.text("forward")
    override val pause: String get() = t.text("pause")
    override val stop: String get() = t.text("stop")
    override val speakStatusStopped: String get() = t.text("speak_status_stopped")
    override fun sleepTimerStarted(minutes: Int): String = t.fmt("sleep_timer_started", minutes)
    override val signInFailed: String get() = t.text("sign_in_failed")
    override val cloudSyncTitle: String get() = t.text("cloud_sync_title")
    override val cloudFetchAndRestoreInitial: String get() = t.text("cloud_fetch_and_restore_initial")
    override val cloudCreateNew: String get() = t.text("cloud_create_new")
    override val cloudDisableSync: String get() = t.text("cloud_disable_sync")
    override val syncCantFetch: String get() = t.text("sync_cant_fetch")
    override val syncUpdateApp: String get() = t.text("sync_update_app")
    override val syncError: String get() = t.text("sync_error")
    override val okay: String get() = t.text("okay")
    override val cancel: String get() = t.text("cancel")
    override val areYouSure: String get() = t.text("are_you_sure")
    override fun overrideBackup(contents: String): String = t.fmt("overrideBackup", contents)
    override fun areYouSureResetLocal(what: String): String = t.fmt("are_you_sure_reset_local", what)
    override fun areYouSureResetCloud(what: String): String = t.fmt("are_you_sure_reset_cloud", what)
    override fun syncDisabling(what: String): String = t.fmt("sync_disabling", what)

    override fun databaseTitle(dbFileName: String): String = t.text(when (dbFileName) {
        "bookmarks.sqlite3" -> "db_bookmarks"
        "readingplans.sqlite3" -> "reading_plans_plural"
        "workspaces.sqlite3" -> "help_workspaces_title"
        "repositories.sqlite3" -> "db_repositories"
        "settings.sqlite3" -> "settings"
        "mydocuments.sqlite3" -> "my_documents_title"
        "ai_settings.sqlite3" -> "ai_settings_sync_title"
        "progress.sqlite3" -> "progress_sync_title"
        else -> throw IllegalStateException("Unknown database file: $dbFileName")
    })
    override val share: String get() = t.text("share")
    override val backupPhoneStorage: String get() = t.text("backup_phone_storage")
    override val sendBackupFile: String get() = t.text("send_backup_file")
    override val backupBackupTitle: String get() = t.text("backup_backup_title")
    override val backupBackupMessage: String get() = t.text("backup_backup_message")
    override val backupSuccess2: String get() = t.text("backup_success2")
    override val pleaseWait: String get() = t.text("please_wait")
    override val restoreBackupSections: String get() = t.text("restore_backup_sections")
    override val backupModulesTitle: String get() = t.text("backup_modules_title")
    override val backupModulesSuccess: String get() = t.text("backup_modules_success")
    override val apkFile: String get() = t.text("apk_file")
    override val backupApp2: String get() = t.text("backup_app2")
    override val disablingSync: String get() = t.text("disabling_sync")
    override val downloadingBackup: String get() = t.text("downloading_backup")
    override val loadingBackup: String get() = t.text("loading_backup")
    override val restoreUnsuccessfull: String get() = t.text("restore_unsuccessfull")
    override val restoreSuccess: String get() = t.text("restore_success")
    override val restoreConfirmation: String get() = t.text("restore_confirmation")
    override val restore: String get() = t.text("restore")
    override val import2: String get() = t.text("import2")
    override val resetDatabaseSuccess: String get() = t.text("reset_database_success")
    override val selectAll: String get() = t.text("select_all")
    override val selectNone: String get() = t.text("select_none")
    override fun backupEmailSubject2(name: String): String = t.fmt("backup_email_subject_2", name)
    override fun backupEmailMessage2(name: String): String = t.fmt("backup_email_message_2", name)
    override fun backupModulesEmailSubject2(name: String): String = t.fmt("backup_modules_email_subject_2", name)
    override fun backupModulesEmailMessage2(name: String, modules: String): String = t.fmt("backup_modules_email_message_2", name, modules)
    override fun overwriteSomething(contents: String): String = t.fmt("overwrite_something", contents)
    override fun askRestoreOrImport(contents: String): String = t.fmt("ask_restore_or_import", contents)
    override fun resetDatabaseConfirm(name: String): String = t.fmt("reset_database_confirm", name)
    override fun backupFileInfo(version: Int, size: String): String = t.fmt("backup_file_info", version, size)
    override fun bookmarksDbStats(firstLabel: String, labels: String, bookmarks: String): String = t.fmt("bookmarks_db_stats", firstLabel, labels, bookmarks)
    override val exportedStudypadsSubject: String get() = t.text("exported_studypads_subject")
    override fun exportedStudypadsMessage(appName: String): String = t.fmt("exported_studypads_message", appName)
    override val sendExportFile: String get() = t.text("send_export_file")
    override val exportDestinationTitle: String get() = t.text("export_destination_title")
    override val exportDestinationMessage: String get() = t.text("export_destination_message")
    override fun searchUnsupportedSyntax(syntax: String): String = t.fmt("search_unsupported_syntax", syntax)
    override val memorizeWordVisibilityEntries: List<String> get() = t.array("memorize_word_visibility_entries")
    override val memorizeWordVisibilityValues: List<String> get() = t.array("memorize_word_visibility_values")
    override val cantChangeWholeVerse: String get() = t.text("cant_change_wholeverse")
    override val wholeVerseTurnedOn: String get() = t.text("whole_verse_turned_on")
    override val help: String get() = t.text("help")
    override val helpMemorizeText: String get() = t.text("help_memorize_text")
    override val helpReadMoreLink: String get() = t.text("help_read_more_link")
    override val verseTip: String get() = t.text("verse_tip")
    override val helpBookmarksText: String get() = t.text("help_bookmarks_text")
    override val watchTutorialVideo: String get() = t.text("watch_tutorial_video")
    override val bookmarksAndMyNotesTitle: String get() = t.text("bookmarks_and_mynotes_title")
}
