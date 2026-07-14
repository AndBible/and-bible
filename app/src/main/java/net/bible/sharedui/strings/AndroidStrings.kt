package net.bible.sharedui.strings

import android.content.Context
import net.bible.android.activity.R

/** R.string-backed [Strings]. Grows alongside the commonMain interface as leaves move. */
class AndroidStrings(private val context: Context) : Strings {
    override val calcWrongFormat: String get() = context.getString(R.string.calc_wrong_format)
    override val calcWrongFormatOperand: String get() = context.getString(R.string.calc_wrong_format_operand)
    override val calcDivisionByZero: String get() = context.getString(R.string.calc_division_by_zero)
    override fun historyFor(workspace: String, window: Int): String =
        context.getString(R.string.history_for, workspace, window)
    override val errorOccurred: String get() = context.getString(R.string.error_occurred)
    override val okay: String get() = context.getString(R.string.okay)
    override val indexingWaitMsg: String get() = context.getString(R.string.indexing_wait_msg)
    override val noTasksRunning: String get() = context.getString(R.string.no_tasks_running)
    override val doInBackground: String get() = context.getString(R.string.do_in_background)
    override val readingPlanSelectorTitle: String get() = context.getString(R.string.rdg_plan_selector_title)
    override val readingPlanTitle: String get() = context.getString(R.string.rdg_plan_title)
    override val planDuplicateUserPlan: String get() = context.getString(R.string.plan_duplicate_user_plan)
    override val resetGeneric: String get() = context.getString(R.string.reset_generic)
    override val resetPlanQuestion: String get() = context.getString(R.string.reset_plan_question)
    override val yes: String get() = context.getString(R.string.yes)
    override val no: String get() = context.getString(R.string.no)
    override val all: String get() = context.getString(R.string.all)
    override val setCurrentDay: String get() = context.getString(R.string.set_current_day)
    override val setStartDate: String get() = context.getString(R.string.rdg_plan_set_start_date)
    override val importReadingPlan: String get() = context.getString(R.string.import_reading_plan)
    override val setCurrentDayQuestion: String get() = context.getString(R.string.msg_set_current_day_reading_plan)
    override val selectPassage: String get() = context.getString(R.string.selectPassage)
    override val speak: String get() = context.getString(R.string.speak)
    override val stop: String get() = context.getString(R.string.stop)
    override val pause: String get() = context.getString(R.string.pause)
    override val done: String get() = context.getString(R.string.done)
    override val generalBookTitle: String get() = context.getString(R.string.general_book)
    override val mapTitle: String get() = context.getString(R.string.doc_type_map)
    override val dictionaryTitle: String get() = context.getString(R.string.dictionary)
    override val searchHint: String get() = context.getString(R.string.search)
    override val bible: String get() = context.getString(R.string.bible)
    override val deuterocanonical: String get() = context.getString(R.string.deuterocanonical)
    override val menuAlphabetical: String get() = context.getString(R.string.sort_by_alphabetical)
    override val menuRowOrder: String get() = context.getString(R.string.book_menu_sort_row_opt)
    override val menuGroupByCategory: String get() = context.getString(R.string.book_menu_group_by_category)
    override val menuShowLongName: String get() = context.getString(R.string.book_menu_show_long_book_name)
    override val menuShowProgressBars: String get() = context.getString(R.string.book_menu_show_progress_bars)

    // Batch 4 — document selection
    override fun docFilterResults(count: Int): String = context.getString(R.string.document_filter_results, count)
    override val docTypeAll: String get() = context.getString(R.string.doc_type_all)
    override val docTypeBible: String get() = context.getString(R.string.doc_type_bible)
    override val docTypeCommentary: String get() = context.getString(R.string.doc_type_commentary)
    override val docTypeDictionary: String get() = context.getString(R.string.doc_type_dictionary)
    override val docTypeGeneralBook: String get() = context.getString(R.string.doc_type_book)
    override val docTypeMaps: String get() = context.getString(R.string.doc_type_map)
    override val docTypeAddon: String get() = context.getString(R.string.doc_type_addons)
    override val languageLabel: String get() = context.getString(R.string.chooce_language_hint)
    override val aboutDoc: String get() = context.getString(R.string.about)
    override val deleteLabel: String get() = context.getString(R.string.delete)
    override val deleteIndexLabel: String get() = context.getString(R.string.delete_index)
    override val unlockModule: String get() = context.getString(R.string.unlock_module)
    override fun deleteDoc(name: String): String = context.getString(R.string.delete_doc, name)
    override fun deleteSearchIndexDoc(name: String): String = context.getString(R.string.delete_search_index_doc, name)
    override val cantDeleteDocument: String get() = context.getString(R.string.cant_delete_document)
    override val downloadDocuments: String get() = context.getString(R.string.download)

    // Batch 4b — download
    override fun moduleSizeMb(mb: Double): String = context.getString(R.string.module_size_megabytes, mb)
    override val cancel: String get() = context.getString(R.string.cancel)

    // Batch 5 — search
    override val search: String get() = context.getString(R.string.search)
    override val searchIndex: String get() = context.getString(R.string.search_index)
    override val allWords: String get() = context.getString(R.string.search_all_words)
    override val anyWord: String get() = context.getString(R.string.search_any_word)
    override val phrase: String get() = context.getString(R.string.search_phrase)
    override val searchAllBible: String get() = context.getString(R.string.search_all_bible)
    override val searchOldTestament: String get() = context.getString(R.string.search_old_testament)
    override val searchNewTestament: String get() = context.getString(R.string.search_new_testament)
    override val searchCurrentBook: String get() = context.getString(R.string.search_current_book)
    override val chooseTranslations: String get() = context.getString(R.string.search_translations)
    override val create: String get() = context.getString(R.string.index_create)
    override val rebuildIndex: String get() = context.getString(R.string.rebuild_index)
    override val openResultsInWindow: String get() = context.getString(R.string.open_in_window)
    // Classic search_index.xml has a single "index required" prompt (index_creation_required);
    // reuse it for both the create and rebuild prompts.
    override val indexCreationRequired: String get() = context.getString(R.string.index_creation_required)
    override val indexRebuildRequired: String get() = context.getString(R.string.index_creation_required)
}
