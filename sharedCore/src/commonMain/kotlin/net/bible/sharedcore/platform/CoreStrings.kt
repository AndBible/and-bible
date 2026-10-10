package net.bible.sharedcore.platform

/** Strings domain code needs. One member per resource; grows per batch (spec L1a section 3). */
interface CoreStrings {
    /** Resolves the stable sync category name; rejects unknown categories. */
    fun syncCategoryContents(category: String): String
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
    val errorOccurred: String // R.string.error_occurred
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
    val ttsLangNotAvailable: String // R.string.tts_lang_not_available
    fun sleepTimerStarted(minutes: Int): String // R.string.sleep_timer_started
    val signInFailed: String
    val cloudSyncTitle: String
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
    fun backupFileInfo(version: Int, size: String): String
}
