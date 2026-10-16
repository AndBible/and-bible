package net.bible.service.cloudsync.webdav

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.database.SyncConfiguration
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.cloudsync.CloudAdapter
import net.bible.service.cloudsync.CloudFile
import net.bible.service.cloudsync.CloudSyncUserCancelledException
import net.bible.service.cloudsync.CloudSyncUserFacingException
import net.bible.service.cloudsync.DownloadProgressListener
import net.bible.service.cloudsync.GZIP_MIMETYPE
import net.bible.service.cloudsync.SyncableDatabaseAccessor
import net.bible.service.cloudsync.nextcloud.FOLDER_MIMETYPE
import net.bible.service.common.CommonUtils
import net.bible.service.common.asyncMap
import net.bible.sharedcore.webdav.*
import java.io.File
import java.io.FileNotFoundException
import io.ktor.utils.io.writeFully
import java.io.OutputStream
import java.util.UUID
import android.util.Log

const val WEBDAV_SECRET_FILE_NAME_KEY = "webDavSecretFile"

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
    private val clientFactory: (WebDavConfig, WebDavStateStore) -> WebDavClient = { c, s -> createWebDavClient(c, s) },
    private val nowMs: () -> Long = System::currentTimeMillis,
) : CloudAdapter {
    private var _client: WebDavClient? = null
    private val client get() = _client ?: throw IllegalStateException("WebDAV adapter not signed in")
    private val base get() = config.baseFolder

    override val signedIn: Boolean get() = _client != null

    override suspend fun signIn(activity: ActivityBase): Boolean = withContext(Dispatchers.IO) { signInLoop() }

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
            .filter { mimeType == null || (mimeType == FOLDER_MIMETYPE) == it.isCollection }
            .filter { sinceServer == null || (it.lastModified ?: Long.MAX_VALUE) >= sinceServer }
            .map { it.toCloudFile() }
            .toList()
    }

    private class CachedMtime(val serverMtime: Long?, val fetchedAt: Long)

    /** Collection mtimes (server clock) seen in recent PROPFINDs; trusted for [CACHE_TTL_MS] only. */
    private val folderMtimes = java.util.concurrent.ConcurrentHashMap<String, CachedMtime>()

    private fun remember(resources: List<DavResource>) {
        val at = nowMs()
        resources.filter { it.isCollection }.forEach { folderMtimes[it.path] = CachedMtime(it.lastModified, at) }
    }

    private suspend fun freshFolderMtime(path: String): Long? {
        folderMtimes[path]?.takeIf { nowMs() - it.fetchedAt <= CACHE_TTL_MS }?.let { return it.serverMtime }
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
        listFiles(parentsIds = listOf(parentId), mimeType = FOLDER_MIMETYPE)

    override suspend fun download(id: String, outputStream: OutputStream, onProgress: DownloadProgressListener?) = dav {
        var total = 0L
        client.get(id) { buf, n -> outputStream.write(buf, 0, n); total += n; onProgress?.invoke(total) }
    }

    override suspend fun createNewFolder(name: String, parentId: String?): CloudFile = dav {
        val parent = parentId ?: base
        val path = joinDavPath(parent, name)
        try { client.mkcol(path) } catch (e: DavProtocolException) { if (e.status == 409) client.mkcols(path) else throw e }
        CloudFile(id = path, name = name, size = 0, createdTime = nowMs(), parentId = parent)
    }

    /** Uploads, and calibrates from this write whether the server propagates child changes to the folder mtime. */
    override suspend fun upload(name: String, file: File, parentId: String): CloudFile = dav {
        val path = joinDavPath(parentId, name)
        // Always a fresh baseline: a cached value from before an earlier delete would let that delete's
        // mtime bump be credited to this PUT.
        val m0 = client.propfind(parentId, 0).also(::remember).firstOrNull { it.path == parentId }?.lastModified
        putFile(path, file)
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
        } catch (e: java.io.IOException) {
            Log.w(TAG, "WebDAV calibration after upload failed; upload kept", e)
        }
        uploaded?.toCloudFile() ?: CloudFile(path, name, file.length(), nowMs(), parentId)
    }

    private suspend fun putFile(path: String, file: File) =
        client.put(path, file.length(), GZIP_MIMETYPE) { ch ->
            file.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) { val n = input.read(buf); if (n < 0) break; ch.writeFully(buf, 0, n) }
            }
        }

    override suspend fun delete(id: String) = dav {
        client.delete(id)
        // The parent's mtime may have just changed; a cached value would be stale.
        folderMtimes.remove(id.substringBeforeLast('/', ""))
        Unit
    }

    override suspend fun isSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String): Boolean {
        val secret = dbDef.dao.getString(WEBDAV_SECRET_FILE_NAME_KEY) ?: return false
        return try { get(joinDavPath(id, secret)); true } catch (e: FileNotFoundException) {
            dbDef.dao.removeConfig(WEBDAV_SECRET_FILE_NAME_KEY); false
        }
    }

    override suspend fun makeSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String) {
        val secret = "device-known-${CommonUtils.deviceIdentifier}-${UUID.randomUUID()}"
        val tmp = File.createTempFile("webdav-secret", null)
        try { upload(secret, tmp, id) } finally { tmp.delete() }
        dbDef.dao.setConfig(WEBDAV_SECRET_FILE_NAME_KEY, secret)
    }

    override fun getConfigs(dbDef: SyncableDatabaseAccessor<*>): List<SyncConfiguration> =
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
