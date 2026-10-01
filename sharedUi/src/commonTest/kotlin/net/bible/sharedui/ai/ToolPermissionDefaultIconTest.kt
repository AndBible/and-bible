package net.bible.sharedui.ai

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import net.bible.sharedcore.ai.ToolPermission
import kotlin.test.Test
import kotlin.test.assertEquals

/** F79: a Default option's icon is the icon of what Default resolves to — never a globe. */
class ToolPermissionDefaultIconTest {
    @Test fun writeToolTokensResolve() {
        assertEquals(ToolPermission.ALLOW, resolvedDefaultPermission(true, ToolPermission.ALLOW.name))
        assertEquals(ToolPermission.DENY, resolvedDefaultPermission(true, ToolPermission.DENY.name))
        assertEquals(ToolPermission.ASK, resolvedDefaultPermission(true, ToolPermission.ASK.name))
    }

    @Test fun readToolTokensResolve() {
        assertEquals(ToolPermission.ENABLED, resolvedDefaultPermission(false, ToolPermission.ENABLED.name))
        assertEquals(ToolPermission.DISABLED, resolvedDefaultPermission(false, ToolPermission.DISABLED.name))
    }

    @Test fun everyTokenMapsToAnIcon() {
        // Unknown tokens fall back exactly like defaultOptionLabel's `else`: allowed / enabled.
        assertEquals(Icons.Filled.CheckCircle, permissionIcon(resolvedDefaultPermission(true, "???")))
        assertEquals(Icons.Filled.CheckCircle, permissionIcon(resolvedDefaultPermission(false, "???")))
        assertEquals(Icons.Filled.Block, permissionIcon(resolvedDefaultPermission(true, ToolPermission.DENY.name)))
        assertEquals(Icons.AutoMirrored.Filled.HelpOutline, permissionIcon(resolvedDefaultPermission(true, ToolPermission.ASK.name)))
    }
}
