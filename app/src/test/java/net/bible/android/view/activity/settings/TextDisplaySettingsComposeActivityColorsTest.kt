/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.android.view.activity.settings

import androidx.compose.runtime.State
import net.bible.android.TestBibleApplication
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.settings.SettingsScope
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings editor sheets T12: [TextDisplaySettingsComposeActivity.intentFor] no longer takes a
 * `startDestination` argument -- that extra, and the `onCreate` block that read it to jump
 * straight into the internal `colors` destination, were deleted (the only caller that ever passed
 * `"colors"`, [net.bible.android.view.activity.page.ColorPreference.openDialog], was itself
 * deleted in the same task: `OptionsMenuStateBuilder.dispatch` now routes COLORS to the reading
 * view's in-place editor sheet before `openDialog` is ever called -- Settings editor sheets T11).
 * This pins the resulting behaviour: a plain `intentFor(context, scope)` launch always starts on
 * the text-options LIST ([colorsScope] stays null), never on Colors.
 *
 * [colorsScope] is private, and this repo has no Compose UI-test harness (nothing uses
 * `createComposeRule`, and `compose-ui-test` cannot be added under strict egress -- see
 * `SettingsBadgeLayoutDriftTest`'s kdoc), so there is no way to observe which destination
 * rendered except reflection on the compiled `colorsScope$delegate` field (a `MutableState`,
 * since the property is `private var colorsScope by mutableStateOf<SettingsScope?>(null)`) --
 * the same private-field-reflection pattern already used by e.g. `ClientPageObjectsTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class TextDisplaySettingsComposeActivityColorsTest {

    @Test
    fun `plain intentFor launch starts at the text list, not colors`() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        val repo = CommonUtils.windowControl.windowRepository
        val intent = TextDisplaySettingsComposeActivity.intentFor(
            org.robolectric.RuntimeEnvironment.getApplication(),
            SettingsScope.Workspace(repo.id.toString()),
        )
        val controller = Robolectric.buildActivity(TextDisplaySettingsComposeActivity::class.java, intent).setup()
        val activity = controller.get()

        val delegateField = TextDisplaySettingsComposeActivity::class.java.getDeclaredField("colorsScope\$delegate")
        delegateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val colorsScopeState = delegateField.get(activity) as State<SettingsScope?>

        assertNull(
            "A plain intentFor launch (no startDestination extra any more) must start on the " +
                "text-options list, not jump straight into Colors",
            colorsScopeState.value,
        )
    }
}
