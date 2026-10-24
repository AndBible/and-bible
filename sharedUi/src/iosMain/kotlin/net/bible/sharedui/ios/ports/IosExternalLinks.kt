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

import net.bible.sharedui.app.ports.ExternalLinks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.UserNotifier
import org.koin.mp.KoinPlatform
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/** Resolves live discrete-mode state rather than retaining the startup environment. */
@kotlin.native.HiddenFromObjC
class IosExternalLinks(
    private val isDiscrete: () -> Boolean = { KoinPlatform.getKoin().get<AppEnvironment>().isDiscrete },
    private val openUrl: (NSURL, (Boolean) -> Unit) -> Unit = { url, completion ->
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = completion)
    },
    private val openingFailed: () -> Unit = {
        val koin = KoinPlatform.getKoin()
        koin.get<UserNotifier>().showError(koin.get<CoreStrings>().errorOccurred)
    },
) : ExternalLinks {
    override val askBeforeOpening: Boolean get() = isDiscrete()
    override fun open(url: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val target = NSURL.URLWithString(url)
            if (target == null) openingFailed()
            else openUrl(target) { ok ->
                if (!ok) CoroutineScope(Dispatchers.Main).launch { openingFailed() }
            }
        }
    }
}
