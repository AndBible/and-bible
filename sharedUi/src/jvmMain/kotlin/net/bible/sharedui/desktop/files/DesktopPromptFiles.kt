package net.bible.sharedui.desktop.files

import java.net.URI
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.service.sword.csvprompt.addCsvPromptBook
import net.bible.sharedcore.platform.AppDirectories
import net.bible.sharedlogic.platform.AddonCatalog
import net.bible.sharedui.app.ai.PromptFiles
import net.bible.sharedui.app.ports.DocumentRef
import okio.Path.Companion.toPath
import org.koin.mp.KoinPlatform

class DesktopPromptFiles(
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
    private val modulesDirectory: () -> Path = { Path.of(KoinPlatform.getKoin().get<AppDirectories>().modulesDir.toString()) },
    private val clearCaches: () -> Unit = { KoinPlatform.getKoin().get<AddonCatalog>().clearCaches() },
) : PromptFiles {
    private fun path(ref: DocumentRef): Path = try {
        val uri = URI(ref.uri)
        require(uri.scheme == "file")
        Path.of(uri).also { require(it.isAbsolute) }
    } catch (e: Exception) { throw IllegalArgumentException(e) }
    override suspend fun read(ref: DocumentRef): String = withContext(Dispatchers.IO) {
        try { Files.readString(path(ref)) } catch (e: java.io.IOException) { throw IllegalArgumentException(e) }
    }
    override suspend fun write(ref: DocumentRef, text: String) = withContext(Dispatchers.IO) {
        try { Files.writeString(path(ref), text); Unit } catch (e: java.io.IOException) { throw IllegalArgumentException(e) }
    }
    override suspend fun installAsAddon(ref: DocumentRef) = withContext(Dispatchers.IO) {
        val source = path(ref)
        val name = source.fileName.toString().replace(Regex("[/\\\\\\x00-\\x1f\\x7f]"), "_")
            .takeUnless { it.isBlank() || it == "." || it == ".." } ?: "prompts.csv"
        val directory = modulesDirectory().resolve("prompts")
        require(!Files.isSymbolicLink(directory))
        Files.createDirectories(directory)
        val destination = directory.resolve(name)
        require(!Files.isSymbolicLink(destination))
        val bytes = Files.readAllBytes(source)
        Files.write(destination, bytes)
        addCsvPromptBook(destination.toString().toPath())
        clearCaches()
    }
    override fun suggestedExportName(): String = "ai_prompts_${now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"))}.csv"
}
