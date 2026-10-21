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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.ios.app

import androidx.navigation.NavHostController
import net.bible.sharedui.app.AppResultChannels
import net.bible.sharedui.nav.NavResultChannel

/**
 * The result channels for iOS. A [NavResultChannel] already publishes to its parent entry and pops; the only
 * Android-specific part is the fallback that finishes the activity with a result. iOS has one NavHost and no
 * activity, so that fallback pops the back stack ([iosExitWithResult]), the only "finish" iOS has.
 */
@kotlin.native.HiddenFromObjC
fun iosResultChannels(navController: () -> NavHostController): AppResultChannels {
    val navigator = NavControllerNavigator(navController)
    fun <T> channel() = NavResultChannel<T>(iosExitWithResult(navigator))
    return AppResultChannels(
        readingProgress = channel(),
        bookmark = channel(),
        manageLabels = channel(),
        labelEdit = channel(),
        repositoryEditor = channel(),
        myDocumentPages = channel(),
        myDocuments = channel(),
        keyChooser = channel(),
        passage = channel(),
        document = channel(),
        installZip = channel(),
        workspace = channel(),
        textSettings = channel(),
        beforeSwitchMyDocument = {},
    )
}

/**
 * The "no parent entry" fallback of a [NavResultChannel]: a result has nobody to be published to, so the screen is left
 * by popping the back stack (the result is dropped, as an Android activity finishing without a caller would).
 */
@kotlin.native.HiddenFromObjC
internal fun <T> iosExitWithResult(navigator: IosNavigator): (T) -> Unit = { navigator.popBackStack() }
