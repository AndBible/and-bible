package net.bible.sharedui.desktop.update

/**
 * How this desktop build was installed. It decides whether the app runs its own GitHub update check
 * ([checksForUpdates]): only installs that the user updates by hand from a GitHub release do.
 *
 * [SYSTEM_PACKAGE] is a GitHub release tarball repackaged by a distribution package manager (the AUR
 * `andbible-bin` package, `packaging/arch/`). The package build marks it by adding the Java option
 * `-Dandbible.systemPackage=<packager>` to the launcher's `.cfg`; the package manager delivers the updates.
 */
enum class DesktopDistribution { DEV, GITHUB_PACKAGE, SYSTEM_PACKAGE, APPIMAGE, FLATPAK, MSIX }

/**
 * [env] is the process environment, [resourcesDir] the `compose.application.resources.dir` system property
 * (set only in a packaged app), [installDir] the `jpackage.app-path` property and [systemPackage] the
 * `andbible.systemPackage` property a distribution package adds to the launcher configuration.
 */
fun detectDistribution(
    env: Map<String, String>,
    resourcesDir: String?,
    installDir: String?,
    systemPackage: String? = null,
): DesktopDistribution = when {
    "FLATPAK_ID" in env -> DesktopDistribution.FLATPAK
    "APPIMAGE" in env -> DesktopDistribution.APPIMAGE
    installDir?.contains("\\WindowsApps\\", ignoreCase = true) == true -> DesktopDistribution.MSIX
    resourcesDir != null && !systemPackage.isNullOrBlank() -> DesktopDistribution.SYSTEM_PACKAGE
    resourcesDir != null -> DesktopDistribution.GITHUB_PACKAGE
    else -> DesktopDistribution.DEV
}

val DesktopDistribution.checksForUpdates: Boolean
    get() = this == DesktopDistribution.GITHUB_PACKAGE || this == DesktopDistribution.APPIMAGE

fun desktopBuildNumber(version: String): Int? =
    version.substringAfterLast('.', "").toIntOrNull()?.takeIf { it > 0 }
