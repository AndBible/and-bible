@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.search

import net.bible.android.control.activity.isIndexJob
import org.crosswire.common.progress.Progress

/** SWORD builds, downloaded indexes and EPUB builds use distinct producer-owned job IDs; the rule lives in sharedLogic [isIndexJob]. */
@kotlin.native.HiddenFromObjC
fun Progress.isSearchIndexJob(): Boolean = isIndexJob()
