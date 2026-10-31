package net.bible.sharedui.webview

import okio.FileSystem
import okio.Path
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The `bibleview://app` request logic of the iOS BibleView (#3918): bundle files, page asset routes, 404s. */
class IosBibleSchemeRequestsTest {
    private val fs = FileSystem.SYSTEM
    private val tmp: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "bibleview-scheme-${Random.nextLong().toULong()}"
    private val bundle = (tmp / "bibleview-js").also {
        fs.createDirectories(it / "assets")
        fs.write(it / "index.html") { writeUtf8("<html></html>") }
        fs.write(it / "assets/app.js") { writeUtf8("js") }
    }
    private val book = (tmp / "modules/KJV").also {
        fs.createDirectories(it)
        fs.write(it / "pic.png") { writeUtf8("png") }
    }
    private val image = (tmp / "bg.png").also { fs.write(it) { writeUtf8("bg") } }
    private val secret = (tmp / "secret.txt").also { fs.write(it) { writeUtf8("secret") } }

    private val routes = PageAssetRoutes(
        backgrounds = { mapOf("BGIMG_x" to image) },
        bookLocation = { if (it == "KJV") book else null },
        bookStyleFile = { null },
        verify = null,
    )
    private val requests = IosBibleSchemeRequests(bundle, { routes })

    @AfterTest
    fun cleanUp() { fs.deleteRecursively(tmp) }

    @Test fun indexAndBundleFilesServedWithUtf8Text() {
        val index = requests.answer("app", "/")
        assertEquals(200, index.status)
        assertEquals("text/html; charset=utf-8", index.contentType)
        assertEquals("<html></html>", index.body.decodeToString())
        assertEquals("text/javascript; charset=utf-8", requests.answer("app", "/assets/app.js").contentType)
    }

    @Test fun pageAssetsServed() {
        val bg = requests.answer("app", "/background/BGIMG_x")
        assertEquals(200, bg.status); assertEquals("image/png", bg.contentType); assertEquals("bg", bg.body.decodeToString())
        assertEquals("png", requests.answer("app", "/module/KJV/pic.png").body.decodeToString())
    }

    @Test fun refusedAndMissingAre404() {
        for (path in listOf("/../secret.txt", "/assets/../../secret.txt", "/module/KJV/../../secret.txt", "/background/nope",
            "/features/KJV/pic.png", "/missing.js", "/epub/x.png")) {
            val answer = requests.answer("app", path)
            assertEquals(404, answer.status, path)
            assertEquals(0, answer.body.size, path)
        }
        assertEquals(404, requests.answer("evil", "/index.html").status)
        assertEquals(404, requests.answer("app", null).status)
    }

    @Test fun noBundleAndNoKoinStill404() {
        val bare = IosBibleSchemeRequests(null, { null })
        assertEquals(404, bare.answer("app", "/index.html").status)
        assertEquals(404, bare.answer("app", "/background/BGIMG_x").status)
    }
}
