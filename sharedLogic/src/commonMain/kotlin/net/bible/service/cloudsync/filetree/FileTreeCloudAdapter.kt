package net.bible.service.cloudsync.filetree

import net.bible.android.database.SyncConfiguration
import net.bible.service.cloudsync.CloudAdapter
import net.bible.service.cloudsync.SyncableDatabaseAccessor
import net.bible.service.common.createTempFile
import net.bible.sharedcore.platform.cloud.CloudFileStore
import okio.FileNotFoundException
import okio.FileSystem
import okio.Path
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

const val FILETREE_SECRET_FILE_NAME_KEY = "fileTreeSecretFile"

/**
 * [CloudAdapter] over a file-tree [CloudFileStore] (iCloud Drive). All file operations delegate to [store].
 *
 * A sync folder is "known" to this device when the marker file it wrote there
 * (`device-known-<deviceId>-<uuid>`, name kept in the local SyncConfiguration) still exists. If another
 * device reset or recreated the folder the marker is gone, so CloudSync can detect that and start over.
 * Same contract as `WebDavCloudAdapter`. The marker is created from a temp file in [tempDir] on [fs].
 */
class FileTreeCloudAdapter(
    private val store: CloudFileStore,
    private val deviceId: String,
    private val tempDir: Path,
    private val fs: FileSystem = FileSystem.SYSTEM,
    private val awaitReady: suspend () -> Unit = {},
) : CloudAdapter, CloudFileStore by store {

    /**
     * Waits for [awaitReady] first: a store whose root resolves asynchronously (the iCloud container)
     * reports null until then, which would make every sign-in fail.
     */
    override suspend fun signIn(): Boolean {
        awaitReady()
        return store.signIn()
    }

    override suspend fun isSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String): Boolean {
        val secret = dbDef.dao.getString(FILETREE_SECRET_FILE_NAME_KEY) ?: return false
        return try {
            store.get(if (id.isEmpty()) secret else "$id/$secret")
            true
        } catch (e: FileNotFoundException) {
            dbDef.dao.removeConfig(FILETREE_SECRET_FILE_NAME_KEY)
            false
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun makeSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String) {
        val secret = "device-known-$deviceId-${Uuid.random()}"
        val tmp = createTempFile(tempDir, "filetree-secret", fs = fs)
        try { store.upload(secret, tmp, id) } finally { fs.delete(tmp) }
        dbDef.dao.setConfig(FILETREE_SECRET_FILE_NAME_KEY, secret)
    }

    override suspend fun getConfigs(dbDef: SyncableDatabaseAccessor<*>): List<SyncConfiguration> =
        listOfNotNull(dbDef.dao.getConfig(FILETREE_SECRET_FILE_NAME_KEY))
}
