package net.bible.android.platform

import android.content.Context
import net.bible.android.activity.R
import net.bible.sharedcore.platform.CoreStrings

/** Reads the resource on every access so locale changes take effect. */
class AndroidCoreStrings(private val context: Context) : CoreStrings {
    override fun syncCategoryContents(category: String): String = context.getString(when (category) {
        "READINGPLANS" -> R.string.reading_plans_content
        "BOOKMARKS" -> R.string.bookmarks_contents
        "WORKSPACES" -> R.string.workspaces_contents
        "MYDOCUMENTS" -> R.string.my_documents_contents
        "AI_SETTINGS" -> R.string.ai_settings_sync_contents
        "PROGRESS" -> R.string.progress_sync_contents
        else -> throw IllegalArgumentException("Unknown sync category: $category")
    })

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
    override val errorOccurred: String get() = context.getString(R.string.error_occurred)
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
    override val ttsLangNotAvailable: String get() = context.getString(R.string.tts_lang_not_available)
    override fun sleepTimerStarted(minutes: Int): String = context.getString(R.string.sleep_timer_started, minutes)
}
