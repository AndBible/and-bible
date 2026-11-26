package net.bible.android

import android.content.res.Configuration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.test.KoinFactorySnapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.coroutines.CoroutineContext

/** A system font-size change must request fresh RemoteViews, even without a SpeakWidgetManager. */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = TestBibleApplication::class, sdk = [TEST_SDK],
    shadows = [WidgetFontScaleChangeTest.WidgetUpdates::class],
    instrumentedPackages = ["androidx.glance.appwidget.GlanceAppWidgetKt"],
)
class WidgetFontScaleChangeTest {
    /** Stop at the external Glance/WorkManager boundary, not at our application's coroutine launch. */
    @org.robolectric.annotation.Implements(
        className = "androidx.glance.appwidget.GlanceAppWidgetKt", isInAndroidSdk = false,
    )
    class WidgetUpdates {
        companion object {
            val requested = mutableListOf<Class<*>>()

            @JvmStatic
            @org.robolectric.annotation.Implementation
            fun updateAll(
                widget: androidx.glance.appwidget.GlanceAppWidget,
                context: android.content.Context,
                continuation: kotlin.coroutines.Continuation<Unit>,
            ): Any {
                requested += widget.javaClass
                return Unit
            }
        }
    }

    @Test fun fontScaleChangesRequestOneWidgetUpdateButOtherConfigurationChangesDoNot() {
        val app = BibleApplication.application
        val koin = KoinPlatform.getKoin()
        val original = KoinFactorySnapshot.of<AppCoroutineScope>(koin)
        val pending = mutableListOf<Runnable>()
        val dispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) { pending += block }
        }
        val scope = AppCoroutineScope(SupervisorJob() + dispatcher)
        val override = module { single<AppCoroutineScope> { scope } }
        koin.loadModules(listOf(override))
        WidgetUpdates.requested.clear()
        try {
            val config = Configuration(app.resources.configuration)
            app.onConfigurationChanged(config)
            assertEquals("unchanged font scale needs no redraw", 0, pending.size)
            config.fontScale = 1.3f
            app.onConfigurationChanged(config)
            assertEquals("font scale change must enqueue a widget redraw", 1, pending.size)
            app.onConfigurationChanged(Configuration(config).apply { orientation = Configuration.ORIENTATION_LANDSCAPE })
            assertEquals("repeated scale and orientation changes need no duplicate redraw", 1, pending.size)
            config.fontScale = 1f
            app.onConfigurationChanged(config)
            assertEquals("returning to the original scale must redraw too", 2, pending.size)
            while (pending.isNotEmpty()) pending.removeAt(0).run()
            val allWidgets = listOf(
                net.bible.android.view.widget.SpeakGlanceWidget::class.java,
                net.bible.android.view.widget.SpeakTextGlanceWidget::class.java,
                net.bible.android.view.widget.ListeningListGlanceWidget::class.java,
                net.bible.android.view.widget.VerseGlanceWidget::class.java,
            )
            assertEquals("both scale changes request all four widget updates", allWidgets + allWidgets, WidgetUpdates.requested)
        } finally {
            scope.cancel()
            while (pending.isNotEmpty()) pending.removeAt(0).run()
            koin.unloadModules(listOf(override))
            original.restore()
            WidgetUpdates.requested.clear()
        }
    }
}
