package net.bible.service.cloudsync.webdav

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [TEST_SDK])
class PrefsWebDavStateStoreTest {
    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("t", 0)

    @Test fun defaults() {
        val s = PrefsWebDavStateStore(prefs)
        assertEquals(Propagation.UNKNOWN, s.propagation)
        assertNull(s.certPin)
    }

    @Test fun roundTrip() {
        val s = PrefsWebDavStateStore(prefs)
        s.propagation = Propagation.YES
        s.certPin = CertPin("nas.local", "ABCD")
        val again = PrefsWebDavStateStore(prefs)
        assertEquals(Propagation.YES, again.propagation)
        assertEquals(CertPin("nas.local", "ABCD"), again.certPin)
        again.certPin = null
        assertNull(PrefsWebDavStateStore(prefs).certPin)
    }

    @Test fun configNormalisesFolder() {
        val c = WebDavConfig("https://nas.local:8443/dav", "u", "p", " //AndBible//sync/ ")
        assertEquals("AndBible/sync", c.baseFolder)
        assertEquals("nas.local", c.host)
        assertEquals("", WebDavConfig("https://h/", "u", "p", "  ").baseFolder)
    }
}
