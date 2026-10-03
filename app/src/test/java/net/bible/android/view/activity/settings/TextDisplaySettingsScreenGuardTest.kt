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

import net.bible.sharedcore.settings.KEY_OPEN_GLOBAL_SETTINGS
import net.bible.sharedcore.settings.KEY_OPEN_WORKSPACE_SETTINGS
import net.bible.sharedcore.settings.TextSettingType
import net.bible.sharedui.settings.isRevertableSettingsKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain JVM unit test (no Robolectric/Compose needed — [isRevertableSettingsKey] is pure Kotlin)
 * for the review Finding 1 fix: long-pressing a drill-up parent-link row
 * ([KEY_OPEN_WORKSPACE_SETTINGS]/[KEY_OPEN_GLOBAL_SETTINGS]) in `TextDisplaySettingsScreen` must
 * NOT open the revert-confirm dialog, since routing either key into `onRevert` ->
 * `TextDisplaySettingsController.onRevert` -> `TextSettingType.valueOf(key)` throws
 * `IllegalArgumentException` (a real crash, reproduced independently below).
 */
class TextDisplaySettingsScreenGuardTest {

    @Test fun parentLinkKeysAreNotRevertable() {
        assertFalse(isRevertableSettingsKey(KEY_OPEN_WORKSPACE_SETTINGS))
        assertFalse(isRevertableSettingsKey(KEY_OPEN_GLOBAL_SETTINGS))
    }

    @Test fun ordinaryTypeKeysAreRevertable() {
        // A representative sample of TextSettingType names -- the keys every other row uses.
        assertTrue(isRevertableSettingsKey(TextSettingType.FONTSIZE.name))
        assertTrue(isRevertableSettingsKey(TextSettingType.MARGINSIZE.name))
        assertTrue(isRevertableSettingsKey(TextSettingType.REDLETTERS.name))
        assertTrue(isRevertableSettingsKey(TextSettingType.COLORS.name))
    }

    /** Documents WHY the guard is needed: `TextSettingType.valueOf` -- what the real controller's
     *  `onRevert` calls -- throws for the two parent-link keys, since they aren't enum names. */
    @Test fun parentLinkKeysAreNotValidTextSettingTypeNames() {
        assertThrowsIllegalArgument { TextSettingType.valueOf(KEY_OPEN_WORKSPACE_SETTINGS) }
        assertThrowsIllegalArgument { TextSettingType.valueOf(KEY_OPEN_GLOBAL_SETTINGS) }
    }

    private fun assertThrowsIllegalArgument(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
