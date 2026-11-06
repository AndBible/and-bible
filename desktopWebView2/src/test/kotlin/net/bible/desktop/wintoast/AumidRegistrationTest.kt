package net.bible.desktop.wintoast

import kotlin.test.Test
import kotlin.test.assertEquals

class AumidRegistrationTest {
    @Test
    fun msixNeedsNoRegistry() {
        assertEquals(emptyList(), aumidRegistryPlan(packaged = true, iconPath = "C:/app/icon.ico"))
    }

    @Test
    fun unpackagedRegistersDisplayNameAndIcon() {
        val key = "HKCU\\Software\\Classes\\AppUserModelId\\org.andbible.AndBible"

        assertEquals(
            listOf(
                RegistryValue(key, "DisplayName", "AndBible"),
                RegistryValue(key, "IconUri", "C:/app/icon.ico"),
            ),
            aumidRegistryPlan(packaged = false, iconPath = "C:/app/icon.ico"),
        )
    }
}
