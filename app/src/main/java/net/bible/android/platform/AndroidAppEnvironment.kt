package net.bible.android.platform

import android.content.Context
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.platform.AppEnvironment

/** Reads live Android facts rather than caching mutable network/storage state. */
class AndroidAppEnvironment(context: Context) : AppEnvironment {
    private val application = context.applicationContext
    override val isInitialized get() = CommonUtils.initialized
    override val deviceIdentifier get() = CommonUtils.deviceIdentifier
    override val isDiscrete get() = CommonUtils.isDiscrete
    override val isBeta get() = CommonUtils.isBeta
    override val isDebugMode get() = CommonUtils.isDebugMode
    override val applicationNameMedium get() = CommonUtils.applicationNameMedium
    override val packageName get() = application.packageName
    override val versionNumber get() = CommonUtils.applicationVersionNumber.toLong()
    override val isMeteredNetwork get() = CommonUtils.isMeteredNetwork
    override val megabytesFree get() = CommonUtils.megabytesFree
    override val tmpDir get() = CommonUtils.tmpDir.path
    override val cacheDir get() = application.cacheDir.path
    override val dbBackupPath get() = CommonUtils.dbBackupPath.path
    override fun databasePath(name: String) = application.getDatabasePath(name).path
    override fun deleteDatabase(name: String) = application.deleteDatabase(name)
    override val lastCrashTime get() = CommonUtils.realSharedPreferences.getLong("app-crashed-time", 0L)
    override val apkPath: String? get() = application.applicationInfo.sourceDir
}
