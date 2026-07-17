package net.bible.sharedcore.ai

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AiModelsControllerTest {

    private val providerA = ProviderVd("p1", "My OpenAI", "OPENAI", true, false, "", "")
    private val providerB = ProviderVd("p2", "My Anthropic", "ANTHROPIC", true, false, "", "")

    private val knownModel = AvailableModelVd(modelId = "gpt-4o", label = "GPT-4o", supported = true, knownPricing = true)
    private val unknownModel = AvailableModelVd(modelId = "weird-model", label = "Weird Model", supported = false, knownPricing = false)

    data class SaveCall(
        val id: String?, val providerId: String, val modelId: String,
        val priceInput: String?, val priceOutput: String?, val setDefault: Boolean,
    )

    private class Fake(
        initialModels: List<ModelVd> = emptyList(),
        val providerChoices: List<ProviderVd> = emptyList(),
        val availableModelsByProvider: Map<String, List<AvailableModelVd>> = emptyMap(),
    ) : LlmModelService {
        val flow = MutableStateFlow(initialModels)
        override val models: StateFlow<List<ModelVd>> = flow

        var lastSave: SaveCall? = null
        var saveCount = 0
        var lastDeleteId: String? = null
        var deleteCount = 0
        var lastSetDefaultId: String? = null
        var setDefaultCount = 0
        var refreshCount = 0

        override fun providersForPicker() = providerChoices
        override suspend fun availableModelsFor(providerId: String) = availableModelsByProvider[providerId] ?: emptyList()
        override suspend fun saveModel(
            id: String?, providerId: String, modelId: String,
            priceInput: String?, priceOutput: String?, setDefault: Boolean,
        ) {
            lastSave = SaveCall(id, providerId, modelId, priceInput, priceOutput, setDefault)
            saveCount++
        }
        override fun deleteModel(id: String) { lastDeleteId = id; deleteCount++ }
        override fun setDefault(id: String) { lastSetDefaultId = id; setDefaultCount++ }
        override fun refresh() { refreshCount++ }
    }

    private fun controller(fake: Fake) = AiModelsController(fake, CoroutineScope(UnconfinedTestDispatcher()))

    @Test fun models_defaultFirstSort() = runTest {
        val m1 = ModelVd("m1", "gpt-4o", "gpt-4o", "p1", isDefault = false, supported = true, pricingSummary = "$3/$15")
        val m2 = ModelVd("m2", "claude", "claude", "p1", isDefault = true, supported = true, pricingSummary = "$3/$15")
        val m3 = ModelVd("m3", "other", "other", "p1", isDefault = false, supported = true, pricingSummary = "")
        val f = Fake(initialModels = listOf(m1, m2, m3))
        val c = controller(f)
        assertEquals(listOf(m2, m1, m3), c.models.value)
    }

    @Test fun startAdd_opensDialogAtPickProviderWithChoices() = runTest {
        val f = Fake(providerChoices = listOf(providerA, providerB))
        val c = controller(f)
        assertNull(c.dialog.value)
        c.startAdd()
        val s = c.dialog.value
        assertNotNull(s)
        assertEquals(ModelEditState.Step.PICK_PROVIDER, s.step)
        assertEquals(listOf(providerA, providerB), s.providerChoices)
        assertFalse(s.canSave)
    }

    @Test fun pickProvider_loadsAvailableModelsAndAdvancesStep() = runTest {
        val f = Fake(
            providerChoices = listOf(providerA),
            availableModelsByProvider = mapOf("p1" to listOf(knownModel, unknownModel)),
        )
        val c = controller(f)
        c.startAdd()
        c.pickProvider("p1")
        val s = c.dialog.value!!
        assertEquals(ModelEditState.Step.PICK_MODEL, s.step)
        assertEquals("p1", s.providerId)
        assertEquals(listOf(knownModel, unknownModel), s.availableModels)
        assertFalse(s.loadingModels)
    }

    @Test fun pickModel_custom_revealsCustomModelAndPriceInputs() = runTest {
        val f = Fake(providerChoices = listOf(providerA), availableModelsByProvider = mapOf("p1" to listOf(knownModel)))
        val c = controller(f)
        c.startAdd()
        c.pickProvider("p1")
        c.pickModel(AiModelsController.CUSTOM_MODEL_ID)
        val s = c.dialog.value!!
        assertTrue(s.isCustom)
        assertTrue(s.showPriceFields)
        assertFalse(s.canSave) // no custom model id text yet

        c.updateField(AiModelsController.Field.CUSTOM_MODEL_ID, "my-custom-model")
        assertTrue(c.dialog.value!!.canSave)
    }

    @Test fun pickModel_knownPricing_hidesEditablePrice() = runTest {
        val f = Fake(providerChoices = listOf(providerA), availableModelsByProvider = mapOf("p1" to listOf(knownModel, unknownModel)))
        val c = controller(f)
        c.startAdd()
        c.pickProvider("p1")
        c.pickModel(knownModel.modelId)
        val s = c.dialog.value!!
        assertFalse(s.isCustom)
        assertFalse(s.showPriceFields)
        assertTrue(s.canSave)
    }

    @Test fun pickModel_unknownPricing_showsEditablePriceButNotCustomInput() = runTest {
        val f = Fake(providerChoices = listOf(providerA), availableModelsByProvider = mapOf("p1" to listOf(knownModel, unknownModel)))
        val c = controller(f)
        c.startAdd()
        c.pickProvider("p1")
        c.pickModel(unknownModel.modelId)
        val s = c.dialog.value!!
        assertFalse(s.isCustom)
        assertTrue(s.showPriceFields)
        assertTrue(s.canSave)
    }

    @Test fun save_knownPricingModel_callsServiceWithNullPricesAndDismisses() = runTest {
        val existing = ModelVd("m0", "existing", "existing", "p1", isDefault = true, supported = true, pricingSummary = "")
        val f = Fake(
            initialModels = listOf(existing),
            providerChoices = listOf(providerA),
            availableModelsByProvider = mapOf("p1" to listOf(knownModel)),
        )
        val c = controller(f)
        c.startAdd()
        c.pickProvider("p1")
        c.pickModel(knownModel.modelId)
        c.save()
        assertEquals(SaveCall(null, "p1", "gpt-4o", null, null, false), f.lastSave)
        assertEquals(1, f.saveCount)
        assertNull(c.dialog.value)
    }

    @Test fun save_customModel_passesEnteredPricesAndSetDefaultFlag() = runTest {
        val existing = ModelVd("m0", "existing", "existing", "p1", isDefault = true, supported = true, pricingSummary = "")
        val f = Fake(initialModels = listOf(existing), providerChoices = listOf(providerA))
        val c = controller(f)
        c.startAdd()
        c.pickProvider("p1")
        c.pickModel(AiModelsController.CUSTOM_MODEL_ID)
        c.updateField(AiModelsController.Field.CUSTOM_MODEL_ID, "my-custom-model")
        c.updateField(AiModelsController.Field.PRICE_INPUT, "1.5")
        c.updateField(AiModelsController.Field.PRICE_OUTPUT, "2.5")
        c.setAsDefault(true)
        c.save()
        assertEquals(SaveCall(null, "p1", "my-custom-model", "1.5", "2.5", true), f.lastSave)
    }

    @Test fun save_firstModel_autoSetsDefaultEvenWithoutCheckbox() = runTest {
        val f = Fake(initialModels = emptyList(), providerChoices = listOf(providerA), availableModelsByProvider = mapOf("p1" to listOf(knownModel)))
        val c = controller(f)
        c.startAdd()
        c.pickProvider("p1")
        c.pickModel(knownModel.modelId)
        // setAsDefault left untouched (false)
        c.save()
        assertEquals(true, f.lastSave?.setDefault)
    }

    @Test fun setDefault_callsService() = runTest {
        val f = Fake()
        val c = controller(f)
        c.setDefault("m1")
        assertEquals("m1", f.lastSetDefaultId)
        assertEquals(1, f.setDefaultCount)
    }

    @Test fun delete_callsService() = runTest {
        val f = Fake()
        val c = controller(f)
        c.delete("m1")
        assertEquals("m1", f.lastDeleteId)
        assertEquals(1, f.deleteCount)
    }

    @Test fun dismissDialog_clearsState() = runTest {
        val f = Fake(providerChoices = listOf(providerA))
        val c = controller(f)
        c.startAdd()
        assertNotNull(c.dialog.value)
        c.dismissDialog()
        assertNull(c.dialog.value)
    }

    @Test fun setShowUnsupported_togglesFlag() = runTest {
        val f = Fake(providerChoices = listOf(providerA))
        val c = controller(f)
        c.startAdd()
        assertFalse(c.dialog.value!!.showUnsupported)
        c.setShowUnsupported(true)
        assertTrue(c.dialog.value!!.showUnsupported)
    }
}
