/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.ios.ports

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.bible.sharedcore.platform.files.IosPresenter
import net.bible.sharedcore.platform.files.IosSheetPresentation
import net.bible.sharedcore.platform.files.SheetAnchor
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIViewController

/**
 * The one presenter of the system share sheet (text and files). It presents through [IosSheetPresentation], so
 * exactly one terminal event (completion, interactive dismissal or cancellation) releases the sheet, and on iPad
 * the popover points at the bottom centre of the presenting view.
 *
 * [presenter], [makeSheet] and [scope] are seams for tests; [onReleased] observes the single release and [onPresentation] hands tests the terminal owner (UIKit's delegate properties are weak).
 */
internal class IosSharePresenter(
    private val presenter: () -> UIViewController? = IosPresenter::topViewController,
    private val makeSheet: (List<Any>) -> UIViewController = { UIActivityViewController(activityItems = it, applicationActivities = null) },
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    private val onReleased: () -> Unit = {},
    private val onPresentation: (IosSheetPresentation<Unit>) -> Unit = {},
) {
    private var active = false

    /** Whether a share sheet can be presented now: a presenter exists and no share sheet is already up. */
    fun canPresent(): Boolean = !active && presenter()?.let { it !is UIActivityViewController } == true

    /**
     * Presents a share sheet for [items] (strings or file [NSURL]s). Fire and forget for callers; the returned job
     * ends after the sheet has been released and is what tests await.
     */
    fun share(items: List<Any>): Job = scope.launch {
        if (active) return@launch
        val host = presenter() ?: return@launch
        if (host is UIActivityViewController) return@launch // a share sheet is already up
        active = true
        val released = CompletableDeferred<Unit>()
        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                val sheet = makeSheet(items)
                val presentation = IosSheetPresentation(sheet, continuation, Unit, SheetAnchor.BottomCenter) {
                    onReleased()
                    released.complete(Unit)
                }
                (sheet as? UIActivityViewController)?.completionWithItemsHandler = { _, _, _, _ -> presentation.finish(Unit) }
                onPresentation(presentation)
                presentation.present(host)
            }
        } finally {
            withContext(NonCancellable) { released.await() }
            active = false
        }
    }
}

private val defaultSharePresenter by lazy { IosSharePresenter() }

/** Whether a share sheet can be presented now: a foreground window exists and no share sheet is already up. */
internal fun iosCanPresentShare(): Boolean = defaultSharePresenter.canPresent()

/** Presents the system share sheet for [items] (strings or file [NSURL]s). On iPad it points at the bottom centre of the screen. */
internal fun iosPresentShare(items: List<Any>) { defaultSharePresenter.share(items) }

/** Shares the file at [path]; the recipient sees the file's name. */
internal fun iosShareFile(path: String) = iosPresentShare(listOf(NSURL.fileURLWithPath(path)))
