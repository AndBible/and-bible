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

package net.bible.android.view.activity.page

import net.bible.android.TEST_SDK
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the Task-3 extract: every drawer id must map to a real `R.id.*` that the id-based
 * `handleMenuRequest` overload accepts. This is a signature/mapping guard, not a behaviour test —
 * exercising the real command bodies would need a live `MainBibleActivity`, which the on-device A/B
 * covers instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class MenuCommandHandlerIdDispatchTest {

    @Test
    fun idBasedOverloadExists_andEveryDrawerIdResolvesToAResId() {
        val method = MenuCommandHandler::class.java.getDeclaredMethod(
            "handleMenuRequest", Int::class.javaPrimitiveType)
        assertTrue("handleMenuRequest(Int) must be public", java.lang.reflect.Modifier.isPublic(method.modifiers))

        for (idName in DrawerMenuStateBuilder.entryIdNames) {
            assertTrue("drawer id $idName has no resId", DrawerMenuStateBuilder.resIdFor(idName) != 0)
        }
    }

    @Test
    fun menuItemOverloadStillExists() {
        MenuCommandHandler::class.java.getDeclaredMethod("handleMenuRequest", android.view.MenuItem::class.java)
    }
}
