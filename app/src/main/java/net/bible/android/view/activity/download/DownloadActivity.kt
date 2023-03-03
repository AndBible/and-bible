/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.view.activity.download

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.view.ActionMode
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.SharedConstants
import net.bible.android.activity.R
import net.bible.android.control.download.DocumentStatus
import net.bible.android.view.activity.base.DocumentSelectionBase
import net.bible.android.view.activity.base.RecommendedDocuments
import net.bible.android.view.activity.installzip.InstallZip
import net.bible.service.common.CommonUtils.json
import net.bible.service.common.CommonUtils.settings
import net.bible.service.db.DatabaseContainer
import net.bible.service.download.DownloadManager
import net.bible.service.download.FakeBookFactory
import net.bible.service.download.GenericFileDownloader
import net.bible.service.download.RepoFactory
import net.bible.service.download.isPseudoBook
import org.crosswire.common.progress.JobManager
import org.crosswire.common.util.Language
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.serializer
import net.bible.android.control.document.canDelete
import net.bible.android.control.event.ABEventBus
import net.bible.android.database.SwordDocumentInfo
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.base.installedDocument
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.service.common.CommonUtils
import net.bible.service.download.urlPrefix
import java.text.Collator

/**
 * Choose Document (Book) to download
 *
 * NotificationManager with ProgressBar example here:
 * http://united-coders.com/nico-heid/show-progressbar-in-notification-area-like-google-does-when-downloading-from-android
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */

val Book.isInstalled: Boolean get() = Books.installed().getBook(initials) != null


open class DownloadActivity : DocumentSelectionBase(
    R.menu.download_documents, R.menu.document_context_menu,
    enableLoadingIndicator = false,
) {
    override fun onPrepareActionMode(mode: ActionMode, menu: Menu, selectedItemPositions: List<Int>): Boolean {
        if(selectedItemPositions.isNotEmpty()) {
            val installedDoc = displayedDocuments[selectedItemPositions[0]].installedDocument
            val isInstalled = installedDoc != null
            menu.findItem(R.id.delete).isVisible = isInstalled && installedDoc.canDelete
            menu.findItem(R.id.delete_index).isVisible = isInstalled
            menu.findItem(R.id.unlock).isVisible = isInstalled && displayedDocuments[selectedItemPositions[0]].isEnciphered
        }
        return super.onPrepareActionMode(mode, menu, selectedItemPositions)
    }

    private val genericFileDownloader = GenericFileDownloader(this) {
        invalidateOptionsMenu()
    }
    private val downloadManager = DownloadManager {
        invalidateOptionsMenu()
    }

    private val hasErrors get() = genericFileDownloader.errors.isNotEmpty() || downloadManager.failedRepos.isNotEmpty()

    private val repoFactory = RepoFactory(downloadManager)
    private val booksNotFound = ArrayList<String>()
    private val docDao get() = DatabaseContainer.db.swordDocumentInfoDao()

    private suspend fun loadRecommendedDocuments() = withContext(Dispatchers.IO) {
        val source = URI("https://andbible.github.io/data/${SharedConstants.RECOMMENDED_JSON}")
        val target = File(SharedConstants.MODULE_DIR, SharedConstants.RECOMMENDED_JSON)
        genericFileDownloader.downloadFile(source, target, "Recommendations", reportError = !target.canRead())
        if (target.canRead()) {
            val jsonString = String(target.readBytes())
            recommendedDocuments.value = json.decodeFromString(RecommendedDocuments.serializer(), jsonString)
        } else {
            Log.e(TAG, "Could not load recommendations")
        }
    }

    private suspend fun loadDefaultDocuments() = withContext(Dispatchers.IO) {
        if(!downloadDefaults) return@withContext

        val source = URI("https://andbible.github.io/data/${SharedConstants.DEFAULT_JSON}")
        val target = File(SharedConstants.MODULE_DIR, SharedConstants.DEFAULT_JSON)
        genericFileDownloader.downloadFile(source, target, "Defaults", reportError = !target.canRead())
        if(target.canRead()) {
            val jsonString = String(target.readBytes())
            defaultDocuments.value = json.decodeFromString(RecommendedDocuments.serializer(), jsonString)
        } else {
            Log.e(TAG, "Could not load default document list")
        }
    }

    private suspend fun loadPseudoBooks() = withContext(Dispatchers.IO) {
        val source = URI("https://andbible.github.io/data/${SharedConstants.PSEUDO_BOOKS}")
        val target = File(SharedConstants.MODULE_DIR, SharedConstants.PSEUDO_BOOKS)
        genericFileDownloader.downloadFile(source, target, "Pseudo books", reportError = !target.canRead())
        if(target.canRead()) {
            val jsonString = String(target.readBytes())
            pseudoBooks.value = json.decodeFromString(serializer(), jsonString)
        } else {
            Log.e(TAG, "Could not load pseudo book list")
        }
    }

    private suspend fun askIfWantToProceed(): Boolean = withContext(Dispatchers.Main) {
        if(settings.getBoolean("download_do_not_ask", false))
            true

        else
            suspendCoroutine<Boolean> {
                AlertDialog.Builder(this@DownloadActivity)
                    .setTitle(R.string.download_question_title)
                    .setMessage(getString(R.string.download_question_message))
                    .setPositiveButton(R.string.yes) {_, _ -> it.resume(true)}
                    .setNegativeButton(R.string.do_not_ask_again) {_, _ ->
                        settings.setBoolean("download_do_not_ask", true)
                        it.resume(true)
                    }
                    .setNeutralButton(R.string.cancel) {_, _ -> it.resume(false)}
                    .setOnCancelListener {_ -> it.resume(false)}
                    .show()
            }
    }

    /** Called when the activity is first created.  */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildActivityComponent().inject(this)

        // reconfigure the layout:
        //  * ensure the ProgressBar for the ChooseDocument activity is hidden
        //  * make the SwipeRefreshLayout visible
        //  * reparent the ListView to be in the SwipeRefreshLayout
        binding.loadingIndicator.visibility = View.GONE
        binding.swipeRefresh.visibility = View.VISIBLE
        with(binding.list) {
            (parent as? ViewGroup)?.removeView(this)
            binding.swipeRefresh.addView(this)
        }
        // configure the SwipeRefreshLayout
        lifecycleScope.launchWhenResumed {
            isLoading.collect { binding.swipeRefresh.isRefreshing = it }
        }
        binding.swipeRefresh.setOnRefreshListener {
            binding.freeTextSearch.setText("")
            // prepare the document list view - done in another thread
            lifecycleScope.launch {
                downloadDocJson()
                populateMasterDocumentList(true)
                updateLastRepoRefreshDate()
                notifyDataSetChanged()
            }
        }

        lifecycleScope.launch {
            if (!askIfWantToProceed()) {
                finish()
                return@launch
            }

            CommonUtils.requestNotificationPermission(this@DownloadActivity)

            invalidateOptionsMenu()

            downloadDocJson()

            documentItemAdapter = DocumentDownloadItemAdapter(
                this@DownloadActivity, downloadControl, recommendedDocuments)
            initialiseView()
            // in the basic flow we force the user to download a bible
            binding.documentTypeSpinner.isEnabled = true

            withContext(Dispatchers.Default) {
                if (isRepoBookListOld) {
                    // prepare the document list view - done in another thread
                    populateMasterDocumentList(true)

                    // restart refresh timeout
                    updateLastRepoRefreshDate()
                }
                else {
                    // normal user downloading with recent doc list
                    populateMasterDocumentList(false)
                }

                withContext(Dispatchers.Main) {
                    invalidateOptionsMenu()
                    val bookStr = intent.extras?.getString(DOCUMENT_IDS_EXTRA)
                    if (bookStr != null) {
                        val booksToDownload: List<SwordDocumentInfo> = json.decodeFromString(serializer(), bookStr)
                        downloadRequestedBooks(booksToDownload)

                        if (booksNotFound.size > 0) {
                            warnUserBooksNotDownloaded()
                        }
                        filterDocuments()
                    }

                    val defaults = defaultDocuments.value
                    if(downloadDefaults && defaults != null) {
                        for(l in listOf(defaults.bibles["en"], defaults.commentaries["en"], defaults.addons["en"], defaults.books["en"], defaults.dictionaries["en"], defaults.maps["en"])) {
                            val l2 = l?.map {
                                if(it.contains("::")) {
                                    val (initials, repository) = it.split("::")
                                    SwordDocumentInfo(initials = initials, repository = repository, language = "en", abbreviation = "", name = "")
                                } else {
                                    SwordDocumentInfo(initials = it, repository = "", language = "en", abbreviation = "", name = "")
                                }
                            }
                            downloadRequestedBooks(l2)
                        }
                        filterDocuments()
                    }
                }
            }
        }
    }

    private val downloadDefaults get() = intent.extras?.getBoolean("download-recommended") == true

    /**
     * Shows a dialog explaining that some books were not downloaded
     */
    private fun warnUserBooksNotDownloaded() {
        val books = booksNotFound.toTypedArray()
        // books here is a list of osisIds
        // look up their full names in the local database
        lifecycleScope.launch {
            val notInstalled: Array<String> = books.map {
                docDao.getBook(it)?.name
            }.filterNotNull().toTypedArray()
            withContext(Dispatchers.Main) {
                val inflater = this@DownloadActivity.layoutInflater
                val v = inflater.inflate(R.layout.books_not_downloaded_dialog, null)

                // set up list view adapter
                val adapter = ArrayAdapter(this@DownloadActivity, R.layout.books_not_downloaded_list_item, notInstalled)
                val list = v.findViewById<ListView>(R.id.bookListView)
                list.adapter = adapter

                AlertDialog.Builder(this@DownloadActivity)
                    .setView(v)
                    .setPositiveButton(R.string.okay, null)
                    .show()
            }
        }
    }

    /**
     * Downloads the requested books, given a list of osisIds
     */
    private fun downloadRequestedBooks(osisIds: List<SwordDocumentInfo>?) {
        osisIds ?: return
        for (it in osisIds) {
            Log.i(TAG, "User request to download $it")
            val book: Book? = findBookByInitials(it.initials, if(it.repository == "") null else it.repository)
            if (book != null) {
                doDownload(book)
            } else {
                booksNotFound.add(it.initials)
            }
        }
    }

    /** if repo list not refreshed in last 30 days then it is old
     *
     * @return
     */
    private val isRepoBookListOld: Boolean
        get() {
            val repoRefreshDate = settings.getLong(REPO_REFRESH_DATE, 0)
            val today = Date()
            return (today.time - repoRefreshDate) / MILLISECS_IN_DAY > REPO_LIST_STALE_AFTER_DAYS
        }

    private fun updateLastRepoRefreshDate() {
        val today = Date()
        settings.setLong(REPO_REFRESH_DATE, today.time)
    }

    override fun showPreLoadMessage(refresh: Boolean) {
        val repositories = repoFactory.repositories.asSequence()
            .mapNotNull { downloadManager.getInstallerFor(it)?.urlPrefix }
            .toSortedSet( Collator.getInstance() )
            .joinToString("\n")

        val repoRefreshDate = settings.getLong(REPO_REFRESH_DATE, 0)
        val date = SimpleDateFormat.getDateInstance().format(Date(repoRefreshDate))

        val message =
            if(refresh) getString(R.string.download_refreshing_book_list) + "\n\n" + getString(R.string.download_source_message1) + "\n" + repositories
            else getString(R.string.download_source_last_updated, date)

        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override suspend fun getDocumentsFromSource(refresh: Boolean): List<Book> {
        downloadManager.refreshInstallManager()
        val docs = downloadControl.getDownloadableDocuments(repoFactory, refresh)
        return if(docs.isNotEmpty()) docs + FakeBookFactory.pseudoDocuments(pseudoBooks.value) else docs
    }

    override fun onStart() {
        super.onStart()
        downloadControl.startMonitoringDownloads()
    }

    override fun onStop() {
        super.onStop()
        downloadControl.stopMonitoringDownloads()
    }

    /**
     * Get normally sorted list of languages for the language selection spinner
     */
    override fun sortLanguages(languages: Collection<Language>?): List<Language> {
        return downloadControl.sortLanguages(languages)
    }

    /** user selected a document so download it
     *
     * @param selectedDocument
     */
    override fun handleDocumentSelection(selectedDocument: Book) {
        Log.i(TAG, "Document selected:" + selectedDocument.initials)
        try {
            manageDownload(selectedDocument)
        } catch (e: Exception) {
            Log.e(TAG, "Error on attempt to download", e)
            Toast.makeText(this, R.string.error_downloading, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showTooManyJobsDialog() {
        Log.i(TAG, "Too many jobs:" + JobManager.getJobCount())
        Dialogs.showErrorMsg(R.string.too_many_jobs)
    }

    private fun manageDownload(documentToDownload: Book?) {
        if (documentToDownload != null
            && downloadControl.getDocumentStatus(documentToDownload).documentInstallStatus  != DocumentStatus.DocumentInstallStatus.BEING_INSTALLED
            && !documentToDownload.isPseudoBook
        ) {
            AlertDialog.Builder(this)
                .setMessage(getText(R.string.download_document_confirm_prefix).toString() + " " + documentToDownload.name)
                .setCancelable(false)
                .setPositiveButton(R.string.okay) { dialog, id -> doDownload(documentToDownload) }
                .setNegativeButton(R.string.cancel) { dialog, id -> }.create().show()
        }
    }

    private val downloadScope = CoroutineScope(Dispatchers.Default)
    private fun doDownload(document: Book) = downloadScope.launch (Dispatchers.Main) {
        try {
            // the download happens in another thread
            downloadControl.downloadDocument(repoFactory, document)

            // update screen so the icon to the left of the book changes
            notifyDataSetChanged()
            ABEventBus.post(MainBibleActivity.UpdateMainBibleActivityDocuments())
        } catch (e: Exception) {
            Log.e(TAG, "Error on attempt to download", e)
            Toast.makeText(this@DownloadActivity, R.string.error_downloading, Toast.LENGTH_SHORT).show()
        }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.i(TAG, "onActivityResult:$resultCode")
        if (resultCode == DOWNLOAD_FINISH) {
            returnToPreviousScreen()
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menu.findItem(R.id.errors).isVisible = hasErrors
        return true
    }

    /**
     * on Click handlers
     */

    private suspend fun downloadDocJson() = coroutineScope {
        awaitAll(
            async { loadRecommendedDocuments() },
            async { loadDefaultDocuments() },
            async { loadPseudoBooks() }
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var isHandled = false
        when (item.itemId) {
            R.id.errors -> {
                var message = ""
                if(downloadManager.failedRepos.isNotEmpty()) {
                    message += getString(R.string.failed_repositories_message, downloadManager.failedRepos.joinToString(",\n"))
                }
                if(genericFileDownloader.errors.isNotEmpty()) {
                    message += getString(R.string.failed_downloads_message, genericFileDownloader.errors.joinToString(",\n"))
                }
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.download_errors))
                    .setMessage(message)
                    .setPositiveButton(R.string.okay, null)
                    .create().show()
            }
            R.id.installZip -> {
                val intent = Intent(this, InstallZip::class.java)
                lifecycleScope.launch {
                    awaitIntent(intent)
                    ABEventBus.post(MainBibleActivity.UpdateMainBibleActivityDocuments())
                }

                isHandled  = true
            }
            R.id.customRepositories -> {
                val intent = Intent(this, CustomRepositories::class.java)
                lifecycleScope.launch {
                    awaitIntent(intent)
                    populateMasterDocumentList(true)
                }
                isHandled  = true
            }
        }
        if (!isHandled) {
            isHandled = super.onOptionsItemSelected(item)
        }
        return isHandled
    }

    companion object {
        private const val REPO_REFRESH_DATE = "repoRefreshDate"
        private const val REPO_LIST_STALE_AFTER_DAYS: Long = 1
        private const val MILLISECS_IN_DAY = 1000 * 60 * 60 * 24.toLong()
        const val DOCUMENT_IDS_EXTRA = "documentIds"
        const val DOWNLOAD_FINISH = 1
        private const val TAG = "Download"
    }
}
