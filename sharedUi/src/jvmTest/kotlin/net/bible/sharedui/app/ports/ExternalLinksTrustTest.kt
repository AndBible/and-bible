package net.bible.sharedui.app.ports

import net.bible.sharedcore.links.TrustedLinkDomains
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedui.MemoryAppSettings
import net.bible.sharedui.strings.iosStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** #4155: `ExternalLinks.confirmAndOpen` asks through the shared policy and remembers a trusted host. */
class ExternalLinksTrustTest {
    private class Links(override val askBeforeOpening: Boolean, override val trustedDomains: TrustedLinkDomains?) : ExternalLinks {
        val opened = mutableListOf<String>()
        override fun open(url: String) { opened += url }
    }

    private val url = "https://www.youtube.com/watch?v=abc"
    private val strings = iosStrings("en")

    private fun AppDialogController.shownRequest(): AppDialogRequest {
        val shown = pending.value ?: error("nothing asked")
        return (shown.request as? AppDialogRequest.Localized)?.resolve() ?: shown.request
    }

    @Test fun tickingDontAskAgainTrustsTheHostAndTheNextLinkOpensWithoutAsking() {
        val trust = TrustedLinkDomains(MemoryAppSettings())
        val links = Links(askBeforeOpening = false, trustedDomains = trust)
        val dialogs = AppDialogController()

        links.confirmAndOpen(url, dialogs) { strings }
        val question = assertIs<AppDialogRequest.Options>(dialogs.shownRequest())
        assertEquals(strings.externalLinkDontAskAgain("www.youtube.com"), question.checkboxLabel)
        assertTrue(links.opened.isEmpty())
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.SelectedWithCheckbox(EXTERNAL_LINK_OPEN, true))
        assertEquals(listOf(url), links.opened)
        assertEquals(setOf("www.youtube.com"), trust.domains())

        links.confirmAndOpen("https://www.youtube.com/other", dialogs) { strings }
        assertNull(dialogs.pending.value, "a trusted host must not ask again")
        assertEquals(listOf(url, "https://www.youtube.com/other"), links.opened)
    }

    @Test fun okWithoutTheCheckboxOpensButDoesNotTrust() {
        val trust = TrustedLinkDomains(MemoryAppSettings())
        val links = Links(askBeforeOpening = false, trustedDomains = trust)
        val dialogs = AppDialogController()
        links.confirmAndOpen(url, dialogs) { strings }
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.SelectedWithCheckbox(EXTERNAL_LINK_OPEN, false))
        assertEquals(listOf(url), links.opened)
        assertEquals(emptySet(), trust.domains())
    }

    @Test fun cancelNeitherOpensNorTrusts() {
        val trust = TrustedLinkDomains(MemoryAppSettings())
        val links = Links(askBeforeOpening = false, trustedDomains = trust)
        val dialogs = AppDialogController()
        links.confirmAndOpen(url, dialogs) { strings }
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Cancel)
        assertTrue(links.opened.isEmpty())
        assertEquals(emptySet(), trust.domains())
    }

    @Test fun discreteAsksThePlainQuestionEvenForATrustedHost() {
        val trust = TrustedLinkDomains(MemoryAppSettings()).apply { trust("www.youtube.com") }
        val links = Links(askBeforeOpening = true, trustedDomains = trust)
        val dialogs = AppDialogController()
        links.confirmAndOpen(url, dialogs) { strings }
        assertIs<AppDialogRequest.Confirm>(dialogs.shownRequest())
        dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Ok)
        assertEquals(listOf(url), links.opened)
    }

    @Test fun removingTheHostAsksAgain() {
        val trust = TrustedLinkDomains(MemoryAppSettings()).apply { trust("www.youtube.com") }
        val links = Links(askBeforeOpening = false, trustedDomains = trust)
        val dialogs = AppDialogController()
        trust.setDomains(emptySet())
        links.confirmAndOpen(url, dialogs) { strings }
        assertIs<AppDialogRequest.Options>(dialogs.shownRequest())
        assertTrue(links.opened.isEmpty())
    }

    @Test fun questionShowsLiteralUrlAndOpensTheOriginalInBothModes() {
        val url = "https://example.org/<b>other.org</b>?a=&amp;&b=\"quoted\""
        for (discrete in listOf(false, true)) {
            val links = Links(discrete, TrustedLinkDomains(MemoryAppSettings()))
            val dialogs = AppDialogController()
            links.confirmAndOpen(url, dialogs) { strings }
            val message = when (val question = dialogs.shownRequest()) {
                is AppDialogRequest.Confirm -> question.message
                is AppDialogRequest.Options -> question.message
                else -> error("unexpected question: $question")
            }
            assertEquals(strings.externalLinkQuestion(url),
                net.bible.sharedcore.ui.dialog.parseHtmlRuns(message!!).joinToString("") { it.text })
            dialogs.respond(dialogs.pending.value!!.id, AppDialogResult.Ok)
            assertEquals(listOf(url), links.opened)
        }
    }

    @Test fun aPortWithoutTrustKeepsThePlainQuestion() {
        val links = Links(askBeforeOpening = false, trustedDomains = null)
        val dialogs = AppDialogController()
        links.confirmAndOpen(url, dialogs) { strings }
        assertIs<AppDialogRequest.Confirm>(dialogs.shownRequest())
    }
}
