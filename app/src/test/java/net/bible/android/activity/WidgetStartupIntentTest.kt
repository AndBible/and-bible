package net.bible.android.activity

import android.appwidget.AppWidgetManager
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.versification.BookInstallWatcher
import net.bible.android.view.activity.startRouteForBoot
import net.bible.service.sword.SwordDocumentFacade
import net.bible.service.sword.hasUsableBible
import net.bible.sharedcore.nav.NavRoutes
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.BooksListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** #887: widget content clicks must not bypass startup when no documents are installed. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class WidgetStartupIntentTest {
    private val app = ApplicationProvider.getApplicationContext<TestBibleApplication>()

    @Test
    fun speechWidgetContentOpensStartupWithoutInstalledModules() = withoutInstalledModules {
        val manager = shadowOf(AppWidgetManager.getInstance(app))
        for (provider in listOf(
            SpeakWidgetManager.SmallSpeakControlWidget::class.java,
            SpeakWidgetManager.MiddleSpeakControlWidget::class.java,
            SpeakWidgetManager.LargeSpeakControlWidget::class.java,
            SpeakWidgetManager.SpeakTextControlWidget::class.java,
        )) {
            val id = manager.createWidget(provider, R.layout.speak_widget)
            assertTrue(manager.getViewFor(id).findViewById<android.view.View>(R.id.layout).performClick())
            assertStartupWasStarted()
        }
    }

    @Test
    fun bookmarkWidgetContentOpensStartupWithoutInstalledModules() = withoutInstalledModules {
        val manager = shadowOf(AppWidgetManager.getInstance(app))
        val id = manager.createWidget(SpeakWidgetManager.SpeakBookmarkWidget::class.java, R.layout.speak_bookmarks_widget)
        assertTrue(manager.getViewFor(id).findViewById<android.view.View>(R.id.root).performClick())
        assertStartupWasStarted()
    }

    private fun assertStartupWasStarted() {
        val launched = shadowOf(app).nextStartedActivity
        assertNotNull("Widget content must launch an activity", launched)
        assertEquals(StartupActivity::class.java.name, launched.component?.className)
    }

    private fun withoutInstalledModules(block: () -> Unit) {
        val registry = Books.installed()
        val books = registry.getBooks().toList()
        // Registry changes must not enqueue asynchronous database writes in the JVM-global watcher.
        val field = BookInstallWatcher::class.java.getDeclaredField("registered").apply { isAccessible = true }
        val listener = field.get(null) as BooksListener?
        listener?.let { registry.removeBooksListener(it) }
        try {
            books.forEach { registry.removeBook(it) }
            assertTrue(SwordDocumentFacade.bibles.isEmpty())
            // Precondition: with no usable Bible the boot route is the welcome flow.
            assertEquals(NavRoutes.WELCOME, startRouteForBoot(hasUsableBible()))
            block()
        } finally {
            try {
                books.forEach { registry.addBook(it) }
            } finally {
                listener?.let { registry.addBooksListener(it) }
            }
        }
    }
}
