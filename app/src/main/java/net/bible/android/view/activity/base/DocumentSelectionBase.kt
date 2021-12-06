/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */
package net.bible.android.view.activity.base

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.AdapterView.OnItemLongClickListener
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import net.bible.android.activity.R
import net.bible.android.activity.databinding.DocumentSelectionBinding
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.download.DocumentStatus
import net.bible.android.control.download.DownloadControl
import net.bible.android.control.download.repo
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import net.bible.android.database.DocumentSearch
import net.bible.android.view.activity.base.Dialogs.Companion.instance
import net.bible.android.view.activity.base.ListActionModeHelper.ActionModeActivity
import net.bible.android.view.activity.download.isRecommended
import net.bible.android.view.activity.page.MainBibleActivity.Companion._mainBibleActivity
import net.bible.service.common.CommonUtils
import net.bible.service.common.Ref
import net.bible.service.db.DatabaseContainer
import net.bible.service.download.DownloadManager
import net.bible.service.download.isPseudoBook
import net.bible.service.sword.AndBibleAddonFilter
import org.crosswire.common.util.Language
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.BookException
import org.crosswire.jsword.book.BookFilter
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookMetaData
import java.util.*
import javax.inject.Inject

/**
 * Choose Document (Book)
 *
 * NotificationManager with ProgressBar example here:
 * http://united-coders.com/nico-heid/show-progressbar-in-notification-area-like-google-does-when-downloading-from-android
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */

@Serializable
data class RecommendedDocuments(
    val bibles: Map<String, List<String>>,
    val commentaries: Map<String, List<String>>,
    val dictionaries: Map<String, List<String>>,
    val books: Map<String, List<String>>,
    val maps: Map<String, List<String>>,
    val addons: Map<String, List<String>> = emptyMap(),
) {
    fun getForBookCategory(c: BookCategory): Map<String, List<String>> {
        return when(c) {
            BookCategory.BIBLE -> bibles
            BookCategory.COMMENTARY -> commentaries
            BookCategory.GENERAL_BOOK -> books
            BookCategory.MAPS -> maps
            BookCategory.DICTIONARY -> dictionaries
            BookCategory.AND_BIBLE -> addons
            else -> emptyMap()
        }
    }
}

@Serializable
data class PseudoBook(
    val id: String,
    val suggested: String,
)

abstract class DocumentSelectionBase(optionsMenuId: Int, private val actionModeMenuId: Int) : ListActivityBase(optionsMenuId), ActionModeActivity {
    @Inject lateinit var downloadControl: DownloadControl

    protected lateinit var binding: DocumentSelectionBinding

    protected lateinit var documentItemAdapter: ArrayAdapter<Book>
    protected var selectedDocumentFilterNo = 0
    private val filterMutex = Mutex()
    // language spinner
    private val languageList = ArrayList<Language>()
    protected var selectedLanguageNo = -1
    private lateinit var langArrayAdapter: ArrayAdapter<Language>

    private var isPopulated = false
    private val dao get() = DatabaseContainer.db.documentDao()

    val pseudoBooks = Ref<List<PseudoBook>>()
    val defaultDocuments = Ref<RecommendedDocuments>()
    val recommendedDocuments = Ref<RecommendedDocuments>()

    private var allDocuments = ArrayList<Book>()
    var displayedDocuments = ArrayList<Book>()

    @Inject lateinit var documentControl: DocumentControl

    private lateinit var listActionModeHelper: ListActionModeHelper
    private var showOkButton: Boolean = false

    /** ask subclass for documents to be displayed
     */
    protected abstract suspend fun getDocumentsFromSource(refresh: Boolean): List<Book>
    protected abstract fun handleDocumentSelection(selectedDocument: Book)
    protected abstract fun sortLanguages(languages: Collection<Language>?): List<Language>

    /** Called when the activity is first created.  */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DocumentSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    protected fun initialiseView() {
        listAdapter = documentItemAdapter
        // prepare action mode
        listActionModeHelper = ListActionModeHelper(listView, actionModeMenuId, true)
        //listView.choiceMode = AbsListView.CHOICE_MODE_SINGLE
        // trigger action mode on long press
        listView.onItemLongClickListener = OnItemLongClickListener { parent, view, position, id -> listActionModeHelper.startActionMode(this@DocumentSelectionBase, position) }
        languageList.clear()
        displayedDocuments.clear()

        //prepare the documentType spinner
        setInitialDocumentType()

        binding.apply {
            okButtonPanel.visibility = if (showOkButton) View.VISIBLE else View.GONE

            documentTypeSpinner.setSelection(selectedDocumentFilterNo)
            documentTypeSpinner.onItemSelectedListener = object : OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedDocumentFilterNo = position
                    filterDocuments()
                }

                override fun onNothingSelected(arg0: AdapterView<*>?) {}
            }

            languageSpinner.onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
                val lang = parent.adapter.getItem(position) as Language
                lastSelectedLanguage = lang
                selectedLanguageNo = languageList.indexOf(lang)
                filterDocuments()
                languageSpinner.clearFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(languageSpinner.windowToken, 0)
            }

            languageSpinner.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val langString = s.toString()
                    if (langString.isEmpty()) {
                        selectedLanguageNo = -1
                        filterDocuments()
                    } else {
                        val langIdx = languageList.indexOfFirst { it.name == langString }
                        if (langIdx != -1) {
                            selectedLanguageNo = langIdx
                            filterDocuments()
                        }
                    }
                }

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
            freeTextSearch.setOnClickListener {
                languageSpinner.setText("")
            }
            freeTextSearch.setOnFocusChangeListener { i, hasFocus ->
                if (hasFocus) {
                    languageSpinner.setText("")
                    freeTextSearch.setText("")
                }
            }
            freeTextSearch.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    filterDocuments()
                }

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })

            languageSpinner.setOnClickListener {
                languageSpinner.setText("")
                languageSpinner.showDropDown()
            }

            languageSpinner.setOnFocusChangeListener { i, hasFocus ->
                if (hasFocus) {
                    languageSpinner.setText("")
                }
            }

            // Note: on Android 8 (API 26 and lower), it looks like languageList does not
            // remain the source data set after filtering. If changes to dataset are made
            // (in populate), they need to be made to adapter too, otherwise in
            // ArrayAdapter's publishResult they will be overwritten.

            langArrayAdapter = ArrayAdapter(
                this@DocumentSelectionBase,
                android.R.layout.simple_spinner_dropdown_item,
                languageList
            )

            languageSpinner.setAdapter(langArrayAdapter)
            list.requestFocus()

            intent.getStringExtra("search")?.run {
                freeTextSearch.setText(this)
            }
        }
        Log.i(TAG, "Initialize finished")
    }

    open fun setDefaultLanguage() {
        if (selectedLanguageNo == -1 && binding.freeTextSearch.text.isEmpty()) {
            val lang: Language?
            // make selected language sticky
            val lastSelectedLanguage = lastSelectedLanguage
            lang = if (lastSelectedLanguage != null && languageList.contains(lastSelectedLanguage)) {
                lastSelectedLanguage
            } else {
                // set default lang to lang of mobile
                defaultLanguage
            }
            selectedLanguageNo = languageList.indexOf(lang)
            binding.languageSpinner.setText(lang.name)
            Log.i(TAG, "Default language set to ${lang}")
        }

        // if last doc in last lang was just deleted then need to adjust index
        checkSpinnerIndexesValid()
    }

    protected fun findBookByInitials(initials: String, repository: String?) : Book? = allDocuments.find {
        if(repository != null) {
            it.initials == initials && it.repo == repository
        } else {
            it.initials == initials
        }
    }

    // get the current language code

    // create the JSword Language for current lang

    // check a bible exists in current lang otherwise use english

    // if no bibles exist in current lang then fall back to one of the languages that have books
    // so the user will not see an initially empty list
    private val defaultLanguage: Language
        get() {
            // get the current language code
            var langCode = Locale.getDefault().language
            if (!Language(langCode).isValidLanguage) {
                langCode = Locale.ENGLISH.language
            }

            // create the JSword Language for current lang
            var localLanguage = Language(langCode)
            Log.i(TAG, "Local language is:$localLanguage")

            // check a bible exists in current lang otherwise use english
            var foundBibleInLocalLanguage = false
            var installedLanguage: Language? = null
            for (book in allDocuments.filter { it.bookCategory == BookCategory.BIBLE }) {
                if (localLanguage == book.language) {
                    foundBibleInLocalLanguage = true
                    break
                }
                val installedDoc = swordDocumentFacade.getDocumentByInitials(book.initials)
                if(installedDoc != null) {
                    installedLanguage = book.language
                }
            }

            // if no bibles exist in current lang then fall back to one of the languages that have books
            // so the user will not see an initially empty list
            if (!foundBibleInLocalLanguage) {
                localLanguage = installedLanguage ?: Language.DEFAULT_LANG
            }
            return localLanguage
        }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        try {
            if (position >= 0 && position < displayedDocuments.size) {
                val selectedBook = displayedDocuments[position]
                Log.i(TAG, "Selected " + selectedBook.initials)
                handleDocumentSelection(selectedBook)

                // prevent the item remaining highlighted.  Unfortunately the highlight is cleared before the selection is handled.
                listView.setItemChecked(position, false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "document selection error", e)
            instance.showErrorMsg(R.string.error_occurred, e)
        }
    }

    internal fun reloadDocuments() = GlobalScope.launch {
        populateMasterDocumentList(false)
    }

    protected open fun showPreLoadMessage(refresh: Boolean) {
        // default to no message
    }

    protected suspend fun populateMasterDocumentList(refresh: Boolean) {
        isPopulated = false
        Log.i(TAG, "populate Master Document List")

        withContext(Dispatchers.Main) {
            binding.loadingIndicator.visibility = View.VISIBLE
            showPreLoadMessage(refresh)
            filterMutex.withLock {
                documentItemAdapter.clear()
                displayedDocuments.clear()
            }
        }
        withContext(Dispatchers.Default) {
            try {
                val newDocs = getDocumentsFromSource(refresh)
                filterMutex.withLock {
                    allDocuments.clear()
                    allDocuments.addAll(newDocs)
                }
                if(refresh) {
                    dao.clear()
                    dao.insertDocuments(allDocuments.map {
                        DocumentSearch(it.osisID, it.abbreviation, if (it.isPseudoBook) "" else it.name, it.language.name, it.getProperty(DownloadManager.REPOSITORY_KEY)
                            ?: "")
                    })
                }

                Log.i(TAG, "Number of documents:" + allDocuments.size)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting documents", e)
                instance.showErrorMsg(R.string.error_occurred, e)
            }
        }
        withContext(Dispatchers.Main) {
            try {
                populateLanguageList()
                setDefaultLanguage()
                isPopulated = true
                filterDocuments()
            } finally {
                binding.loadingIndicator.visibility = View.GONE
            }
        }
    }

    /** a spinner has changed so refilter the doc list
     */
    fun filterDocuments() {
        if(!isPopulated) return
        // documents list has changed so force action mode to exit, if displayed, because selections are invalidated
        listActionModeHelper.exitActionMode()
        GlobalScope.launch {
            filterMutex.withLock {
                try {
                    // re-filter documents
                    if (allDocuments.size > 0) {
                        Log.i(TAG, "filtering documents")
                        displayedDocuments.clear()
                        val lang = selectedLanguage
                        val searchString = "${binding.freeTextSearch.text}*"
                        val osisIds = if(searchString.length < 3) null else dao.search(searchString).toSet()

                        for (doc in allDocuments) {
                            val filter = DOCUMENT_TYPE_SPINNER_FILTERS[selectedDocumentFilterNo]
                            if (filter.test(doc) && (lang == null || doc.language == lang || doc.bookCategory == BookCategory.AND_BIBLE) && (osisIds == null || osisIds.contains(doc.osisID))) {
                                displayedDocuments.add(doc)
                            }
                        }

                        displayedDocuments.sortWith(
                            compareBy(
                                {
                                    when(downloadControl.getDocumentStatus(it).documentInstallStatus) {
                                        DocumentStatus.DocumentInstallStatus.BEING_INSTALLED -> 0
                                        DocumentStatus.DocumentInstallStatus.UPGRADE_AVAILABLE -> 1
                                        else -> 2
                                    }
                                },
                                { swordDocumentFacade.getDocumentByInitials(it.initials) == null },
                                { if (lang != null) !it.isRecommended(recommendedDocuments.value) else false },
                                {
                                    when (it.bookCategory) {
                                        BookCategory.BIBLE -> 0
                                        BookCategory.COMMENTARY -> 1
                                        BookCategory.DICTIONARY -> 2
                                        BookCategory.GENERAL_BOOK -> 4
                                        BookCategory.MAPS -> 5
                                        BookCategory.AND_BIBLE -> 6
                                        else -> 7
                                    }
                                },
                                { it.abbreviation.toLowerCase(Locale(it.language.code)) }
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error initialising view", e)
                    ABEventBus.getDefault().post(
                        ToastEvent(getString(R.string.error) + " " + e.message,
                            Toast.LENGTH_SHORT)
                    )
                }
                withContext(Dispatchers.Main) {
                    documentItemAdapter.clear()
                    documentItemAdapter.addAll(displayedDocuments)
                    binding.resultCount.text = getString(R.string.document_filter_results, displayedDocuments.size)
                }
            }
        }
    }

    /** a spinner has changed so refilter the doc list
     */
    private fun populateLanguageList() {
        try {
            // temporary Set to remove duplicate Languages
            val langSet = HashSet<Language>()
            if (allDocuments.size > 0) {
                Log.i(TAG, "initialising language list")
                for (doc in allDocuments) {
                    langSet.add(doc.language)
                }
                val sortedLanguages = sortLanguages(langSet)
                languageList.clear()
                languageList.addAll(sortedLanguages)
                langArrayAdapter.clear()
                langArrayAdapter.addAll(sortedLanguages)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initialising view", e)
            Toast.makeText(this, getString(R.string.error) + " " + e.message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActionItemClicked(item: MenuItem, selectedItemPositions: List<Int>): Boolean {
        val documents: MutableList<Book> = ArrayList()
        for (posn in selectedItemPositions) {
            if (posn < displayedDocuments.size) {
                documents.add(displayedDocuments[posn])
            }
        }
        if (documents.isNotEmpty()) {
            when (item.itemId) {
                R.id.about -> {
                    handleAbout(documents)
                    return true
                }
                R.id.delete -> {
                    handleDelete(documents)
                    return true
                }
                R.id.delete_index -> {
                    handleDeleteIndex(documents)
                    return true
                }
            }
        }
        return false
    }

    internal fun handleAbout(documents: List<Book>) {
        val document = documents[0]
        try {
            // ensure repo key is retained but reload sbmd to ensure About text is loaded
            val sbmd = document.bookMetaData as SwordBookMetaData
            val repoKey = sbmd.getProperty(DownloadManager.REPOSITORY_KEY)
            sbmd.reload()
            sbmd.setProperty(DownloadManager.REPOSITORY_KEY, repoKey)
            GlobalScope.launch(Dispatchers.Main) {CommonUtils.showAbout(this@DocumentSelectionBase, document) }
        } catch (e: BookException) {
            Log.e(TAG, "Error expanding SwordBookMetaData for $document", e)
            instance.showErrorMsg(R.string.error_occurred, e)
        }
    }

    private fun handleDelete(documents: List<Book>) {
        for (document in documents) {
            if (documentControl.canDelete(document.installedDocument)) {
                val msg: CharSequence = getString(R.string.delete_doc, document.name)
                AlertDialog.Builder(this)
                    .setMessage(msg).setCancelable(true)
                    .setPositiveButton(R.string.yes
                    ) { dialog, buttonId ->
                        try {
                            Log.i(TAG, "Deleting:$document")
                            documentControl.deleteDocument(document.installedDocument)

                            // the doc list should now change
                            reloadDocuments()
                            _mainBibleActivity?.updateDocuments()
                        } catch (e: Exception) {
                            Log.e(TAG, "Deleting document crashed", e)
                            instance.showErrorMsg(R.string.error_occurred, e)
                        }
                    }
                    .setNegativeButton(R.string.no, null)
                    .create()
                    .show()
            } else {
                ABEventBus.getDefault().post(ToastEvent(R.string.cant_delete_last_bible))
            }
        }
    }

    private fun handleDeleteIndex(documents: List<Book>) {
        for (document in documents) {
            val msg: CharSequence = getString(R.string.delete_search_index_doc, document.name)
            AlertDialog.Builder(this)
                .setMessage(msg).setCancelable(true)
                .setPositiveButton(R.string.okay
                ) { dialog, buttonId ->
                    try {
                        Log.i(TAG, "Deleting index:$document")
                        swordDocumentFacade.deleteDocumentIndex(document.installedDocument)
                    } catch (e: Exception) {
                        Log.e(TAG, "Deleting index crashed", e)
                        instance.showErrorMsg(R.string.error_occurred, e)
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .create()
                .show()
        }
    }

    override fun isItemChecked(position: Int): Boolean {
        return listView.isItemChecked(position)
    }

    /**
     * deletion may have removed a language or document type so need to check current spinner selection is still valid
     */
    private fun checkSpinnerIndexesValid() {
        if (selectedLanguageNo >= languageList.size) {
            selectedLanguageNo = languageList.size - 1
        }
    }

    private val selectedLanguage: Language? get() {
        if(selectedLanguageNo == -1) return null
        return languageList[selectedLanguageNo]
    }

    /** allow selection of initial doc type
     */
    protected open fun setInitialDocumentType() {
        selectedDocumentFilterNo = if(intent?.getBooleanExtra("addons", false) == true) 6 else 0
    }

    fun setShowOkButtonBar(visible: Boolean) {
        showOkButton = visible
    }

    companion object {
        private val DOCUMENT_TYPE_SPINNER_FILTERS = arrayOf(
            BookFilter { it.bookCategory != BookCategory.AND_BIBLE },
            BookFilter { it.bookCategory == BookCategory.BIBLE },
            BookFilter { it.bookCategory == BookCategory.COMMENTARY },
            BookFilter { it.bookCategory == BookCategory.DICTIONARY },
            BookFilter { it.bookCategory == BookCategory.GENERAL_BOOK },
            BookFilter { it.bookCategory == BookCategory.MAPS },
            AndBibleAddonFilter(),
        )
        private var lastSelectedLanguage // allow sticky language selection
            : Language? = null
        private const val TAG = "DocumentSelectionBase"
    }

}

val Book.installedDocument get() = Books.installed().getBook(initials)
