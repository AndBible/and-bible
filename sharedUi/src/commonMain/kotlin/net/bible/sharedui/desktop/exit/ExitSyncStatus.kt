package net.bible.sharedui.desktop.exit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.theme.LocalDisableAnimations

@Composable
fun ExitSyncStatus(closing: Boolean, onQuitNow: () -> Unit, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    var quitRequested by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (LocalDisableAnimations.current) {
            Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(40.dp).testTag("exitSyncStaticIcon"),
                tint = MaterialTheme.colorScheme.primary)
        } else if (LocalInspectionMode.current) {
            CircularProgressIndicator(progress = { 0.5f }, modifier = Modifier.size(40.dp).testTag("exitSyncSpinner"))
        } else {
            CircularProgressIndicator(modifier = Modifier.size(40.dp).testTag("exitSyncSpinner"))
        }
        Text(
            text = if (closing || quitRequested) strings.exitSyncClosing else strings.exitSyncTitle,
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = strings.exitSyncBody,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        TextButton(
            onClick = { if (!quitRequested && !closing) { quitRequested = true; onQuitNow() } },
            enabled = !closing && !quitRequested,
            modifier = Modifier.padding(top = 16.dp),
        ) { Text(strings.exitSyncQuitNow) }
    }
}
