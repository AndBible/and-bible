package net.bible.sharedui.ios.startup.legacy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.bible.sharedui.strings.LocalStrings

/** A fast run must never flash this screen: nothing but the background shows for this long. */
internal const val LEGACY_PROGRESS_DELAY_MS = 400L

/**
 * Shown while the legacy migration runs. No app name and no image, so the discrete build stays unrevealing:
 * [neutral] picks the neutral wording. The caller wraps it in `AbTheme(darkTheme = splashDark)`.
 */
@Composable
internal fun LegacyMigrationProgressScreen(neutral: Boolean) {
    val strings = LocalStrings.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LEGACY_PROGRESS_DELAY_MS)
        visible = true
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        if (visible) {
            Column(
                Modifier.testTag("legacy-progress").padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator()
                Text(
                    if (neutral) strings.legacyMigrationProgressNeutral else strings.legacyMigrationProgress,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
