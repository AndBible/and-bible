package net.bible.desktop.wintoast

import kotlin.test.Test
import kotlin.test.assertEquals

class AumidRegistrationTest {
    @Test
    fun msixNeedsNoRegistry() {
        assertEquals(emptyList(), aumidRegistryPlan(packaged = true, iconPath = "C:/app/icon.ico"))
    }

    @Test
    fun registryIdentityRoutesShellClicksToTheRegisteredComActivator() {
        val plan = aumidRegistryPlan(false, "icon.ico", "C:/AndBible/AndBible.exe")
        assertEquals(
            RegistryValue("HKCU\\Software\\Classes\\AppUserModelId\\org.andbible.AndBible", "CustomActivator", "{C8B03B6B-3F3A-4F8E-9A57-24A610C03A13}"),
            plan.single { it.name == "CustomActivator" },
        )
        assertEquals(emptyList(), aumidRegistryPlan(true, "icon.ico", "C:/AndBible/AndBible.exe"))
    }

    @Test
    fun installedComServerQuotesExecutableAndUsesStableClassId() {
        val value = aumidRegistryPlan(false, "icon.ico", "C:\\Program Files\\AndBible\\AndBible.exe").last()
        assertEquals("HKCU\\Software\\Classes\\CLSID\\{C8B03B6B-3F3A-4F8E-9A57-24A610C03A13}\\LocalServer32", value.key)
        assertEquals("", value.name)
        assertEquals("\"C:\\Program Files\\AndBible\\AndBible.exe\" -ToastActivated", value.value)
    }

    @Test
    fun unpackagedRegistersDisplayNameAndIcon() {
        val key = "HKCU\\Software\\Classes\\AppUserModelId\\org.andbible.AndBible"

        assertEquals(
            listOf(
                RegistryValue(key, "DisplayName", "AndBible"),
                RegistryValue(key, "IconUri", "C:/app/icon.ico"),
                RegistryValue(key, "CustomActivator", "{C8B03B6B-3F3A-4F8E-9A57-24A610C03A13}"),
            ),
            aumidRegistryPlan(packaged = false, iconPath = "C:/app/icon.ico"),
        )
    }
}
