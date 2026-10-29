package net.bible.sharedui.assembly

import net.bible.sharedcore.platform.CoreStrings
import net.bible.service.llm.LlmStrings
import net.bible.sharedlogic.platform.LabelNameStrings
import net.bible.sharedlogic.platform.DistributedPlanCatalog
import net.bible.sharedui.strings.lookup
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class IosLanguageSwitchTest {
    @Test
    fun coreStringsFollowTheSwitch() {
        val app = koinApplication { modules(buildIosPlatformBindings(IosAppConfig(uiLanguage = "en"))) }
        try {
            val core = app.koin.get<CoreStrings>()
            val llm = app.koin.get<LlmStrings>()
            val labels = app.koin.get<LabelNameStrings>()
            val plans = app.koin.get<DistributedPlanCatalog>()
            val before = listOf(core.studypads, llm.actionActiveWindow, labels.speak, plans.plans().first().planName)
            app.koin.get<HostStringTableHolder>().switchTo("fi")
            assertEquals(lookup("fi", "studypads"), core.studypads)
            val after = listOf(core.studypads, llm.actionActiveWindow, labels.speak, plans.plans().first().planName)
            before.zip(after).forEach { (en, fi) -> assertNotEquals(en, fi) }
        } finally { app.close() }
    }

    @Test
    fun retainedLiveTableFollowsTheSwitchIncludingItsTag() {
        val holder = HostStringTableHolder(hostStringTable("en"))
        val retained = holder.live // a host keeps this facade across language switches
        assertEquals("en", retained.tag)
        holder.switchTo("fi")
        assertEquals("fi", holder.current.tag)
        assertEquals("fi", retained.tag)
        assertEquals(lookup("fi", "studypads"), retained.text("studypads"))
        holder.switchTo("en")
        assertEquals("en", retained.tag)
        assertEquals(lookup("en", "studypads"), retained.text("studypads"))
    }
}
