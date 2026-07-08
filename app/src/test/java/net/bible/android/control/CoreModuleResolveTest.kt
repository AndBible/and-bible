package net.bible.android.control

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.speak.SpeakControl
import net.bible.android.view.activity.speak.actionbarbuttons.SpeakActionBarButton
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Actually RESOLVES beans from a real Koin container (as opposed to
 * [CoreModuleVerifyTest], which only runs Koin's static [org.koin.test.verify.verify]).
 *
 * verify() special-cases `dagger.Lazy` and so did NOT catch that
 * `singleOf(::SpeakControl)` could not be constructed at runtime (SpeakControl's
 * constructor takes a `dagger.Lazy<TextToSpeechServiceManager>`, for which nothing
 * is registered — a real `get<SpeakControl>()` threw NoDefinitionFoundException).
 * This test would have caught that regression: it FAILS before the explicit
 * `single { SpeakControl(dagger.Lazy { ... }, get()) }` binding and PASSES after.
 *
 * The Koin container is the production one, started by [net.bible.android.BibleApplication.onCreate]
 * (via [TestBibleApplication]) with androidContext + coreModule.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CoreModuleResolveTest {

    @Test
    fun `SpeakControl resolves from a real Koin container`() {
        val koin = GlobalContext.get()
        // Previously threw NoDefinitionFoundException: No definition found for type 'dagger.Lazy'
        val speakControl = koin.get<SpeakControl>()
        assertNotNull(speakControl)
        // An action-bar button that depends (transitively) on SpeakControl also resolves.
        val speakButton = koin.get<SpeakActionBarButton>()
        assertNotNull(speakButton)
    }

    @After
    fun tearDown() {
        // Don't leak the container into other test classes; BibleApplication.onCreate
        // restarts it (its start is guarded on GlobalContext.getOrNull() == null).
        if (GlobalContext.getOrNull() != null) {
            stopKoin()
        }
    }
}
