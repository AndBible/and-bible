package net.bible.desktop

import java.nio.file.Files
import java.nio.file.Path

/** Finds the directory holding `WebView2Loader.dll` for [net.bible.desktop.webview2.WebView2Engine]. */
object WebView2LoaderLocation {
    private const val DEV_PROPERTY = "webview2.loaderDir"
    private const val RESOURCES_PROPERTY = "compose.application.resources.dir"
    private const val LOADER_DIR_NAME = "webview2"
    const val LOADER_FILE_NAME = "WebView2Loader.dll"

    /**
     * Resolution order: `webview2.loaderDir` (dev `run`), then `<compose.application.resources.dir>/webview2`
     * (the packaged distribution). Fails when neither property is set, or when the resolved dir has no regular
     * file `WebView2Loader.dll`.
     */
    fun resolve(props: (String) -> String?): Result<Path> {
        val dir = props(DEV_PROPERTY)?.let { Path.of(it) }
            ?: props(RESOURCES_PROPERTY)?.let { Path.of(it).resolve(LOADER_DIR_NAME) }
            ?: return Result.failure(
                IllegalStateException(
                    "WebView2 loader location unknown: neither $DEV_PROPERTY (dev run) nor $RESOURCES_PROPERTY " +
                        "(packaged app) is set",
                ),
            )
        if (!Files.isRegularFile(dir.resolve(LOADER_FILE_NAME))) {
            return Result.failure(IllegalStateException("WebView2 loader dir $dir does not contain $LOADER_FILE_NAME"))
        }
        return Result.success(dir)
    }
}
