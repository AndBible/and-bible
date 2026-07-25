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

package net.bible.sharedui.reading

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.reading.DrawerMenuState

/**
 * Classic's drawer is `wrap_content` (`navigation_drawer_with_footer.xml:29`), i.e. as wide as its
 * widest row — ~300dp in practice — while M3's `ModalDrawerSheet` defaults to a fixed 360dp. A
 * fixed classic-scale width is used instead of reproducing `wrap_content`, so the drawer does not
 * change size with the longest translated label (which would also jump between LTR and RTL).
 *
 * These four constants are the density knobs: if a later A/B pass still reads as too roomy, tune
 * them here rather than sprinkling paddings through the tree.
 */
val ReadingDrawerWidth: Dp = 300.dp
private val DrawerRowHeight = 48.dp
private val DrawerRowHorizontalPadding = 16.dp
private val DrawerIconLabelGap = 16.dp
private val DrawerIconSize = 24.dp
private val DrawerGroupSpacing = 4.dp

/**
 * The reading view's main navigation drawer content — the Compose port of classic
 * `navigation_drawer_with_footer.xml` (`NavigationView` over `R.menu.main_bible_drawer_menu`
 * + `nav_header_main.xml` header + version-text footer).
 *
 * Stateless: the item list, labels and the version string all come from [state] (built host-side by
 * `DrawerMenuStateBuilder`), and icons are resolved through [icon] — the same host-lambda shape
 * `mountComposeView` uses for `windowIcon`, which keeps this file free of Android types.
 *
 * Captured directly by `ReadingDrawerGoldenTest`: never force-open the real `ModalNavigationDrawer`
 * in a Roborazzi capture, it hangs.
 */
@Composable
fun ReadingDrawerContent(
    state: DrawerMenuState,
    icon: @Composable (iconKey: String) -> Painter?,
    onItemClick: (id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            ReadingDrawerHeader(appName = state.appName, logo = icon("ic_logo"))
            state.groups.forEachIndexed { index, group ->
                val title = group.title
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = DrawerRowHorizontalPadding, top = 12.dp, bottom = 4.dp),
                    )
                } else if (index > 0) {
                    Spacer(Modifier.height(DrawerGroupSpacing))
                }
                for (item in group.items) {
                    DrawerRow(
                        label = item.label,
                        icon = icon(item.iconKey),
                        enabled = item.enabled,
                        onClick = { onItemClick(item.id) },
                    )
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
        Text(
            text = state.versionText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        )
    }
}

/**
 * One drawer row at classic `NavigationView` scale: 48dp tall, 16dp horizontal padding, 24dp icon,
 * 16dp icon→label gap — versus M3 `NavigationDrawerItem`'s 56dp plus an extra `ItemPadding`, which
 * read as too roomy next to classic (A/B feedback batch 1, F6). Disabled rows keep the 0.38 alpha
 * the previous `NavigationDrawerItemDefaults`-based colours used.
 *
 * Every real drawer row resolves an [icon] in production (`DrawerMenuStateBuilder`'s static table
 * gives every entry an `iconKey`, and `ComposeReadingViewHostTest.drawerIconResIdsCoverEveryBuilderIconKey`
 * guards that the host's lookup table resolves all of them) — a `null` [icon] only occurs in
 * `ReadingDrawerGoldenTest`'s icon-free captures, where it applies uniformly to every row, so there is
 * no icon/no-icon misalignment to guard against here (unlike [ReadingOverflowMenuRows], which mixes
 * icon-less dynamic rows into the same list as iconed static ones and must reserve a shared slot).
 */
@Composable
private fun DrawerRow(
    label: String,
    icon: Painter?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val contentColor =
        if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DrawerRowHeight)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = DrawerRowHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painter = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(DrawerIconSize))
            Spacer(Modifier.width(DrawerIconLabelGap))
        }
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = contentColor, maxLines = 1)
    }
}

/**
 * The drawer header: logo + app name, mirroring `nav_header_main.xml`.
 *
 * [logo] is rendered with [Image], not `Icon`: classic `nav_header_main.xml` shows the app logo in
 * an untinted `ImageView`, and `res/drawable/ic_logo.xml` is a five-colour vector (`#4d4c4c`,
 * `#7b4639`, `#FEBA2A`, `#d97e35`, `#df983b`). `Icon` would flatten it to a single tint colour — a
 * real visual regression — so the logo uses [Image] while the single-colour 24dp [DrawerRow] glyphs
 * below keep using `Icon`, tinted to match each row's label colour (including the disabled 0.38-alpha
 * treatment).
 */
@Composable
fun ReadingDrawerHeader(appName: String, logo: Painter?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = DrawerRowHorizontalPadding, end = DrawerRowHorizontalPadding, top = 12.dp, bottom = 4.dp),
    ) {
        if (logo != null) {
            Image(painter = logo, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(Modifier.size(12.dp))
        }
        Text(
            text = appName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
