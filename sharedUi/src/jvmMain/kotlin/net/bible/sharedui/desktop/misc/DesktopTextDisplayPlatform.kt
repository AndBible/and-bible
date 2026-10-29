package net.bible.sharedui.desktop.misc

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CancellationException
import net.bible.android.view.activity.bookmark.ManageLabelsContract
import net.bible.android.view.activity.bookmark.ManageLabelsMapper
import net.bible.android.view.activity.settings.TextDisplaySettingsRouteEntry
import net.bible.android.view.activity.settings.TextDisplaySettingsServiceImpl
import net.bible.sharedcore.nav.TextDisplaySettingsArgs
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.TextDisplaySettingsController
import net.bible.sharedcore.settings.TextSettingType
import net.bible.sharedlogic.addons.SharedAddonCatalog
import net.bible.sharedlogic.platform.BackgroundImageCatalog
import net.bible.sharedui.app.ports.TextDisplayEntry
import net.bible.sharedui.app.ports.TextDisplayPlatform
import okio.Path
import org.jetbrains.skia.Image
import org.koin.mp.KoinPlatform
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.max

class DesktopTextDisplayPlatform(
    private val sharedService: () -> TextDisplaySettingsServiceImpl = { KoinPlatform.getKoin().get() },
    private val inventory: () -> Map<String, Path> = { KoinPlatform.getKoin().get<BackgroundImageCatalog>().backgroundImages.mapValues { it.value.path } },
    private val windowPixels: () -> Pair<Int, Int> = { 1200 to 800 },
    private val onModulesChanged: (() -> Unit) -> Unit = { callback ->
        (KoinPlatform.getKoin().get<BackgroundImageCatalog>() as? SharedAddonCatalog)?.addClearListener(callback)
    },
) : TextDisplayPlatform {
    private val thumbnails = LinkedHashMap<String, ImageBitmap?>(16, .75f, true)
    private var generation = 0L
    private var hooked = false

    override fun entryFor(args: TextDisplaySettingsArgs): TextDisplayEntry {
        val entry = TextDisplaySettingsRouteEntry(args, sharedService())
        return TextDisplayEntry(entry.service, entry.scope, { scope: SettingsScope ->
            ManageLabelsMapper.hideLabelsData(entry.service.currentHideLabelsIds(scope), scope is SettingsScope.Window).toJSON()
        }, entry::resultOnLeave)
    }

    override fun applyHideLabelsResult(resultJson: String, controller: TextDisplaySettingsController) {
        val data = ManageLabelsContract.ManageLabelsData.fromJSON(resultJson)
        if (data.reset) controller.onRevert(TextSettingType.BOOKMARKS_HIDELABELS.name)
        else controller.onHideLabelsChange(data.selectedLabels.map { it.toString() })
    }

    override fun thumbnail(token: String): ImageBitmap? {
        synchronized(thumbnails) {
            if (!hooked) {
                onModulesChanged { synchronized(thumbnails) { generation++; thumbnails.clear() } }
                hooked = true
            }
        }
        val bounds = windowPixels()
        val key = "$token:${bounds.first}:${bounds.second}"
        val started = synchronized(thumbnails) {
            if (thumbnails.containsKey(key)) return thumbnails[key]
            generation
        }
        val image = try { inventory()[token]?.let { decode(it, bounds) } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
        synchronized(thumbnails) {
            if (generation != started) return null
            thumbnails[key] = image
            while (thumbnails.size > 16 || thumbnails.values.sumOf { it?.let { bitmap -> bitmap.width.toLong() * bitmap.height * 4 } ?: 0L } > 32L * 1024 * 1024) {
                thumbnails.entries.iterator().apply { next(); remove() }
            }
        }
        return image
    }

    private fun decode(path: Path, bounds: Pair<Int, Int>): ImageBitmap? {
        val file = Paths.get(path.toString())
        if (Files.size(file) > 32L * 1024 * 1024) return null
        val maxWidth = (bounds.first.toLong() * 2).coerceIn(1, 4096).toInt()
        val maxHeight = (bounds.second.toLong() * 2).coerceIn(1, 4096).toInt()
        ImageIO.createImageInputStream(file.toFile()).use { input ->
            val readers = ImageIO.getImageReaders(input)
            if (!readers.hasNext()) return null
            val reader = readers.next()
            try {
                reader.setInput(input, false, true)
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                // ImageIO decoders may allocate a full source scanline even with subsampling.
                if (width <= 0 || height <= 0 || width > 16384 || height > 16384) return null
                val pixelBudget = 4_000_000.0
                val sample = ceil(max(max(width.toDouble() / maxWidth, height.toDouble() / maxHeight),
                    kotlin.math.sqrt(width.toDouble() * height / pixelBudget))).toInt().coerceAtLeast(1)
                val params = reader.defaultReadParam.apply { setSourceSubsampling(sample, sample, 0, 0) }
                val sampled = reader.read(0, params) ?: return null
                try {
                    val bytes = ByteArrayOutputStream().use { output ->
                        if (!ImageIO.write(sampled, "png", output)) return null
                        output.toByteArray()
                    }
                    return Image.makeFromEncoded(bytes).toComposeImageBitmap()
                } finally { sampled.flush() }
            } finally { reader.dispose() }
        }
    }
}
