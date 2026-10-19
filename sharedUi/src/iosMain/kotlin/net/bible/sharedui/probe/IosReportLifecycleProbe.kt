@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package net.bible.sharedui.probe

import kotlinx.coroutines.*
import net.bible.sharedcore.platform.files.FilePresentation
import net.bible.sharedui.ios.report.Attachment
import net.bible.sharedui.ios.report.IosMailOrShare
import platform.Foundation.NSURL
import platform.UIKit.UIViewController
import kotlin.native.Platform

/**
 * Swift-visible bridge for app-hosted lifecycle tests: UIKit needs a real UIApplication scene.
 * Deliberately exported (not HiddenFromObjC) for XCTest; all entry points reject release binaries.
 * Only the remote ShareSheet service is substituted. Jobs, staging, FilePresentation and animated
 * UIKit presentation/dismissal are real. No test deadline participates in their completion.
 */
class IosReportLifecycleProbe {
    init { check(Platform.isDebugBinary) { "Lifecycle probe requires a debug framework" } }

    private val scope = MainScope()
    private var job: Job? = null
    private var terminal: (() -> Unit)? = null
    val started: Boolean get() = job != null
    var completed: Boolean = false
        private set
    var cancelled: Boolean = false
        private set
    var releases: Int = 0
        private set

    fun startReport(
        host: UIViewController,
        sheet: UIViewController,
        staged: (String) -> Unit,
        completion: (String?) -> Unit,
    ) = start(completion) {
        IosMailOrShare(shareController = { items, _, finish ->
            terminal = finish
            staged((items.last() as NSURL).path!!)
            sheet
        }, presenter = { host }).send(
            "not-sent@example.invalid", "test", "body",
            listOf(Attachment("log.gz", "application/gzip", "payload".encodeToByteArray())),
        )
    }

    fun startPresentation(host: UIViewController, sheet: UIViewController, completion: (String?) -> Unit) = start(completion) {
        val released = CompletableDeferred<Unit>()
        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                val presentation = FilePresentation(sheet, continuation, Unit, release = {
                    releases++
                    released.complete(Unit)
                })
                terminal = { presentation.finish(Unit) }
                presentation.present(host)
            }
        } finally {
            withContext(NonCancellable) { released.await() }
        }
    }

    private fun start(completion: (String?) -> Unit, block: suspend () -> Unit) {
        check(Platform.isDebugBinary)
        check(job == null) { "One request per probe" }
        var failure: String? = null
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                block()
            } catch (error: CancellationException) {
                cancelled = true
                throw error
            } catch (error: Throwable) {
                failure = error.toString()
            }
        }.also { request ->
            request.invokeOnCompletion {
                completed = true
                completion(failure)
            }
        }
    }

    /** Cancels the actual sending/presentation coroutine, not a synthesized terminal result. */
    fun cancel() {
        check(Platform.isDebugBinary)
        checkNotNull(job).cancel()
    }

    fun finishTwice() {
        check(Platform.isDebugBinary)
        val finish = checkNotNull(terminal)
        finish()
        finish()
    }
}
