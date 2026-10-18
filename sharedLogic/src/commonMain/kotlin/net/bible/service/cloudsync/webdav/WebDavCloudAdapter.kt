package net.bible.service.cloudsync.webdav

import okio.use
import kotlinx.coroutines.IO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.database.SyncConfiguration
import net.bible.service.cloudsync.CloudAdapter
import net.bible.sharedcore.platform.cloud.CloudFile
import net.bible.sharedcore.platform.cloud.DownloadProgressListener
import net.bible.service.cloudsync.CloudSyncUserCancelledException
import net.bible.service.cloudsync.CloudSyncUserFacingException
import net.bible.service.cloudsync.GZIP_MIMETYPE
import net.bible.service.cloudsync.SyncableDatabaseAccessor
import net.bible.service.common.asyncMap
import net.bible.service.common.createTempFile
import net.bible.sharedcore.webdav.*
import io.ktor.utils.io.writeFully
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import okio.FileNotFoundException
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Sink
import okio.buffer
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import net.bible.sharedcore.log.Log

const val WEBDAV_SECRET_FILE_NAME_KEY = "webDavSecretFile"

/** Same value as the Nextcloud adapter's folder mime type: the sync layer's generic "folder" marker. */
private const val WEBDAV_FOLDER_MIMETYPE = "DIR"

/**
 * [CloudAdapter] over a generic WebDAV server (spec 2026-10-09-webdav-sync-design).
 *
 * IDs are [DavPath] paths relative to the WebDAV root. Every time handed to the sync layer is in
 * the **device** clock (server time − [WebDavClient.serverClockOffsetMs]), so CloudSync's
 * device-clock `lastSynchronized` and DocumentSync's listing-derived watermark compare
 * consistently against what this adapter returns.
 */
class WebDavCloudAdapter(
    private val config: WebDavConfig,
    private val state: WebDavStateStore,
    private val ui: WebDavSignInUi,
    private val deviceId: String,
    private val clientFactory: (WebDavConfig, WebDavStateStore) -> WebDavClient,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : CloudAdapter {
    private var _client: WebDavClient? = null
    private val client get() = _client ?: throw IllegalStateException("WebDAV adapter not signed in")
    private val base get() = config.baseFolder

    override val signedIn: Boolean get() = _client != null

    override suspend fun signIn(): Boolean = withContext(Dispatchers.IO) { signInLoop() }

    private suspend fun signInLoop(): Boolean {
        var trustedNow = false
        while (true) {
            val c = clientFactory(config, state)
            try {
                val root = c.propfind("", 0).firstOrNull { it.path == "" }
                if (root == null || !root.isCollection) throw Exception(ui.message(WebDavMessage.NOT_A_FOLDER))
                if (base.isNotEmpty()) c.mkcols(base)
                _client = c
                return true
            } catch (e: DavUntrustedCertificateException) {
                if (trustedNow) return false
                // Declining the trust prompt is the user's choice, not a failure to report.
                if (!ui.confirmCertificate(e.certificate)) throw CloudSyncUserCancelledException()
                state.certPin = CertPin(e.certificate.host, e.certificate.sha256)
                trustedNow = true
            } catch (e: DavAuthException) {
                throw Exception(ui.message(WebDavMessage.WRONG_CREDENTIALS), e)
            } catch (e: DavNotFoundException) {
                throw Exception(ui.message(WebDavMessage.NOT_A_FOLDER), e)
            } catch (e: DavProtocolException) {
                // 405, or a 200 that is not a multistatus (an HTML page): not a WebDAV folder.
                throw Exception(ui.message(WebDavMessage.NOT_A_FOLDER), e)
            }
        }
    }

    override suspend fun signOut() {
        _client = null
        state.propagation = Propagation.UNKNOWN
    }

    override suspend fun get(id: String): CloudFile = dav {
        client.propfind(id, 0).firstOrNull { it.path == id }?.toCloudFile() ?: throw DavNotFoundException(id)
    }

    override suspend fun listFiles(parentsIds: List<String>?, name: String?, mimeType: String?, createdTimeAtLeast: Long?): List<CloudFile> = dav {
        val parents = parentsIds ?: listOf(base)
        val sinceServer = createdTimeAtLeast?.let { it + client.serverClockOffsetMs - CLOCK_MARGIN_MS }
        parents.asyncMap { p -> listChildren(p, sinceServer) }.flatten()
            .asSequence()
            .filter { name == null || it.name == name }
            .filter { mimeType == null || (mimeType == WEBDAV_FOLDER_MIMETYPE) == it.isCollection }
            .filter { sinceServer == null || (it.lastModified ?: Long.MAX_VALUE) >= sinceServer }
            .map { it.toCloudFile() }
            .toList()
    }

    private class CachedMtime(val serverMtime: Long?, val fetchedAt: Long)

    /** Collection mtimes (server clock) seen in recent PROPFINDs; trusted for [CACHE_TTL_MS] only. */
    private val folderMtimes = HashMap<String, CachedMtime>()
    private val folderMtimesLock = SynchronizedObject()

    private fun remember(resources: List<DavResource>) {
        val at = nowMs()
        synchronized(folderMtimesLock) { // L1-common-ok: atomicfu lock
            resources.filter { it.isCollection }.forEach { folderMtimes[it.path] = CachedMtime(it.lastModified, at) }
        }
    }

    private suspend fun freshFolderMtime(path: String): Long? {
        val cached = synchronized(folderMtimesLock) { folderMtimes[path] } // L1-common-ok: atomicfu lock
        cached?.takeIf { nowMs() - it.fetchedAt <= CACHE_TTL_MS }?.let { return it.serverMtime }
        return client.propfind(path, 0).also(::remember).firstOrNull { it.path == path }?.lastModified
    }

    /**
     * Lists [parent]'s children. When the server is known to propagate child changes into the
     * collection mtime and that mtime is older than [sinceServer], nothing below can be newer, so the
     * Depth:1 listing is skipped.
     */
    private suspend fun listChildren(parent: String, sinceServer: Long?): List<DavResource> {
        if (sinceServer != null && state.propagation == Propagation.YES) {
            val m = freshFolderMtime(parent)
            if (m != null && m < sinceServer) return emptyList()
        }
        return client.propfind(parent, 1).also(::remember).filter { it.path != parent }
    }

    override suspend fun getFolders(parentId: String): List<CloudFile> =
        listFiles(parentsIds = listOf(parentId), mimeType = WEBDAV_FOLDER_MIMETYPE)

    override suspend fun download(id: String, sink: Sink, onProgress: DownloadProgressListener?) = sink.buffer().use { out ->
        dav {
            var total = 0L
            client.get(id) { buf, n -> out.write(buf, 0, n); total += n; onProgress?.invoke(total) }
        }
    }

    override suspend fun createNewFolder(name: String, parentId: String?): CloudFile = dav {
        val parent = parentId ?: base
        val path = joinDavPath(parent, name)
        try { client.mkcol(path) } catch (e: DavProtocolException) { if (e.status == 409) client.mkcols(path) else throw e }
        CloudFile(id = path, name = name, size = 0, createdTime = nowMs(), parentId = parent)
    }

    /** Uploads, and calibrates from this write whether the server propagates child changes to the folder mtime. */
    override suspend fun upload(name: String, file: Path, parentId: String): CloudFile = upload(name, file, parentId, FileSystem.SYSTEM)

    private suspend fun upload(name: String, file: Path, parentId: String, fs: FileSystem): CloudFile = dav {
        val path = joinDavPath(parentId, name)
        // Always a fresh baseline: a cached value from before an earlier delete would let that delete's
        // mtime bump be credited to this PUT.
        val m0 = client.propfind(parentId, 0).also(::remember).firstOrNull { it.path == parentId }?.lastModified
        putFile(path, file, fs)
        // The PUT succeeded; calibration is best effort and must never fail the upload.
        var uploaded: DavResource? = null
        try {
            val m1 = client.propfind(parentId, 0).also(::remember).firstOrNull { it.path == parentId }?.lastModified
            uploaded = client.propfind(path, 0).firstOrNull { it.path == path }
            decidePropagation(m0, m1, uploaded?.lastModified)?.let {
                if (it != state.propagation) Log.i(TAG, "WebDAV folder-mtime propagation: $it")
                state.propagation = it
            }
        } catch (e: DavException) {
            Log.w(TAG, "WebDAV calibration after upload failed; upload kept", e)
        } catch (e: IOException) {
            Log.w(TAG, "WebDAV calibration after upload failed; upload kept", e)
        }
        uploaded?.toCloudFile() ?: CloudFile(path, name, fs.metadata(file).size ?: 0L, nowMs(), parentId)
    }

    private suspend fun putFile(path: String, file: Path, fs: FileSystem) =
        client.put(path, fs.metadata(file).size ?: 0L, GZIP_MIMETYPE) { ch ->
            fs.source(file).buffer().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) { val n = input.read(buf); if (n < 0) break; ch.writeFully(buf, 0, n) }
            }
        }

    override suspend fun delete(id: String) = dav {
        client.delete(id)
        // The parent's mtime may have just changed; a cached value would be stale.
        synchronized(folderMtimesLock) { folderMtimes.remove(id.substringBeforeLast('/', "")) } // L1-common-ok: atomicfu lock
        Unit
    }

    override suspend fun isSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String): Boolean {
        val secret = dbDef.dao.getString(WEBDAV_SECRET_FILE_NAME_KEY) ?: return false
        return try { get(joinDavPath(id, secret)); true } catch (e: FileNotFoundException) {
            dbDef.dao.removeConfig(WEBDAV_SECRET_FILE_NAME_KEY); false
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun makeSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String) {
        val secret = "device-known-$deviceId-${Uuid.random()}"
        // Same location as the old File.createTempFile default: the JVM temp directory.
        val tmp = createTempFile(FileSystem.SYSTEM_TEMPORARY_DIRECTORY, "webdav-secret")
        try { upload(secret, tmp, id, FileSystem.SYSTEM) } finally { FileSystem.SYSTEM.delete(tmp) }
        dbDef.dao.setConfig(WEBDAV_SECRET_FILE_NAME_KEY, secret)
    }

    override suspend fun getConfigs(dbDef: SyncableDatabaseAccessor<*>): List<SyncConfiguration> =
        listOfNotNull(dbDef.dao.getConfig(WEBDAV_SECRET_FILE_NAME_KEY))

    private fun DavResource.toCloudFile() = CloudFile(
        id = path, name = name, size = contentLength,
        createdTime = ((lastModified ?: creationDate) ?: 0L) - client.serverClockOffsetMs,
        parentId = parent,
    )

    /** Maps a missing resource to FileNotFoundException (the sync layer's "not there" signal) and user-actionable failures to [CloudSyncUserFacingException]. */
    private suspend fun <T> dav(block: suspend () -> T): T = try { block() }
    catch (e: DavNotFoundException) { throw FileNotFoundException("WebDAV: not found ${e.path}") }
    catch (e: DavUntrustedCertificateException) { throw CloudSyncUserFacingException(ui.message(WebDavMessage.CERTIFICATE_CHANGED), true, e) }
    catch (e: DavAuthException) { throw CloudSyncUserFacingException(ui.message(WebDavMessage.WRONG_CREDENTIALS), true, e) }
    catch (e: DavQuotaException) { throw CloudSyncUserFacingException(ui.message(WebDavMessage.STORAGE_FULL), false, e) }

    companion object {
        const val CLOCK_MARGIN_MS = 120_000L
        const val CACHE_TTL_MS = 60_000L
        private const val TAG = "WebDavCloudAdapter"
    }
}
