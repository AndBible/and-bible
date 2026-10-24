package net.bible.sharedcore.platform

/**
 * Renders Markdown (CommonMark plus GFM tables) to XHTML that is safe to embed in the OSIS/XML pipeline:
 * self-closing void elements and only the five XML built-in entities.
 *
 * A port rather than a common implementation: no KMP Markdown library reproduces commonmark-java's output
 * byte for byte (see `MarkdownToXhtml` in `:app`), and stored/displayed pages must not change on a swap.
 */
interface MarkdownRenderer {
    fun toXhtml(markdown: String): String
}
