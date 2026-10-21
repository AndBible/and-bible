@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.ios.reading

@kotlin.native.HiddenFromObjC
interface IosReadingExtras {
    /** Drawer row ids (ReadingDrawerMenu.ID_*) this build offers; a row not in the set is not shown. */
    val offeredRows: Set<String>
    fun runRow(id: String)
    fun reportJsError()
    object None : IosReadingExtras { override val offeredRows = emptySet<String>(); override fun runRow(id: String) {}; override fun reportJsError() {} }
}

