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

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.sharedui.app.workspace.buildBackgroundImageChooserLabels
import net.bible.sharedui.app.workspace.buildColorSettingsLabels
import net.bible.sharedui.app.workspace.buildTextDisplayControllerLabels
import net.bible.sharedui.app.workspace.buildTextDisplayScreenLabels
import net.bible.sharedui.strings.AndroidStrings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The reading view's in-place text-settings editor builds its labels with the `Strings` extensions
 * (`TextDisplayLabels.kt`, via `AndroidStrings`); it used to use the `Context` builders in
 * `TextDisplaySettingsLabelBuilders.kt`, which the settings activity still uses. The two must give the
 * same text for every label, or the reading editor silently changes wording. Compared property by
 * property (public getters) so a bundle that is not a data class cannot compare vacuously.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class TextDisplayLabelBuilderParityTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val strings get() = AndroidStrings(context)

    private fun assertSameLabels(name: String, expected: Any, actual: Any) {
        val getters = expected.javaClass.methods.filter {
            it.parameterCount == 0 && it.name.startsWith("get") && it.name != "getClass"
        }
        assertTrue(getters.size >= 5, "$name: reflection found only ${getters.size} getters -- the check would be vacuous")
        getters.forEach { g ->
            assertEquals(g.invoke(expected), g.invoke(actual), "$name.${g.name} differs between the Context and Strings builders")
        }
        assertEquals(expected, actual, "$name differs as a whole")
    }

    @Test fun controllerLabelsMatch() =
        assertSameLabels("controller", buildTextDisplayControllerLabels(context), strings.buildTextDisplayControllerLabels())

    @Test fun screenLabelsMatch() =
        assertSameLabels("screen", buildTextDisplayScreenLabels(context), strings.buildTextDisplayScreenLabels())

    @Test fun colourLabelsMatch() =
        assertSameLabels("colour", buildColorSettingsLabels(context), strings.buildColorSettingsLabels())

    @Test fun chooserLabelsMatch() =
        assertSameLabels("chooser", buildBackgroundImageChooserLabels(context), strings.buildBackgroundImageChooserLabels())
}
