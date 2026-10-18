package net.bible.android.view.activity.nav

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.nav.ports.AndroidDocumentPicker
import net.bible.android.view.activity.nav.ports.AndroidImagePicker
import net.bible.sharedcore.nav.NavRoutes
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
 * are built in a field initializer, so a full create-start-resume must not throw and the launchers must exist.
 */
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
            val ports = controller.get().hostPorts
            val picker = ports.documentPicker as AndroidDocumentPicker
            assertEquals(4, picker.launchers.size)
            assertTrue(picker.launchers.all { it != null })
            assertNotNull((ports.imagePicker as AndroidImagePicker).registeredLauncher)
        } finally { controller.close() }
    }
}
