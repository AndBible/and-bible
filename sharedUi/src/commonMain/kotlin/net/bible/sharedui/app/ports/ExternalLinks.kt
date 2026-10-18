@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app.ports

/** Opening web links; discrete builds ask the user first. */
@kotlin.native.HiddenFromObjC
interface ExternalLinks {
    val askBeforeOpening: Boolean
    fun open(url: String)
}
