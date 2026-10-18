package net.bible.android.view.activity.nav.ports

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.sharedui.app.ports.DocumentRef
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * A picker that is open while the Activity is recreated: the registry redelivers its result to the launcher the
 * new instance registers under the same key, before any host awaits it. [AndroidDocumentPicker] must keep that
 * result for `resumeDocumentTree` / `resumeOpenDocuments` instead of dropping it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class)
class AndroidDocumentPickerOrphanResultTest {
    /** Registers the picker in a field initializer, as the nav host does, so it is ready before STARTED. */
    class PickerActivity : ComponentActivity() {
        val picker = AndroidDocumentPicker(this)
    }

    private val treeUri = Uri.parse("content://tree/folder")

    /** Launches [launch] on the first instance, recreates, and returns the new instance and the request code. */
    private fun recreatedWithPickOpen(launch: suspend (AndroidDocumentPicker) -> Unit): Pair<PickerActivity, Int> {
        val controller = Robolectric.buildActivity(PickerActivity::class.java).setup()
        val first = controller.get()
        GlobalScope.async(Dispatchers.Unconfined, start = CoroutineStart.DEFAULT) { launch(first.picker) }
        val requestCode = shadowOf(first).nextStartedActivityForResult.requestCode
        controller.recreate()
        return controller.get() to requestCode
    }

    @Test
    fun redeliveredFolderIsKeptForTheNewInstance() {
        val (second, rc) = recreatedWithPickOpen { it.openDocumentTree() }
        second.activityResultRegistry.dispatchResult(rc, Activity.RESULT_OK, Intent().setData(treeUri))
        assertEquals(DocumentRef(treeUri.toString()), runBlocking { second.picker.resumeDocumentTree() })
        // Claimed once: the buffer is empty afterwards.
        val again = GlobalScope.async(Dispatchers.Unconfined) { second.picker.resumeDocumentTree() }
        assertFalse(again.isCompleted)
        again.cancel()
    }

    @Test
    fun redeliveredCancelledFolderResumesAsNull() {
        val (second, rc) = recreatedWithPickOpen { it.openDocumentTree() }
        second.activityResultRegistry.dispatchResult(rc, Activity.RESULT_CANCELED, null)
        assertNull(runBlocking { second.picker.resumeDocumentTree() })
    }

    @Test
    fun redeliveredDocumentsAreKept() {
        val (second, rc) = recreatedWithPickOpen { it.openDocuments(listOf("text/*"), multiple = true) }
        val uri = Uri.parse("content://docs/a.md")
        second.activityResultRegistry.dispatchResult(rc, Activity.RESULT_OK, Intent().setData(uri))
        assertEquals(listOf(DocumentRef(uri.toString())), runBlocking { second.picker.resumeOpenDocuments() })
    }

    @Test
    fun resumeBeforeTheResultArrivesWaitsForIt() {
        val (second, rc) = recreatedWithPickOpen { it.openDocumentTree() }
        val resumed = GlobalScope.async(Dispatchers.Unconfined) { second.picker.resumeDocumentTree() }
        assertFalse(resumed.isCompleted, "nothing redelivered yet")
        second.activityResultRegistry.dispatchResult(rc, Activity.RESULT_OK, Intent().setData(treeUri))
        assertEquals(DocumentRef(treeUri.toString()), runBlocking { resumed.await() })
    }

    @Test
    fun aNewTreePickDiscardsAnUnclaimedStaleOrphan() {
        val (second, rc) = recreatedWithPickOpen { it.openDocumentTree() }
        second.activityResultRegistry.dispatchResult(rc, Activity.RESULT_OK, Intent().setData(treeUri))   // orphan, never claimed
        val fresh = GlobalScope.async(Dispatchers.Unconfined) { second.picker.openDocumentTree() }
        val newUri = Uri.parse("content://tree/new")
        second.activityResultRegistry.dispatchResult(shadowOf(second).nextStartedActivityForResult.requestCode, Activity.RESULT_OK, Intent().setData(newUri))
        assertEquals(DocumentRef(newUri.toString()), runBlocking { fresh.await() })
        val resumed = GlobalScope.async(Dispatchers.Unconfined) { second.picker.resumeDocumentTree() }
        assertFalse(resumed.isCompleted, "the stale orphan must not be returned")
        resumed.cancel()
    }

    @Test
    fun aNewDocumentsPickDiscardsAnUnclaimedStaleOrphan() {
        val (second, rc) = recreatedWithPickOpen { it.openDocuments(listOf("text/*"), multiple = true) }
        second.activityResultRegistry.dispatchResult(rc, Activity.RESULT_OK, Intent().setData(Uri.parse("content://docs/stale.md")))
        val fresh = GlobalScope.async(Dispatchers.Unconfined) { second.picker.openDocuments(listOf("text/*"), multiple = true) }
        val newUri = Uri.parse("content://docs/new.md")
        second.activityResultRegistry.dispatchResult(shadowOf(second).nextStartedActivityForResult.requestCode, Activity.RESULT_OK, Intent().setData(newUri))
        assertEquals(listOf(DocumentRef(newUri.toString())), runBlocking { fresh.await() })
        val resumed = GlobalScope.async(Dispatchers.Unconfined) { second.picker.resumeOpenDocuments() }
        assertFalse(resumed.isCompleted, "the stale orphan must not be returned")
        resumed.cancel()
    }
}
