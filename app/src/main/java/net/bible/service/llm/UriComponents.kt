package net.bible.service.llm

/** Encodes a URI component using the Android Uri.encode default allowed characters. */
fun encodeUriComponent(s: String): String = buildString {
    for (byte in s.toByteArray(Charsets.UTF_8)) {
        val value = byte.toInt() and 0xff
        val char = value.toChar()
        if (char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char in "_-!.~'()*") {
            append(char)
        } else {
            append('%')
            append("0123456789ABCDEF"[value shr 4])
            append("0123456789ABCDEF"[value and 15])
        }
    }
}
