package net.bible.sharedcore.bookmark

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins which modes show the style tags. A style example answers "what will this label look like on
 * the page", which is a question only ASSIGN (you are choosing the look this bookmark gets) and
 * WORKSPACE (you are managing the label and its override) actually ask. STUDYPAD picks a notes
 * document; HIDELABELS picks which bookmarks disappear — an example of a rendering the screen is
 * about to suppress.
 *
 * What this actually protects: [ManageLabelsMode.styleTagsShown] is a plain equality check
 * (`this == WORKSPACE || this == ASSIGN`), not an exhaustive `when`, so this test pins the CURRENT
 * split — changing which existing modes show style examples fails it, and so has to be a deliberate,
 * reviewed edit rather than a silent one. It does NOT force a newly added [ManageLabelsMode] entry to
 * choose: a fifth entry compiles cleanly without touching [ManageLabelsMode.styleTagsShown] at all,
 * silently defaulting to no style examples for it.
 */
class ManageLabelsModeTest {
    @Test fun styleTagsShown_per_mode() {
        assertEquals(
            setOf(ManageLabelsMode.ASSIGN, ManageLabelsMode.WORKSPACE),
            ManageLabelsMode.entries.filter { it.styleTagsShown }.toSet(),
        )
    }
}
