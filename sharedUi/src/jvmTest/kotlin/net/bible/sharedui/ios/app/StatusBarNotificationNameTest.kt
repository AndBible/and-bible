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
package net.bible.sharedui.ios.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** The status-bar notification name is spelled in Kotlin and in Swift; they must stay equal (nothing else links them). */
class StatusBarNotificationNameTest {
    @Test
    fun swiftAndKotlinUseTheSameNotificationName() {
        val kotlin = File("src/iosMain/kotlin/net/bible/sharedui/ios/app/IosStatusBar.kt").readText()
        val swift = File("../iosApp/iosApp/iOSApp.swift").readText()
        val name = Regex("IOS_STATUS_BAR_HIDDEN_NOTIFICATION: String = \"([^\"]+)\"").find(kotlin)?.groupValues?.get(1)
        assertTrue(name != null, "notification name literal not found in IosStatusBar.kt")
        assertTrue(swift.contains("Notification.Name(\"$name\")"), "iOSApp.swift must observe '$name'")
    }
}
