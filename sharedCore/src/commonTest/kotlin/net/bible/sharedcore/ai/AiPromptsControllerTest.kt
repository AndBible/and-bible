package net.bible.sharedcore.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AiPromptsControllerTest {

    private val cat1 = PromptCategoryVd(id = "cat1", name = "Category 1", isBuiltIn = false, isHidden = false)

    private fun prompt(
        id: String, name: String = id, categoryId: String? = "cat1",
        isBuiltIn: Boolean = false, isReadOnly: Boolean = false,
        isFavorite: Boolean = false, isHidden: Boolean = false,
    ) = PromptVd(
        id = id, name = name, description = "", categoryId = categoryId,
        isBuiltIn = isBuiltIn, isReadOnly = isReadOnly, isFavorite = isFavorite, isHidden = isHidden,
    )

    private class Fake(
        configuredInitial: Boolean = true,
        groupsInitial: List<PromptGroupVd> = emptyList(),
        showHiddenInitial: Boolean = false,
    ) : PromptService {
        val configuredFlow = MutableStateFlow(configuredInitial)
        override val configured: StateFlow<Boolean> = configuredFlow
        val groupsFlow = MutableStateFlow(groupsInitial)
        override val groups: StateFlow<List<PromptGroupVd>> = groupsFlow
        val showHiddenFlow = MutableStateFlow(showHiddenInitial)
        override val showHidden: StateFlow<Boolean> = showHiddenFlow

        var lastShowHidden: Boolean? = null
        var lastToggleFavoriteId: String? = null
        var lastSetPromptHidden: Pair<String, Boolean>? = null
        var lastSetCategoryHidden: Pair<String, Boolean>? = null
        var lastDeletePromptId: String? = null
        var lastDeleteCategoryId: String? = null
        var lastMovePrompt: Pair<String, Boolean>? = null
        var lastMoveCategory: Pair<String, Boolean>? = null
        var lastCreateCategoryName: String? = null
        var lastRenameCategory: Pair<String, String>? = null
        var refreshCount = 0

        override fun setShowHidden(v: Boolean) { lastShowHidden = v; showHiddenFlow.value = v }
        override fun toggleFavorite(promptId: String) { lastToggleFavoriteId = promptId }
        override fun setPromptHidden(promptId: String, hidden: Boolean) { lastSetPromptHidden = promptId to hidden }
        override fun setCategoryHidden(categoryId: String, hidden: Boolean) { lastSetCategoryHidden = categoryId to hidden }
        override fun deletePrompt(promptId: String) { lastDeletePromptId = promptId }
        override fun deleteCategory(categoryId: String) { lastDeleteCategoryId = categoryId }
        override fun movePrompt(promptId: String, up: Boolean) { lastMovePrompt = promptId to up }
        override fun moveCategory(categoryId: String, up: Boolean) { lastMoveCategory = categoryId to up }
        override fun createCategory(name: String) { lastCreateCategoryName = name }
        override fun renameCategory(categoryId: String, name: String) { lastRenameCategory = categoryId to name }
        override fun refresh() { refreshCount++ }

        override fun prompt(id: String): PromptEditData? = null
        override fun newPromptData(template: String?, defaultContext: String?): PromptEditData =
            throw NotImplementedError()
        override fun categories(): List<PromptCategoryVd> = emptyList()
        override fun toolsByCategory(): List<Pair<ToolCategoryVd, List<ToolVd>>> = emptyList()
        override fun globalToolPermission(toolId: String): ToolPermission = ToolPermission.DEFAULT
        override fun modelChoices(): List<net.bible.sharedcore.settings.SettingsItem.Choice> = emptyList()
        override fun savePrompt(data: PromptEditData): String = ""
        override fun deletePromptById(id: String) {}
        override fun copyPrompt(id: String): String = ""
    }

    private class NavCalls {
        var openedPromptId: String? = null
        var newPromptCount = 0
        var openedConnectionSettingsCount = 0
    }

    private fun controller(fake: Fake, nav: NavCalls = NavCalls()) = AiPromptsController(
        service = fake,
        scope = CoroutineScope(UnconfinedTestDispatcher()),
        onOpenPrompt = { nav.openedPromptId = it },
        onNewPrompt = { nav.newPromptCount++ },
        onOpenConnectionSettings = { nav.openedConnectionSettingsCount++ },
    )

    @Test fun configured_reflectsServiceFlow() = runTest {
        val f = Fake(configuredInitial = false)
        val c = controller(f)
        assertFalse(c.configured.value)
        f.configuredFlow.value = true
        assertTrue(c.configured.value)
    }

    @Test fun groups_reflectsServiceFlow_includingFavoritesGroup() = runTest {
        val favGroup = PromptGroupVd(category = null, isFavorites = true, prompts = listOf(prompt("p1", isFavorite = true)))
        val catGroup = PromptGroupVd(category = cat1, isFavorites = false, prompts = listOf(prompt("p2")))
        val f = Fake(groupsInitial = listOf(favGroup, catGroup))
        val c = controller(f)
        assertEquals(listOf(favGroup, catGroup), c.groups.value)
        assertTrue(c.groups.value.any { it.isFavorites })

        val updated = listOf(catGroup)
        f.groupsFlow.value = updated
        assertEquals(updated, c.groups.value)
    }

    @Test fun showHidden_reflectsServiceFlow() = runTest {
        val f = Fake(showHiddenInitial = false)
        val c = controller(f)
        assertFalse(c.showHidden.value)
        f.showHiddenFlow.value = true
        assertTrue(c.showHidden.value)
    }

    @Test fun onSetShowHidden_routesToService() = runTest {
        val f = Fake()
        val c = controller(f)
        c.onSetShowHidden(true)
        assertEquals(true, f.lastShowHidden)
        assertTrue(c.showHidden.value)
    }

    @Test fun onToggleFavorite_routesToService() = runTest {
        val f = Fake(); val c = controller(f)
        c.onToggleFavorite("p1")
        assertEquals("p1", f.lastToggleFavoriteId)
    }

    @Test fun onSetPromptHidden_routesToService() = runTest {
        val f = Fake(); val c = controller(f)
        c.onSetPromptHidden("p1", true)
        assertEquals("p1" to true, f.lastSetPromptHidden)
    }

    @Test fun onSetCategoryHidden_routesToService() = runTest {
        val f = Fake(); val c = controller(f)
        c.onSetCategoryHidden("cat1", true)
        assertEquals("cat1" to true, f.lastSetCategoryHidden)
    }

    @Test fun onDeletePrompt_routesToService() = runTest {
        val f = Fake(); val c = controller(f)
        c.onDeletePrompt("p1")
        assertEquals("p1", f.lastDeletePromptId)
    }

    @Test fun onDeleteCategory_routesToService() = runTest {
        val f = Fake(); val c = controller(f)
        c.onDeleteCategory("cat1")
        assertEquals("cat1", f.lastDeleteCategoryId)
    }

    @Test fun onMovePrompt_routesToService() = runTest {
        val f = Fake(); val c = controller(f)
        c.onMovePrompt("p1", true)
        assertEquals("p1" to true, f.lastMovePrompt)
        c.onMovePrompt("p1", false)
        assertEquals("p1" to false, f.lastMovePrompt)
    }

    @Test fun onMoveCategory_routesToService() = runTest {
        val f = Fake(); val c = controller(f)
        c.onMoveCategory("cat1", true)
        assertEquals("cat1" to true, f.lastMoveCategory)
    }

    @Test fun onCreateCategory_routesToService() = runTest {
        val f = Fake(); val c = controller(f)
        c.onCreateCategory("New category")
        assertEquals("New category", f.lastCreateCategoryName)
    }

    @Test fun onRenameCategory_routesToService() = runTest {
        val f = Fake(); val c = controller(f)
        c.onRenameCategory("cat1", "Renamed")
        assertEquals("cat1" to "Renamed", f.lastRenameCategory)
    }

    @Test fun onOpenPrompt_forwardsIdToNavLambda() = runTest {
        val nav = NavCalls()
        val c = controller(Fake(), nav)
        c.onOpenPrompt("p42")
        assertEquals("p42", nav.openedPromptId)
    }

    @Test fun onNewPrompt_forwardsToNavLambda() = runTest {
        val nav = NavCalls()
        val c = controller(Fake(), nav)
        c.onNewPrompt()
        assertEquals(1, nav.newPromptCount)
    }

    @Test fun onOpenConnectionSettings_forwardsToNavLambda() = runTest {
        val nav = NavCalls()
        val c = controller(Fake(), nav)
        c.onOpenConnectionSettings()
        assertEquals(1, nav.openedConnectionSettingsCount)
    }
}
