package net.bible.service.format.osistohtml.osishandlers

/** Removes skipped links and empty surrounding brackets without changing unrelated text. */
internal object SkippedSpeakLinks {
    const val marker = "\uE000"
    private val bracketDebris = Regex("[\\s,;:.!?\\[\\](){}$marker]+")

    fun clean(text: String): String = cleanParts(listOf(text)).single()

    /** Keeps surviving characters in their original part, so bookmark/highlight ordinals do not move. */
    fun cleanParts(parts: List<String>): List<String> {
        val text = parts.joinToString("")
        if (marker !in text) return parts
        val removed = BooleanArray(text.length) { text[it] == marker[0] }
        val separators = mutableSetOf<Int>()
        val brackets = ArrayList<Int>()
        for (i in text.indices) {
            when (text[i]) {
                '(', '[', '{' -> brackets.add(i)
                ')', ']', '}' -> {
                    val start = brackets.lastOrNull() ?: continue
                    if (text[i] != when (text[start]) { '(' -> ')'; '[' -> ']'; else -> '}' }) continue
                    brackets.removeAt(brackets.lastIndex)
                    val content = text.substring(start + 1, i)
                    val markerStart = content.indexOf(marker)
                    val markerEnd = content.lastIndexOf(marker) + marker.length
                    // In an otherwise link-only bracket, text between links is connector material.
                    val candidate = if (markerStart >= 0) content.removeRange(markerStart, markerEnd) else content
                    val words = bracketDebris.replace(candidate, " ").trim().lowercase()
                    if (markerStart >= 0 && words in listOf("", "see", "see also", "cf")) {
                        for (j in start..i) removed[j] = true
                    }
                }
            }
        }
        for (i in text.indices) {
            if (text[i] != marker[0]) continue
            val left = (i - 1 downTo 0).firstOrNull { !removed[it] && !text[it].isWhitespace() }
            val right = (i + 1 until text.length).firstOrNull { !removed[it] && !text[it].isWhitespace() }
            if (left != null && right != null && text[right] in ",;:" && text[left] in ",;:([{") removed[right] = true
        }
        // Only normalize gaps containing removed characters, never whitespace elsewhere.
        var i = 0
        while (i < text.length) {
            if (!removed[i] && !text[i].isWhitespace()) { i++; continue }
            val start = i
            while (i < text.length && (removed[i] || text[i].isWhitespace())) i++
            if ((start until i).none { removed[it] }) continue
            val space = (start until i).firstOrNull { !removed[it] && text[it].isWhitespace() }
            val keepSpace = start > 0 && i < text.length && text[start - 1] !in "([{" && text[i] !in ",;:.!?)]}"
            for (j in start until i) removed[j] = !(keepSpace && j == space)
            if (keepSpace && space == null) separators.add(start)
        }
        if (text.indices.none { !removed[it] && text[it].isLetterOrDigit() }) return parts.map { "" }
        var offset = 0
        return parts.map { part ->
            buildString {
                for (j in part.indices) {
                    if (offset + j in separators) append(' ')
                    if (!removed[offset + j]) append(part[j])
                }
            }.also { offset += part.length }
        }
    }
}
