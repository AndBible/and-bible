package net.bible.sharedui.host.reading

import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.nav.ManageLabelsResult
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.host.app.HostNavShell
import net.bible.sharedui.host.app.HostNavigator
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.reading.host.ReadingHostState
import net.bible.sharedui.strings.iosStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HostReadingNavigatorLinksTest {
    private class Links(override val askBeforeOpening: Boolean) : ExternalLinks {
        val opened = mutableListOf<String>()
        override fun open(url: String) { opened += url }
    }

    private class Fixture(scope: kotlinx.coroutines.CoroutineScope, discrete: Boolean) {
        val links = Links(discrete)
        val dialogs = AppDialogController()
        var language = "en"
        private val shell = HostNavShell(scope, object : HostNavigator {
            override fun navigate(route: String) = error("Unexpected navigation: $route")
            override fun popBackStack() = false
            override fun popBackStackTo(route: String, inclusive: Boolean) = false
        })
        val navigator = HostReadingNavigator(
            shell = shell,
            hostState = unusedHostState(),
            links = links,
            dialogs = dialogs,
            strings = { iosStrings(language) },
            labelResults = NavResultChannel<ManageLabelsResult> {},
            downloadControl = { error("Unused download control") },
            fakeBooks = { error("Unused book factory") },
        )

        // Link navigation must not access reading/window state or require a database fixture.
        private fun unusedHostState(): ReadingHostState {
            val field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible = true }
            val unsafe = field.get(null)
            return unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
                .invoke(unsafe, ReadingHostState::class.java) as ReadingHostState
        }

        fun confirmation(): net.bible.sharedcore.ui.dialog.ShownDialog {
            val shown = assertNotNull(dialogs.pending.value, "A document link must request confirmation")
            val request = (shown.request as? AppDialogRequest.Localized)?.resolve() ?: shown.request
            val confirm = assertIs<AppDialogRequest.Confirm>(request)
            assertEquals("External link", confirm.title)
            assertEquals("This will open external link https://example.org/document in an browser application, do you wish to continue?", confirm.message)
            return shown
        }
    }

    @Test fun documentLinkOpensOnlyAfterConfirmation() = runTest {
        for (discrete in listOf(false, true)) {
            val f = Fixture(this, discrete)
            f.navigator.openExternalUrl("https://example.org/document")
            val shown = f.confirmation()
            assertEquals(emptyList(), f.links.opened)
            f.dialogs.respond(shown.id, AppDialogResult.Ok)
            assertEquals(listOf("https://example.org/document"), f.links.opened)
            assertNull(f.dialogs.pending.value, "Confirming must not ask a second time in discrete mode")
            f.dialogs.respond(shown.id, AppDialogResult.Ok)
            assertEquals(1, f.links.opened.size, "A stale confirmation must not open the link twice")
        }
    }

    @Test fun cancellingDocumentLinkOpensNothing() = runTest {
        for (discrete in listOf(false, true)) {
            val f = Fixture(this, discrete)
            f.navigator.openExternalUrl("https://example.org/document")
            val shown = f.confirmation()
            f.dialogs.respond(shown.id, AppDialogResult.Cancel)
            assertEquals(emptyList(), f.links.opened)
            assertNull(f.dialogs.pending.value)
        }
    }

    @Test fun queuedConfirmationUsesTheCurrentLanguage() = runTest {
        val f = Fixture(this, discrete = false)
        f.navigator.openExternalUrl("https://example.org/document")
        val shown = f.confirmation()
        f.language = "fi"
        val localized = assertIs<AppDialogRequest.Localized>(shown.request)
        assertEquals(iosStrings("fi").externalLink, localized.resolve().title)
        assertTrue(f.links.opened.isEmpty())
        f.dialogs.cancelAll()
        assertTrue(f.links.opened.isEmpty(), "Dropping the pending dialog must not open a link")
    }

    @Test fun appUiLinkKeepsTheExistingNonAskingOpenPath() = runTest {
        for (discrete in listOf(false, true)) {
            val f = Fixture(this, discrete)
            f.navigator.openWebLink("https://example.org/app")
            assertEquals(listOf("https://example.org/app"), f.links.opened)
            assertNull(f.dialogs.pending.value)
            assertEquals(discrete, f.links.askBeforeOpening, "UI link routing must retain its discrete-mode policy")
        }
    }
}
