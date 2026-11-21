package net.bible.android.view.widget

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.activity.R
import net.bible.android.control.widget.VerseChangeFrequency
import net.bible.android.control.widget.VerseRef
import net.bible.android.control.widget.VerseSource
import net.bible.android.control.widget.VerseWidgetConfig
import net.bible.android.control.widget.VerseWidgetContent
import net.bible.android.database.IdType
import net.bible.android.platform.AndroidWidgetStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = Application::class)
class VerseGlanceWidgetTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val strings get() = AndroidWidgetStrings(context)
    private fun s(id: Int) = context.getString(id)

    private val verse = VerseWidgetContent.Verse(
        ref = VerseRef("KJV", "KJV", 100, 100),
        reference = "Psalm 23:1", text = "The LORD is my shepherd; I shall not want.", bibleAbbreviation = "KJV",
        sourceName = "Memory verses", periodLabel = "Fri 20 Nov",
        link = "https://andbible.org/bible?ref=Ps.23.1",
    )
    private val config = VerseWidgetConfig(source = VerseSource.Label(IdType()), seed = 1L)

    private fun body(content: VerseWidgetContent, cfg: VerseWidgetConfig = config, w: Int = 334, h: Int = 182) =
        renderWidget(context, w, h) { VerseWidgetBody(content, cfg, strings, 7, w.dp, h.dp) }

    @Test fun verseShowsSourceDateReferenceAndText() {
        val v = body(verse)
        assertTrue(v.texts().contains("Memory verses · Fri 20 Nov"))
        assertTrue(v.texts().contains("The LORD is my shepherd; I shall not want."))
        assertTrue(v.texts().contains("Psalm 23:1 · KJV"))
        assertNotNull(v.byDescription(s(R.string.widget_shuffle)))
        assertNotNull(v.byDescription(s(R.string.widget_speak_verse)))
    }

    @Test fun hidingButtonsRemovesShuffleAndSpeak() {
        val v = body(verse, config.copy(showButtons = false))
        assertNull(v.byDescription(s(R.string.widget_shuffle)))
        assertNull(v.byDescription(s(R.string.widget_speak_verse)))
    }

    @Test fun hidingSourceRemovesEyebrow() {
        val v = body(verse, config.copy(showSourceAndDate = false))
        assertFalse(v.texts().any { it.contains("Memory verses") })
        assertTrue(v.texts().contains("Psalm 23:1 · KJV"))
    }

    @Test fun rootTapOpensReadAndBibleLink() {
        val v = body(verse, config.copy(showSourceAndDate = false))
        v.rootClickable()!!.performClick()
        val started = shadowOf(context as Application).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(Uri.parse(verse.link), started.data)
        assertEquals(context.packageName, started.`package`)
    }

    @Test fun unavailableTapOpensConfig() {
        val v = body(VerseWidgetContent.Unavailable(VerseWidgetContent.Unavailable.Reason.LABEL_MISSING))
        v.rootClickable()!!.performClick()
        val started = shadowOf(context as Application).nextStartedActivity
        assertEquals(VerseWidgetConfigActivity::class.java.name, started.component?.className)
        assertEquals(7, started.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
    }

    /** Review Focus 2: a deleted or emptied label leaves a calm explanation, never a crash. */
    @Test fun labelDeletedAfterConfigShowsUnavailable() {
        VerseWidgetContent.Unavailable.Reason.entries.forEach { reason ->
            val v = body(VerseWidgetContent.Unavailable(reason))
            assertTrue("$reason", v.texts().contains(strings.verseUnavailable(reason)))
        }
    }

    @Test fun configStoreRoundTripsEveryField() {
        val full = VerseWidgetConfig(
            VerseSource.Label(IdType()), VerseChangeFrequency.EVERY_3_HOURS, "KJV", showButtons = false,
            showSourceAndDate = false, seed = -5L, shuffleCount = 3,
        )
        listOf(
            full, full.copy(source = VerseSource.Random(VerseSource.RandomPart.PSALMS_PROVERBS), bibleInitials = null),
            full.copy(source = VerseSource.ReadingPlan, frequency = VerseChangeFrequency.ON_SHUFFLE),
        ).forEach { assertEquals(it, VerseWidgetConfigStore.decode(VerseWidgetConfigStore.encode(it))) }
        VerseWidgetConfigStore.save(context, 11, full)
        assertEquals(full, VerseWidgetConfigStore.load(context, 11))
        VerseWidgetConfigStore.delete(context, 11)
        assertNull(VerseWidgetConfigStore.load(context, 11))
    }

    @Test fun anUnreadableConfigLoadsAsNull() {
        context.getSharedPreferences("verse_widget_configs", 0).edit().putString("12", "{broken").commit()
        assertNull(VerseWidgetConfigStore.load(context, 12))
    }

    @Test fun deletingTheWidgetRemovesItsConfigAndAlarm() {
        VerseWidgetConfigStore.save(context, 13, config)
        VerseWidgetScheduler.schedule(context, 13, System.currentTimeMillis() + 3_600_000L)
        val alarms = shadowOf(context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager)
        assertEquals(1, alarms.scheduledAlarms.size)
        VerseWidgetReceiver().let { r -> r.callAsBroadcast { r.onDeleted(context, intArrayOf(13)) } }
        assertNull(VerseWidgetConfigStore.load(context, 13))
        assertEquals(0, alarms.scheduledAlarms.size)
    }

    @Test fun shuffleOnlyWidgetsScheduleNoAlarm() {
        VerseWidgetScheduler.schedule(context, 14, System.currentTimeMillis() + 3_600_000L)
        VerseWidgetScheduler.schedule(context, 14, null)
        assertEquals(0, shadowOf(context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager).scheduledAlarms.size)
    }

    @Test fun shuffleAdvancesTheCounterAndSoTheVerse() {
        VerseWidgetConfigStore.save(context, 15, config)
        val picks = (0..9).map {
            val c = VerseWidgetConfigStore.shuffle(context, 15)!!
            net.bible.android.control.widget.VerseSelector.pick(100, c.seed, 5L, c.shuffleCount)
        }
        assertEquals(10, VerseWidgetConfigStore.load(context, 15)!!.shuffleCount)
        assertTrue("shuffling reaches other verses", picks.toSet().size > 1)
        assertNull(VerseWidgetConfigStore.shuffle(context, 99))
    }

    @Test fun restoredWidgetsKeepTheirConfigUnderTheNewIds() {
        val other = config.copy(seed = 77L)
        VerseWidgetConfigStore.save(context, 21, config)
        VerseWidgetConfigStore.save(context, 22, other)
        // ids swap, and a widget without a config is skipped
        // The receiver's onRestored() is this call plus Glance's own redraw, which needs WorkManager and leaks an
        // uncaught exception into later tests of the JVM when run in a unit test.
        VerseWidgetConfigStore.moveIds(context, intArrayOf(21, 22, 23), intArrayOf(22, 24, 25))
        assertEquals(config, VerseWidgetConfigStore.load(context, 22))
        assertEquals(other, VerseWidgetConfigStore.load(context, 24))
        assertNull(VerseWidgetConfigStore.load(context, 21))
        assertNull(VerseWidgetConfigStore.load(context, 25))
    }

    @Test fun aDefaultIsOnlyStoredWhenNoConfigExists() {
        val chosen = config.copy(seed = 5L)
        // nothing saved: the default is stored and returned, and is stable
        val first = VerseWidgetConfigStore.loadOrSaveDefault(context, 31) { config.copy(seed = 6L) }
        assertEquals(config.copy(seed = 6L), first)
        assertEquals(first, VerseWidgetConfigStore.loadOrSaveDefault(context, 31) { error("not needed") })
        // a config saved by the configure screen is never replaced, the default is not even built
        VerseWidgetConfigStore.save(context, 32, chosen)
        assertEquals(chosen, VerseWidgetConfigStore.loadOrSaveDefault(context, 32) { error("not needed") })
    }

    @Test fun anAppThatDidNotOpenGivesNoLoadsAndNeverTouchesKoin() = kotlinx.coroutines.runBlocking {
        // The crash safe mode (#4176) and damaged data (#4112): Koin was never started, so resolving it threw and the
        // widget showed Glance's error layout instead of the attention card.
        val loads = verseWidgetLoadsFor(context, 7, WidgetEntry(appOpens = false, mono = false)) {
            throw AssertionError("Koin must not be touched when the app did not open")
        }
        assertNull(loads)
    }

    @Test fun candidatesAreResolvedOnTheMainLooperNotOnAWorker() {
        // VerseCandidates pulls in main-bound singletons (ReadingPlanControl, windows, bookmarks); first-building them on
        // a worker thread broke widgets after an update (#4175). The provideGlance caller is a worker thread.
        val main = android.os.Looper.getMainLooper()
        var resolvedOnMain: Boolean? = null
        val koin = org.koin.core.KoinApplication.init().apply {
            modules(org.koin.dsl.module {
                single<net.bible.android.control.widget.LocalZone> { net.bible.android.control.widget.LocalZone { 0L } }
                single {
                    resolvedOnMain = android.os.Looper.myLooper() == main
                    net.bible.android.control.widget.VerseCandidates(org.mockito.Mockito.mock(net.bible.android.control.widget.VerseWidgetSources::class.java))
                }
            })
        }.koin
        var failure: Throwable? = null
        val worker = Thread {
            try {
                kotlinx.coroutines.runBlocking {
                    verseWidgetLoadsFor(context, 8, WidgetEntry(appOpens = true, mono = false)) { koin }
                }
            } catch (t: Throwable) { failure = t }
        }
        worker.start()
        val deadline = System.currentTimeMillis() + 20_000
        while (worker.isAlive && System.currentTimeMillis() < deadline) {
            shadowOf(main).idle()
            worker.join(20)
        }
        assertFalse("worker did not finish", worker.isAlive)
        failure?.let { throw AssertionError("loads failed", it) }
        assertEquals(true, resolvedOnMain)
    }
}
