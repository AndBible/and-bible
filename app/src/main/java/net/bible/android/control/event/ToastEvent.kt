package net.bible.android.control.event

class ToastEvent(val message: String, val duration: Int? = null) {
    constructor(message: String) : this(message, null) {

    }
}
