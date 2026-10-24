@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app.ports

@kotlin.native.HiddenFromObjC
interface ImagePicker {
    suspend fun pickImage(): String?
}
