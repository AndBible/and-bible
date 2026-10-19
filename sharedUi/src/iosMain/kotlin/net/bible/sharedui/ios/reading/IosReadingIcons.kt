/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 * Licensed under the GNU General Public License, version 3 or later.
 */
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.ios.reading

import androidx.compose.runtime.Composable
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedui.icons.AbIcons
import net.bible.sharedui.icons.readingBibleIconKey
import net.bible.sharedui.reading.ReadingToolbarIcons
import org.jetbrains.compose.resources.painterResource
import org.koin.mp.KoinPlatform

/** The shared copies of Android's toolbar drawables; Android retains its native resources. */
@Composable
@kotlin.native.HiddenFromObjC
fun rememberIosReadingIcons(): ReadingToolbarIcons {
    val resources = AbIcons.readingToolbarIconResources
    val discrete = KoinPlatform.getKoin().get<AppEnvironment>().isDiscrete
    return ReadingToolbarIcons(
        home = painterResource(resources.getValue("ic_menu")),
        search = painterResource(resources.getValue("ic_search_24dp")),
        speak = painterResource(resources.getValue("ic_baseline_headphones_24")),
        strongs = painterResource(resources.getValue("ic_strongs_hebrew")),
        bible = painterResource(resources.getValue(readingBibleIconKey(discrete))),
        commentary = painterResource(resources.getValue("ic_commentary")),
        workspace = painterResource(resources.getValue("ic_workspace_solid_24dp")),
        overflow = painterResource(resources.getValue("ic_more_vert_black_24dp")),
        sync = painterResource(resources.getValue("ic_syncdb_24dp")),
    )
}
