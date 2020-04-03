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
import android.widget.AdapterView
import android.widget.AdapterView.OnItemLongClickListener
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import kotlinx.android.synthetic.main.document_selection.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.document.DocumentControl
import net.bible.android.view.activity.base.Dialogs.Companion.instance
import net.bible.android.view.activity.base.ListActionModeHelper.ActionModeActivity
import net.bible.android.view.activity.download.isRecommended
import net.bible.service.common.CommonUtils
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
    private var selectedDocumentFilterNo = 0

    // language spinner
    private val languageList = ArrayList<Language>()
    protected var selectedLanguageNo = -1
    lateinit var langArrayAdapter: ArrayAdapter<Language>

    var isPopulated = false

    protected val recommendedDocuments : RecommendedDocuments by lazy {
        val jsonString = String(
            assets.open("recommended_documents.json").readBytes()
        )
        Json(CommonUtils.JSON_CONFIG).parse(RecommendedDocuments.serializer(), jsonString)
    }

    // the document list
    private var allDocuments = ArrayList<Book>()

    //TODO just use displayedDocuments with a model giving 2 lines in list
    var displayedDocuments = ArrayList<Book>()

    @Inject lateinit var documentControl: DocumentControl

    lateinit var listActionModeHelper: ListActionModeHelper
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
            this@DocumentSelectionBase.filterDocuments()
        }

        languageSpinner.setOnClickListener {languageSpinner.showDropDown()}
        languageSpinner.addTextChangedListener( object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val langString = s.toString()
                if(langString.isEmpty()) {
                    selectedLanguageNo = -1
                    this@DocumentSelectionBase.filterDocuments()
                } else {
                    val langIdx = languageList.indexOfFirst {it.name == langString}
                    if(langIdx != -1) {
                        selectedLanguageNo = langIdx
                        this@DocumentSelectionBase.filterDocuments()
                    }
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

        })

        langArrayAdapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item,
            languageList
        )
    }

    open fun setDefaultLanguage() {
        if (selectedLanguageNo == -1) {
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

    private fun reloadDocuments() {
        populateMasterDocumentList(false)
    }

    protected open fun showPreLoadMessage() {
        // default to no message
    }

    protected fun populateMasterDocumentList(refresh: Boolean) {
        isPopulated = false
        Log.d(TAG, "populate Master Document List")
        GlobalScope.launch {

            withContext(Dispatchers.Main) {
                loadingIndicator.visibility = View.VISIBLE
                showPreLoadMessage()
            }
            withContext(Dispatchers.Default) {
                try {
                    allDocuments.clear()
                    allDocuments.addAll(getDocumentsFromSource(refresh))
                    Log.i(TAG, "Number of documents:" + allDocuments.size)
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting documents", e)
                    instance.showErrorMsg(R.string.error_occurred, e)
                }
            }
            withContext(Dispatchers.Main) {
                try {
                    populateLanguageList()
                    isPopulated = true
                    setDefaultLanguage()
                    filterDocuments()
                    languageSpinner.setAdapter(langArrayAdapter)
                } finally {
                    loadingIndicator.visibility = View.GONE
                }
            }
        }
    }

    /** a spinner has changed so refilter the doc list
     */
    private fun filterDocuments() {
        if(!isPopulated) return
        try {
            // documents list has changed so force action mode to exit, if displayed, because selections are invalidated
            listActionModeHelper.exitActionMode()

            // re-filter documents
            if (allDocuments.size > 0) {
                Log.d(TAG, "filtering documents")
                displayedDocuments.clear()
                val lang = selectedLanguage
                for (doc in allDocuments) {
                    val filter = DOCUMENT_TYPE_SPINNER_FILTERS[selectedDocumentFilterNo]
                    if (filter.test(doc) && (lang == null || doc.language == lang) ) {
                        displayedDocuments.add(doc)
                    }
                }

                displayedDocuments.sortWith(
                    compareBy (
                        {!it.isRecommended(recommendedDocuments)},
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
                notifyDataSetChanged()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initialising view", e)
            Toast.makeText(this, getString(R.string.error) + " " + e.message, Toast.LENGTH_SHORT).show()
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
                langArrayAdapter.notifyDataSetChanged()
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
                    .setPositiveButton(R.string.okay
                    ) { dialog, buttonId ->
                        try {
                            Log.d(TAG, "Deleting:$document")
                            documentControl.deleteDocument(document)

                            // the doc list should now change
                            reloadDocuments()
                        } catch (e: Exception) {
                            instance.showErrorMsg(R.string.error_occurred, e)
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
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

        // Copyright and distribution information
        val shortCopyright = document.bookMetaData.getProperty(SwordBookMetaData.KEY_SHORT_COPYRIGHT)
        val copyright = document.bookMetaData.getProperty(SwordBookMetaData.KEY_COPYRIGHT)
        val distributionLicense = document.bookMetaData.getProperty(SwordBookMetaData.KEY_DISTRIBUTION_LICENSE)
        var copyrightMerged = ""
        if (StringUtils.isNotBlank(shortCopyright)) {
            copyrightMerged += """
                $shortCopyright

                """.trimIndent()
        } else if (StringUtils.isNotBlank(copyright)) {
            copyrightMerged += """
                $copyright

                """.trimIndent()
        }
        if (StringUtils.isNotBlank(distributionLicense)) {
            copyrightMerged += """
                $distributionLicense

                """.trimIndent()
        }
        if (StringUtils.isNotBlank(copyrightMerged)) {
            val copyrightMsg = BibleApplication.application.getString(R.string.about_copyright, copyrightMerged)
            about += """


                $copyrightMsg
                """.trimIndent()
        }

        // add version
        val version = document.bookMetaData.getProperty("Version")
        if (version != null) {
            val versionObj = Version(version)
            val versionMsg = BibleApplication.application.getString(R.string.about_version, versionObj.toString())
            about += """


                $versionMsg
                """.trimIndent()
        }

        // add versification
        if (document is SwordBook) {
            val versification = document.versification
            val versificationMsg = BibleApplication.application.getString(R.string.about_versification, versification.name)
            about += """


                $versificationMsg
                """.trimIndent()
        }

        // add id
        if (document is SwordBook) {
            val osisIdMessage = BibleApplication.application.getString(R.string.about_osisId, document.osisID)
            about += """


                $osisIdMessage
                """.trimIndent()
        }
        AlertDialog.Builder(this)
            .setMessage(about)
            .setCancelable(false)
            .setPositiveButton(R.string.okay) { dialog, buttonId ->
                //do nothing
            }.create().show()
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

    /** map between book category and item no
     */
    fun setSelectedBookCategory(bookCategory: BookCategory?) {
        selectedDocumentFilterNo = when (bookCategory) {
            BookCategory.BIBLE -> 0
            BookCategory.COMMENTARY -> 1
            BookCategory.DICTIONARY -> 2
            BookCategory.GENERAL_BOOK -> 3
            BookCategory.MAPS -> 4
            else -> 0
        }
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
