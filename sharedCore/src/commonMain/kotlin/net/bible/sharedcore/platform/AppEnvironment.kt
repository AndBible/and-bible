package net.bible.sharedcore.platform

/** Live app/process facts for domain code. Paths are strings at the platform boundary. */
interface AppEnvironment {
    val isInitialized: Boolean
    val deviceIdentifier: String
    val isDiscrete: Boolean
    val isBeta: Boolean
    val isDebugMode: Boolean
    val applicationNameMedium: String
    val packageName: String
    val versionNumber: Long
    val isMeteredNetwork: Boolean
    val megabytesFree: Long
    val tmpDir: String
    val cacheDir: String
    val dbBackupPath: String
    fun databasePath(name: String): String
    fun deleteDatabase(name: String): Boolean
    val apkPath: String?
}
