package net.bible.android.view.activity.nav

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.test.resetComposeUiDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The port implementations call `registerForActivityResult`, which throws once the Activity is STARTED. They
 * are built in a field initializer; the first access happens only after resume here, so a lazy (late)
 * registration fails this test. The picks are driven end to end through the Activity's registry.
 */
@OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class HostPortsLauncherRegistrationTest {
    @Test
    fun portsRegisterLaunchersBeforeStarted() {
        resetComposeUiDispatcher()
        val controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.WELCOME),
        )
        try {
            controller.create().start().resume().visible()
            shadowOf(Looper.getMainLooper()).idle()
            val activity = controller.get()
            // Registration must have happened already (field initializer), not on first use below.
            val registeredAtCreate = registeredKeyCount(activity)
            val shadow = shadowOf(activity)
            val picked = Uri.parse("content://test/tree/1")

            val tree = GlobalScope.async(Dispatchers.Main, CoroutineStart.UNDISPATCHED) {
                activity.hostPorts.documentPicker.openDocumentTree()
            }
            val treeIntent = shadow.peekNextStartedActivityForResult()
            assertNotNull("openDocumentTree launched nothing", treeIntent)
            assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, treeIntent.intent.action)
            shadow.receiveResult(treeIntent.intent, Activity.RESULT_OK, Intent().setData(picked))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(DocumentRef(picked.toString()), tree.getCompleted())

            val image = GlobalScope.async(Dispatchers.Main, CoroutineStart.UNDISPATCHED) {
                activity.hostPorts.imagePicker.pickImage()
            }
            val imageIntent = shadow.peekNextStartedActivityForResult()
            assertNotNull("pickImage launched nothing", imageIntent)
            val photo = Uri.parse("content://test/photo/1")
            shadow.receiveResult(imageIntent.intent, Activity.RESULT_OK, Intent().setData(photo))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(photo.toString(), image.getCompleted())
            assertEquals(
                "a port registered an ActivityResult launcher lazily, after create()",
                registeredAtCreate, registeredKeyCount(activity),
            )
            assertTrue("no launchers were registered by create()", registeredAtCreate > 0)
        } finally { controller.close() }
    }

    /** Number of keys the Activity's registry has handed out (`ActivityResultRegistry.keyToRc`). */
    private fun registeredKeyCount(activity: androidx.activity.ComponentActivity): Int {
        val field = androidx.activity.result.ActivityResultRegistry::class.java.getDeclaredField("keyToRc")
        field.isAccessible = true
        return (field.get(activity.activityResultRegistry) as Map<*, *>).size
    }
}
