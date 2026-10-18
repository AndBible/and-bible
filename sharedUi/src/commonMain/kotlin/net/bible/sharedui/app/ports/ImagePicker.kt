package net.bible.sharedui.app.ports

interface ImagePicker {
    suspend fun pickImage(): String?
}
