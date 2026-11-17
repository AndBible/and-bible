package net.bible.sharedui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.settings.TextSettingRowValue
import net.bible.sharedcore.settings.TextSettingType
import net.bible.sharedui.components.choiceFontFamily
import net.bible.sharedui.strings.LocalStrings

/** Shared numeric editor body: the preview observes local drag state, while the sheet owns OK/Cancel. */
@Composable
fun NumericTextSettingContent(
    type: TextSettingType,
    numeric: TextSettingRowValue.Numeric,
    fontSize: Int,
    lineSpacing: Int,
    font: SettingsItem.ChoiceFont?,
    onValueChange: (Int) -> Unit,
) {
    var current by remember(type, numeric.value, numeric.min, numeric.max) { mutableIntStateOf(numeric.value) }
    Column {
        NumericSliderContent(numeric = numeric, onValueChange = { current = it; onValueChange(it) })
        if (type == TextSettingType.FONTSIZE || type == TextSettingType.LINE_SPACING) {
            val size = if (type == TextSettingType.FONTSIZE) current else fontSize
            val spacing = if (type == TextSettingType.LINE_SPACING) current else lineSpacing
            val family = remember(font) { choiceFontFamily(font) }
            val strings = LocalStrings.current
            // A fixed, scrollable viewport keeps the action row stationary even at 60 pt / 3x spacing.
            // Clip the viewport as well: scrolling text must never paint over the slider or actions.
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                .height(120.dp).clipToBounds().verticalScroll(rememberScrollState())) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontSize = (size * 0.7f).sp)) {
                            append(strings.fontPreviewVerseNumber)
                        }
                        append(" ")
                        append(strings.fontPreviewVerse)
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = size.sp,
                        lineHeight = (size * spacing / 10f).sp,
                        fontFamily = family,
                    ),
                )
            }
        }
    }
}
