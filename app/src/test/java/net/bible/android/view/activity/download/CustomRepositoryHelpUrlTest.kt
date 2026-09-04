package net.bible.android.view.activity.download

import net.bible.sharedui.download.customRepositoriesHelpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The custom-repository help URL exists as TWO constants — one in :sharedUi for the Compose
 * screens, one in :app for the classic activities — because :app cannot be depended on from
 * :sharedUi. They must never drift: a user landing on the old GitHub wiki from one screen and the
 * manual from the other is exactly the defect this pins.
 */
class CustomRepositoryHelpUrlTest {
    @Test fun both_constants_point_at_the_manual() {
        assertEquals("https://docs.andbible.org/en/latest/custom_repositories.html", customRepositoriesHelpUrl)
        assertEquals(customRepositoriesHelpUrl, net.bible.android.view.activity.download.customRepositoriesHelpUrl)
    }
}
