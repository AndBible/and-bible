@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.strings.keyed

import net.bible.sharedcore.docs.TutorialVideos
import net.bible.sharedcore.docs.UserGuideToc

@kotlin.native.HiddenFromObjC
class KeyedUserGuideStrings(private val t: StringTable) {
    fun section(section: UserGuideToc.Section): String = resolve(section.key, section.title)
    fun page(page: UserGuideToc.Page): String = resolve(page.key, page.title)
    /** The episode title without its series number, for the table of contents. */
    fun episode(episode: TutorialVideos.Episode): String = UserGuideToc.episodeTitles[episode]
        ?.let { resolve(it.key, it.title) } ?: episode.slug

    /** The episode title prefixed with its series number ("3. Title"); unnumbered episodes get no prefix. */
    fun numberedEpisode(episode: TutorialVideos.Episode): String {
        val title = episode(episode)
        val number = UserGuideToc.episodeTitles[episode]?.number
        return if (number != null) "$number. $title" else title
    }

    private fun resolve(key: String, fallback: String): String = t.text(key).let { if (it == key) fallback else it }

    companion object {
        fun english(): KeyedUserGuideStrings = KeyedUserGuideStrings(
            StringTable.of("en", { it }, { key, _ -> key }, { emptyList() }),
        )
    }
}
