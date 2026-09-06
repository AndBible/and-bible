package net.bible.sharedcore.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AiDocumentFilterControllerTest {

    private val kjv = AiDocVd("KJV", "King James Version", allowed = true)
    private val esv = AiDocVd("ESV", "English Standard Version", allowed = false)
    private val strongs = AiDocVd("StrongsGreek", "Strong's Greek", allowed = true)

    private val groups = listOf(
        AiDocGroupVd("BIBLE", "Bible", listOf(kjv, esv)),
        AiDocGroupVd("DICTIONARY", "Dictionary", listOf(strongs)),
    )

    private class Fake(private val groupsList: List<AiDocGroupVd>) : DocumentFilterService {
        var lastExcluded: Set<String>? = null
        var setExcludedCount = 0
        override fun groups(): List<AiDocGroupVd> = groupsList
        override fun setExcluded(excludedInitials: Set<String>) {
            lastExcluded = excludedInitials
            setExcludedCount++
        }
    }

    private fun controller(fake: Fake) = AiDocumentFilterController(fake, CoroutineScope(UnconfinedTestDispatcher()))

    @Test fun state_reflectsServiceGroupsAndWorkingExcludedSet() = runTest {
        val f = Fake(groups)
        val c = controller(f)
        assertEquals(groups, c.state.value)
        assertFalse(c.isDirty.value)
    }

    @Test fun toggle_flipsAllowedAndDirty() = runTest {
        val f = Fake(groups)
        val c = controller(f)

        c.toggle("KJV")
        val bibleDocs = c.state.value.first { it.categoryId == "BIBLE" }.docs
        assertFalse(bibleDocs.first { it.initials == "KJV" }.allowed)
        assertTrue(c.isDirty.value)
        // nothing persisted until save()
        assertEquals(0, f.setExcludedCount)

        // re-excluding an already-excluded doc allows it (toggle is a flip)
        c.toggle("ESV")
        val bibleDocs2 = c.state.value.first { it.categoryId == "BIBLE" }.docs
        assertTrue(bibleDocs2.first { it.initials == "ESV" }.allowed)

        // toggling KJV back restores the original state -> not dirty (ESV toggle still pending though)
        c.toggle("KJV")
        assertTrue(c.isDirty.value) // ESV flip still differs from initial
        c.toggle("ESV")
        assertFalse(c.isDirty.value) // both flips undone
    }

    @Test fun save_persistsWorkingExcludedSetAndRebaselines() = runTest {
        val f = Fake(groups)
        val c = controller(f)
        c.toggle("KJV") // exclude KJV
        c.toggle("ESV") // allow ESV

        c.save()

        assertEquals(setOf("KJV"), f.lastExcluded)
        assertEquals(1, f.setExcludedCount)
        assertFalse(c.isDirty.value)

        // further edits are dirty relative to the new baseline
        c.toggle("StrongsGreek")
        assertTrue(c.isDirty.value)
    }

    @Test fun resetAll_allowsEverythingLocallyWithoutTouchingService() = runTest {
        val f = Fake(groups)
        val c = controller(f)

        c.resetAll()

        assertTrue(c.state.value.flatMap { it.docs }.all { it.allowed })
        // ESV was excluded initially, so "all allowed" differs from the loaded baseline
        assertTrue(c.isDirty.value)
        assertEquals(0, f.setExcludedCount)
    }

    @Test fun resetAll_thenSave_persistsEmptyExcludedSet() = runTest {
        val f = Fake(groups)
        val c = controller(f)
        c.resetAll()
        c.save()
        assertEquals(emptySet<String>(), f.lastExcluded)
        assertFalse(c.isDirty.value)
    }

    /**
     * Regression for the whole-branch review's C1 ("Discard changes?" does not discard): the host
     * used to build ONE controller instance and hold it for the whole Activity (`remember {}` at
     * host scope), so an unsaved toggle discarded via Back survived into the next visit and could
     * ride along into a LATER save(). The fix (AiNavGraph.kt's `controllerFor` factory, `remember`ed
     * per back-stack entry) relies on exactly this property: a controller freshly constructed over
     * the same service never carries an earlier, never-saved instance's staged edits.
     */
    @Test fun freshControllerOverSameService_isCleanRegardlessOfEarlierUnsavedEdits() = runTest {
        val f = Fake(groups)
        val first = controller(f)
        first.toggle("KJV")
        assertTrue(first.isDirty.value)
        assertEquals(0, f.setExcludedCount) // never saved

        val second = controller(f)
        assertFalse(second.isDirty.value)
        assertEquals(groups, second.state.value)
    }
}
