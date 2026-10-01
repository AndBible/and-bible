package net.bible.sharedcore.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Implements the WHOLE [TextDisplaySettingsService] interface with no-op/default bodies so the
 *  interface stays instantiable in tests. Reused by later Batch 12d-B tasks. */
open class FakeColoursOnlyService : TextDisplaySettingsService {
    override fun loadText(scope: SettingsScope): TextSettingsSnapshot =
        TextSettingsSnapshot(scope, "Title", "WS", emptyMap(),
            showParentCategory = false, showWorkspaceLink = false, showGlobalLink = false)
    override fun setValue(scope: SettingsScope, type: TextSettingType, value: TextSettingValue) {}
    override fun revert(scope: SettingsScope, type: TextSettingType) {}
    override fun reset(scope: SettingsScope) {}

    override fun loadColors(scope: SettingsScope): ColorsSnapshot =
        ColorsSnapshot(
            title = "Colours", dayTextColor = 0, dayBackground = 0, dayNoise = 0,
            nightTextColor = 0, nightBackground = 0, nightNoise = 0,
            workspaceColor = 0, workspaceColorVisible = false,
            dayBackgroundImageInitials = null, dayBackgroundImageName = "None", dayBackgroundImageOpacity = 100,
            nightBackgroundImageInitials = null, nightBackgroundImageName = "None", nightBackgroundImageOpacity = 100,
            inheritedFrom = InheritedFrom.NONE,
        )
    override fun loadBackgroundOptions(): List<BackgroundImageOption> = emptyList()
    override fun setColor(scope: SettingsScope, field: ColorField, argb: Int) {}
    override fun setNoise(scope: SettingsScope, night: Boolean, value: Int) {}
    override fun setWorkspaceColor(scope: SettingsScope, argb: Int) {}
    override fun clearWorkspaceColor(scope: SettingsScope) {}
    override fun setBackgroundImage(scope: SettingsScope, night: Boolean, initials: String?) {}
    override fun setBackgroundOpacity(scope: SettingsScope, night: Boolean, opacity: Int) {}
    override fun resetColors(scope: SettingsScope) {}
    override suspend fun importBackgroundImage(picker: suspend () -> String?): BackgroundImageOption? = null
    override fun deleteBackgroundImage(initials: String) {}
}

class ColorsSettingsDtoTest {
    private fun snap() = ColorsSnapshot(
        title = "Colours", dayTextColor = -16777216, dayBackground = -1, dayNoise = 0,
        nightTextColor = -1, nightBackground = -16777216, nightNoise = 0,
        workspaceColor = -12303292, workspaceColorVisible = true,
        dayBackgroundImageInitials = null, dayBackgroundImageName = "None", dayBackgroundImageOpacity = 100,
        nightBackgroundImageInitials = "BGIMG_hills", nightBackgroundImageName = "hills", nightBackgroundImageOpacity = 80,
        inheritedFrom = InheritedFrom.WORKSPACE,
    )

    @Test fun snapshotHoldsMergedValues() {
        val s = snap()
        assertEquals(-1, s.dayBackground)
        assertNull(s.dayBackgroundImageInitials)
        assertEquals("hills", s.nightBackgroundImageName)
        assertEquals(80, s.nightBackgroundImageOpacity)
        assertEquals(InheritedFrom.WORKSPACE, s.inheritedFrom)
    }

    // T8: ColorField grew a fifth value, WORKSPACE, so SettingsEditorPage.ColorPick(field) can also
    // address the workspace swatch -- it is not one of TextDisplaySettingsService.setColor's four
    // Colors fields (ColorSettingsController.onColorChange routes it to setWorkspaceColor instead).
    @Test fun colorFieldHasFourColorsFieldsPlusWorkspace() {
        assertEquals(
            listOf(
                ColorField.DAY_TEXT, ColorField.DAY_BACKGROUND, ColorField.NIGHT_TEXT, ColorField.NIGHT_BACKGROUND,
                ColorField.WORKSPACE,
            ),
            ColorField.entries.toList(),
        )
    }

    @Test fun colorForResolvesAllFiveFieldsIncludingWorkspace() {
        val s = snap()
        assertEquals(s.dayTextColor, s.colorFor(ColorField.DAY_TEXT))
        assertEquals(s.dayBackground, s.colorFor(ColorField.DAY_BACKGROUND))
        assertEquals(s.nightTextColor, s.colorFor(ColorField.NIGHT_TEXT))
        assertEquals(s.nightBackground, s.colorFor(ColorField.NIGHT_BACKGROUND))
        assertEquals(s.workspaceColor, s.colorFor(ColorField.WORKSPACE))
    }

    @Test fun colorForPageReadsThroughUiState() {
        val state = ColorSettingsUiState(colors = snap(), backgroundOptions = emptyList())
        assertEquals(snap().workspaceColor, colorForPage(state, ColorField.WORKSPACE))
        assertEquals(snap().dayTextColor, colorForPage(state, ColorField.DAY_TEXT))
    }

    @Test fun serviceExposesColoursAndBackgroundMembers() {
        // Compiles only if the interface carries all Plan-B members with these exact signatures.
        val calls = mutableListOf<String>()
        val svc = object : FakeColoursOnlyService() {
            override fun setColor(scope: SettingsScope, field: ColorField, argb: Int) { calls += "setColor:$field:$argb" }
        }
        svc.setColor(SettingsScope.Workspace("ws"), ColorField.DAY_TEXT, 123)
        assertEquals(listOf("setColor:DAY_TEXT:123"), calls)
    }
}
