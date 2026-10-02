package net.bible.android.view.compose

import android.content.Context
import android.media.AudioManager
import android.os.Looper
import android.view.KeyEvent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingHostPresence
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fix batch 5 F105: on a non-reading destination the host's volume keys page the registered Compose list,
 * under the same gates as `ActivityBase.onKeyDown` (pref on, no music).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class VolumeScrollHostTest {
    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    @Before
    fun firstTimeIsPinnedFalse() {
        firstTime = false
    }

    @After
    fun tearDown() {
        CommonUtils.settings.removeBoolean("volume_keys_scroll")
        controllers.forEach { it.close() }
        controllers.clear()
        ReadingHostPresence.setForeground(null)
        ReadingViewVisibility.setVisible(false)
        DatabaseResetter.resetDatabase()
    }

    private fun idleMain() {
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2))
    }

    private fun key(code: Int, action: Int = KeyEvent.ACTION_DOWN) = KeyEvent(action, code)

    /** A host on a non-reading route with a counting fake target registered. */
    private class Fixture(val activity: NavHostComposeActivity, val pages: MutableList<Float>)

    private fun hostOn(route: String): Fixture {
        val controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).also { controllers += it }
        controller.create().start().resume().visible()
        val activity = controller.get()
        idleMain()
        activity.navigateInGraph(route)
        idleMain()
        val nc = NavHostComposeActivity::class.java.getDeclaredField("navController").apply { isAccessible = true }
            .get(activity) as androidx.navigation.NavHostController?
        val current = nc?.currentDestination?.route
        if (route != NavRoutes.READING) {
            assertTrue(current?.substringBefore('?') == route, "host must be on $route, is on $current")
        }
        val pages = mutableListOf<Float>()
        val fake = object : androidx.compose.foundation.gestures.ScrollableState {
            override val isScrollInProgress = false
            override val canScrollForward = true
            override val canScrollBackward = true
            override fun dispatchRawDelta(delta: Float): Float = delta
            override suspend fun scroll(
                scrollPriority: androidx.compose.foundation.MutatePriority,
                block: suspend androidx.compose.foundation.gestures.ScrollScope.() -> Unit,
            ) {
                block(object : androidx.compose.foundation.gestures.ScrollScope {
                    override fun scrollBy(pixels: Float): Float { pages += pixels; return pixels }
                })
            }
        }
        activity.volumeScrollRegistry.register(fake) { 1000 }
        return Fixture(activity, pages)
    }

    @Test
    fun volumeDownConsumesAndPagesOnce() {
        CommonUtils.settings.setBoolean("volume_keys_scroll", true)
        val f = hostOn(NavRoutes.SETTINGS)
        assertTrue(f.activity.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, key(KeyEvent.KEYCODE_VOLUME_DOWN)))
        idleMain()
        assertTrue(f.pages.isNotEmpty(), "a page scroll must have been dispatched")
        assertTrue(f.pages.sum() > 0f, "volume down pages forward")
        assertTrue(f.activity.onKeyUp(KeyEvent.KEYCODE_VOLUME_DOWN, key(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_UP)),
            "onKeyUp must match onKeyDown")
    }

    @Test
    fun volumeUpPagesBackwards() {
        CommonUtils.settings.setBoolean("volume_keys_scroll", true)
        val f = hostOn(NavRoutes.SETTINGS)
        assertTrue(f.activity.onKeyDown(KeyEvent.KEYCODE_VOLUME_UP, key(KeyEvent.KEYCODE_VOLUME_UP)))
        idleMain()
        assertTrue(f.pages.sum() < 0f, "volume up pages backward")
    }

    @Test
    fun volumeKeysAreNotConsumedWhileMusicPlaysOrPrefIsOff() {
        val f = hostOn(NavRoutes.SETTINGS)

        CommonUtils.settings.setBoolean("volume_keys_scroll", false)
        assertFalse(f.activity.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, key(KeyEvent.KEYCODE_VOLUME_DOWN)), "pref off")
        assertFalse(f.activity.onKeyUp(KeyEvent.KEYCODE_VOLUME_DOWN, key(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_UP)), "pref off, key up")

        CommonUtils.settings.setBoolean("volume_keys_scroll", true)
        val audio = f.activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        shadowOf(audio).setIsMusicActive(true)
        assertFalse(f.activity.onKeyDown(KeyEvent.KEYCODE_VOLUME_UP, key(KeyEvent.KEYCODE_VOLUME_UP)), "music playing")
        assertFalse(f.activity.onKeyUp(KeyEvent.KEYCODE_VOLUME_UP, key(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_UP)), "music playing, key up")
        idleMain()
        assertEquals(emptyList(), f.pages, "no page may have been dispatched")
    }

    @Test
    fun onReadingTheRegistryIsNotConsulted() {
        CommonUtils.settings.setBoolean("volume_keys_scroll", true)
        val f = hostOn(NavRoutes.READING)
        // No reading handlers are published in this host, so the reading path declines; the registry must not take over.
        f.activity.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, key(KeyEvent.KEYCODE_VOLUME_DOWN))
        idleMain()
        assertEquals(emptyList(), f.pages, "the reading destination owns the volume keys")
    }
}
