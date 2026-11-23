package net.bible.sharedcore.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.bible.sharedcore.log.Log

/** Full UI state the colours screen (and the chooser sub-screen) render from. */
data class ColorSettingsUiState(
    val colors: ColorsSnapshot,
    val backgroundOptions: List<BackgroundImageOption>,
    val loading: Boolean = false,                       // during importBackgroundImage()
    val deleteConfirm: BackgroundImageOption? = null,   // non-null → confirm dialog shown
)

/**
 * Drives the Compose colours editor + background-image chooser for a given [scope], off
 * [TextDisplaySettingsService]'s colours/background members. Unlike [SyncSettingsController]
 * (which collects a live `service.snapshot` flow), this controller re-loads explicitly: every
 * mutator delegates to the matching `service.*` call, then rebuilds [state] from
 * `service.loadColors(scope)` + `service.loadBackgroundOptions()`.
 */
class ColorSettingsController(
    private val service: TextDisplaySettingsService,
    private val scope: SettingsScope,
    private val coroutineScope: CoroutineScope,
    private val imagePicker: suspend () -> String?,
    private val onImportError: (Exception?) -> Unit = {},
) {
    private val _state = MutableStateFlow(
        ColorSettingsUiState(colors = service.loadColors(scope), backgroundOptions = service.loadBackgroundOptions())
    )
    val state: StateFlow<ColorSettingsUiState> = _state.asStateFlow()

    private fun reload() {
        _state.value = _state.value.copy(
            colors = service.loadColors(scope),
            backgroundOptions = service.loadBackgroundOptions(),
        )
    }

    /** [ColorField.WORKSPACE] has no `Colors` field of its own to write — it is routed to
     *  [TextDisplaySettingsService.setWorkspaceColor] instead of [TextDisplaySettingsService.setColor],
     *  same as the pre-sheet workspace swatch's separate dialog/callback always did. */
    fun onColorChange(field: ColorField, argb: Int) {
        if (field == ColorField.WORKSPACE) service.setWorkspaceColor(scope, argb) else service.setColor(scope, field, argb)
        reload()
    }

    fun onNoiseChange(night: Boolean, value: Int) {
        service.setNoise(scope, night, value)
        reload()
    }

    fun onWorkspaceColorChange(argb: Int) {
        service.setWorkspaceColor(scope, argb)
        reload()
    }

    fun onWorkspaceColorReset() {
        service.clearWorkspaceColor(scope)
        reload()
    }

    fun onOpacityChange(night: Boolean, value: Int) {
        service.setBackgroundOpacity(scope, night, value)
        reload()
    }

    fun onSelectBackgroundImage(night: Boolean, initials: String?) {
        service.setBackgroundImage(scope, night, initials)
        reload()
    }

    fun onImportBackgroundImage() {
        if (_state.value.loading) return
        _state.value = _state.value.copy(loading = true)
        coroutineScope.launch {
            var imageSelected = false
            val trackedPicker: suspend () -> String? = {
                imagePicker().also { if (it != null) imageSelected = true }
            }
            try {
                val result = service.importBackgroundImage(trackedPicker)
                if (imageSelected && result == null) {
                    Log.e("ColorSettingsController", "Background image import returned no result after image selection")
                    onImportError(null)
                }
                reload()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ColorSettingsController", "Background image import failed", e)
                onImportError(e)
            } finally {
                _state.value = _state.value.copy(loading = false)
            }
        }
    }

    fun onRequestDeleteBackgroundImage(option: BackgroundImageOption) {
        _state.value = _state.value.copy(deleteConfirm = option)
    }

    fun onConfirmDeleteBackgroundImage() {
        val option = _state.value.deleteConfirm ?: return
        service.deleteBackgroundImage(option.initials)
        reload()
        _state.value = _state.value.copy(deleteConfirm = null)
    }

    fun onDismissDeleteConfirm() {
        _state.value = _state.value.copy(deleteConfirm = null)
    }

    fun onReset() {
        service.resetColors(scope)
        reload()
    }
}
