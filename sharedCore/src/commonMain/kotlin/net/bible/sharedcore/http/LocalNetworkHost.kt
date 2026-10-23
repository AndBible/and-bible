package net.bible.sharedcore.http

/** Literal local-network hosts only; never resolves DNS or trusts a local-looking prefix. */
object LocalNetworkHost {
    fun isLocal(host: String): Boolean {
        val name = if (host.startsWith("[") && host.endsWith("]")) host.drop(1).dropLast(1) else host
        if (name.equals("localhost", ignoreCase = true)) return true
        if (name.endsWith(".local", ignoreCase = true) && name.length > 6 &&
            name.split('.').all { label -> label.isNotEmpty() && label.all { it.isLetterOrDigit() || it == '-' } }) return true
        if (':' in name) {
            val words = ipv6(name) ?: return false
            return words.dropLast(1).all { it == 0 } && words.last() == 1 || words.first() in 0xfe80..0xfebf
        }
        val parts = name.split('.')
        // Multi-digit leading-zero components can be interpreted as octal by networking stacks.
        if (parts.size != 4 || parts.any {
                it.isEmpty() || it.length > 1 && it.startsWith('0') || it.any { c -> c !in '0'..'9' }
            }) return false
        val bytes = parts.map { it.toIntOrNull()?.takeIf { n -> n in 0..255 } ?: return false }
        return bytes[0] == 127 || bytes[0] == 10 ||
            bytes[0] == 172 && bytes[1] in 16..31 ||
            bytes[0] == 192 && bytes[1] == 168 || bytes[0] == 169 && bytes[1] == 254
    }

    private fun ipv6(host: String): List<Int>? {
        if (host.count { it == ':' } < 2) return null
        val halves = host.split("::")
        if (halves.size > 2) return null
        fun words(part: String): List<Int>? {
            if (part.isEmpty()) return emptyList()
            return part.split(':').map {
                if (it.isEmpty() || it.length > 4 || it.any { c -> c !in '0'..'9' && c.lowercaseChar() !in 'a'..'f' }) return null
                it.toInt(16)
            }
        }
        val left = words(halves.first()) ?: return null
        if (halves.size == 1) return left.takeIf { it.size == 8 }
        val right = words(halves.last()) ?: return null
        val missing = 8 - left.size - right.size
        if (missing < 1) return null
        return left + List(missing) { 0 } + right
    }
}
