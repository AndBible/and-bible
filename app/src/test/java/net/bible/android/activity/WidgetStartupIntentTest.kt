package net.bible.android.activity

import androidx.compose.ui.unit.dp
import net.bible.android.view.widget.rootClickable
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

    private val strings get() = net.bible.android.platform.AndroidWidgetStrings(app)

    /** The background of every speak-state widget opens the app through the startup gate, never the reading screen. */
    @Test
    fun speechWidgetContentOpensStartupWithoutInstalledModules() = withoutInstalledModules {
        val state = net.bible.android.view.widget.stoppedState()
        val widgets: List<Pair<String, @androidx.compose.runtime.Composable () -> Unit>> = listOf(
            "speak" to { net.bible.android.view.widget.SpeakStripContent(state, strings, 334.dp) },
            "speak text" to { net.bible.android.view.widget.SpeakTextContent(state, strings, 334.dp, 182.dp) },
            "listening list" to { net.bible.android.view.widget.ListeningListContent(state, strings, 334.dp, 182.dp) },
        )
        for ((name, content) in widgets) {
            val view = net.bible.android.view.widget.renderWidget(app, 334, 182, content = content)
            val root = view.rootClickable()
            assertNotNull("$name widget has a background tap", root)
            assertTrue(root!!.performClick())
            assertStartupWasStarted()
        }
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
