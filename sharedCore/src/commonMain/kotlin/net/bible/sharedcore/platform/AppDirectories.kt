package net.bible.sharedcore.platform

import okio.Path

/** App storage locations that differ per platform. Plain name constants live next to their users. */
interface AppDirectories {
    /** App-private files directory. */
    val internalFilesDir: Path

    /** Where Sword modules live: internal "modules" when discrete or when no external files dir exists. */
    val modulesDir: Path

    /** External storage "jsword" directory for manually installed modules. */
    val manualInstallDir: Path

    /** External storage "sword" directory for manually installed modules. */
    val manualInstallDir2: Path

    /** Directory for manually installed reading plans. */
    val manualReadingPlanDir: Path
}
