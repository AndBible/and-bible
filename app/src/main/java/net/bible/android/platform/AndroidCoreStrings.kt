package net.bible.android.platform

import android.content.Context
import net.bible.android.activity.R
import net.bible.sharedcore.platform.CoreStrings

/** Reads the resource on every access so locale changes take effect. */
class AndroidCoreStrings(private val context: Context) : CoreStrings {
    override val namelessTitle: String get() = context.getString(R.string.nameless)
    override fun processingEpub(title: String): String = context.getString(R.string.processing_epub, title)
    override fun creatingIndexFor(name: String): String = context.getString(R.string.creating_index_for, name)
    override fun syncCategoryContents(category: String): String = context.getString(when (category) {
        "READINGPLANS" -> R.string.reading_plans_content
        "BOOKMARKS" -> R.string.bookmarks_contents
        "WORKSPACES" -> R.string.workspaces_contents
        "MYDOCUMENTS" -> R.string.my_documents_contents
        "AI_SETTINGS" -> R.string.ai_settings_sync_contents
        "PROGRESS" -> R.string.progress_sync_contents
        else -> throw IllegalArgumentException("Unknown sync category: $category")
    })

    override val syncIntroduction: String get() = context.getString(R.string.prefs_sync_introduction_summary1)
    override fun syncDriveIntroduction(discrete: Boolean): String =
        context.getString(R.string.prefs_sync_introduction_summary2, context.getString(syncSummaryAppName(discrete)))
    override fun syncAdapterSummary(adapter: String): String = context.getString(R.string.sync_adapter_summary, adapter)
    override fun syncLastUpdated(time: String): String = context.getString(R.string.last_updated, time)
    override fun syncCloudInfo(megabytes: String): String = context.getString(R.string.cloud_info_summary, megabytes)
    override val documentSyncNothing: String get() = context.getString(R.string.document_sync_enable_dialog_nothing)
    override fun documentSyncUpload(count: Int): String = context.getString(R.string.cloud_doc_summary_upload, count)
    override fun documentSyncUploadSize(count: Int, size: String): String = context.getString(R.string.cloud_doc_summary_upload_size, count, size)
    override fun documentSyncDownload(count: Int): String = context.getString(R.string.cloud_doc_summary_download, count)
    override fun documentSyncDownloadSize(count: Int, size: String): String = context.getString(R.string.cloud_doc_summary_download_size, count, size)
    override val documentSyncWifiWaiting: String get() = context.getString(R.string.cloud_doc_wifi_waiting)
    override val storageSpaceWarning: String get() = context.getString(R.string.storage_space_warning)
    override val errorDownloading: String get() = context.getString(R.string.error_downloading)
    override val journalDescription: String get() = context.getString(R.string.journal_description)
    override val studypads: String get() = context.getString(R.string.studypads)
    override val compareDescription: String get() = context.getString(R.string.compare_description)
    override val compareAbbreviation: String get() = context.getString(R.string.compare_abbreviation)
    override val memorizeDescription: String get() = context.getString(R.string.memorize_description)
    override val memorizeAbbreviation: String get() = context.getString(R.string.memorize_abbreviation)
    override val multiDescription: String get() = context.getString(R.string.multi_description)
    override val multiAbbreviation: String get() = context.getString(R.string.multi_abbreviation)
    override val myNotesDescription: String get() = context.getString(R.string.my_notes_description)
    override val myNotesAbbreviation: String get() = context.getString(R.string.my_notes_abbreviation)
    override fun httpStatusText(code: Int): String = context.getString(when (code) {
        403 -> R.string.http_forbidden
        404 -> R.string.http_not_found
        else -> R.string.http_unknown
    })
    override fun errorDownloadingStatus(url: String, statusText: String, code: Int): String =
        context.getString(R.string.error_downloading_status, url, statusText, code)
    override val labelAll: String get() = context.getString(R.string.all)
    override val installZipSuccessful: String get() = context.getString(R.string.install_zip_successfull)
    override val installZipCanceled: String get() = context.getString(R.string.install_zip_canceled)
    override val invalidModule: String get() = context.getString(R.string.invalid_module)
    override val sqliteInvalidFile: String get() = context.getString(R.string.sqlite_invalid_file)
    override val aiUnknownPrompt: String get() = context.getString(R.string.ai_unknown_prompt)
    override val errorPageTooLarge: String get() = context.getString(R.string.error_page_too_large)
    override fun workspaceNumber(n: Int): String = context.getString(R.string.workspace_number, n)
    override val errorOccurred: String get() = context.getString(R.string.error_occurred)
    override val errorNoContent: String get() = context.getString(R.string.error_no_content)
    override fun errorKeyNotInDocument(key: String, initials: String): String =
        context.getString(R.string.error_key_not_in_document2, key, initials)
    override val verseShareAdvertise: String get() =
        context.getString(R.string.verse_share_advertise, context.getString(R.string.app_name_long))
    override fun somethingWithParenthesis(a: String, b: String): String = context.getString(R.string.something_with_parenthesis, a, b)
    override fun readingPlanDay(day: String): String = context.getString(R.string.rdg_plan_day, day)
    override val sortByAlphabetical: String get() = context.getString(R.string.sort_by_alphabetical)
    override val sortByBibleBook: String get() = context.getString(R.string.sort_by_bible_book)
    override fun documentNotInstalled(initials: String): String = context.getString(R.string.document_not_installed, initials)
    override val noIndexedBibleWithStrongsRef: String get() = context.getString(R.string.no_indexed_bible_with_strongs_ref)
    override val wordNotFoundInDictionaries: String get() = context.getString(R.string.word_not_found_in_dictionaries)
    override val speakNoBooksAvailable: String get() = context.getString(R.string.speak_no_books_available)
    override val speakGeneralError: String get() = context.getString(R.string.speak_general_error)
    override val verseRangeModeDisabled: String get() = context.getString(R.string.verse_range_mode_disabled)
    override val rewind: String get() = context.getString(R.string.rewind)
    override val forward: String get() = context.getString(R.string.forward)
    override val pause: String get() = context.getString(R.string.pause)
    override val stop: String get() = context.getString(R.string.stop)
    override val speakStatusStopped: String get() = context.getString(R.string.speak_status_stopped)
    override fun sleepTimerStarted(minutes: Int): String = context.getString(R.string.sleep_timer_started, minutes)
    override val signInFailed: String get() = context.getString(R.string.sign_in_failed)
    override val cloudSyncTitle: String get() = context.getString(R.string.cloud_sync_title)
    override val cloudFetchAndRestoreInitial: String get() = context.getString(R.string.cloud_fetch_and_restore_initial)
    override val cloudCreateNew: String get() = context.getString(R.string.cloud_create_new)
    override val cloudDisableSync: String get() = context.getString(R.string.cloud_disable_sync)
    override val syncCantFetch: String get() = context.getString(R.string.sync_cant_fetch)
    override val syncUpdateApp: String get() = context.getString(R.string.sync_update_app)
    override val syncError: String get() = context.getString(R.string.sync_error)
    override val okay: String get() = context.getString(R.string.okay)
    override val cancel: String get() = context.getString(R.string.cancel)
    override val areYouSure: String get() = context.getString(R.string.are_you_sure)
    override fun overrideBackup(contents: String): String = context.getString(R.string.overrideBackup, contents)
    override fun areYouSureResetLocal(what: String): String = context.getString(R.string.are_you_sure_reset_local, what)
    override fun areYouSureResetCloud(what: String): String = context.getString(R.string.are_you_sure_reset_cloud, what)
    override fun syncDisabling(what: String): String = context.getString(R.string.sync_disabling, what)

    override fun databaseTitle(dbFileName: String): String = context.getString(when (dbFileName) {
        "bookmarks.sqlite3" -> R.string.db_bookmarks
        "readingplans.sqlite3" -> R.string.reading_plans_plural
        "workspaces.sqlite3" -> R.string.help_workspaces_title
        "repositories.sqlite3" -> R.string.db_repositories
        "settings.sqlite3" -> R.string.settings
        "mydocuments.sqlite3" -> R.string.my_documents_title
        "ai_settings.sqlite3" -> R.string.ai_settings_sync_title
        "progress.sqlite3" -> R.string.progress_sync_title
        else -> throw IllegalStateException("Unknown database file: $dbFileName")
    })
    override val share: String get() = context.getString(R.string.share)
    override val backupPhoneStorage: String get() = context.getString(R.string.backup_phone_storage)
    override val sendBackupFile: String get() = context.getString(R.string.send_backup_file)
    override val backupBackupTitle: String get() = context.getString(R.string.backup_backup_title)
    override val backupBackupMessage: String get() = context.getString(R.string.backup_backup_message)
    override val backupSuccess2: String get() = context.getString(R.string.backup_success2)
    override val pleaseWait: String get() = context.getString(R.string.please_wait)
    override val restoreBackupSections: String get() = context.getString(R.string.restore_backup_sections)
    override val backupModulesTitle: String get() = context.getString(R.string.backup_modules_title)
    override val backupModulesSuccess: String get() = context.getString(R.string.backup_modules_success)
    override val apkFile: String get() = context.getString(R.string.apk_file)
    override val backupApp2: String get() = context.getString(R.string.backup_app2)
    override val disablingSync: String get() = context.getString(R.string.disabling_sync)
    override val downloadingBackup: String get() = context.getString(R.string.downloading_backup)
    override val loadingBackup: String get() = context.getString(R.string.loading_backup)
    override val restoreUnsuccessfull: String get() = context.getString(R.string.restore_unsuccessfull)
    override val restoreSuccess: String get() = context.getString(R.string.restore_success)
    override val restoreConfirmation: String get() = context.getString(R.string.restore_confirmation)
    override val restore: String get() = context.getString(R.string.restore)
    override val import2: String get() = context.getString(R.string.import2)
    override val resetDatabaseSuccess: String get() = context.getString(R.string.reset_database_success)
    override val selectAll: String get() = context.getString(R.string.select_all)
    override val selectNone: String get() = context.getString(R.string.select_none)
    override fun backupEmailSubject2(name: String): String = context.getString(R.string.backup_email_subject_2, name)
    override fun backupEmailMessage2(name: String): String = context.getString(R.string.backup_email_message_2, name)
    override fun backupModulesEmailSubject2(name: String): String = context.getString(R.string.backup_modules_email_subject_2, name)
    override fun backupModulesEmailMessage2(name: String, modules: String): String = context.getString(R.string.backup_modules_email_message_2, name, modules)
    override fun overwriteSomething(contents: String): String = context.getString(R.string.overwrite_something, contents)
    override fun askRestoreOrImport(contents: String): String = context.getString(R.string.ask_restore_or_import, contents)
    override fun resetDatabaseConfirm(name: String): String = context.getString(R.string.reset_database_confirm, name)
    override fun backupFileInfo(version: Int, size: String): String = context.getString(R.string.backup_file_info, version, size)
    override val exportedStudypadsSubject: String get() = context.getString(R.string.exported_studypads_subject)
    override fun exportedStudypadsMessage(appName: String): String = context.getString(R.string.exported_studypads_message, appName)
    override val sendExportFile: String get() = context.getString(R.string.send_export_file)
    override val exportDestinationTitle: String get() = context.getString(R.string.export_destination_title)
    override val exportDestinationMessage: String get() = context.getString(R.string.export_destination_message)
    override fun searchUnsupportedSyntax(syntax: String): String = context.getString(R.string.search_unsupported_syntax, syntax)
}
