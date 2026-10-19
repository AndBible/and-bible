package net.bible.sharedcore.calculator

/** Android normalizes the stored PIN only; the controller separately excludes arithmetic inputs. */
object CalculatorUnlock {
    fun allows(entered: String, storedPin: String?): Boolean {
        val stored = storedPin ?: "1234"
        val pin = Regex("""^(0+)(\d+)$""").matchEntire(stored)?.groupValues?.get(2) ?: stored
        return pin.isEmpty() || entered == pin
    }
}
