@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)
package net.bible.sharedui.app.ports

import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedui.strings.keyed.StringTable
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.common.util.Version
import net.bible.sharedcore.docs.DocsLinks
import net.bible.sharedcore.docs.SiteLinks
import net.bible.service.download.DownloadManager

/** Platform-neutral document metadata and the classic unlock/upgrade dialog sequence. */
@kotlin.native.HiddenFromObjC
class DocumentDialogsCore(
    private val dialogs: AppDialogController,
    private val table: () -> StringTable,
    private val live: Boolean = false,
) {
    // Match Apache StringUtils/Character.isWhitespace (NBSP is not Java whitespace).
    private fun isBlank(value: String?) = value == null || value.all {
        it.isWhitespace() && it != '\u00a0' && it != '\u2007' && it != '\u202f'
    }
    private suspend fun await(build: () -> AppDialogRequest): AppDialogResult {
        var last = build() // Initial failures belong to the caller's error boundary, not composition.
        return dialogs.await(if (live) AppDialogRequest.Localized {
            try { build().also { last = it } }
            catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
            catch (e: Exception) {
                net.bible.sharedcore.log.Log.e("DocumentDialogs", "Could not refresh dialog text", e)
                last
            }
        } else last)
    }
    suspend fun showAbout(document: Book) = await { AppDialogRequest.Message(null, aboutHtml(document, Books.installed().getBook(document.initials)), table().text("okay"), cancellable = false) }
    fun aboutHtml(document: Book, installed: Book?): String {
        var about = "<b>${document.name}</b>\n\n"
        val badDocument = document.bookMetaData.getProperty("BadDocument")
        if(badDocument != null) {
            about += "<b>${table().fmt("warn_bad_document")}</b>\n\n"
        }
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
        if (!isBlank(shortCopyright)) {
            copyrightMerged += shortCopyright
        } else if (!isBlank(copyright)) {
            copyrightMerged += "\n\n" + copyright
        }
        if (!isBlank(distributionLicense)) {
            copyrightMerged += "\n\n" +distributionLicense
        }
        if (!isBlank(copyrightMerged)) {
            val copyrightMsg = table().fmt("module_about_copyright", copyrightMerged)
            about += "\n\n" + copyrightMsg
        }
        if(unlockInfo != null) {
            about += "\n\n<b>${table().fmt("unlock_info")}</b>\n\n$unlockInfo"
        }

        // add version
        val existingDocument = installed
        val existingVersion = existingDocument?.bookMetaData?.getProperty("Version")
        val existingVersionDate = existingDocument?.bookMetaData?.getProperty("SwordVersionDate") ?: "-"

        val versionLatest = document.bookMetaData.getProperty("Version")
        val versionLatestDate = document.bookMetaData.getProperty("SwordVersionDate") ?: "-"

        val versionMessageInstalled = if(existingVersion != null)
            table().fmt("module_about_installed_version",
                try {
                    Version(existingVersion).toString()
                } catch (e: Exception) {
                    net.bible.sharedcore.log.Log.e("DocumentDialogs", "Error parsing version $existingVersion", e)
                    existingVersion
                }, existingVersionDate)
        else null

        val versionMessageLatest = if(versionLatest != null)
            table().fmt((
                if (existingDocument != null)
                    "module_about_latest_version"
                else
                    "module_about_installed_version"),
                try {Version(versionLatest).toString()} catch(e: Exception) {versionLatest}, versionLatestDate)
        else null

        if(versionMessageLatest != null) {
            about += "\n\n" + versionMessageLatest
        }

        val history = document.bookMetaData.getValues("History")
        if(history != null) {
            about += "\n\n" + table().fmt("about_version_history", "\n" +
                history.reversed().joinToString("\n"))
        }

        // add versification
        if (document is SwordBook) {
            val versification = document.versification
            val versificationMsg = table().fmt("module_about_versification", versification.name)
            about += "\n\n" + versificationMsg
        }

        // add id
        if (document is SwordBook) {
            val repoName = document.getProperty(DownloadManager.REPOSITORY_KEY)
            val repoMessage = if(repoName != null) table().fmt("module_about_repository", repoName) else ""
            val osisIdMessage = table().fmt("module_about_osisId", document.initials)
            about += """


                $osisIdMessage
                
                $repoMessage
                """.trimIndent()
        }
        about = about.replace("\n", "<br>")
        return about
    }

    suspend fun unlockDocument(book: Book, storeKey: suspend (String) -> Unit): Boolean {
        var repeat = true
        while (repeat) {
            val answer = await {
                AppDialogRequest.TextInput(
                    title = table().fmt("give_passphrase_for_module", book.initials),
                    message = null,
                    initial = book.unlockKey ?: "",
                    confirmText = table().fmt("okay"),
                    dismissText = table().fmt("cancel"),
                    neutralText = table().fmt("show_unlock_info"),
                    cancellable = false,
                    draftKey = "unlock:${book.initials}",
                )
            }
            val passphrase: String? = when (answer) {
                is AppDialogResult.Text -> answer.value
                AppDialogResult.Neutral -> {
                    showAbout(book)
                    continue
                }
                else -> null
            }
            if (passphrase != null) {
                val success = book.unlock(passphrase)
                if (success) {
                    storeKey(passphrase)
                    return true
                }
            }
            repeat = await {
                AppDialogRequest.Confirm(
                    title = table().fmt("try_again_passphrase"),
                    message = null,
                    confirmText = table().fmt("yes"),
                    dismissText = table().fmt("no"),
                    cancellable = false,
                )
            } == AppDialogResult.Ok
        }
        return false
    }

    fun helpDialog(title: String, message: String, url: String) = AppDialogRequest.Message(
        title, message + "<br><br><i><a href=\"$url\">${table().text("help_read_more_link")}</a></i>", table().text("okay"), cancellable = true,
    )
    suspend fun confirmUpgrade(): Boolean = await {
        val t = table()
        AppDialogRequest.Confirm(t.text("bookmark_warning"), "${t.text("bookmark_warning2")}<br><br>${t.text("bookmark_warning4")}<br><br>${t.text("bookmark_warning3")}", t.text("yes"), t.text("cancel"), cancellable = false)
    } == AppDialogResult.Ok

    @kotlin.native.HiddenFromObjC
    data class HelpItem(val title: String, val text: String, val videoLink: String? = null, val docUrl: String? = null)

    fun helpNotice(help: List<HelpItem>, versionMsg: String? = null, okText: String = table().text("okay"), includeSponsor: Boolean = true): AppDialogRequest.Notice {
        val buyMessage = "<b>${table().text("buy_development2")}</b>: <a href=\"${SiteLinks.SHOP_URL}\">${table().text("buy_development")}</a>"
        var htmlMessage = ""

        for(helpItem in help) {
            val helpText = helpItem.text.replace("\n", "<br>")

            val links = mutableListOf<String>()
            if(helpItem.videoLink != null) {
                links.add("&bull;&nbsp;<i><a href=\"${helpItem.videoLink}\">${table().text("watch_tutorial_video")}</a></i>")
            }
            if(helpItem.docUrl != null) {
                links.add("&bull;&nbsp;<i><a href=\"${helpItem.docUrl}\">${table().text("help_read_more_link")}</a></i>")
            }
            val linksHtml = if(links.isNotEmpty()) "<br>${links.joinToString("<br>")}<br>" else ""

            htmlMessage += "<b>${helpItem.title}</b><br>$helpText<br>$linksHtml<br>"
        }

        val fullDocsLink = table().text("help_full_documentation_link")
        val fullDocsMessage = "<a href=\"${DocsLinks.BASE_URL}\">$fullDocsLink</a>"

        return AppDialogRequest.Notice(
                title = table().text("help"),
                showTitleLogo = true,
                blocks = listOfNotNull(
                    AppDialogRequest.NoticeBlock.Html(htmlMessage),
                    AppDialogRequest.NoticeBlock.Html(fullDocsMessage),
                    if (includeSponsor) AppDialogRequest.NoticeBlock.IconLine(AppDialogRequest.NoticeIcon.Money, "&nbsp;$buyMessage") else null,
                    if (versionMsg != null) AppDialogRequest.NoticeBlock.Html("<i>$versionMsg</i>") else null,
                ),
                confirmText = okText,
            )
    }
}
