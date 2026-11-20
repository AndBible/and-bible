package net.bible.sharedlogic.platform

import net.bible.sharedcore.event.Events
import okio.Path

/**
 * A bookmark collection database provided by an add-on module (`AndBibleProvidesLabels`): the module's initials and
 * human name, its conf `Version` (`"0"` when missing) and the resolved SQLite [file] inside the module directory.
 */
data class CollectionPackRef(val moduleInitials: String, val moduleName: String, val version: String, val file: Path)

/**
 * The add-on collection databases available right now. [collections] is re-read on every call; [reloaded] fires
 * when the add-on set changes.
 */
interface CollectionPackSource {
    fun collections(): List<CollectionPackRef>
    val reloaded: Events<Unit>
}
