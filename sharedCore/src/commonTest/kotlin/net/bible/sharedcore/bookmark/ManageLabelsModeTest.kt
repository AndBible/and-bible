package net.bible.sharedcore.bookmark

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    /**
     * Re-order's ONLY effect is to regroup rows into the ACTIVE bucket: `bucket()` has three
     * buckets and RECENT is constant for the session, so a mode with no ACTIVE section has no
     * row a user action can move. The two flags must therefore name the same set of modes; if a
     * future round makes them differ, that is the thing to justify.
     */
    @Test fun reOrderIsOfferedExactlyWhereTheActiveSectionIs() {
        ManageLabelsMode.entries.forEach { mode ->
            assertEquals(
                mode.showActiveCategory,
                mode.hasReOrderButton,
                "$mode: re-order only regroups into ACTIVE, so it must be offered exactly there",
            )
        }
    }

    /** Round 17b: WORKSPACE has no leading selection control, so a "Selected labels" heading
     *  named something the user could not see themselves doing. ASSIGN and HIDELABELS draw a real
     *  checkbox and keep it. */
    @Test fun workspaceHasNoActiveSectionOrReOrder() {
        assertFalse(ManageLabelsMode.WORKSPACE.showActiveCategory)
        assertFalse(ManageLabelsMode.WORKSPACE.hasReOrderButton)
        assertTrue(ManageLabelsMode.ASSIGN.showActiveCategory)
        assertTrue(ManageLabelsMode.HIDELABELS.showActiveCategory)
        assertFalse(ManageLabelsMode.STUDYPAD.showActiveCategory)
    }
}
