package net.bible.sharedcore.ai.reading

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingLlmDialogControllerTest {

    private fun prompt(id: String, specify: Boolean = false, fav: Boolean = false) =
        ReadingPromptVd(id, name = id, description = "", isFavorite = fav, specifyBeforeRun = specify)

    private class Fake : ReadingLlmService {
        var groups: List<ReadingPromptGroupVd> = emptyList()
        var models: List<ReadingModelVd> = emptyList()
        var askModel = false
        var promptNeedsModel = false
        var regenNeedsModel = false
        var lastFavorite: String? = null
        var lastSetDefault: Pair<String, String>? = null
        val collapse = mutableMapOf<String, Boolean>()

        override suspend fun promptGroupsFor(contextId: String, docCategoryId: String?) = groups
        override suspend fun toggleFavorite(promptId: String) { lastFavorite = promptId }
        override suspend fun configuredModels() = models
        override fun askModelBeforeRun() = askModel
        override suspend fun promptRequiresModelChoice(promptId: String) = promptNeedsModel
        override suspend fun setPromptModelDefault(promptId: String, modelId: String) { lastSetDefault = promptId to modelId }
        override suspend fun regenerateRequiresModelChoice(pageId: String) = regenNeedsModel
        override fun isCategoryCollapsed(contextId: String, categoryId: String?) = collapse["$contextId:$categoryId"] ?: false
        override fun setCategoryCollapsed(contextId: String, categoryId: String?, collapsed: Boolean) { collapse["$contextId:$categoryId"] = collapsed }
    }

    private class ExecCalls {
        var exec: Triple<String, String?, String?>? = null
        var regen: List<Any?>? = null
    }

    private fun controller(fake: Fake) = ReadingLlmDialogController(fake, CoroutineScope(UnconfinedTestDispatcher()))

    private fun ExecCalls.openSelector(c: ReadingLlmDialogController) =
        c.openPromptSelector("VERSE_SELECTION", null) { id, spec, model -> exec = Triple(id, spec, model) }

    @Test fun openPromptSelector_emptyGroups_staysNone() = runTest {
        val f = Fake().apply { groups = emptyList() }
        val c = controller(f)
        ExecCalls().openSelector(c)
        assertEquals(ReadingLlmDialog.None, c.state.value.dialog)
    }

    @Test fun openPromptSelector_showsGroups() = runTest {
        val f = Fake().apply { groups = listOf(ReadingPromptGroupVd("Uncategorized", null, false, false, listOf(prompt("p1")))) }
        val c = controller(f)
        ExecCalls().openSelector(c)
        val d = c.state.value.dialog
        assertIs<ReadingLlmDialog.PromptSelector>(d)
        assertEquals("p1", d.groups.single().prompts.single().id)
    }

    @Test fun choosePrompt_specifyBeforeRun_opensSpecify() = runTest {
        val f = Fake().apply { groups = listOf(ReadingPromptGroupVd("U", null, false, false, listOf(prompt("p1", specify = true)))) }
        val c = controller(f)
        ExecCalls().openSelector(c)
        c.onPromptChosen("p1")
        assertIs<ReadingLlmDialog.SpecifyBeforeRun>(c.state.value.dialog)
    }

    @Test fun choosePrompt_noModelAsk_executesDirectly() = runTest {
        val f = Fake().apply { groups = listOf(ReadingPromptGroupVd("U", null, false, false, listOf(prompt("p1")))); promptNeedsModel = false }
        val c = controller(f); val calls = ExecCalls().also { it.openSelector(c) }
        c.onPromptChosen("p1")
        assertEquals(Triple("p1", null, null), calls.exec)
        assertEquals(ReadingLlmDialog.None, c.state.value.dialog)
    }

    @Test fun choosePrompt_modelAskDue_opensModelSelection() = runTest {
        val f = Fake().apply {
            groups = listOf(ReadingPromptGroupVd("U", null, false, false, listOf(prompt("p1"))))
            promptNeedsModel = true
            models = listOf(ReadingModelVd("m1", "gpt-4o", "OpenAI", isDefault = true, supported = true))
        }
        val c = controller(f); ExecCalls().openSelector(c)
        c.onPromptChosen("p1")
        val d = c.state.value.dialog
        assertIs<ReadingLlmDialog.ModelSelection>(d)
        assertTrue(d.allowSetDefault)
        assertEquals("m1", d.models.single().id)
    }

    @Test fun modelChosen_setDefault_persistsAndExecutesWithOverride() = runTest {
        val f = Fake().apply {
            groups = listOf(ReadingPromptGroupVd("U", null, false, false, listOf(prompt("p1"))))
            promptNeedsModel = true; models = listOf(ReadingModelVd("m1", "gpt-4o", "OpenAI", true, true))
        }
        val c = controller(f); val calls = ExecCalls().also { it.openSelector(c) }
        c.onPromptChosen("p1")
        c.onModelChosen("m1", setAsDefault = true)
        assertEquals("p1" to "m1", f.lastSetDefault)
        assertEquals(Triple("p1", null, "m1"), calls.exec)
        assertEquals(ReadingLlmDialog.None, c.state.value.dialog)
    }

    @Test fun specifySubmitted_blank_ignored() = runTest {
        val f = Fake().apply { groups = listOf(ReadingPromptGroupVd("U", null, false, false, listOf(prompt("p1", specify = true)))) }
        val c = controller(f); val calls = ExecCalls().also { it.openSelector(c) }
        c.onPromptChosen("p1"); c.onSpecifySubmitted("   ")
        assertNull(calls.exec)
        assertIs<ReadingLlmDialog.SpecifyBeforeRun>(c.state.value.dialog)
    }

    @Test fun specifySubmitted_text_executesWithSpec() = runTest {
        val f = Fake().apply { groups = listOf(ReadingPromptGroupVd("U", null, false, false, listOf(prompt("p1", specify = true)))); promptNeedsModel = false }
        val c = controller(f); val calls = ExecCalls().also { it.openSelector(c) }
        c.onPromptChosen("p1"); c.onSpecifySubmitted("summarise")
        assertEquals(Triple("p1", "summarise", null), calls.exec)
    }

    @Test fun regenerate_noModelAsk_regeneratesDirectly() = runTest {
        val f = Fake().apply { regenNeedsModel = false }
        val c = controller(f)
        val calls = ExecCalls()
        c.openRegenerate("page1") { id, instr, keep, fresh, model -> calls.regen = listOf(id, instr, keep, fresh, model) }
        assertIs<ReadingLlmDialog.Regenerate>(c.state.value.dialog)
        c.onRegenerateConfirmed("more detail", keepPrevious = true, freshRun = false)
        assertEquals(listOf<Any?>("page1", "more detail", true, false, null), calls.regen)
        assertEquals(ReadingLlmDialog.None, c.state.value.dialog)
    }

    @Test fun regenerate_modelAskDue_opensModelSelectionThenRegenerates() = runTest {
        val f = Fake().apply { regenNeedsModel = true; models = listOf(ReadingModelVd("m1", "gpt-4o", "OpenAI", true, true)) }
        val c = controller(f)
        val calls = ExecCalls()
        c.openRegenerate("page1") { id, instr, keep, fresh, model -> calls.regen = listOf(id, instr, keep, fresh, model) }
        c.onRegenerateConfirmed(null, keepPrevious = false, freshRun = true)
        val d = c.state.value.dialog
        assertIs<ReadingLlmDialog.ModelSelection>(d)
        assertFalse(d.allowSetDefault)
        c.onModelChosen("m1", setAsDefault = true)   // setAsDefault ignored in regenerate flow
        assertNull(f.lastSetDefault)
        assertEquals(listOf<Any?>("page1", null, false, true, "m1"), calls.regen)
    }

    @Test fun toggleFavorite_reQueriesGroups() = runTest {
        val f = Fake().apply { groups = listOf(ReadingPromptGroupVd("U", null, false, false, listOf(prompt("p1")))) }
        val c = controller(f); ExecCalls().openSelector(c)
        f.groups = listOf(ReadingPromptGroupVd("Favorites", ReadingLlmService.FAVORITES_CATEGORY_ID, true, false, listOf(prompt("p1", fav = true))),
                          ReadingPromptGroupVd("U", null, false, false, listOf(prompt("p1"))))
        c.onToggleFavorite("p1")
        assertEquals("p1", f.lastFavorite)
        val d = c.state.value.dialog
        assertIs<ReadingLlmDialog.PromptSelector>(d)
        assertTrue(d.groups.any { it.isFavorites })
    }

    @Test fun onCategoryExpandedChanged_persistsCollapse() = runTest {
        val f = Fake().apply { groups = listOf(ReadingPromptGroupVd("Cat", "c1", false, false, listOf(prompt("p1")))) }
        val c = controller(f); ExecCalls().openSelector(c)
        c.onCategoryExpandedChanged("c1", expanded = false)
        assertTrue(f.collapse["VERSE_SELECTION:c1"] == true)
    }

    @Test fun dismiss_returnsToNone() = runTest {
        val f = Fake().apply { groups = listOf(ReadingPromptGroupVd("U", null, false, false, listOf(prompt("p1")))) }
        val c = controller(f); ExecCalls().openSelector(c)
        c.dismiss()
        assertEquals(ReadingLlmDialog.None, c.state.value.dialog)
    }
}
