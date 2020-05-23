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
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.AdapterView.OnItemLongClickListener
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import kotlinx.android.synthetic.main.document_selection.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import net.bible.android.activity.R
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import net.bible.android.database.Document
import net.bible.android.view.activity.base.Dialogs.Companion.instance
import net.bible.android.view.activity.base.ListActionModeHelper.ActionModeActivity
import net.bible.android.view.activity.download.DownloadActivity
import net.bible.android.view.activity.download.isRecommended
import net.bible.service.db.DatabaseContainer
import net.bible.service.download.DownloadManager
import org.apache.commons.lang3.StringUtils
import org.crosswire.common.util.Language
import org.crosswire.common.util.Version
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.BookException
import org.crosswire.jsword.book.BookFilters
import org.crosswire.jsword.book.sword.SwordBook
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
    val maps: Map<String, List<String>>
) {
    fun getForBookCategory(c: BookCategory): Map<String, List<String>> {
        return when(c) {
            BookCategory.BIBLE -> bibles
            BookCategory.COMMENTARY -> commentaries
            BookCategory.GENERAL_BOOK -> books
            BookCategory.MAPS -> maps
            BookCategory.DICTIONARY -> dictionaries
            else -> emptyMap()
        }
    }
}

abstract class DocumentSelectionBase(optionsMenuId: Int, private val actionModeMenuId: Int) : ListActivityBase(optionsMenuId), ActionModeActivity {
    protected lateinit var documentItemAdapter: ArrayAdapter<Book>
    protected var selectedDocumentFilterNo = 0
    private val filterMutex = Mutex()
    // language spinner
    private val languageList = ArrayList<Language>()
    protected var selectedLanguageNo = -1
    private lateinit var langArrayAdapter: ArrayAdapter<Language>

    private var isPopulated = false
    private val dao = DatabaseContainer.db.documentDao()

    open val recommendedDocuments: RecommendedDocuments? = null

    private var allDocuments = ArrayList<Book>()
    private var displayedDocuments = ArrayList<Book>()

    @Inject lateinit var documentControl: DocumentControl

    private lateinit var listActionModeHelper: ListActionModeHelper
    private var layoutResource = R.layout.document_selection

    /** ask subclass for documents to be displayed
     */
    protected abstract fun getDocumentsFromSource(refresh: Boolean): List<Book>
    protected abstract fun handleDocumentSelection(selectedDocument: Book?)
    protected abstract fun sortLanguages(languages: Collection<Language>?): List<Language>

    /** Called when the activity is first created.  */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layoutResource)
    }

    protected fun initialiseView() {
        listAdapter = documentItemAdapter
        // prepare action mode
        listActionModeHelper = ListActionModeHelper(listView, actionModeMenuId)
        // trigger action mode on long press
        listView.onItemLongClickListener = OnItemLongClickListener { parent, view, position, id -> listActionModeHelper.startActionMode(this@DocumentSelectionBase, position) }
        languageList.clear()
        displayedDocuments.clear()

        //prepare the documentType spinner
        setInitialDocumentType()
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

        languageSpinner.addTextChangedListener( object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val langString = s.toString()
                if(langString.isEmpty()) {
                    selectedLanguageNo = -1
                    filterDocuments()
                } else {
                    val langIdx = languageList.indexOfFirst {it.name == langString}
                    if(langIdx != -1) {
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
            if(hasFocus) {
                languageSpinner.setText("")
                freeTextSearch.setText("")
            }
        }
        freeTextSearch.addTextChangedListener(object: TextWatcher {
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
            if(hasFocus) {
                languageSpinner.setText("")
            }
        }

        // Note: on Android 8 (API 26 and lower), it looks like languageList does not
        // remain the source data set after filtering. If changes to dataset are made
        // (in populate), they need to be made to adapter too, otherwise in
        // ArrayAdapter's publishResult they will be overwritten.

        langArrayAdapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item,
            languageList
        )

        languageSpinner.setAdapter(langArrayAdapter)
        list.requestFocus()
        Log.d(TAG, "Initialize finished")
    }

    open fun setDefaultLanguage() {
        if (selectedLanguageNo == -1 && freeTextSearch.text.isEmpty()) {
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
            languageSpinner.setText(lang.name)
            Log.d(TAG, "Default language set to ${lang}")
        }

        // if last doc in last lang was just deleted then need to adjust index
        checkSpinnerIndexesValid()
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
            Log.d(TAG, "Local language is:$localLanguage")

            // check a bible exists in current lang otherwise use english
            var foundBibleInLocalLanguage = false
            var existingLanguage: Language? = null
            for (book in allDocuments) {
                if (book.bookCategory == BookCategory.BIBLE) {
                    if (localLanguage == book.language) {
                        foundBibleInLocalLanguage = true
                        break
                    }
                    existingLanguage = book.language
                }
            }

            // if no bibles exist in current lang then fall back to one of the languages that have books
            // so the user will not see an initially empty list
            if (!foundBibleInLocalLanguage) {
                localLanguage = existingLanguage ?: Language.DEFAULT_LANG
            }
            return localLanguage
        }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        try {
            if (position >= 0 && position < displayedDocuments.size) {
                val selectedBook = displayedDocuments[position]
                Log.d(TAG, "Selected " + selectedBook.initials)
                handleDocumentSelection(selectedBook)

                // prevent the item remaining highlighted.  Unfortunately the highlight is cleared before the selection is handled.
                listView.setItemChecked(position, false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "document selection error", e)
            instance.showErrorMsg(R.string.error_occurred, e)
        }
    }

    private fun reloadDocuments() = GlobalScope.launch {
        populateMasterDocumentList(false)
    }

    protected open fun showPreLoadMessage() {
        // default to no message
    }

    protected suspend fun populateMasterDocumentList(refresh: Boolean) {
        isPopulated = false
        Log.d(TAG, "populate Master Document List")

        withContext(Dispatchers.Main) {
            loadingIndicator.visibility = View.VISIBLE
            showPreLoadMessage()
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
                dao.clear()
                dao.insertDocuments(allDocuments.map { Document(it.osisID, it.abbreviation, it.name, it.language.name, it.getProperty(DownloadManager.REPOSITORY_KEY) ?: "") })

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
                loadingIndicator.visibility = View.GONE
            }
        }
    }

    /** a spinner has changed so refilter the doc list
     */
    private fun filterDocuments() {
        if(!isPopulated) return
        // documents list has changed so force action mode to exit, if displayed, because selections are invalidated
        listActionModeHelper.exitActionMode()
        GlobalScope.launch {
            filterMutex.withLock {
                try {
                    // re-filter documents
                    if (allDocuments.size > 0) {
                        Log.d(TAG, "filtering documents")
                        displayedDocuments.clear()
                        val lang = selectedLanguage
                        val searchString = "${freeTextSearch.text}*"
                        val osisIds = if(searchString.length < 3) null else dao.search(searchString).toSet()

                        for (doc in allDocuments) {
                            val filter = DOCUMENT_TYPE_SPINNER_FILTERS[selectedDocumentFilterNo]
                            if (filter.test(doc) && (lang == null || doc.language == lang) && (osisIds == null || osisIds.contains(doc.osisID))) {
                                displayedDocuments.add(doc)
                            }
                        }

                        displayedDocuments.sortWith(
                            compareBy (
                                { swordDocumentFacade.getDocumentByInitials(it.initials) == null },
                                { if(lang != null) !it.isRecommended(recommendedDocuments) else false },
                                {when(it.bookCategory) {
                                    BookCategory.BIBLE -> 0
                                    BookCategory.COMMENTARY -> 1
                                    BookCategory.DICTIONARY -> 2
                                    BookCategory.GENERAL_BOOK -> 4
                                    BookCategory.MAPS -> 5
                                    else -> 6
                                } },
                                {it.abbreviation.toLowerCase(Locale(it.language.code))}
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
                    resultCount.text = getString(R.string.document_filter_results, displayedDocuments.size)
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
                Log.d(TAG, "initialising language list")
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
        if (!documents.isEmpty()) {
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

    private fun handleAbout(documents: List<Book>) {
        val document = documents[0]
        try {
            // ensure repo key is retained but reload sbmd to ensure About text is loaded
            val sbmd = document.bookMetaData as SwordBookMetaData
            val repoKey = sbmd.getProperty(DownloadManager.REPOSITORY_KEY)
            sbmd.reload()
            sbmd.setProperty(DownloadManager.REPOSITORY_KEY, repoKey)
            showAbout(document)
        } catch (e: BookException) {
            Log.e(TAG, "Error expanding SwordBookMetaData for $document", e)
            instance.showErrorMsg(R.string.error_occurred, e)
        }
    }

    private fun handleDelete(documents: List<Book>) {
        for (document in documents) {
            if (documentControl.canDelete(document)) {
                val msg: CharSequence = getString(R.string.delete_doc, document.name)
                AlertDialog.Builder(this)
                    .setMessage(msg).setCancelable(true)
                    .setPositiveButton(R.string.yes
                    ) { dialog, buttonId ->
                        try {
                            Log.d(TAG, "Deleting:$document")
                            documentControl.deleteDocument(document)

                            // the doc list should now change
                            reloadDocuments()
                        } catch (e: Exception) {
                            Log.e(TAG, "Deleting document crashed", e)
                            instance.showErrorMsg(R.string.error_occurred, e)
                        }
                    }
                    .setNegativeButton(R.string.no, null)
                    .create()
                    .show()
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
                        Log.d(TAG, "Deleting index:$document")
                        swordDocumentFacade.deleteDocumentIndex(document)
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

    /** about display is generic so handle it here
     */
    private fun showAbout(document: Book) {

        //get about text
        var about = document.bookMetaData.getProperty("About")
        if (about != null) {
            // either process the odd formatting chars in about
            about = about.replace("\\pard", "")
            about = about.replace("\\par", "\n")
        } else {
            // or default to name if there is no About
            about = document.name
        }

        val shortPromo = document.bookMetaData.getProperty(SwordBookMetaData.KEY_SHORT_PROMO)

        if(shortPromo != null) {
            about += "\n\n${shortPromo}"
        }

        // Copyright and distribution information
        val shortCopyright = document.bookMetaData.getProperty(SwordBookMetaData.KEY_SHORT_COPYRIGHT)
        val copyright = document.bookMetaData.getProperty(SwordBookMetaData.KEY_COPYRIGHT)
        val distributionLicense = document.bookMetaData.getProperty(SwordBookMetaData.KEY_DISTRIBUTION_LICENSE)
        var copyrightMerged = ""
        if (StringUtils.isNotBlank(shortCopyright)) {
            copyrightMerged += shortCopyright
        } else if (StringUtils.isNotBlank(copyright)) {
            copyrightMerged += "\n\n" + copyright
        }
        if (StringUtils.isNotBlank(distributionLicense)) {
            copyrightMerged += "\n\n" +distributionLicense
        }
        if (StringUtils.isNotBlank(copyrightMerged)) {
            val copyrightMsg = getString(R.string.module_about_copyright, copyrightMerged)
            about += "\n\n" + copyrightMsg
        }

        // add version
        val existingDocument = swordDocumentFacade.getDocumentByInitials(document.initials)
        val existingVersion = existingDocument?.bookMetaData?.getProperty("Version")
        val existingVersionDate = existingDocument?.bookMetaData?.getProperty("SwordVersionDate") ?: "-"

        val inDownloadScreen = this is DownloadActivity

        val versionLatest = document.bookMetaData.getProperty("Version")
        val versionLatestDate = document.bookMetaData.getProperty("SwordVersionDate") ?: "-"

        val versionMessageInstalled = if(existingVersion != null)
            getString(R.string.module_about_installed_version, Version(existingVersion).toString(), existingVersionDate)
        else null

        val versionMessageLatest = if(versionLatest != null)
            getString((
                if(inDownloadScreen)
                    R.string.module_about_latest_version
                else
                    R.string.module_about_installed_version),
                Version(versionLatest).toString(), versionLatestDate)
        else null

        if(versionMessageLatest != null) {
            about += "\n\n" + versionMessageLatest
            if(versionMessageInstalled != null && inDownloadScreen)
                about += "\n" + versionMessageInstalled
        }

        val history = document.bookMetaData.getValues("History")
        if(history != null) {
            about += "\n\n" + getString(R.string.about_version_history, "\n" +
                history.reversed().joinToString("\n"))
        }

        // add versification
        if (document is SwordBook) {
            val versification = document.versification
            val versificationMsg = getString(R.string.module_about_versification, versification.name)
            about += "\n\n" + versificationMsg
        }

        // add id
        if (document is SwordBook) {
            val repoName = document.getProperty(DownloadManager.REPOSITORY_KEY)
            val repoMessage = if(repoName != null) getString(R.string.module_about_repository, repoName) else ""
            val osisIdMessage = getString(R.string.module_about_osisId, document.initials)
            about += """


                $osisIdMessage
                
                $repoMessage
                """.trimIndent()
        }
        about = about.replace("\n", "<br>")
        val spanned = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(about, Html.FROM_HTML_MODE_LEGACY)
        } else {
            Html.fromHtml(about)
        }

        val d = AlertDialog.Builder(this)
            .setMessage(spanned)
            .setCancelable(false)
            .setPositiveButton(R.string.okay) { dialog, buttonId ->
                //do nothing
            }.create()
        d.show()
        d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
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
        selectedDocumentFilterNo = 0
    }

    fun setLayoutResource(layoutResource: Int) {
        this.layoutResource = layoutResource
    }

    companion object {
        private val DOCUMENT_TYPE_SPINNER_FILTERS = arrayOf(
            BookFilters.getAll(),
            BookFilters.getBibles(),
            BookFilters.getCommentaries(),
            BookFilters.getDictionaries(),
            BookFilters.getGeneralBooks(),
            BookFilters.getMaps()
        )
        private var lastSelectedLanguage // allow sticky language selection
            : Language? = null
        private const val TAG = "DocumentSelectionBase"
    }

}
