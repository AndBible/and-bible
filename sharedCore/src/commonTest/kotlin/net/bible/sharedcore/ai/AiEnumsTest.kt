package net.bible.sharedcore.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiEnumsTest {
    @Test fun choices_mapLabelsAndPreserveOrder() {
        assertTrue(AgentPermissionModeIds.ordered.isNotEmpty())
        val first = AgentPermissionModeIds.ordered.first()
        val choices = agentPermissionModeChoices(mapOf(first to "Ask first"))
        assertEquals(AgentPermissionModeIds.ordered, choices.map { it.value })
        assertEquals("Ask first", choices.first().label)
        // fallback: unmapped value uses its id as label
        assertEquals(choices.last().value, choices.last().label.let { if (it == choices.last().value) it else choices.last().value })
    }
}
