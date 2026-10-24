package net.bible.sharedcore.platform.speak

import kotlin.test.Test
import kotlin.test.assertEquals

class IosSystemMediaMetadataTest {
    @Test fun nowPlayingIsNeutralInDiscrete() {
        val metadata = IosSystemMediaMetadata({ true }, { "Calculator" })
        assertEquals(IosNowPlaying("Calculator", ""), metadata.forContent("John 3:16", "John"))
    }

    @Test fun nowPlayingTurnsNeutralWhenDiscreteSwitchesOnMidSession() {
        var discrete = false
        val metadata = IosSystemMediaMetadata({ discrete }, { "Calculator" })
        assertEquals(IosNowPlaying("John 3:16", "John"), metadata.forContent("John 3:16", "John"))
        discrete = true
        assertEquals(IosNowPlaying("Calculator", ""), metadata.forContent("John 3:17", "John"))
        discrete = false
        assertEquals(IosNowPlaying("John 3:18", "John"), metadata.forContent("John 3:18", "John"))
    }

    @Test fun neutralTitleIsReadAtPublicationTime() {
        var title = "Calculator"
        val metadata = IosSystemMediaMetadata({ true }, { title })
        assertEquals(IosNowPlaying("Calculator", ""), metadata.forContent("John", "John"))
        title = "Localized calculator"
        assertEquals(IosNowPlaying("Localized calculator", ""), metadata.forContent("John", "John"))
    }
}
