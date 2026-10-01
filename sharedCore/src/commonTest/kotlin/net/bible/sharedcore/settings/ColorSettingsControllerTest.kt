package net.bible.sharedcore.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColorSettingsControllerTest {
    private class RecordingService : FakeColoursOnlyService() {
        val calls = mutableListOf<String>()
        var options = listOf(BackgroundImageOption("BGIMG_a", "a", "BGIMG_a"))
        var dayImage: String? = null
        override fun loadColors(scope: SettingsScope) = ColorsSnapshot(
            title = "Colours", dayTextColor = -16777216, dayBackground = -1, dayNoise = 0,
            nightTextColor = -1, nightBackground = -16777216, nightNoise = 0,
            workspaceColor = -12303292, workspaceColorVisible = scope !is SettingsScope.Window,
            dayBackgroundImageInitials = dayImage, dayBackgroundImageName = dayImage ?: "None", dayBackgroundImageOpacity = 100,
            nightBackgroundImageInitials = null, nightBackgroundImageName = "None", nightBackgroundImageOpacity = 100,
            inheritedFrom = InheritedFrom.NONE,
        )
        override fun loadBackgroundOptions() = options
        override fun setColor(scope: SettingsScope, field: ColorField, argb: Int) { calls += "setColor:$field:$argb" }
        override fun setNoise(scope: SettingsScope, night: Boolean, value: Int) { calls += "setNoise:$night:$value" }
        override fun setWorkspaceColor(scope: SettingsScope, argb: Int) { calls += "setWorkspaceColor:$argb" }
        override fun clearWorkspaceColor(scope: SettingsScope) { calls += "clearWorkspaceColor" }
        override fun setBackgroundImage(scope: SettingsScope, night: Boolean, initials: String?) { calls += "setBg:$night:$initials"; if (!night) dayImage = initials }
        override fun setBackgroundOpacity(scope: SettingsScope, night: Boolean, opacity: Int) { calls += "setOpacity:$night:$opacity" }
        override suspend fun importBackgroundImage(picker: suspend () -> String?): BackgroundImageOption? {
            val uri = picker() ?: run { calls += "import:cancelled"; return null }
            val opt = BackgroundImageOption("BGIMG_new", "new", "BGIMG_new")
            options = options + opt
            calls += "import:$uri"
            return opt
        }
        override fun deleteBackgroundImage(initials: String) { calls += "delete:$initials"; options = options.filterNot { it.initials == initials } }
        override fun resetColors(scope: SettingsScope) { calls += "reset"; dayImage = null }
    }

    // Dispatchers.Unconfined (not a plain CoroutineScope(Job()), which has no dispatcher and so
    // schedules launch{} on the real Dispatchers.Default background pool — a genuine cross-thread
    // race against the assertions below) runs a non-suspending launch{} body eagerly on the calling
    // thread, same pattern as SyncSettingsControllerTest.
    private fun controller(
        svc: RecordingService,
        scope: SettingsScope = SettingsScope.Workspace("ws"),
        imagePicker: suspend () -> String? = { null },
    ) = ColorSettingsController(svc, scope, CoroutineScope(Dispatchers.Unconfined), imagePicker)

    @Test fun seedsFromServiceLoads() {
        val svc = RecordingService()
        val c = controller(svc)
        assertEquals("None", c.state.value.colors.dayBackgroundImageName)
        assertEquals(1, c.state.value.backgroundOptions.size)
    }

    @Test fun mutatorsDelegateThenReload() {
        val svc = RecordingService()
        val c = controller(svc)
        c.onColorChange(ColorField.DAY_TEXT, 5)
        c.onNoiseChange(true, 30)
        c.onWorkspaceColorChange(9)
        c.onOpacityChange(false, 70)
        assertEquals(listOf("setColor:DAY_TEXT:5", "setNoise:true:30", "setWorkspaceColor:9", "setOpacity:false:70"), svc.calls)
    }

    @Test fun workspaceColorResetClearsInsteadOfWritingAColour() {
        val svc = RecordingService()
        val c = controller(svc)
        c.onWorkspaceColorReset()
        assertEquals(listOf("clearWorkspaceColor"), svc.calls)
    }

    // A ColorPick page addressing the workspace swatch (SettingsEditorPage.ColorPick(ColorField.WORKSPACE))
    // must land on setWorkspaceColor, not setColor -- ColorField.WORKSPACE has no `Colors` field.
    @Test fun workspaceFieldRoutesToSetWorkspaceColor() {
        val svc = RecordingService()
        val c = controller(svc)
        c.onColorChange(ColorField.WORKSPACE, 7)
        assertEquals(listOf("setWorkspaceColor:7"), svc.calls)
    }

    @Test fun selectBackgroundImageAppliesAndReflectsInState() {
        val svc = RecordingService()
        val c = controller(svc)
        c.onSelectBackgroundImage(night = false, initials = "BGIMG_a")
        assertTrue(svc.calls.contains("setBg:false:BGIMG_a"))
        assertEquals("BGIMG_a", c.state.value.colors.dayBackgroundImageName)
    }

    @Test fun importAddsOptionAndClearsLoading() {
        val svc = RecordingService()
        val c = controller(svc, imagePicker = { "content://picked" })
        c.onImportBackgroundImage()
        assertTrue(svc.calls.contains("import:content://picked"))
        assertEquals(2, c.state.value.backgroundOptions.size)
        assertEquals(false, c.state.value.loading)
    }

    // The picker is a PARAMETER of ColorSettingsController now (T9), not a settable property on the
    // service -- this is what makes "two hosts clobber each other's picker" structurally impossible.
    @Test fun importPassesTheHostsPickerToTheService() {
        val svc = RecordingService()
        var pickerCalls = 0
        val c = ColorSettingsController(
            svc, SettingsScope.Workspace("ws"), CoroutineScope(Dispatchers.Unconfined),
            imagePicker = { pickerCalls++; "content://picked" },
        )
        c.onImportBackgroundImage()
        assertEquals(1, pickerCalls)
        assertTrue(svc.calls.contains("import:content://picked"))
        assertEquals(false, c.state.value.loading)
    }

    @Test fun importWithACancelledPickerChangesNothing() {
        val svc = RecordingService()
        val c = ColorSettingsController(
            svc, SettingsScope.Workspace("ws"), CoroutineScope(Dispatchers.Unconfined),
            imagePicker = { null },
        )
        val before = c.state.value.backgroundOptions
        c.onImportBackgroundImage()
        assertEquals(before, c.state.value.backgroundOptions)
        assertEquals(false, c.state.value.loading)
    }

    @Test fun deleteConfirmFlow() {
        val svc = RecordingService()
        val c = controller(svc)
        val opt = svc.options.first()
        c.onRequestDeleteBackgroundImage(opt)
        assertEquals(opt, c.state.value.deleteConfirm)
        c.onConfirmDeleteBackgroundImage()
        assertTrue(svc.calls.contains("delete:BGIMG_a"))
        assertNull(c.state.value.deleteConfirm)
        assertEquals(0, c.state.value.backgroundOptions.size)
    }

    @Test fun dismissDeleteConfirmDoesNotDelete() {
        val svc = RecordingService()
        val c = controller(svc)
        c.onRequestDeleteBackgroundImage(svc.options.first())
        c.onDismissDeleteConfirm()
        assertNull(c.state.value.deleteConfirm)
        assertTrue(svc.calls.none { it.startsWith("delete") })
    }

    @Test fun resetDelegatesToServiceAndReloads() {
        val svc = RecordingService()
        svc.dayImage = "BGIMG_a"
        val c = controller(svc)
        c.onReset()
        assertTrue(svc.calls.contains("reset"))
        assertEquals("None", c.state.value.colors.dayBackgroundImageName)   // reload reflects the reset
    }
}
