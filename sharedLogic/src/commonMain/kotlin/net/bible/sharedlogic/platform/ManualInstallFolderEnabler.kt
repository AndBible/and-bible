package net.bible.sharedlogic.platform

/**
 * Port: makes the manual-install folder usable as a Sword module source. Android wires this to
 * `SwordEnvironmentInitialisation.enableDefaultAndManualInstallFolder`; iOS binds a no-op.
 */
fun interface ManualInstallFolderEnabler {
    fun enable()
}
