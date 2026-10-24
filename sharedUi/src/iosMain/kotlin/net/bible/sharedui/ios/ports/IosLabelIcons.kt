@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)
package net.bible.sharedui.ios.ports

import net.bible.sharedui.app.bookmark.ResourceLabelIcons
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** iOS uses the shared copies of Android's label and bookmark glyphs. */
@kotlin.native.HiddenFromObjC
class IosLabelIcons : ResourceLabelIcons() {
    @androidx.compose.runtime.Composable
    override fun ManageLabelIcon(name: String?, tint: androidx.compose.ui.graphics.Color, studyPadMode: Boolean) {
        androidx.compose.material3.Icon(
            org.jetbrains.compose.resources.painterResource(manageResource(name, studyPadMode)),
            contentDescription = null,
            modifier = androidx.compose.ui.Modifier.size(24.dp).testTag("label-glyph-${name ?: "default"}"),
            tint = tint,
        )
    }
}
