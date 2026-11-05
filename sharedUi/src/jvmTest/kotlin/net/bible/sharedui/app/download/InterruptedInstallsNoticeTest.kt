package net.bible.sharedui.app.download

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import net.bible.android.control.download.PendingRepoInstall
import net.bible.android.database.SwordDocumentInfo
import net.bible.sharedcore.platform.AppJson
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedui.strings.iosStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** #3973: an install stopped with the app is reported at the next launch with a retry, never silently missing. */
class InterruptedInstallsNoticeTest {
    private val strings = iosStrings("en")
    private val kjv = PendingRepoInstall("CrossWire", "KJV")
    private val nasb = PendingRepoInstall("Lockman (CrossWire)", "NASB")

    private val asked = mutableListOf<AppDialogRequest>()
    private val forgotten = mutableListOf<PendingRepoInstall>()
    private var opened: String? = null

    private fun show(pending: List<PendingRepoInstall>, answer: AppDialogResult) = runBlocking {
        InterruptedInstallsNotice.show(
            interrupted = { pending },
            forget = { forgotten.addAll(it) },
            ask = { asked.add(it); answer },
            strings = strings,
            openDownload = { opened = it },
        )
    }

    @Test
    fun nothingInterruptedAsksNothing() {
        show(emptyList(), AppDialogResult.Ok)
        assertTrue(asked.isEmpty())
        assertNull(opened)
    }

    @Test
    fun interruptedInstallIsReportedByNameAndRetryOpensTheDownloadOfExactlyThoseDocuments() {
        show(listOf(kjv, nasb), AppDialogResult.Ok)
        val message = (asked.single() as AppDialogRequest.Message).message
        assertTrue("KJV" in message && "NASB" in message, message)
        val requested = AppJson.decodeFromString(ListSerializer(SwordDocumentInfo.serializer()), opened!!)
        assertEquals(listOf("KJV" to "CrossWire", "NASB" to "Lockman (CrossWire)"), requested.map { it.initials to it.repository })
        // Still pending until the new download settles, so a second interruption is reported again.
        assertTrue(forgotten.isEmpty())
    }

    @Test
    fun explicitDeclineForgetsTheRequests() {
        show(listOf(kjv), AppDialogResult.Neutral)
        assertEquals(listOf(kjv), forgotten)
        assertNull(opened)
    }

    @Test
    fun droppedDialogKeepsTheRequestsForTheNextLaunch() {
        // AppDialogController.cancelAll answers Cancel: the user did not decide anything.
        show(listOf(kjv), AppDialogResult.Cancel)
        assertTrue(forgotten.isEmpty())
        assertNull(opened)
    }
}
