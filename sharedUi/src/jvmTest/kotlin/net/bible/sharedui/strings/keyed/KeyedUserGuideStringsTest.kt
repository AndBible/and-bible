package net.bible.sharedui.strings.keyed

import net.bible.sharedcore.docs.TutorialVideos.Episode
import net.bible.sharedcore.docs.UserGuideToc
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class KeyedUserGuideStringsTest {
    private val section = UserGuideToc.sections.first()
    private val page = section.pages.first()
    private val episode = Episode.TUTORIAL_01_GETTING_STARTED
    private val episodeTitle = UserGuideToc.episodeTitles.getValue(episode)

    private fun strings(translations: Map<String, String>) = KeyedUserGuideStrings(
        StringTable.of("fi", { translations[it] ?: it }, { key, _ -> key }, { emptyList() }),
    )

    @Test fun translatedTitlesAreUsed() {
        val strings = strings(mapOf(section.key to "Osio", page.key to "Sivu", episodeTitle.key to "Video"))
        assertEquals("Osio", strings.section(section))
        assertEquals("Sivu", strings.page(page))
        assertEquals("Video", strings.episode(episode))
        assertEquals("1. Video", strings.numberedEpisode(episode))
    }

    @Test fun missingKeysFallBackToEnglish() {
        val strings = strings(emptyMap())
        assertEquals(section.title, strings.section(section))
        assertEquals(page.title, strings.page(page))
        assertEquals(episodeTitle.title, strings.episode(episode))
        assertEquals("1. ${episodeTitle.title}", strings.numberedEpisode(episode))
    }

    @Test fun unnumberedEpisodeHasNoNumberPrefix() {
        val title = UserGuideToc.episodeTitles.getValue(Episode.WHATS_NEW_6_0)
        assertEquals("Uutta", strings(mapOf(title.key to "Uutta")).numberedEpisode(Episode.WHATS_NEW_6_0))
        assertEquals(title.title, strings(emptyMap()).numberedEpisode(Episode.WHATS_NEW_6_0))
    }
    @Test fun allGuideKeysMatchEnglishResources() {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("../app/src/main/res/values/strings.xml"))
        val nodes = doc.getElementsByTagName("string")
        val resources = (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            node.attributes.getNamedItem("name").nodeValue to node.textContent.replace("\\'", "'").replace("\\\"", "\"")
        }
        val titles = UserGuideToc.sections.map { it.key to it.title } +
            UserGuideToc.sections.flatMap { section -> section.pages.map { it.key to it.title } } +
            UserGuideToc.episodeTitles.values.map { it.key to it.title }
        assertEquals(titles.size, titles.map { it.first }.toSet().size)
        titles.forEach { (key, title) -> assertEquals(title, resources[key], key) }
    }

}
