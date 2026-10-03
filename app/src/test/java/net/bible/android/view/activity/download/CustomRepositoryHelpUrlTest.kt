package net.bible.android.view.activity.download

import net.bible.sharedcore.docs.DocsLinks
import net.bible.sharedui.download.customRepositoriesHelpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

/** The custom-repository help URL has a single definition in :sharedUi, built through [DocsLinks]. */
class CustomRepositoryHelpUrlTest {
    @Test fun help_url_points_at_the_manual() {
        assertEquals(DocsLinks.page("custom_repositories"), customRepositoriesHelpUrl)
        assertEquals("https://andbible.org/docs/custom_repositories/", customRepositoriesHelpUrl)
    }
}
