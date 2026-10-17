package net.bible.android.platform

import net.bible.service.common.AndBibleAddons
import net.bible.sharedcore.event.Events
import net.bible.sharedlogic.platform.PromptPackRef
import net.bible.sharedlogic.platform.PromptPackSource

/** Android [PromptPackSource]: prompt packs of the installed SWORD add-on modules. */
object AndroidPromptPackSource : PromptPackSource {
    override val packs: List<PromptPackRef>
        get() = AndBibleAddons.providedPromptPacks.map { PromptPackRef(it.moduleName, it.file.path) }
    override val reloaded: Events<Unit> get() = AndBibleAddons.reloaded
}
