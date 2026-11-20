package net.bible.android.platform

import net.bible.service.common.AndBibleAddons
import net.bible.sharedcore.event.Events
import net.bible.sharedlogic.platform.CollectionPackRef
import net.bible.sharedlogic.platform.CollectionPackSource

/** Android [CollectionPackSource]: collection databases of the installed SWORD add-on modules. */
object AndroidCollectionPackSource : CollectionPackSource {
    override fun collections(): List<CollectionPackRef> = AndBibleAddons.providedCollections
    override val reloaded: Events<Unit> get() = AndBibleAddons.reloaded
}
