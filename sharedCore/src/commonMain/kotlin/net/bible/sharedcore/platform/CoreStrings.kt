package net.bible.sharedcore.platform

/** Strings domain code needs. One member per resource; grows per batch (spec L1a section 3). */
interface CoreStrings {
    val errorReport: String // R.string.report_error
    val errorBackup: String // R.string.backup_and_restore
    val errorSkip: String // R.string.error_skip
    val debugTestError: String // R.string.debug_test_error
    val namelessTitle: String
    fun processingEpub(title: String): String
    fun creatingIndexFor(name: String): String
    /** Resolves the stable sync category name; rejects unknown categories. */
    fun syncCategoryContents(category: String): String
    val syncIntroduction: String
    fun syncDriveIntroduction(discrete: Boolean): String
    fun syncAdapterSummary(adapter: String): String
    fun syncLastUpdated(time: String): String
    fun syncCloudInfo(megabytes: String): String
    val documentSyncNothing: String
    fun documentSyncUpload(count: Int): String
    fun documentSyncUploadSize(count: Int, size: String): String
    fun documentSyncDownload(count: Int): String
    fun documentSyncDownloadSize(count: Int, size: String): String
    val documentSyncWifiWaiting: String
    val storageSpaceWarning: String
    val errorDownloading: String
    val journalDescription: String
    val studypads: String
    val compareDescription: String
    val compareAbbreviation: String
    val memorizeDescription: String
    val memorizeAbbreviation: String
    val multiDescription: String
    val multiAbbreviation: String
    val myNotesDescription: String
    val myNotesAbbreviation: String
    fun httpStatusText(code: Int): String
    fun errorDownloadingStatus(url: String, statusText: String, code: Int): String
    val labelAll: String // R.string.all
    val installZipSuccessful: String // R.string.install_zip_successfull
    val installZipCanceled: String // R.string.install_zip_canceled
    val invalidModule: String // R.string.invalid_module
    val sqliteInvalidFile: String // R.string.sqlite_invalid_file
    val aiUnknownPrompt: String // R.string.ai_unknown_prompt
    val errorPageTooLarge: String // R.string.error_page_too_large
    fun workspaceNumber(n: Int): String // R.string.workspace_number
    val errorOccurred: String // R.string.error_occurred
    val errorNoContent: String // R.string.error_no_content
    fun errorKeyNotInDocument(key: String, initials: String): String // R.string.error_key_not_in_document2
    val verseShareAdvertise: String // R.string.verse_share_advertise, formatted with app_name_long
    fun somethingWithParenthesis(a: String, b: String): String // R.string.something_with_parenthesis: "%1$s (%2$s)"
    fun readingPlanDay(day: String): String // R.string.rdg_plan_day: "Day %s"
    val sortByAlphabetical: String // R.string.sort_by_alphabetical
    val sortByBibleBook: String // R.string.sort_by_bible_book
    fun documentNotInstalled(initials: String): String // R.string.document_not_installed: "Please download '%s'"
    val noIndexedBibleWithStrongsRef: String // R.string.no_indexed_bible_with_strongs_ref
    val wordNotFoundInDictionaries: String // R.string.word_not_found_in_dictionaries
    val speakNoBooksAvailable: String // R.string.speak_no_books_available
    val speakGeneralError: String // R.string.speak_general_error
    val verseRangeModeDisabled: String // R.string.verse_range_mode_disabled
    val rewind: String // R.string.rewind
    val forward: String // R.string.forward
    val pause: String // R.string.pause
    val stop: String // R.string.stop
    val speakStatusStopped: String // R.string.speak_status_stopped
    fun sleepTimerStarted(minutes: Int): String // R.string.sleep_timer_started
    val webDavUntrustedCertificateTitle: String
    val webDavTrustCertificate: String
    val webDavWrongCredentials: String
    val webDavNotAFolder: String
    val webDavCertificateChanged: String
    val webDavStorageFull: String
    val webDavHttpsRequired: String
    val iCloudDrive: String
    fun webDavUntrustedCertificateMessage(host: String, subject: String, issuer: String, from: String, until: String, fingerprint: String): String
    /** H2: device-neutral wording of [webDavUntrustedCertificateMessage] for iOS ("this device" instead of "Android"). */
    fun webDavUntrustedCertificateMessageDevice(host: String, subject: String, issuer: String, from: String, until: String, fingerprint: String): String
    val signInFailed: String
    val cloudSyncTitle: String
    val cloudMergeData: String
    val cloudFetchAndRestoreInitial: String
    val cloudCreateNew: String
    val cloudDisableSync: String
    val syncCantFetch: String
    val syncUpdateApp: String
    val syncError: String
    val okay: String
    val cancel: String
    val areYouSure: String
    fun overrideBackup(contents: String): String
    fun areYouSureMerge(contents: String): String
    fun areYouSureResetLocal(what: String): String
    fun areYouSureResetCloud(what: String): String
    fun syncDisabling(what: String): String

    fun databaseTitle(dbFileName: String): String
    val share: String
    val backupPhoneStorage: String
    val sendBackupFile: String
    val backupBackupTitle: String
    val backupBackupMessage: String
    val backupSuccess2: String
    val pleaseWait: String
    val restoreBackupSections: String
    val backupModulesTitle: String
    val backupModulesSuccess: String
    val apkFile: String
    val backupApp2: String
    val disablingSync: String
    val downloadingBackup: String
    val loadingBackup: String
    val restoreUnsuccessfull: String
    val restoreSuccess: String
    val restoreConfirmation: String
    val restore: String
    val import2: String
    val resetDatabaseSuccess: String
    val selectAll: String
    val selectNone: String
    fun backupEmailSubject2(name: String): String
    fun backupEmailMessage2(name: String): String
    fun backupModulesEmailSubject2(name: String): String
    fun backupModulesEmailMessage2(name: String, modules: String): String
    fun overwriteSomething(contents: String): String
    fun askRestoreOrImport(contents: String): String
    fun resetDatabaseConfirm(name: String): String
    fun backupModulesConflict(first: String, second: String, file: String): String // R.string.backup_modules_conflict
    fun backupModulesConflictBook(name: String, version: String, folder: String): String // R.string.backup_modules_conflict_book
    fun backupFileInfo(version: Int, size: String): String
    fun bookmarksDbStats(firstLabel: String, labels: String, bookmarks: String): String // R.string.bookmarks_db_stats
    val exportedStudypadsSubject: String // R.string.exported_studypads_subject
    fun exportedStudypadsMessage(appName: String): String // R.string.exported_studypads_message
    val sendExportFile: String // R.string.send_export_file
    val exportDestinationTitle: String // R.string.export_destination_title
    val exportDestinationMessage: String // R.string.export_destination_message
    fun searchUnsupportedSyntax(syntax: String): String // R.string.search_unsupported_syntax
    val memorizeWordVisibilityEntries: List<String> // R.array.memorize_word_visibility_entries
    val memorizeWordVisibilityValues: List<String> // R.array.memorize_word_visibility_values
    val cantChangeWholeVerse: String // R.string.cant_change_wholeverse
    val wholeVerseTurnedOn: String // R.string.whole_verse_turned_on

    // --- I2 Stream B (reading) ---
    val help: String // R.string.help
    val helpMemorizeText: String // R.string.help_memorize_text
    val helpReadMoreLink: String // R.string.help_read_more_link
    val verseTip: String // R.string.verse_tip
    val helpBookmarksText: String // R.string.help_bookmarks_text
    val watchTutorialVideo: String // R.string.watch_tutorial_video
    val bookmarksAndMyNotesTitle: String // R.string.bookmarks_and_mynotes_title

    val prefsNightModeDescriptionsSystemAutoManual: List<String> // R.array.prefs_night_mode_descriptions_system_auto_manual
    val prefsNightModeValuesSystemAutoManual: List<String> // R.array.prefs_night_mode_values_system_auto_manual
    val prefsNightModeDescriptionsSystemManual: List<String> // R.array.prefs_night_mode_descriptions_system_manual
    val prefsNightModeValuesSystemManual: List<String> // R.array.prefs_night_mode_values_system_manual
    val prefsBibleBookmarkModalActionNames: List<String> // R.array.prefs_bible_bookmark_modal_action_names
    val prefsBibleBookmarkModalActionIds: List<String> // R.array.prefs_bible_bookmark_modal_action_ids
    val prefsGenBookmarkModalActionNames: List<String> // R.array.prefs_gen_bookmark_modal_action_names
    val prefsGenBookmarkModalActionIds: List<String> // R.array.prefs_gen_bookmark_modal_action_ids
    val experimentalFeaturesNames: List<String> // R.array.experimental_features_names
    val experimentalFeaturesValues: List<String> // R.array.experimental_features_values
    val prefsToolbarButtonActionDescriptions: List<String> // R.array.prefs_toolbar_button_action_descriptions
    val prefsToolbarButtonActionValues: List<String> // R.array.prefs_toolbar_button_action_values
    val prefsBibleViewSwipeModeDescriptions: List<String> // R.array.prefs_bible_view_swipe_mode_descriptions
    val prefsBibleViewSwipeModeValues: List<String> // R.array.prefs_bible_view_swipe_mode_values
    val prefsThemePaletteStyleNames: List<String> // R.array.prefs_theme_palette_style_names
    val prefsThemePaletteStyleValues: List<String> // R.array.prefs_theme_palette_style_values
    val prefsInterfaceLocaleDescriptions: List<String> // R.array.prefs_interface_locale_descriptions
    val prefsInterfaceLocaleValues: List<String> // R.array.prefs_interface_locale_values
    val prefsNotesContentTypeEntries: List<String> // R.array.prefs_notes_content_type_entries
    val prefsNotesContentTypeValues: List<String> // R.array.prefs_notes_content_type_values
    val prefsDisplayColorModeNames: List<String> // R.array.prefs_display_color_mode_names
    val prefsDisplayColorModeValues: List<String> // R.array.prefs_display_color_mode_values
}
