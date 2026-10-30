@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.search

import net.bible.service.sword.epub.EPUB_INDEX_JOB_PREFIX
import org.crosswire.common.progress.Progress

/** SWORD builds, downloaded indexes and EPUB builds use distinct producer-owned job IDs. */
@kotlin.native.HiddenFromObjC
fun Progress.isSearchIndexJob(): Boolean =
    jobID.startsWith(Progress.CREATE_INDEX.substringBefore("%s")) ||
        jobID.startsWith(Progress.DOWNLOAD_SEARCH_INDEX.substringBefore("%s")) ||
        jobID.startsWith(EPUB_INDEX_JOB_PREFIX)
