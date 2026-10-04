package net.bible.sharedcore.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AiProvidersControllerTest {

    private val builtinType = ProviderTypeVd(
        id = "OPENAI", displayName = "OpenAI", tier = "RECOMMENDED",
        apiKeyUrl = "https://openai.com/keys", defaultEndpoint = "https://api.openai.com",
        supportsDynamicModels = true, isCustom = false,
    )
    private val customType = ProviderTypeVd(
        id = "CUSTOM", displayName = "Custom", tier = "UNCATEGORIZED",
        apiKeyUrl = null, defaultEndpoint = "", supportsDynamicModels = false, isCustom = true,
    )

    data class SaveCall(
        val id: String?, val typeId: String, val displayName: String,
        val apiKey: String, val endpoint: String, val apiFormatId: String,
    )

    private open class Fake(
        initialProviders: List<ProviderVd> = emptyList(),
        val types: List<ProviderTypeVd>,
        val apiKeys: MutableMap<String, String> = mutableMapOf(),
    ) : LlmProviderService {
        val flow = MutableStateFlow(initialProviders)
        override val providers: StateFlow<List<ProviderVd>> = flow
        var lastSave: SaveCall? = null
        var lastDeleteId: String? = null
        var saveCount = 0
        var deleteCount = 0
        override fun providerTypes() = types
        override fun apiKeyFor(providerId: String) = apiKeys[providerId] ?: ""
        override suspend fun saveProvider(
            id: String?, typeId: String, displayName: String, apiKey: String, endpoint: String, apiFormatId: String,
        ) {
            lastSave = SaveCall(id, typeId, displayName, apiKey, endpoint, apiFormatId)
            saveCount++
        }
        override fun deleteProvider(id: String) { lastDeleteId = id; deleteCount++ }
        override suspend fun testConnection(typeId: String, endpoint: String, apiKey: String) = Result.success(Unit)
        override suspend fun fetchAvailableModels(providerId: String) = emptyList<AvailableModelVd>()
        override fun refresh() {}
        override fun recommendedSetups() = emptyList<RecommendedSetupVd>()
        override suspend fun performEasySetup(setupId: String, apiKey: String) {}
        override fun disclaimerAccepted() = true
        override fun acceptDisclaimer() {}
    }

    private fun controller(fake: Fake) = AiProvidersController(fake, CoroutineScope(UnconfinedTestDispatcher()))

    @Test fun providers_reflectsServiceFlow() = runTest {
        val p = ProviderVd("p1", "My OpenAI", "OPENAI", true, false, "", "")
        val f = Fake(listOf(p), types = listOf(builtinType, customType))
        val c = controller(f)
        assertEquals(listOf(p), c.providers.value)
        val p2 = ProviderVd("p2", "Another", "OPENAI", true, false, "", "")
        f.flow.value = listOf(p, p2)
        assertEquals(listOf(p, p2), c.providers.value)
    }

    @Test fun startAdd_opensDialogAtPickType() = runTest {
        val f = Fake(types = listOf(builtinType, customType))
        val c = controller(f)
        assertNull(c.dialog.value)
        c.startAdd()
        val s = c.dialog.value
        assertNotNull(s)
        assertEquals(ProviderEditState.Step.PICK_TYPE, s.step)
        assertNull(s.id)
        assertFalse(s.canSave)
    }

    @Test fun pickType_custom_opensFormEditableAndRequiresNameAndKey() = runTest {
        val f = Fake(types = listOf(builtinType, customType))
        val c = controller(f)
        c.startAdd()
        c.pickType("CUSTOM")
        var s = c.dialog.value!!
        assertEquals(ProviderEditState.Step.FORM, s.step)
        assertTrue(s.isCustom)
        assertFalse(s.canSave) // no apiKey, no name yet

        c.updateField(AiProvidersController.Field.API_KEY, "sk-123")
        s = c.dialog.value!!
        assertFalse(s.canSave) // custom still needs a name

        c.updateField(AiProvidersController.Field.NAME, "My Custom")
        s = c.dialog.value!!
        assertTrue(s.canSave)
    }

    @Test fun pickType_builtin_lockNameAndToggleCanSaveOnApiKey() = runTest {
        val f = Fake(types = listOf(builtinType, customType))
        val c = controller(f)
        c.startAdd()
        c.pickType("OPENAI")
        var s = c.dialog.value!!
        assertEquals(ProviderEditState.Step.FORM, s.step)
        assertFalse(s.isCustom)
        assertEquals(builtinType.displayName, s.displayName)
        assertFalse(s.canSave)

        c.updateField(AiProvidersController.Field.API_KEY, "sk-abc")
        s = c.dialog.value!!
        assertTrue(s.canSave)

        c.updateField(AiProvidersController.Field.API_KEY, "")
        s = c.dialog.value!!
        assertFalse(s.canSave)
    }

    @Test fun updateField_recomputesCanSave() = runTest {
        val f = Fake(types = listOf(builtinType, customType))
        val c = controller(f)
        c.startAdd()
        c.pickType("CUSTOM")
        c.updateField(AiProvidersController.Field.NAME, "N")
        c.updateField(AiProvidersController.Field.API_KEY, "k")
        assertTrue(c.dialog.value!!.canSave)
        c.updateField(AiProvidersController.Field.API_KEY, "")
        assertFalse(c.dialog.value!!.canSave)
    }

    @Test fun save_callsServiceWithRightArgsThenDismisses() = runTest {
        val f = Fake(types = listOf(builtinType, customType))
        val c = controller(f)
        c.startAdd()
        c.pickType("CUSTOM")
        c.updateField(AiProvidersController.Field.NAME, "My Custom")
        c.updateField(AiProvidersController.Field.API_KEY, "sk-999")
        c.updateField(AiProvidersController.Field.ENDPOINT, "https://example.com/v1")
        c.updateField(AiProvidersController.Field.API_FORMAT, "ANTHROPIC")
        c.save()
        assertEquals(SaveCall(null, "CUSTOM", "My Custom", "sk-999", "https://example.com/v1", "ANTHROPIC"), f.lastSave)
        assertEquals(1, f.saveCount)
        assertNull(c.dialog.value)
    }

    @Test fun startEdit_prefillsFromProviderVd() = runTest {
        val p = ProviderVd(
            id = "p1", displayName = "Existing", providerTypeId = "CUSTOM",
            apiKeySet = true, isCustom = true, endpoint = "https://old.example", apiFormatId = "OPENAI",
        )
        val f = Fake(listOf(p), types = listOf(builtinType, customType), apiKeys = mutableMapOf("p1" to "sk-existing"))
        val c = controller(f)
        c.startEdit("p1")
        val s = c.dialog.value!!
        assertEquals(ProviderEditState.Step.FORM, s.step)
        assertEquals("p1", s.id)
        assertEquals("CUSTOM", s.typeId)
        assertEquals("Existing", s.displayName)
        assertEquals("sk-existing", s.apiKey)
        assertEquals("https://old.example", s.endpoint)
        assertEquals("OPENAI", s.apiFormatId)
        assertTrue(s.isCustom)
        assertTrue(s.canSave) // apiKey + name already present
    }

    @Test fun delete_callsServiceDeleteProvider() = runTest {
        val f = Fake(types = listOf(builtinType, customType))
        val c = controller(f)
        c.delete("p1")
        assertEquals("p1", f.lastDeleteId)
        assertEquals(1, f.deleteCount)
    }

    @Test fun dismissDialog_clearsState() = runTest {
        val f = Fake(types = listOf(builtinType, customType))
        val c = controller(f)
        c.startAdd()
        assertNotNull(c.dialog.value)
        c.dismissDialog()
        assertNull(c.dialog.value)
    }

    // --- Task 6: easy-setup + disclaimer pass-throughs --------------------------------------

    @Test fun providerTypes_returnsServiceList() = runTest {
        val f = Fake(types = listOf(builtinType, customType))
        val c = controller(f)
        assertEquals(listOf(builtinType, customType), c.providerTypes())
    }

    @Test fun disclaimerAccepted_reflectsService() = runTest {
        val f = Fake(types = listOf(builtinType, customType))
        val c = controller(f)
        assertTrue(c.disclaimerAccepted()) // Fake defaults to true
    }

    @Test fun recommendedSetups_returnsServiceList() = runTest {
        val f = Fake(types = listOf(builtinType, customType))
        val c = controller(f)
        assertEquals(emptyList(), c.recommendedSetups())
    }

    @Test fun testConnection_delegatesWithEmptyEndpoint() = runTest {
        var seenTypeId: String? = null
        var seenEndpoint: String? = null
        var seenApiKey: String? = null
        val f = object : Fake(types = listOf(builtinType, customType)) {
            override suspend fun testConnection(typeId: String, endpoint: String, apiKey: String): Result<Unit> {
                seenTypeId = typeId; seenEndpoint = endpoint; seenApiKey = apiKey
                return Result.success(Unit)
            }
        }
        val c = controller(f)
        val result = c.testConnection("OPENAI", "sk-123")
        assertTrue(result.isSuccess)
        assertEquals("OPENAI", seenTypeId)
        assertEquals("", seenEndpoint)
        assertEquals("sk-123", seenApiKey)
    }

    @Test fun performEasySetup_delegatesToService() = runTest {
        var seenSetupId: String? = null
        var seenApiKey: String? = null
        val f = object : Fake(types = listOf(builtinType, customType)) {
            override suspend fun performEasySetup(setupId: String, apiKey: String) {
                seenSetupId = setupId; seenApiKey = apiKey
            }
        }
        val c = controller(f)
        c.performEasySetup("setup1", "sk-999")
        assertEquals("setup1", seenSetupId)
        assertEquals("sk-999", seenApiKey)
    }
}
