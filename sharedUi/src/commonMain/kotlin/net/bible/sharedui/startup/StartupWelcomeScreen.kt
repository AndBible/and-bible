/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.sharedui.startup

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.bible.sharedcore.startup.StartupWelcomeState
import net.bible.sharedcore.startup.StartupWelcomeTab
import net.bible.sharedui.components.AbLoadingIndicator
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.components.volumeVerticalScroll

/**
 * First-run welcome screen. English users get an Easy | Advanced switch: Easy is one recommended
 * path (Quick start, plus a redownload hint after a previous install); Advanced lists every way in.
 * Other locales have no curated defaults, so they see the Advanced list alone. All actions are host
 * seams; the tab lives in `StartupWelcomeController` ([onSelectTab]). No top app bar (this is a
 * launcher-context screen, like the classic splash which hides the action bar).
 */
@Composable
fun StartupWelcomeScreen(
    state: StartupWelcomeState,
    /**
     * F62: the header row classic opens with (`startup_view.xml:44-67`). Parameters rather than
     * resource lookups, like [net.bible.sharedui.reading.ReadingDrawerHeader]'s, so this file stays
     * iOS-clean. The host decides what they are -- including the discrete-mode swap.
     */
    appName: String,
    logo: Painter?,
    onSelectTab: (StartupWelcomeTab) -> Unit,
    onDownload: () -> Unit,
    onImport: () -> Unit,
    onRestore: () -> Unit,
    onRedownload: () -> Unit,
    onEasyStart: () -> Unit,
    onOpenHomepage: () -> Unit,
    onOpenGithub: () -> Unit,
) {
    val strings = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .volumeVerticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Rendered with Image, not Icon: ic_logo is a five-colour vector that Icon would flatten to a
        // single tint. Proportions from ReadingDrawerHeader, which A/B round 6 tuned against classic
        // (100dp min height, 24dp top padding, 48dp logo, 18sp bold) -- not classic's 75sp/weight-4
        // row, which that round already judged the worse of the two.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp).heightIn(min = 100.dp),
        ) {
            if (logo != null) {
                Image(painter = logo, contentDescription = null, modifier = Modifier.size(48.dp))
                Spacer(Modifier.size(12.dp))
            }
            Text(
                text = appName,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Text(
            strings.welcomeIntro,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.progressText != null) {
            AbLoadingIndicator(modifier = Modifier.fillMaxWidth())
            Text(state.progressText!!, style = MaterialTheme.typography.bodySmall)
        }

        if (state.showTabs) {
            val tabs = listOf(StartupWelcomeTab.EASY to strings.welcomeTabEasy, StartupWelcomeTab.ADVANCED to strings.welcomeTabAdvanced)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { index, (tab, label) ->
                    SegmentedButton(
                        selected = state.selectedTab == tab,
                        onClick = { onSelectTab(tab) },
                        shape = SegmentedButtonDefaults.itemShape(index, tabs.size),
                    ) { Text(label) }
                }
            }
        }

        when (state.selectedTab) {
            StartupWelcomeTab.EASY -> EasyContent(state, onEasyStart, onRedownload)
            StartupWelcomeTab.ADVANCED -> AdvancedList(state, onDownload, onImport, onRestore, onRedownload)
        }

        Text(state.versionText, style = MaterialTheme.typography.labelSmall)
        // Batch 6 A6: both open AndBible URLs, so discrete mode hides them.
        if (state.homepageButtonsVisible) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FooterLink(Icons.Outlined.Language, strings.welcomeHomepageLabel, onOpenHomepage)
                FooterLink(Icons.Outlined.Code, strings.welcomeGithubLabel, onOpenGithub)
            }
        }
    }
}

/** Easy: the Quick start card, and the redownload hint after a previous install. */
@Composable
private fun EasyContent(state: StartupWelcomeState, onEasyStart: () -> Unit, onRedownload: () -> Unit) {
    val strings = LocalStrings.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.welcomeQuickStartTitle, style = MaterialTheme.typography.titleMedium)
            Text(strings.welcomeQuickStartMessage, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onEasyStart, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(strings.welcomeQuickStartButton)
            }
        }
    }
    if (state.showRedownloadHint) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            ) {
                Icon(Icons.Outlined.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(12.dp))
                Text(
                    strings.welcomeRedownloadHint,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRedownload) { Text(strings.welcomeRedownloadHintAction) }
            }
        }
    }
}

/** Advanced: every way in, as one grouped list. Redownload leads when there is a previous install. */
@Composable
private fun AdvancedList(
    state: StartupWelcomeState,
    onDownload: () -> Unit,
    onImport: () -> Unit,
    onRestore: () -> Unit,
    onRedownload: () -> Unit,
) {
    val strings = LocalStrings.current
    val rows = buildList {
        if (state.showRedownload) add(WelcomeRow(Icons.Outlined.Restore, strings.welcomeRedownloadButton, strings.welcomeRedownloadRowHint, onRedownload))
        add(WelcomeRow(Icons.Outlined.CloudDownload, strings.welcomeDownloadButton, strings.welcomeDownloadRowHint, onDownload))
        add(WelcomeRow(Icons.Outlined.FolderOpen, strings.welcomeImportButton, state.supportedFormatsText, onImport))
        add(WelcomeRow(Icons.Outlined.SettingsBackupRestore, strings.welcomeRestoreButton, strings.welcomeRestoreRowHint, onRestore))
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(Modifier.padding(start = 56.dp))
                ListItem(
                    headlineContent = { Text(row.title) },
                    supportingContent = { Text(row.hint) },
                    leadingContent = { Icon(row.icon, contentDescription = null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable(role = Role.Button, onClick = row.onClick),
                )
            }
        }
    }
}

private class WelcomeRow(val icon: ImageVector, val title: String, val hint: String, val onClick: () -> Unit)

/** A compact icon + name link chip for the footer; the full URL stays out of the layout. */
@Composable
private fun FooterLink(icon: ImageVector, label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
    )
}
