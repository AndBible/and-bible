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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.ios.ports

import net.bible.sharedui.app.ports.TextShare
import kotlinx.coroutines.*
import net.bible.sharedcore.platform.files.IosPresenter
import net.bible.sharedcore.platform.files.IosSheetPresentation
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIViewController

/** iOS has no chooser title; the activity receives only the shared text. */
@kotlin.native.HiddenFromObjC
class IosTextShare(
    private val presenter: () -> UIViewController? = IosPresenter::topViewController,
) : TextShare {
    override fun shareText(text: String, chooserTitle: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val host = presenter() ?: return@launch
            val dismissed = CompletableDeferred<Unit>()
            try {
                suspendCancellableCoroutine<Unit> { continuation ->
                    val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
                    val presentation = IosSheetPresentation(sheet, continuation, Unit) { dismissed.complete(Unit) }
                    sheet.completionWithItemsHandler = { _, _, _, _ -> presentation.finish(Unit) }
                    presentation.present(host)
                }
            } finally {
                withContext(NonCancellable) { dismissed.await() }
            }
        }
    }
}
