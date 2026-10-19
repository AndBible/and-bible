package net.bible.sharedui.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DebugFeatureScenarioTest {
    @Test fun releaseNeverAcceptsAnyScenarioOrFixture() {
        for (name in listOf("chooser", "plan-settings", "labels", "download")) {
            assertNull(DebugFeatureScenario.parse(false, listOf("-featureScenario", name)))
        }
        assertFalse(debugStandardFixture(false, listOf("-standardTestFixture")))
    }

    @Test fun onlyFiniteNamesAreAccepted() {
        assertEquals(DebugFeatureScenario.CHOOSER, DebugFeatureScenario.parse(true, listOf("-openRef", "John.3.16", "-featureScenario", "chooser")))
        assertNull(DebugFeatureScenario.parse(true, listOf("-featureScenario")))
        assertNull(DebugFeatureScenario.parse(true, listOf("-featureScenario", "settings/app")))
        assertNull(DebugFeatureScenario.parse(true, listOf("-featureScenario", "-resetData")))
        assertTrue(debugStandardFixture(true, listOf("-standardTestFixture")))
    }

    @Test fun normalizationIsExplicitAndDoesNotChangeReleaseOrOrdinaryLaunches() {
        val changes = mutableMapOf<String, Any>()
        fun apply(debug: Boolean, args: List<String>) = applyDebugStandardFixture(debug, args,
            { key, value -> changes[key] = value }, { key, value -> changes[key] = value })
        apply(false, listOf("-standardTestFixture"))
        apply(true, listOf("-resetData"))
        assertTrue(changes.isEmpty())
        apply(true, listOf("-standardTestFixture"))
        assertEquals(mapOf<String, Any>("locale_pref" to "en", "discrete_mode" to false, "show_calculator" to false), changes)
    }

    @Test fun chooserConsumesActualResultOnlyWhenReadyAndNeverForOtherScenarios() {
        val channel = net.bible.sharedui.nav.NavResultChannel<net.bible.sharedcore.nav.DocumentResult> { error("not an external result") }
        channel.publishForTest(net.bible.sharedcore.nav.DocumentResult("KJV"))
        val selected = mutableListOf<String>()
        fun consume(scenario: DebugFeatureScenario?, ready: Boolean) = consumeDebugDocument(scenario, ready, channel::consume, selected::add)
        consume(null, true)
        consume(DebugFeatureScenario.LABELS, true)
        consume(DebugFeatureScenario.CHOOSER, false)
        assertTrue(selected.isEmpty())
        assertEquals("KJV", channel.pending.value?.book)
        consume(DebugFeatureScenario.CHOOSER, true)
        consume(DebugFeatureScenario.CHOOSER, true)
        assertEquals(listOf("KJV"), selected)
        assertNull(channel.pending.value)
    }

    @Test fun discreteReadingBibleUsesTheNeutralBookGlyph() {
        assertEquals("ic_bible_24dp", net.bible.sharedui.icons.readingBibleIconKey(false))
        assertEquals("ic_baseline_menu_book_24", net.bible.sharedui.icons.readingBibleIconKey(true))
    }

    @Test fun settingsLauncherCannotCoverSettingsOrOtherDestinations() {
        val routes = net.bible.sharedcore.nav.NavRoutes
        assertTrue(debugSettingsLauncherVisible(routes.DAILY_READING_PATTERN, true))
        assertFalse(debugSettingsLauncherVisible(routes.DAILY_READING_PATTERN, false))
        assertFalse(debugSettingsLauncherVisible(routes.SETTINGS, true))
        assertFalse(debugSettingsLauncherVisible(routes.READING, true))
        assertFalse(debugSettingsLauncherVisible(null, true))
    }

    @Test fun navigationWaitsForReadingAndReferenceAndRunsOnce() {
        val gate = DebugFeatureOnce()
        assertFalse(gate.take(ready = false, referenceApplied = true))
        assertFalse(gate.take(ready = true, referenceApplied = false))
        assertTrue(gate.take(ready = true, referenceApplied = true))
        assertFalse(gate.take(ready = true, referenceApplied = true))
    }
}
