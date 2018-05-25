package net.bible.android.control.speak

data class SpeakSettings(val repeat: Boolean = false,
                         val queue: Boolean = false,
                         val continuous: Boolean = false,
                         val amount: Int = 0 // how many chapters, verse etc.?
                         )

