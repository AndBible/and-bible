package net.bible.sharedui.desktop.update

enum class DesktopDistribution { DEV, GITHUB_PACKAGE, APPIMAGE, FLATPAK, MSIX }

fun detectDistribution(env: Map<String, String>, resourcesDir: String?, installDir: String?): DesktopDistribution = when {
    "FLATPAK_ID" in env -> DesktopDistribution.FLATPAK
    "APPIMAGE" in env -> DesktopDistribution.APPIMAGE
    installDir?.contains("\\WindowsApps\\", ignoreCase = true) == true -> DesktopDistribution.MSIX
    resourcesDir != null -> DesktopDistribution.GITHUB_PACKAGE
    else -> DesktopDistribution.DEV
}

val DesktopDistribution.checksForUpdates: Boolean
    get() = this == DesktopDistribution.GITHUB_PACKAGE || this == DesktopDistribution.APPIMAGE

fun desktopBuildNumber(version: String): Int? =
    version.substringAfterLast('.', "").toIntOrNull()?.takeIf { it > 0 }
