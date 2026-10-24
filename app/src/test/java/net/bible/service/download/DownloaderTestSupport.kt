package net.bible.service.download

import net.bible.service.common.FileManager
import net.bible.sharedcore.http.DownloadHttp
import org.koin.java.KoinJavaComponent

/** What the app's `GenericFileDownloader` construction passes: the Koin `DownloadHttp` and `FileManager.copyFile`. */
internal fun testDownloadHttp(): DownloadHttp = KoinJavaComponent.get(DownloadHttp::class.java)

internal val testCopyFile: (okio.Path, okio.Path) -> Boolean = { from, to -> FileManager.copyFile(from.toFile(), to.toFile()) }
