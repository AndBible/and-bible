package net.bible.service.format.osistohtml.osishandlers

/** Removes only punctuation/introductions belonging to skipped links, across SAX chunks or BVA ordinals. */
internal object SkippedSpeakLinks {
    const val marker = "\uE000"
    private val introduction = Regex("\\bsee\\s*(?=$marker)", RegexOption.IGNORE_CASE)
    private val emptyReference = Regex("[\\s,;:.!?\\[\\](){}$marker]|\\bsee\\b", RegexOption.IGNORE_CASE)

    fun clean(text: String): String = cleanParts(listOf(text)).single()

    /** Keeps surviving characters in their original part, so bookmark/highlight ordinals do not move. */
    fun cleanParts(parts: List<String>): List<String> {
        val text = parts.joinToString("")
        if (marker !in text) return parts
        val removed = BooleanArray(text.length) { text[it] == marker[0] }
        val brackets = ArrayList<Int>()
        for (i in text.indices) {
            when (text[i]) {
                '(', '[', '{' -> brackets.add(i)
                ')', ']', '}' -> {
                    val start = brackets.lastOrNull() ?: continue
                    val expected = when (text[start]) { '(' -> ')'; '[' -> ']'; else -> '}' }
                    if (text[i] != expected) continue
                    brackets.removeAt(brackets.lastIndex)
                    val content = text.substring(start + 1, i)
                    if (marker in content && emptyReference.replace(content, "").isEmpty()) {
                        for (j in start..i) removed[j] = true
                    }
                }
            }
        }
        introduction.findAll(text).forEach { match ->
            for (i in match.range) removed[i] = true
        }
        // A removed inline citation must not leave doubled separators or a leading separator in an aside.
        for (i in text.indices) {
            if (text[i] != marker[0]) continue
            val left = (i - 1 downTo 0).firstOrNull { !removed[it] && !text[it].isWhitespace() }
            val right = (i + 1 until text.length).firstOrNull { !removed[it] && !text[it].isWhitespace() }
            if (left != null && right != null && text[right] in ",;:" && text[left] in ",;:([{") {
                removed[right] = true
            }
        }
        // Collapse the gap left by a link and remove spaces before closing/sentence punctuation.
        var previous: Int? = null
        for (i in text.indices) {
            if (removed[i]) continue
            val prev = previous
            if (prev != null && text[prev].isWhitespace()) {
                if (text[i].isWhitespace()) {
                    removed[i] = true
                    continue
                }
                if (text[i] in ",;:.!?)]}") removed[prev] = true
            }
            if (prev != null && text[prev] in "([{" && text[i].isWhitespace()) {
                removed[i] = true
                continue
            }
            previous = i
        }
        var offset = 0
        if (text.indices.none { !removed[it] && text[it].isLetterOrDigit() }) return parts.map { "" }
        return parts.map { part ->
            buildString {
                for (i in part.indices) if (!removed[offset + i]) append(part[i])
            }.also { offset += part.length }
        }
    }
}
