@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package net.bible.sharedui.ios.ports

import kotlinx.coroutines.*
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.*
import platform.PhotosUI.*
import platform.UIKit.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Real picker delegates with a recording host; these do not claim app-hosted presentation coverage. */
class IosSmallPortsTest {
    private class Host : UIViewController(null, null) {
        var attached: UIViewController? = null
        val presentations = mutableListOf<UIViewController>()
        override fun presentedViewController(): UIViewController? = attached
        override fun presentViewController(viewControllerToPresent: UIViewController, animated: Boolean, completion: (() -> Unit)?) {
            check(attached == null)
            attached = viewControllerToPresent
            presentations.add(viewControllerToPresent)
            completion?.invoke()
        }
    }

    // General UIPasteboard requires an application bundle. Its real roundtrip belongs to the
    // app-hosted runtime gate, not this unbundled native executable (which hangs at native access).

    @Test fun externalLinksAsksFirstInDiscrete() {
        var discrete = false
        val links = IosExternalLinks(isDiscrete = { discrete })
        assertFalse(links.askBeforeOpening)
        discrete = true
        assertTrue(links.askBeforeOpening)
        discrete = false
        assertFalse(links.askBeforeOpening)
    }

    @Test fun externalLinkFailureNotifiesOnlyOnFailure() = onMain {
        val urls = mutableListOf<String>()
        var completion: ((Boolean) -> Unit)? = null
        var errors = 0
        val links = IosExternalLinks(openUrl = { url, done ->
            urls.add(assertNotNull(url.absoluteString)); completion = done
        }, openingFailed = { errors++ })
        links.open(net.bible.sharedcore.docs.DocsLinks.BASE_URL)
        yield()
        assertEquals(listOf(net.bible.sharedcore.docs.DocsLinks.BASE_URL), urls)
        assertNotNull(completion).invoke(true)
        assertEquals(0, errors)
        assertNotNull(completion).invoke(false)
        yield()
        assertEquals(1, errors)
    }

    @Test fun systemScreensOpensApplicationSettings() = onMain {
        var url: String? = null
        IosSystemScreens(openUrl = { url = it.absoluteString }).openAppLinkSettings()
        yield()
        assertEquals(UIApplicationOpenSettingsURLString, url)
    }

    @Test fun orientationLockReturnsANoOpRestore() {
        val restore = IosOrientationLock().lockCurrentOrientation()
        restore()
        restore()
    }

    private class Center : IosNotificationCenter {
        var queries = 0
        var requests = 0
        var status: ((Boolean) -> Unit)? = null
        var completion: (() -> Unit)? = null
        override fun authorizationDecided(completion: (Boolean) -> Unit) {
            queries++
            status = { decided -> status = null; completion(decided) }
        }
        override fun requestAlertAndSound(completion: () -> Unit) {
            requests++
            this.completion = { this.completion = null; completion() }
        }
    }

    @Test fun notificationRequestIsIdempotent() = onMain {
        val center = Center()
        val permission = IosNotificationPermission(center)
        val first = async(start = CoroutineStart.UNDISPATCHED) { permission.request() }
        val second = async(start = CoroutineStart.UNDISPATCHED) { permission.request() }
        try {
            awaitCondition { center.status != null }
            assertEquals(1, center.queries)
            assertNotNull(center.status).invoke(false)
            awaitCondition { center.completion != null }
            assertEquals(1, center.requests)
            assertFalse(first.isCompleted)
            assertFalse(second.isCompleted)
            assertNotNull(center.completion).invoke()
            first.await(); second.await()
            permission.request()
            assertEquals(1, center.requests)
            assertEquals(1, center.queries)
        } finally {
            // suspendCoroutine represents an OS request that cannot be cancelled. Deliver any
            // pending fake callbacks before joining children, even when an assertion fails.
            first.cancel(); second.cancel()
            center.status?.invoke(true)
            center.completion?.invoke()
            first.join(); second.join()
        }
    }

    @Test fun notificationAlreadyDecidedDoesNotPrompt() = onMain {
        val center = Center()
        val request = async(start = CoroutineStart.UNDISPATCHED) { IosNotificationPermission(center).request() }
        try {
            awaitCondition { center.status != null }
            assertNotNull(center.status).invoke(true)
            request.await()
            assertEquals(0, center.requests)
        } finally {
            request.cancel()
            center.status?.invoke(true)
            center.completion?.invoke()
            request.join()
        }
    }

    @Test fun imagePickerCancelReturnsNull() = onMain {
        withHost { host ->
            val picker = IosImagePicker(presenter = { host })
            val request = async(start = CoroutineStart.UNDISPATCHED) { picker.pickImage() }
            val sheet = assertIs<PHPickerViewController>(host.presentations.single())
            assertEquals(1L, sheet.configuration.selectionLimit)
            assertNotNull(sheet.configuration.filter)
            val delegate = assertNotNull(sheet.delegate)
            delegate.picker(sheet, didFinishPicking = emptyList<Any>())
            assertNull(request.await())
            delegate.picker(sheet, didFinishPicking = emptyList<Any>())
            assertNull(sheet.delegate)
        }
    }

    @Test fun imageCoroutineCancellationReleasesDelegateAfterDismissal() = onMain {
        withHost { host ->
            val request = async(start = CoroutineStart.UNDISPATCHED) {
                IosImagePicker(presenter = { host }).pickImage()
            }
            val sheet = assertIs<PHPickerViewController>(host.presentations.single())
            request.cancel()
            repeat(3) { yield() }
            request.join()
            assertNull(sheet.delegate)
        }
    }

    private class Provider : NSItemProvider() {
        var callback: ((NSURL?, NSError?) -> Unit)? = null
        var requestedType: String? = null
        override fun loadFileRepresentationForTypeIdentifier(
            typeIdentifier: String, completionHandler: (NSURL?, NSError?) -> Unit,
        ): NSProgress {
            requestedType = typeIdentifier
            callback = completionHandler
            return NSProgress.progressWithTotalUnitCount(1)
        }
    }

    @Test fun imageCopiesProviderFileBeforeCallbackReturns() = onMain {
        withImageFiles { root, source ->
            withHost { host ->
                val provider = Provider()
                val request = async(start = CoroutineStart.UNDISPATCHED) {
                    IosImagePicker(presenter = { host },
                        tempDirectory = { root.toString() }, imageProvider = { provider }).pickImage()
                }
                val sheet = assertIs<PHPickerViewController>(host.presentations.single())
                sheet.delegate!!.picker(sheet, didFinishPicking = listOf(Unit))
                assertEquals("public.image", provider.requestedType)
                assertNotNull(provider.callback).invoke(NSURL.fileURLWithPath(source.toString()), null)
                // NSItemProvider may remove the original as soon as its completion returns.
                FileSystem.SYSTEM.delete(source)
                val staging = FileSystem.SYSTEM.list(root).single { it.name.startsWith("andbible-image-") }
                assertEquals("image bytes", FileSystem.SYSTEM.read(staging / source.name) { readUtf8() })
                repeat(3) { yield() }
                val url = assertNotNull(NSURL.URLWithString(assertNotNull(request.await())))
                assertTrue(url.isFileURL())
                assertEquals("image bytes", FileSystem.SYSTEM.read(assertNotNull(url.path).toPath()) { readUtf8() })
                assertNull(sheet.delegate)
            }
        }
    }

    @Test fun imageCopiesExactProviderBackslashFileNotNormalizedDecoy() = onMain {
        val root = NSTemporaryDirectory().toPath() / "image-exact-url-${NSUUID().UUIDString}"
        val rootUrl = NSURL.fileURLWithPath(root.toString(), isDirectory = true)
        assertTrue(NSFileManager.defaultManager.createDirectoryAtURL(rootUrl, true, null, null))
        var primary: Throwable? = null
        try {
            val selected = NSURL.fileURLWithPath("$root/provider\\image.jpg")
            assertTrue(NSString.create(string = "picked image bytes").writeToURL(selected, true, NSUTF8StringEncoding, null))
            FileSystem.SYSTEM.createDirectory(root / "provider")
            FileSystem.SYSTEM.write(root / "provider" / "image.jpg") { writeUtf8("wrong normalized image bytes") }
            withHost { host ->
                val provider = Provider()
                val request = async(start = CoroutineStart.UNDISPATCHED) {
                    IosImagePicker(presenter = { host }, tempDirectory = { root.toString() },
                        imageProvider = { provider }).pickImage()
                }
                try {
                    val sheet = assertIs<PHPickerViewController>(host.presentations.single())
                    assertNotNull(sheet.delegate).picker(sheet, didFinishPicking = listOf(Unit))
                    assertEquals("public.image", provider.requestedType)
                    assertNotNull(provider.callback).invoke(selected, null)
                    // The provider's exact URL expires as soon as this callback returns.
                    assertTrue(NSFileManager.defaultManager.removeItemAtURL(selected, null))
                    val copied = assertNotNull(NSURL.URLWithString(assertNotNull(request.await())))
                    assertTrue(copied.isFileURL())
                    assertNull(sheet.delegate)
                    assertEquals("picked image bytes", NSString.stringWithContentsOfURL(copied, NSUTF8StringEncoding, null))
                    assertEquals("wrong normalized image bytes", FileSystem.SYSTEM.read(root / "provider" / "image.jpg") { readUtf8() })
                } finally { request.cancelAndJoin() }
            }
        } catch (failure: Throwable) {
            primary = failure
            throw failure
        } finally {
            // Recursively remove by Foundation URL, including any literal backslash children.
            if (!NSFileManager.defaultManager.removeItemAtURL(rootUrl, null)) {
                val cleanup = AssertionError("Failed to remove exact image URL fixture")
                primary?.addSuppressed(cleanup) ?: throw cleanup
            }
        }
    }

    @Test fun imageProviderErrorReturnsNullWithoutStaging() = onMain {
        withImageFiles { root, _ ->
            withHost { host ->
                val provider = Provider()
                val request = async(start = CoroutineStart.UNDISPATCHED) {
                    IosImagePicker(presenter = { host },
                        tempDirectory = { root.toString() }, imageProvider = { provider }).pickImage()
                }
                val sheet = assertIs<PHPickerViewController>(host.presentations.single())
                sheet.delegate!!.picker(sheet, didFinishPicking = listOf(Unit))
                assertNotNull(provider.callback).invoke(null, NSError.errorWithDomain("test", 1, null))
                repeat(3) { yield() }
                assertNull(request.await())
                assertFalse(FileSystem.SYSTEM.list(root).any { it.name.startsWith("andbible-image-") })
            }
        }
    }

    @Test fun imageCopyFailureRemovesPartialStaging() = onMain {
        withImageFiles { root, source ->
            withHost { host ->
                val provider = Provider()
                val request = async(start = CoroutineStart.UNDISPATCHED) {
                    IosImagePicker(presenter = { host },
                        tempDirectory = { root.toString() }, imageProvider = { provider }).pickImage()
                }
                val sheet = assertIs<PHPickerViewController>(host.presentations.single())
                sheet.delegate!!.picker(sheet, didFinishPicking = listOf(Unit))
                FileSystem.SYSTEM.delete(source)
                assertNotNull(provider.callback).invoke(NSURL.fileURLWithPath(source.toString()), null)
                repeat(3) { yield() }
                assertNull(request.await())
                assertEquals(emptyList(), FileSystem.SYSTEM.list(root))
            }
        }
    }

    @Test fun imageLateProviderCallbackAfterCancellationDeletesItsCopy() = onMain {
        withImageFiles { root, source ->
            withHost { host ->
                val provider = Provider()
                val request = async(start = CoroutineStart.UNDISPATCHED) {
                    IosImagePicker(presenter = { host },
                        tempDirectory = { root.toString() }, imageProvider = { provider }).pickImage()
                }
                val sheet = assertIs<PHPickerViewController>(host.presentations.single())
                sheet.delegate!!.picker(sheet, didFinishPicking = listOf(Unit))
                request.cancel()
                repeat(3) { yield() }
                request.join()
                assertNotNull(provider.callback).invoke(NSURL.fileURLWithPath(source.toString()), null)
                repeat(3) { yield() }
                assertEquals(listOf(source), FileSystem.SYSTEM.list(root))
                assertNull(sheet.delegate)
            }
        }
    }

    private suspend fun CoroutineScope.withImageFiles(
        block: suspend CoroutineScope.(okio.Path, okio.Path) -> Unit,
    ) {
        val root = NSTemporaryDirectory().toPath() / "small-ports-test-${NSUUID().UUIDString}"
        FileSystem.SYSTEM.createDirectory(root)
        val source = root / "provider-image.jpg"
        FileSystem.SYSTEM.write(source) { writeUtf8("image bytes") }
        try { block(root, source) }
        finally { FileSystem.SYSTEM.deleteRecursively(root, mustExist = false) }
    }

    // UIActivityViewController initializes scene-hosted UIKit views asynchronously. Its actual
    // controller type, items and presentation need the app-hosted runtime gate (a nil scene aborts
    // this unbundled executable); only the no-presenter path is exercised here.

    @Test fun missingPresenterCancelsImageAndShare() = onMain {
        assertNull(IosImagePicker(presenter = { null }).pickImage())
        IosTextShare(presenter = { null }).shareText("text", "title")
        yield()
    }

    private suspend fun CoroutineScope.withHost(block: suspend CoroutineScope.(Host) -> Unit) {
        // Only attach a view; never ask UIKit to make the test executable an app host.
        val window = UIWindow(frame = UIScreen.mainScreen.bounds)
        val host = Host()
        window.addSubview(host.view)
        try { block(host) } finally { host.view.removeFromSuperview() }
    }

    private suspend fun awaitCondition(condition: () -> Boolean) {
        val started = TimeSource.Monotonic.markNow()
        while (!condition()) {
            check(started.elapsedNow() < 2.seconds) { "Native callback was not reached" }
            delay(1)
        }
    }

    private fun onMain(block: suspend CoroutineScope.() -> Unit) {
        check(NSThread.isMainThread)
        val result = CoroutineScope(Dispatchers.Main).async(block = block)
        val started = TimeSource.Monotonic.markNow()
        while (!result.isCompleted) {
            if (started.elapsedNow() > 20.seconds) {
                result.cancel()
                fail("Small ports test timed out")
            }
            NSRunLoop.mainRunLoop.runMode(NSDefaultRunLoopMode, beforeDate = NSDate.dateWithTimeIntervalSinceNow(0.01))
        }
        result.getCompleted()
    }
}
