package net.bible.sharedlogic.platform

import net.bible.sharedcore.event.Events

/** A prompt CSV file provided by an add-on module: the module's initials and the CSV file's path. */
data class PromptPackRef(val moduleName: String, val path: String)

/**
 * The add-on prompt packs available right now. [packs] is re-read on every access; [reloaded] fires when the
 * add-on set changes so cached parses can be dropped.
 */
interface PromptPackSource {
    val packs: List<PromptPackRef>
    val reloaded: Events<Unit>
}
