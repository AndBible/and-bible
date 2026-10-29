package net.bible.desktop

import java.nio.file.Files
import java.nio.file.Path

/** Finds the CEF natives directory (the one holding `libcef.so`) for [net.bible.sharedui.desktop.webengine.kcef.KcefWebEngine]. */
object CefNativesLocation {
    private const val DEV_PROPERTY = "kcef.installDir"
    private const val RESOURCES_PROPERTY = "compose.application.resources.dir"
    private const val NATIVES_DIR_NAME = "kcef-natives"

    /**
     * Resolution order: `kcef.installDir` (dev `run`), then `<compose.application.resources.dir>/kcef-natives`
     * (the packaged distribution). Fails when neither property is set, or when the resolved dir has no `libcef.so`.
     */
    fun resolve(props: (String) -> String?): Result<Path> {
        val dir = props(DEV_PROPERTY)?.let { Path.of(it) }
            ?: props(RESOURCES_PROPERTY)?.let { Path.of(it).resolve(NATIVES_DIR_NAME) }
            ?: return Result.failure(
                IllegalStateException(
                    "CEF natives location unknown: neither $DEV_PROPERTY (dev run) nor $RESOURCES_PROPERTY " +
                        "(packaged app) is set",
                ),
            )
        if (!Files.isRegularFile(dir.resolve("libcef.so"))) {
            return Result.failure(IllegalStateException("CEF natives dir $dir does not contain libcef.so"))
        }
        return Result.success(dir)
    }
}
