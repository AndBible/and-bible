package net.bible.sharedcore.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ToolPermissionCategoryStateTest {

    private fun tool(id: String, write: Boolean = false) =
        ToolVd(id = id, displayName = id, description = "", requiresPermission = write, categoryId = "C")

    @Test fun emptyCategoryHasNoState() {
        assertNull(categoryPermissionState(emptyList()) { ToolPermission.ENABLED })
    }

    @Test fun allToolsAgreeing_reportsThatExactPermission() {
        val tools = listOf(tool("a"), tool("b"))
        assertEquals(
            CategoryPermissionState.Uniform(ToolPermission.DISABLED),
            categoryPermissionState(tools) { ToolPermission.DISABLED },
        )
    }

    @Test fun askAndAllowAreDifferentUniformStates_notBothOn() {
        // The old CategoryToggleState collapsed both to ALL_ON, which is exactly why a category
        // header could not offer the same three options its rows do.
        val tools = listOf(tool("w1", write = true), tool("w2", write = true))
        assertEquals(
            CategoryPermissionState.Uniform(ToolPermission.ASK),
            categoryPermissionState(tools) { ToolPermission.ASK },
        )
        assertEquals(
            CategoryPermissionState.Uniform(ToolPermission.ALLOW),
            categoryPermissionState(tools) { ToolPermission.ALLOW },
        )
    }

    @Test fun disagreeingTools_reportMixed() {
        val tools = listOf(tool("a"), tool("b"))
        val map = mapOf("a" to ToolPermission.ENABLED, "b" to ToolPermission.DISABLED)
        assertEquals(CategoryPermissionState.Mixed, categoryPermissionState(tools) { map.getValue(it) })
    }
}
