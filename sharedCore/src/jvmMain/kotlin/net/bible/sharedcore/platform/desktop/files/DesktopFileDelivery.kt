package net.bible.sharedcore.platform.desktop.files

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.bible.sharedcore.platform.files.DeliveryResult
import net.bible.sharedcore.platform.files.FileDelivery
import net.bible.sharedcore.platform.files.SaveOrShare
import kotlin.coroutines.coroutineContext

class DesktopFileDelivery(
    private val chooser: FileChooserBackend,
    private val openInput: (Path) -> java.io.InputStream = { Files.newInputStream(it) },
    private val isPortalPath: (Path) -> Boolean = { Regex("^/run/user/[^/]+/doc/").containsMatchIn(it.toString()) },
) : FileDelivery {
    override suspend fun deliver(file: String, fileName: String, mode: SaveOrShare, shareMimeType: String,
        saveMimeType: String, subject: String?, message: String?, chooserTitle: String): DeliveryResult {
        try {
            val source = Path.of(file)
            if (!Files.isRegularFile(source)) return DeliveryResult.FAILED
            val target = chooser.save(fileName, fileFiltersFor(listOf(if (mode == SaveOrShare.SAVE) saveMimeType else shareMimeType)))
                ?: return DeliveryResult.CANCELLED
            withContext(Dispatchers.IO) { write(source, target) }
            return DeliveryResult.DELIVERED
        } catch (e: CancellationException) { throw e } catch (_: Exception) { return DeliveryResult.FAILED }
    }

    private suspend fun write(source: Path, target: Path) {
        val portal = isPortalPath(target)
        val part = if (portal) Files.createTempFile("andbible-export-", ".part") else target.resolveSibling(target.fileName.toString() + ".part")
        var owned = portal
        try {
            openInput(source).use { input ->
                val output = if (portal) Files.newOutputStream(part, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
                    else Files.newOutputStream(part, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).also { owned = true }
                output.use {
                    val bytes = ByteArray(65536)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(bytes)
                        if (count < 0) break
                        output.write(bytes, 0, count)
                    }
                }
            }
            coroutineContext.ensureActive()
            if (portal) {
                var targetOpened = false
                try {
                    openInput(part).use { input ->
                        Files.newOutputStream(target, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { output ->
                            targetOpened = true
                            val bytes = ByteArray(65536)
                            while (true) {
                                coroutineContext.ensureActive()
                                val count = input.read(bytes)
                                if (count < 0) break
                                output.write(bytes, 0, count)
                            }
                        }
                    }
                } catch (error: Throwable) {
                    if (targetOpened) {
                        try { Files.newOutputStream(target, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).close() } catch (_: Exception) { }
                    }
                    throw error
                }
            } else Files.move(part, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { if (owned) Files.deleteIfExists(part) }
    }
}
