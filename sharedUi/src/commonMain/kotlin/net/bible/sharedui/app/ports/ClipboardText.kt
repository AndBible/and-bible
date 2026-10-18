package net.bible.sharedui.app.ports

interface ClipboardText {
    fun read(): String?
    fun write(label: String, text: String)
}
