package net.bible.sharedcore.ai

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.settings.SettingsItem
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PromptEditControllerTest {

    private fun data(
        id: String? = "p1",
        name: String = "My prompt",
        description: String = "desc",
        template: String = "template {{selection}}",
        categoryId: String? = null,
        contexts: Set<String> = setOf("VERSE_SELECTION"),
        isTextTransformation: Boolean = false,
        permissionMode: String? = null,
        allowedTools: Set<String> = emptySet(),
        deniedTools: Set<String> = emptySet(),
        modelOverrideId: String? = null,
        maxIterations: Int? = null,
        strictContextMatching: Boolean = true,
        specifyBeforeRun: Boolean = false,
        noDocumentCreation: Boolean = false,
        autoIncludeDocuments: Boolean = false,
        autoIncludeCommentaries: Boolean = false,
        isReadOnly: Boolean = false,
        isBuiltIn: Boolean = false,
        bibleOnly: Boolean = false,
    ) = PromptEditData(
        id = id, name = name, description = description, template = template,
        categoryId = categoryId, contexts = contexts, isTextTransformation = isTextTransformation,
        permissionMode = permissionMode, allowedTools = allowedTools, deniedTools = deniedTools,
        modelOverrideId = modelOverrideId, maxIterations = maxIterations,
        strictContextMatching = strictContextMatching, specifyBeforeRun = specifyBeforeRun,
        noDocumentCreation = noDocumentCreation, autoIncludeDocuments = autoIncludeDocuments,
        autoIncludeCommentaries = autoIncludeCommentaries, isReadOnly = isReadOnly, isBuiltIn = isBuiltIn,
        bibleOnly = bibleOnly,
    )

    private class Fake(
        private val prompts: MutableMap<String, PromptEditData> = mutableMapOf(),
        private val newData: PromptEditData? = null,
        private val toolCatalog: List<Pair<ToolCategoryVd, List<ToolVd>>> = emptyList(),
    ) : PromptService {
        override val configured: StateFlow<Boolean> = MutableStateFlow(true)
        override val groups: StateFlow<List<PromptGroupVd>> = MutableStateFlow(emptyList())
        override val showHidden: StateFlow<Boolean> = MutableStateFlow(false)
        override val hasHiddenPrompts: StateFlow<Boolean> = MutableStateFlow(false)
        override fun setShowHidden(v: Boolean) {}
        override fun toggleFavorite(promptId: String) {}
        override fun setPromptHidden(promptId: String, hidden: Boolean) {}
        override fun setCategoryHidden(categoryId: String, hidden: Boolean) {}
        override fun deletePrompt(promptId: String) {}
        override fun deleteCategory(categoryId: String, deletePrompts: Boolean) {}
        override fun movePrompt(promptId: String, up: Boolean) {}
        override fun moveCategory(categoryId: String, up: Boolean) {}
        override fun createCategory(name: String) {}
        override fun renameCategory(categoryId: String, name: String) {}
        override fun refresh() {}

        override fun prompt(id: String): PromptEditData? = prompts[id]
        override fun newPromptData(template: String?, defaultContext: String?): PromptEditData =
            newData ?: PromptEditData(
                id = null, name = "", description = "", template = template ?: "",
                categoryId = null,
                contexts = defaultContext?.let { setOf(it) } ?: emptySet(),
                isTextTransformation = false, permissionMode = null,
                allowedTools = emptySet(), deniedTools = emptySet(),
                modelOverrideId = null, maxIterations = null,
                strictContextMatching = true, specifyBeforeRun = false, noDocumentCreation = false,
                autoIncludeDocuments = false, autoIncludeCommentaries = false,
                isReadOnly = false, isBuiltIn = false, bibleOnly = false,
            )

        override fun categories(): List<PromptCategoryVd> = emptyList()
        override fun toolsByCategory(): List<Pair<ToolCategoryVd, List<ToolVd>>> = toolCatalog
        override fun globalToolPermission(toolId: String): ToolPermission = ToolPermission.DEFAULT
        override fun modelChoices(): List<SettingsItem.Choice> = emptyList()

        var lastSaved: PromptEditData? = null
        var saveCount = 0
        var saveIdToReturn = "saved-id"
        override fun savePrompt(data: PromptEditData): String {
            lastSaved = data
            saveCount++
            return saveIdToReturn
        }

        var lastDeletedId: String? = null
        var deleteCount = 0
        override fun deletePromptById(id: String) {
            lastDeletedId = id
            deleteCount++
        }

        var lastCopiedId: String? = null
        var copyCount = 0
        var copyIdToReturn = "copy-id"
        override fun copyPrompt(id: String): String {
            lastCopiedId = id
            copyCount++
            return copyIdToReturn
        }

        var lastBuiltinOverridePromptId: String? = null
        var lastBuiltinOverrideModelId: String? = null
        var builtinOverrideCount = 0
        override fun setBuiltinPromptModelOverride(promptId: String, modelId: String?) {
            lastBuiltinOverridePromptId = promptId
            lastBuiltinOverrideModelId = modelId
            builtinOverrideCount++
        }
    }

    // --- load / new ---

    @Test fun load_prefillsStateFromPromptId() = runTest {
        val d = data(id = "p1", name = "Existing", description = "d", template = "t")
        val f = Fake(mutableMapOf("p1" to d))
        val c = PromptEditController(f, promptId = "p1")
        assertEquals(d, c.state.value)
        assertFalse(c.isDirty.value)
    }

    @Test fun load_missingPromptFallsBackToNew() = runTest {
        val f = Fake()
        val c = PromptEditController(f, promptId = "does-not-exist")
        assertNull(c.state.value.id)
        assertFalse(c.isDirty.value)
    }

    @Test fun new_usesNewPromptData() = runTest {
        val f = Fake()
        val c = PromptEditController(f, promptId = null, template = "tmpl", defaultContext = "TEXT_SELECTION")
        assertNull(c.state.value.id)
        assertEquals("tmpl", c.state.value.template)
        assertEquals(setOf("TEXT_SELECTION"), c.state.value.contexts)
        assertFalse(c.isDirty.value)
    }

    // --- dirty tracking ---

    @Test fun setName_marksDirty_thenRevertClearsDirty() = runTest {
        val d = data(name = "Original")
        val f = Fake(mutableMapOf("p1" to d))
        val c = PromptEditController(f, promptId = "p1")
        assertFalse(c.isDirty.value)
        c.setName("Changed")
        assertTrue(c.isDirty.value)
        assertEquals("Changed", c.state.value.name)
        c.setName("Original")
        assertFalse(c.isDirty.value)
    }

    @Test fun setDescription_setTemplate_setCategory_markDirty() = runTest {
        val f = Fake(mutableMapOf("p1" to data()))
        val c = PromptEditController(f, promptId = "p1")
        c.setDescription("new desc")
        assertTrue(c.isDirty.value)
        assertEquals("new desc", c.state.value.description)

        val f2 = Fake(mutableMapOf("p1" to data()))
        val c2 = PromptEditController(f2, promptId = "p1")
        c2.setTemplate("new template")
        assertTrue(c2.isDirty.value)

        val f3 = Fake(mutableMapOf("p1" to data()))
        val c3 = PromptEditController(f3, promptId = "p1")
        c3.setCategory("cat1")
        assertTrue(c3.isDirty.value)
        assertEquals("cat1", c3.state.value.categoryId)
    }

    @Test fun toggleContext_addsThenRemoves() = runTest {
        val f = Fake(mutableMapOf("p1" to data(contexts = setOf("VERSE_SELECTION"))))
        val c = PromptEditController(f, promptId = "p1")
        c.toggleContext("TEXT_SELECTION")
        assertEquals(setOf("VERSE_SELECTION", "TEXT_SELECTION"), c.state.value.contexts)
        assertTrue(c.isDirty.value)
        c.toggleContext("TEXT_SELECTION")
        assertEquals(setOf("VERSE_SELECTION"), c.state.value.contexts)
        assertFalse(c.isDirty.value)
    }

    // --- bibleOnly gating ---

    @Test fun setBibleOnly_true_clearsWorkspaceAndNoteContexts() = runTest {
        val d = data(contexts = setOf("VERSE_SELECTION", "WORKSPACE_MENU", "NOTE_EDITOR"))
        val f = Fake(mutableMapOf("p1" to d))
        val c = PromptEditController(f, promptId = "p1")
        c.setBibleOnly(true)
        assertTrue(c.state.value.bibleOnly)
        assertEquals(setOf("VERSE_SELECTION"), c.state.value.contexts)
    }

    @Test fun bibleOnly_disablesWorkspaceAndNoteContexts() = runTest {
        val f = Fake(mutableMapOf("p1" to data()))
        val c = PromptEditController(f, promptId = "p1")
        assertTrue(c.disabledContexts.isEmpty())
        c.setBibleOnly(true)
        assertEquals(setOf("WORKSPACE_MENU", "NOTE_EDITOR"), c.disabledContexts)
    }

    @Test fun toggleContext_isNoOpForDisabledContextWhileBibleOnly() = runTest {
        val f = Fake(mutableMapOf("p1" to data(bibleOnly = true, contexts = setOf("VERSE_SELECTION"))))
        val c = PromptEditController(f, promptId = "p1")
        c.toggleContext("WORKSPACE_MENU")
        assertEquals(setOf("VERSE_SELECTION"), c.state.value.contexts)
    }

    @Test fun load_bibleOnlyPromptWithStaleContexts_isClearedOnLoad() = runTest {
        val d = data(bibleOnly = true, contexts = setOf("VERSE_SELECTION", "WORKSPACE_MENU"))
        val f = Fake(mutableMapOf("p1" to d))
        val c = PromptEditController(f, promptId = "p1")
        assertEquals(setOf("VERSE_SELECTION"), c.state.value.contexts)
    }

    // --- textTransformation gating ---

    @Test fun setTextTransformation_true_removesPermissionsFromAvailableTabs() = runTest {
        val f = Fake(mutableMapOf("p1" to data()))
        val c = PromptEditController(f, promptId = "p1")
        assertTrue(PromptEditTab.PERMISSIONS in c.availableTabs.value)
        c.setTextTransformation(true)
        assertFalse(PromptEditTab.PERMISSIONS in c.availableTabs.value)
        c.setTextTransformation(false)
        assertTrue(PromptEditTab.PERMISSIONS in c.availableTabs.value)
    }

    @Test fun selectTab_movesAwayFromPermissionsWhenItBecomesUnavailable() = runTest {
        val f = Fake(mutableMapOf("p1" to data()))
        val c = PromptEditController(f, promptId = "p1")
        c.selectTab(PromptEditTab.PERMISSIONS)
        assertEquals(PromptEditTab.PERMISSIONS, c.tab.value)
        c.setTextTransformation(true)
        assertEquals(PromptEditTab.PROMPT, c.tab.value)
    }

    @Test fun selectTab_ignoresUnavailableTab() = runTest {
        val f = Fake(mutableMapOf("p1" to data(isTextTransformation = true)))
        val c = PromptEditController(f, promptId = "p1")
        assertEquals(PromptEditTab.PROMPT, c.tab.value)
        c.selectTab(PromptEditTab.PERMISSIONS)
        assertEquals(PromptEditTab.PROMPT, c.tab.value)
        c.selectTab(PromptEditTab.ADVANCED)
        assertEquals(PromptEditTab.ADVANCED, c.tab.value)
    }

    // --- save / delete / copy ---

    @Test fun save_callsServiceWithCurrentDataAndReturnsId() = runTest {
        val f = Fake(mutableMapOf("p1" to data(name = "Original")))
        f.saveIdToReturn = "p1"
        val c = PromptEditController(f, promptId = "p1")
        c.setName("Renamed")
        val id = c.save()
        assertEquals("p1", id)
        assertEquals(1, f.saveCount)
        assertEquals("Renamed", f.lastSaved?.name)
    }

    @Test fun save_resetsDirtyFlagAndAssignsIdForNewPrompt() = runTest {
        val f = Fake()
        f.saveIdToReturn = "brand-new-id"
        val c = PromptEditController(f, promptId = null)
        c.setName("Fresh")
        c.setTemplate("Body")
        assertTrue(c.isDirty.value)
        val id = c.save()
        assertEquals("brand-new-id", id)
        assertEquals("brand-new-id", c.state.value.id)
        assertFalse(c.isDirty.value)
    }

    @Test fun delete_callsServiceDeletePromptById() = runTest {
        val f = Fake(mutableMapOf("p1" to data()))
        val c = PromptEditController(f, promptId = "p1")
        c.delete()
        assertEquals("p1", f.lastDeletedId)
        assertEquals(1, f.deleteCount)
    }

    @Test fun copyToCustomize_callsServiceCopyPrompt() = runTest {
        val f = Fake(mutableMapOf("p1" to data()))
        f.copyIdToReturn = "p1-copy"
        val c = PromptEditController(f, promptId = "p1")
        val id = c.copyToCustomize()
        assertEquals("p1-copy", id)
        assertEquals("p1", f.lastCopiedId)
        assertEquals(1, f.copyCount)
    }

    // --- read-only ---

    @Test fun readOnly_blocksEditsAndSave_butAllowsCopy() = runTest {
        val d = data(isReadOnly = true, isBuiltIn = true, name = "Builtin")
        val f = Fake(mutableMapOf("p1" to d))
        f.copyIdToReturn = "copy-of-builtin"
        val c = PromptEditController(f, promptId = "p1")

        assertFalse(c.canSave.value)
        c.setName("Should not change")
        assertEquals("Builtin", c.state.value.name)
        assertFalse(c.isDirty.value)

        val savedId = c.save()
        assertNull(savedId)
        assertEquals(0, f.saveCount)

        c.delete()
        assertEquals(0, f.deleteCount)

        val copyId = c.copyToCustomize()
        assertEquals("copy-of-builtin", copyId)
        assertEquals(1, f.copyCount)
    }

    @Test fun canSave_falseWhenNameBlank() = runTest {
        val f = Fake(mutableMapOf("p1" to data(name = "Has name")))
        val c = PromptEditController(f, promptId = "p1")
        assertTrue(c.canSave.value)
        c.setName("")
        assertFalse(c.canSave.value)
        c.setName("   ")
        assertFalse(c.canSave.value)
    }

    @Test fun canSave_falseWhenTemplateBlank_trueOnceFilled() = runTest {
        val f = Fake(mutableMapOf("p1" to data(name = "Has name", template = "orig template")))
        val c = PromptEditController(f, promptId = "p1")
        assertTrue(c.canSave.value)

        // Classic parity: blank template blocks saving even with a non-blank name.
        c.setTemplate("")
        assertFalse(c.canSave.value)

        c.setTemplate("   ")
        assertFalse(c.canSave.value)

        c.setTemplate("filled in")
        assertTrue(c.canSave.value)
    }

    // --- tool permissions ---

    @Test fun setToolPermission_allowAndDenyAndDefault() = runTest {
        val f = Fake(mutableMapOf("p1" to data()))
        val c = PromptEditController(f, promptId = "p1")

        c.setToolPermission("readBible", ToolPermission.ALLOW)
        assertEquals(setOf("readBible"), c.state.value.allowedTools)
        assertTrue(c.state.value.deniedTools.isEmpty())

        c.setToolPermission("readBible", ToolPermission.DENY)
        assertEquals(setOf("readBible"), c.state.value.deniedTools)
        assertTrue(c.state.value.allowedTools.isEmpty())

        c.setToolPermission("readBible", ToolPermission.DEFAULT)
        assertTrue(c.state.value.allowedTools.isEmpty())
        assertTrue(c.state.value.deniedTools.isEmpty())
    }

    @Test fun setToolPermission_enabledAndDisabledMapLikeAllowAndDeny() = runTest {
        val f = Fake(mutableMapOf("p1" to data()))
        val c = PromptEditController(f, promptId = "p1")

        c.setToolPermission("readNotes", ToolPermission.ENABLED)
        assertEquals(setOf("readNotes"), c.state.value.allowedTools)

        c.setToolPermission("readNotes", ToolPermission.DISABLED)
        assertEquals(setOf("readNotes"), c.state.value.deniedTools)
        assertTrue(c.state.value.allowedTools.isEmpty())
    }

    @Test fun resetToolPermissions_clearsBothSets() = runTest {
        val d = data(allowedTools = setOf("a"), deniedTools = setOf("b"))
        val f = Fake(mutableMapOf("p1" to d))
        val c = PromptEditController(f, promptId = "p1")
        c.resetToolPermissions()
        assertTrue(c.state.value.allowedTools.isEmpty())
        assertTrue(c.state.value.deniedTools.isEmpty())
    }

    // --- category bulk read/write toggles (E2/F37) ---

    private val bulkCategory = ToolCategoryVd("BULK", "Bulk")
    private val readA = ToolVd("readA", "Read A", "d", requiresPermission = false, categoryId = "BULK")
    private val readB = ToolVd("readB", "Read B", "d", requiresPermission = false, categoryId = "BULK")
    private val writeA = ToolVd("writeA", "Write A", "d", requiresPermission = true, categoryId = "BULK")
    private val writeB = ToolVd("writeB", "Write B", "d", requiresPermission = true, categoryId = "BULK")
    private val bulkCatalog: List<Pair<ToolCategoryVd, List<ToolVd>>> =
        listOf(bulkCategory to listOf(readA, readB, writeA, writeB))

    @Test fun setCategoryRead_setsAllReadToolsOnly_toDefaultOrDisabled() = runTest {
        val d = data(allowedTools = setOf("readA", "readB"))
        val f = Fake(mutableMapOf("p1" to d), toolCatalog = bulkCatalog)
        val c = PromptEditController(f, promptId = "p1")

        // OFF -> explicit DISABLED override (deniedTools).
        c.setCategoryRead("BULK", false)
        assertEquals(setOf("readA", "readB"), c.state.value.deniedTools)
        assertTrue(c.state.value.allowedTools.isEmpty())

        // ON -> DEFAULT (clears the override back out of both sets), write tools untouched.
        c.setCategoryRead("BULK", true)
        assertTrue(c.state.value.allowedTools.isEmpty())
        assertTrue(c.state.value.deniedTools.isEmpty())
    }

    @Test fun setCategoryWrite_setsAllWriteToolsOnly_toDefaultOrDeny() = runTest {
        val d = data(allowedTools = setOf("writeA", "readA"))
        val f = Fake(mutableMapOf("p1" to d), toolCatalog = bulkCatalog)
        val c = PromptEditController(f, promptId = "p1")

        c.setCategoryWrite("BULK", false)
        assertEquals(setOf("writeA", "writeB"), c.state.value.deniedTools)
        // readA's ALLOW override is untouched by the write toggle.
        assertEquals(setOf("readA"), c.state.value.allowedTools)

        c.setCategoryWrite("BULK", true)
        assertEquals(setOf("readA"), c.state.value.allowedTools)
        assertTrue(c.state.value.deniedTools.isEmpty())
    }

    @Test fun categoryReadState_and_categoryWriteState_reflectAllOn_mixed_allOff() = runTest {
        val f = Fake(mutableMapOf("p1" to data()), toolCatalog = bulkCatalog)
        val c = PromptEditController(f, promptId = "p1")

        // Fresh prompt: no overrides -> every tool is DEFAULT -> ALL_ON for both.
        assertEquals(CategoryToggleState.ALL_ON, c.categoryReadState("BULK"))
        assertEquals(CategoryToggleState.ALL_ON, c.categoryWriteState("BULK"))

        c.setToolPermission("readA", ToolPermission.DISABLED)
        assertEquals(CategoryToggleState.MIXED, c.categoryReadState("BULK"))

        c.setToolPermission("readB", ToolPermission.DISABLED)
        assertEquals(CategoryToggleState.ALL_OFF, c.categoryReadState("BULK"))

        c.setToolPermission("writeA", ToolPermission.DENY)
        assertEquals(CategoryToggleState.MIXED, c.categoryWriteState("BULK"))
    }

    @Test fun categoryState_nullWhenCategoryHasNoToolsOfThatKind() = runTest {
        val readOnlyCategory = ToolCategoryVd("READ_ONLY", "Read only")
        val readOnlyTool = ToolVd("readOnlyTool", "Read only tool", "d", requiresPermission = false, categoryId = "READ_ONLY")
        val f = Fake(mutableMapOf("p1" to data()), toolCatalog = listOf(readOnlyCategory to listOf(readOnlyTool)))
        val c = PromptEditController(f, promptId = "p1")

        assertNotNull(c.categoryReadState("READ_ONLY"))
        assertNull(c.categoryWriteState("READ_ONLY"))

        // A bulk write op on a category with no write tools is a safe no-op.
        c.setCategoryWrite("READ_ONLY", false)
        assertTrue(c.state.value.deniedTools.isEmpty())
    }

    @Test fun setCategoryRead_isNoOpWhenReadOnly() = runTest {
        val d = data(isReadOnly = true, isBuiltIn = false)
        val f = Fake(mutableMapOf("p1" to d), toolCatalog = bulkCatalog)
        val c = PromptEditController(f, promptId = "p1")

        c.setCategoryRead("BULK", false)
        assertTrue(c.state.value.deniedTools.isEmpty())
        assertFalse(c.isDirty.value)
    }

    // --- misc setters ---

    @Test fun setModelOverride_setMaxIterations_setSwitch() = runTest {
        val f = Fake(mutableMapOf("p1" to data()))
        val c = PromptEditController(f, promptId = "p1")
        c.setModelOverride("model-1")
        assertEquals("model-1", c.state.value.modelOverrideId)
        c.setMaxIterations(7)
        assertEquals(7, c.state.value.maxIterations)
        c.setSwitch("specify_before_run", true)
        assertTrue(c.state.value.specifyBeforeRun)
        c.setSwitch("no_document_creation", true)
        assertTrue(c.state.value.noDocumentCreation)
        c.setSwitch("auto_include_documents", true)
        assertTrue(c.state.value.autoIncludeDocuments)
        c.setSwitch("auto_include_commentaries", true)
        assertTrue(c.state.value.autoIncludeCommentaries)
        c.setSwitch("strict_context_matching", false)
        assertFalse(c.state.value.strictContextMatching)
    }

    @Test fun setPermissionMode_updatesState() = runTest {
        val f = Fake(mutableMapOf("p1" to data()))
        val c = PromptEditController(f, promptId = "p1")
        c.setPermissionMode("ALLOW_ALL")
        assertEquals("ALLOW_ALL", c.state.value.permissionMode)
        c.setPermissionMode(null)
        assertNull(c.state.value.permissionMode)
    }

    // --- isNew ---

    @Test fun isNew_isComputedNotStale_reflectsIdAfterSave() = runTest {
        val f = Fake()
        f.saveIdToReturn = "brand-new-id"
        val c = PromptEditController(f, promptId = null)
        assertTrue(c.isNew)
        c.setName("Fresh")
        c.setTemplate("Body")
        c.save()
        assertFalse(c.isNew)
        assertEquals("brand-new-id", c.state.value.id)
    }

    // --- built-in prompt model-override save path ---

    @Test fun builtin_setModelOverride_allowed_canSaveOnlyOnceChanged_savesViaOverridePath() = runTest {
        val d = data(isReadOnly = true, isBuiltIn = true, name = "Builtin", modelOverrideId = "orig-model")
        val f = Fake(mutableMapOf("p1" to d))
        val c = PromptEditController(f, promptId = "p1")

        // Not yet changed: no Save.
        assertFalse(c.canSave.value)

        // setModelOverride is allowed even though the prompt is read-only.
        c.setModelOverride("new-model")
        assertEquals("new-model", c.state.value.modelOverrideId)

        // Other fields stay locked.
        c.setName("Should not change")
        assertEquals("Builtin", c.state.value.name)

        // canSave flips true only because the override actually changed.
        assertTrue(c.canSave.value)

        val id = c.save()
        assertEquals("p1", id)
        assertEquals(1, f.builtinOverrideCount)
        assertEquals("p1", f.lastBuiltinOverridePromptId)
        assertEquals("new-model", f.lastBuiltinOverrideModelId)
        assertEquals(0, f.saveCount) // savePrompt (the editable path) must NOT be used

        // Re-baselined: canSave/dirty go back to false until changed again.
        assertFalse(c.canSave.value)
        assertFalse(c.isDirty.value)

        // Setting it back to the same value it was just saved as is a no-op for canSave.
        c.setModelOverride("new-model")
        assertFalse(c.canSave.value)
    }

    @Test fun builtin_setModelOverride_backToOriginal_canSaveFalseAgain() = runTest {
        val d = data(isReadOnly = true, isBuiltIn = true, modelOverrideId = "orig-model")
        val f = Fake(mutableMapOf("p1" to d))
        val c = PromptEditController(f, promptId = "p1")

        c.setModelOverride("changed")
        assertTrue(c.canSave.value)
        c.setModelOverride("orig-model")
        assertFalse(c.canSave.value)
    }

    // --- genuinely read-only add-on prompt: no save path at all ---

    @Test fun readOnlyAddOn_setModelOverride_isGuarded_noSaveEverAvailable() = runTest {
        val d = data(isReadOnly = true, isBuiltIn = false, name = "AddOn", modelOverrideId = "orig-model")
        val f = Fake(mutableMapOf("p1" to d))
        val c = PromptEditController(f, promptId = "p1")

        assertFalse(c.canSave.value)
        c.setModelOverride("attempted-change")
        assertEquals("orig-model", c.state.value.modelOverrideId)
        assertFalse(c.canSave.value)
        assertFalse(c.isDirty.value)

        val savedId = c.save()
        assertNull(savedId)
        assertEquals(0, f.saveCount)
        assertEquals(0, f.builtinOverrideCount)
    }
}
