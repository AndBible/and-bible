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
package net.bible.android.view.activity.download

import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.activity.databinding.DocumentListItemBinding
import net.bible.android.control.download.DownloadControl
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.base.RecommendedDocuments
import net.bible.service.common.Ref
import net.bible.service.download.DownloadManager
import org.apache.commons.lang3.StringUtils
import org.crosswire.common.util.Version
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookException
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * nice example here: http://shri.blog.kraya.co.uk/2010/04/19/android-multi-line-select-list/
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class DocumentDownloadItemAdapter(
    context: Context,
    private val downloadControl: DownloadControl,
    private val recommendedDocuments: Ref<RecommendedDocuments>
) : ArrayAdapter<Book>(context, R.layout.document_list_item, ArrayList<Book>())
{
    private lateinit var bindings: DocumentListItemBinding

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val document = getItem(position)!!

        bindings = if (convertView == null) {
            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            DocumentListItemBinding.inflate(inflater, parent, false)
        } else {
            DocumentListItemBinding.bind(convertView)
        }

        val view = (convertView?: bindings.root) as DocumentListItem
        view.binding = bindings

        // remember which item is being shown
        view.document = document
        view.recommendedDocuments = recommendedDocuments.value

        view.setIcons(downloadScreen = true)
        bindings.undoButton.setOnClickListener {
            downloadControl.cancelDownload(document)
        }

        // add function for document information
        bindings.aboutButton.setOnClickListener {
            handleAbout(document)
        }


        view.updateControlState(downloadControl.getDocumentStatus(document))

        // Set value for the first text field
        // eBible repo uses abbreviation for initials and initials now contains the repo name!!!
        // but helpfully JSword uses initials if abbreviation does not exist, as will be the case for all other repos.
        val initials = document.abbreviation
        bindings.documentAbbreviation.text = initials

        // set value for the second text field
        bindings.documentName.text = document.name
        return view
    }

    private fun handleAbout(document: Book) {
        val TAG = "DownloadItemAdapter"
        try {
            // ensure repo key is retained but reload sbmd to ensure About text is loaded
            val sbmd = document.bookMetaData as SwordBookMetaData
            val repoKey = sbmd.getProperty(DownloadManager.REPOSITORY_KEY)
            sbmd.reload()
            sbmd.setProperty(DownloadManager.REPOSITORY_KEY, repoKey)
            GlobalScope.launch(Dispatchers.Main) { showAbout(this, document) }
        } catch (e: BookException) {
            Log.e(TAG, "Error expanding SwordBookMetaData for $document", e)
            Dialogs.instance.showErrorMsg(R.string.error_occurred, e)
        }
    }

    /** about display is generic so handle it here
     */
    suspend fun showAbout(context: CoroutineScope, document: Book) {
        var about = "<b>${document.name}</b>\n\n"
        about += document.bookMetaData.getProperty("About") ?: ""
        // either process the odd formatting chars in about
        about = about.replace("\\pard", "")
        about = about.replace("\\par", "\n")

        val shortPromo = document.bookMetaData.getProperty(SwordBookMetaData.KEY_SHORT_PROMO)

        if(shortPromo != null) {
            about += "\n\n${shortPromo}"
        }

        // Copyright and distribution information
        val shortCopyright = document.bookMetaData.getProperty(SwordBookMetaData.KEY_SHORT_COPYRIGHT)
        val copyright = document.bookMetaData.getProperty(SwordBookMetaData.KEY_COPYRIGHT)
        val distributionLicense = document.bookMetaData.getProperty(SwordBookMetaData.KEY_DISTRIBUTION_LICENSE)
        val unlockInfo = document.bookMetaData.getProperty(SwordBookMetaData.KEY_UNLOCK_INFO)
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
            val copyrightMsg = BibleApplication.application.getString(R.string.module_about_copyright, copyrightMerged)
            about += "\n\n" + copyrightMsg
        }
        if(unlockInfo != null) {
            about += "\n\n<b>${BibleApplication.application.getString(R.string.unlock_info)}</b>\n\n$unlockInfo"
        }

        // add version
        val existingDocument = Books.installed().getBook(document.initials)
        val existingVersion = existingDocument?.bookMetaData?.getProperty("Version")
        val existingVersionDate = existingDocument?.bookMetaData?.getProperty("SwordVersionDate") ?: "-"

        val inDownloadScreen = context is DownloadActivity

        val versionLatest = document.bookMetaData.getProperty("Version")
        val versionLatestDate = document.bookMetaData.getProperty("SwordVersionDate") ?: "-"

        val versionMessageInstalled = if(existingVersion != null)
            BibleApplication.application.getString(R.string.module_about_installed_version, Version(existingVersion).toString(), existingVersionDate)
        else null

        val versionMessageLatest = if(versionLatest != null)
            BibleApplication.application.getString((
                if (existingDocument != null)
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
            about += "\n\n" + BibleApplication.application.getString(R.string.about_version_history, "\n" +
                history.reversed().joinToString("\n"))
        }

        // add versification
        if (document is SwordBook) {
            val versification = document.versification
            val versificationMsg = BibleApplication.application.getString(R.string.module_about_versification, versification.name)
            about += "\n\n" + versificationMsg
        }

        // add id
        if (document is SwordBook) {
            val repoName = document.getProperty(DownloadManager.REPOSITORY_KEY)
            val repoMessage = if(repoName != null) BibleApplication.application.getString(R.string.module_about_repository, repoName) else ""
            val osisIdMessage = BibleApplication.application.getString(R.string.module_about_osisId, document.initials)
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
        suspendCoroutine<Any?> {
            val d = AlertDialog.Builder(getContext())
                .setMessage(spanned)
                .setCancelable(false)
                .setPositiveButton(R.string.okay) { dialog, buttonId ->
                    it.resume(null)
                }.create()
            d.show()
            val textView = d.findViewById<TextView>(android.R.id.message)!!
            textView.movementMethod = LinkMovementMethod.getInstance()
        }
    }

}
