@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app.ports

@kotlin.native.HiddenFromObjC
interface ClipboardText {
    fun read(): String?
    fun write(label: String, text: String)
}
