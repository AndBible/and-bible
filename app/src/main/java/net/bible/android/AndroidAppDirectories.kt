package net.bible.android

import net.bible.sharedcore.platform.AppDirectories
import okio.Path
import okio.Path.Companion.toOkioPath

/** Delegates to [SharedConstants], which stays the single source of truth until callers switch. */
class AndroidAppDirectories : AppDirectories {
    override val internalFilesDir: Path get() = SharedConstants.internalFilesDir.toOkioPath()
    override val modulesDir: Path get() = SharedConstants.modulesDir.toOkioPath()
    override val manualInstallDir: Path get() = SharedConstants.manualInstallDir.toOkioPath()
    override val manualInstallDir2: Path get() = SharedConstants.manualInstallDir2.toOkioPath()
    override val manualReadingPlanDir: Path get() = SharedConstants.manualReadingPlanDir.toOkioPath()
}
