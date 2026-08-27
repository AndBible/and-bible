package net.bible.sharedcore.bookmark

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins which modes show the style tags. A style example answers "what will this label look like on
 * the page", which is a question only ASSIGN (you are choosing the look this bookmark gets) and
 * WORKSPACE (you are managing the label and its override) actually ask. STUDYPAD picks a notes
 * document; HIDELABELS picks which bookmarks disappear — an example of a rendering the screen is
 * about to suppress. A new mode must decide this deliberately, which is what this test forces.
 */
class ManageLabelsModeTest {
    @Test fun styleTagsShown_per_mode() {
        assertEquals(
            setOf(ManageLabelsMode.ASSIGN, ManageLabelsMode.WORKSPACE),
            ManageLabelsMode.entries.filter { it.styleTagsShown }.toSet(),
        )
    }
}
