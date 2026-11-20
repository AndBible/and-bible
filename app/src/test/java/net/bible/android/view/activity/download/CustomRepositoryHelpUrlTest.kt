package net.bible.android.view.activity.download

import net.bible.sharedcore.docs.DocsLink
import net.bible.sharedcore.docs.HelpLinks
import net.bible.sharedui.download.customRepositoriesHelpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

/** The custom-repository help URL has a single definition in :sharedUi: a help link to the guide's page (#4159). */
class CustomRepositoryHelpUrlTest {
    @Test fun help_url_opens_the_user_guide_page() {
        assertEquals(DocsLink.CUSTOM_REPOSITORIES, HelpLinks.parse(customRepositoriesHelpUrl))
    }
}
