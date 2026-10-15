package net.bible.sharedcore.platform

import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.secrets.SecretStore
import platform.Foundation.*
import platform.Network.*
import platform.darwin.dispatch_queue_create

/** Sandbox and process facts. Owners of short-lived environments must call [close]. */
@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
class IosAppEnvironment(
    private val secrets: SecretStore,
    isDiscrete: () -> Boolean,
    override val isBeta: Boolean = false,
    override val isDebugMode: Boolean = false,
) : AppEnvironment {
    private val discreteProvider = isDiscrete
    private val files = NSFileManager.defaultManager
    private val metered = atomic(false)
    private val closed = atomic(false)
    private val monitor = nw_path_monitor_create()
    private val queue = dispatch_queue_create("org.andbible.environment.network", null)

    init {
        // Capture only the atomic value: the monitor's block must not retain this owner.
        val state = metered
        nw_path_monitor_set_update_handler(monitor) { path ->
            if (path != null) state.value = nw_path_is_expensive(path) || nw_path_is_constrained(path)
        }
        nw_path_monitor_set_queue(monitor, queue)
        nw_path_monitor_start(monitor)
    }

    /** Cancels the private monitor and releases its callback; safe to call repeatedly. */
    fun close() {
        if (closed.compareAndSet(expect = false, update = true)) {
            nw_path_monitor_cancel(monitor)
            nw_path_monitor_set_update_handler(monitor, null)
        }
    }

    override val isInitialized: Boolean get() = true
    override val isDiscrete: Boolean get() = discreteProvider()
    override val deviceIdentifier: String
        get() = secrets.get("device_identifier")
            ?: NSUUID().UUIDString.also { secrets.put("device_identifier", it) }
    override val applicationNameMedium: String
        get() = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleDisplayName") as? String ?: ""
    override val packageName: String get() = NSBundle.mainBundle.bundleIdentifier ?: ""
    override val versionNumber: Long
        get() = (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)?.toLongOrNull() ?: 0L
    override val isMeteredNetwork: Boolean get() = metered.value
    override val lastCrashTime: Long get() = 0L
    override val apkPath: String? get() = null

    private val documents: String get() = directory(NSDocumentDirectory)
    private val applicationSupport: String get() = directory(NSApplicationSupportDirectory)
    val modulesDir: String get() = ensureDirectory("$documents/sword")
    override val tmpDir: String get() = ensureDirectory(NSTemporaryDirectory())
    override val cacheDir: String get() = ensureDirectory(directory(NSCachesDirectory))
    override val dbBackupPath: String get() = ensureDirectory("$applicationSupport/backup")

    override fun databasePath(name: String): String = "${ensureDirectory("$applicationSupport/databases")}/$name"

    override fun deleteDatabase(name: String): Boolean {
        val path = databasePath(name)
        var removedDatabase = false
        var succeeded = true
        listOf(path, "$path-wal", "$path-shm").forEach { file ->
            if (files.fileExistsAtPath(file)) {
                memScoped {
                    val error = alloc<ObjCObjectVar<NSError?>>().apply { value = null }
                    if (files.removeItemAtPath(file, error.ptr)) {
                        if (file == path) removedDatabase = true
                    } else {
                        succeeded = false
                        Log.w(TAG, "Database removal failed: ${error.value?.localizedDescription}")
                    }
                }
            }
        }
        return removedDatabase && succeeded
    }

    override val megabytesFree: Long
        get() = memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>().apply { value = null }
            val values = NSURL.fileURLWithPath(documents).resourceValuesForKeys(
                listOf(NSURLVolumeAvailableCapacityForImportantUsageKey), error.ptr,
            )
            val bytes = values?.get(NSURLVolumeAvailableCapacityForImportantUsageKey) as? NSNumber
            if (error.value != null || bytes == null) {
                Log.w(TAG, "Free capacity query failed: ${error.value?.localizedDescription ?: "capacity unavailable"}")
                0L
            } else bytes.longLongValue / (1024L * 1024L)
        }

    private fun directory(kind: NSSearchPathDirectory): String =
        NSSearchPathForDirectoriesInDomains(kind, NSUserDomainMask, true).first() as String

    private fun ensureDirectory(path: String): String = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>().apply { value = null }
        if (!files.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = error.ptr)) {
            Log.w(TAG, "Directory creation failed: ${error.value?.localizedDescription}")
        }
        path
    }

    private companion object { const val TAG = "IosAppEnvironment" }
}
