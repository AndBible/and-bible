@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.strings.keyed

import net.bible.sharedcore.docs.TutorialVideos
import net.bible.sharedcore.docs.UserGuideToc

@kotlin.native.HiddenFromObjC
class KeyedUserGuideStrings(private val t: StringTable) {
    fun section(section: UserGuideToc.Section): String = resolve(section.key, section.title)
    fun page(page: UserGuideToc.Page): String = resolve(page.key, page.title)
    fun episode(episode: TutorialVideos.Episode): String = UserGuideToc.episodeTitles[episode]?.let {
        val title = resolve(it.key, it.title)
        if (it.number != null) "${it.number}. $title" else title
    } ?: episode.slug

    private fun resolve(key: String, fallback: String): String = t.text(key).let { if (it == key) fallback else it }

    companion object {
        fun english(): KeyedUserGuideStrings = KeyedUserGuideStrings(
            StringTable.of("en", { it }, { key, _ -> key }, { emptyList() }),
        )
    }
}
