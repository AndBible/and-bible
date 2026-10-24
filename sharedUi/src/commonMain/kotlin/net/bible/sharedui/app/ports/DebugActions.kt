@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app.ports

@kotlin.native.HiddenFromObjC
interface DebugActions {
    fun crashAppDelayed()
    fun showTestError(message: String)
}
