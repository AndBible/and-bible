package net.bible.sharedui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.bible.sharedui.theme.LocalIsDarkTheme
import net.bible.sharedui.theme.isPureMonochrome
import net.bible.sharedui.theme.monoBorder
import net.bible.sharedui.theme.monoInk

/**
 * THE sheet (fix batch 5 F106). Each material3 1.4.0 ModalBottomSheet is its own full-screen dialog
 * window whose bars default to visible, so `hide_status_bar` and fullscreen leaked through every sheet.
 * This mirrors the host's bar state into that window. `SheetSystemBarsGuardTest` keeps every sheet on it.
 * It does not touch `contentWindowInsets` (`SheetImeInsetGuardTest`, fix batch 2 C1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    properties: ModalBottomSheetProperties = ModalBottomSheetDefaults.properties,
    content: @Composable ColumnScope.() -> Unit,
) {
    val mono = isPureMonochrome()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.monoBorder(BottomSheetDefaults.ExpandedShape),
        sheetState = sheetState,
        properties = properties,
        containerColor = if (mono) MaterialTheme.colorScheme.surface else BottomSheetDefaults.ContainerColor,
        scrimColor = if (mono) Color.Transparent else BottomSheetDefaults.ScrimColor,
        // M3 1.4.0 defaults tonalElevation to 0.dp and Surface's shadowElevation to 0.dp.
        tonalElevation = 0.dp,
        dragHandle = {
            if (mono) BottomSheetDefaults.DragHandle(color = monoInk(LocalIsDarkTheme.current))
            else BottomSheetDefaults.DragHandle()
        },
    ) {
        MirrorHostSystemBars()
        if (mono) NoDialogNavigationContrast()
        content()
    }
}
