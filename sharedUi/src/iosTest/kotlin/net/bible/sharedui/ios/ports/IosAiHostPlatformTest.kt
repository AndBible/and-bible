package net.bible.sharedui.ios.ports

import net.bible.sharedui.assembly.iosStringTable
import net.bible.sharedui.strings.keyed.KeyedCoreStrings
import net.bible.sharedlogic.platform.ios.IosLocaleSupport
import net.bible.sharedui.app.ai.AI_CUSTOM_LANGUAGE_TAG
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import platform.Foundation.defaultTimeZone
import platform.Foundation.setDefaultTimeZone
import platform.Foundation.timeZoneWithName

class IosAiHostPlatformTest {
    @Test fun errorDialogPreservesMessageAndCause() = kotlinx.coroutines.runBlocking {
        val table = { iosStringTable("en") }
        var message: String? = null
        var cause: Throwable? = null
        val failure = IllegalArgumentException("original")
        val port = IosAiHostPlatform(KeyedCoreStrings(table), IosLocaleSupport, table, { iosAiLogTimestamp(table().tag, it) }) { m, c -> message = m; cause = c }
        port.showErrorDialog("Failed", failure)
        assertEquals("Failed", message)
        kotlin.test.assertSame(failure, cause)
    }

    @Test fun timestampUsesLocalTimeAndFixedMinutePattern() {
        val saved = platform.Foundation.NSTimeZone.defaultTimeZone
        try {
            platform.Foundation.NSTimeZone.setDefaultTimeZone(platform.Foundation.NSTimeZone.timeZoneWithName("Europe/Helsinki")!!)
            val table = { iosStringTable("en") }
            val port = IosAiHostPlatform(KeyedCoreStrings(table), IosLocaleSupport, table, { iosAiLogTimestamp(table().tag, it) }) { _, _ -> }
            assertEquals("2026-03-28 09:17", port.formatLogTimestamp(1774682232345L))
        } finally { platform.Foundation.NSTimeZone.setDefaultTimeZone(saved) }
    }

    @Test fun aiLanguageChoicesIncludeAppDefaultAndCustom() {
        var language = "en"
        val table = { iosStringTable(language) }
        val port = IosAiHostPlatform(KeyedCoreStrings(table), IosLocaleSupport, table, { iosAiLogTimestamp(table().tag, it) }) { _, _ -> }
        val choices = port.languageChoices()
        assertEquals("", choices.first().value)
        assertEquals(AI_CUSTOM_LANGUAGE_TAG, choices.last().value)
        assertEquals(KeyedCoreStrings(table).prefsInterfaceLocaleValues.filter { it.isNotEmpty() },
            choices.drop(1).dropLast(1).map { it.value })
        language = "fi"
        assertNotEquals(choices.first().label, port.languageChoices().first().label)
        assertNotEquals(choices.last().label, port.languageChoices().last().label)
    }
}
