package net.bible.sharedcore.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Edit-dialog state for the add/edit-provider flow. `PICK_TYPE` is the type picker (builtin
 * providers + the synthetic "CUSTOM" choice); `FORM` is the detail form. For a custom provider
 * the name/endpoint/api-format fields are editable and a display name is required to save; for a
 * builtin provider the display name is preset from the [ProviderTypeVd] (name "locked" in the UI)
 * and only the API key is required.
 */
data class ProviderEditState(
    val id: String?,                 // null = new
    val step: Step,
    val typeId: String,
    val displayName: String,
    val apiKey: String,
    val endpoint: String,
    val apiFormatId: String,
    val isCustom: Boolean,
    val apiKeyUrl: String?,
    val canSave: Boolean,            // derived: apiKey non-blank && (!isCustom || displayName non-blank)
) {
    enum class Step { PICK_TYPE, FORM }
}

/**
 * Staging brain for the providers list/edit screen. Backed directly by [LlmProviderService.providers]
 * (the service already publishes a `StateFlow`, so no local mirror is needed) plus a dialog stack for
 * add/edit, mirroring the classic add/edit-provider dialog: pick a provider type, then fill in the
 * form (name/endpoint/api-format only editable for a custom provider), Save persists via
 * [LlmProviderService.saveProvider] and dismisses the dialog.
 */
class AiProvidersController(
    private val service: LlmProviderService,
    private val scope: CoroutineScope,
) {
    enum class Field { NAME, API_KEY, ENDPOINT, API_FORMAT }

    val providers: StateFlow<List<ProviderVd>> = service.providers

    private val _dialog = MutableStateFlow<ProviderEditState?>(null)
    val dialog: StateFlow<ProviderEditState?> = _dialog.asStateFlow()

    fun startAdd() {
        _dialog.value = ProviderEditState(
            id = null,
            step = ProviderEditState.Step.PICK_TYPE,
            typeId = "",
            displayName = "",
            apiKey = "",
            endpoint = "",
            apiFormatId = "",
            isCustom = false,
            apiKeyUrl = null,
            canSave = false,
        )
    }

    fun pickType(typeId: String) {
        val current = _dialog.value ?: return
        val type = service.providerTypes().firstOrNull { it.id == typeId } ?: return
        publish(
            current.copy(
                step = ProviderEditState.Step.FORM,
                typeId = type.id,
                displayName = if (type.isCustom) current.displayName else type.displayName,
                endpoint = type.defaultEndpoint,
                apiFormatId = if (type.isCustom) DEFAULT_CUSTOM_API_FORMAT else current.apiFormatId,
                isCustom = type.isCustom,
                apiKeyUrl = type.apiKeyUrl,
            )
        )
    }

    fun startEdit(id: String) {
        val p = service.providers.value.firstOrNull { it.id == id } ?: return
        val type = service.providerTypes().firstOrNull { it.id == p.providerTypeId }
        publish(
            ProviderEditState(
                id = p.id,
                step = ProviderEditState.Step.FORM,
                typeId = p.providerTypeId,
                displayName = p.displayName,
                apiKey = service.apiKeyFor(p.id),
                endpoint = p.endpoint,
                apiFormatId = p.apiFormatId,
                isCustom = p.isCustom,
                apiKeyUrl = type?.apiKeyUrl,
                canSave = false,
            )
        )
    }

    fun updateField(field: Field, value: String) {
        val current = _dialog.value ?: return
        publish(
            when (field) {
                Field.NAME -> current.copy(displayName = value)
                Field.API_KEY -> current.copy(apiKey = value)
                Field.ENDPOINT -> current.copy(endpoint = value)
                Field.API_FORMAT -> current.copy(apiFormatId = value)
            }
        )
    }

    fun save() {
        val s = _dialog.value ?: return
        if (!s.canSave) return
        scope.launch {
            service.saveProvider(s.id, s.typeId, s.displayName, s.apiKey, s.endpoint, s.apiFormatId)
            _dialog.value = null
        }
    }

    fun delete(id: String) = service.deleteProvider(id)

    fun dismissDialog() { _dialog.value = null }

    /** F31: accepts the AI disclaimer (Quick-setup / Add-provider gate), via the shared
     *  [LlmProviderService] — routed through the controller so the Compose screen never calls the
     *  service directly. */
    fun acceptDisclaimer() = service.acceptDisclaimer()

    private fun publish(next: ProviderEditState) {
        _dialog.value = next.copy(canSave = canSave(next))
    }

    private fun canSave(s: ProviderEditState): Boolean =
        s.apiKey.isNotBlank() && (!s.isCustom || s.displayName.isNotBlank())

    private companion object {
        const val DEFAULT_CUSTOM_API_FORMAT = "OPENAI"
    }
}
