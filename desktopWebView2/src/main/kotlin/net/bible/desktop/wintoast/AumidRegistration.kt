package net.bible.desktop.wintoast

data class RegistryValue(val key: String, val name: String, val value: String)

fun aumidRegistryPlan(packaged: Boolean, iconPath: String): List<RegistryValue> {
    if (packaged) return emptyList()
    val key = "HKCU\\Software\\Classes\\AppUserModelId\\$ANDBIBLE_AUMID"
    return listOf(
        RegistryValue(key, "DisplayName", "AndBible"),
        RegistryValue(key, "IconUri", iconPath),
    )
}
