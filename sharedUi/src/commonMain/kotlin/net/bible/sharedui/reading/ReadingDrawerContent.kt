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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.reading.DrawerMenuState

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
                        modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 4.dp),
                    )
                } else if (index > 0) {
                    Spacer(Modifier.height(8.dp))
                }
                for (item in group.items) {
                    NavigationDrawerItem(
                        label = { Text(item.label) },
                        icon = {
                            icon(item.iconKey)?.let {
                                Icon(painter = it, contentDescription = null)
                            }
                        },
                        selected = false,
                        onClick = { if (item.enabled) onItemClick(item.id) },
                        colors = if (item.enabled) {
                            NavigationDrawerItemDefaults.colors()
                        } else {
                            NavigationDrawerItemDefaults.colors(
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            )
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
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
 * The drawer header: logo + app name, mirroring `nav_header_main.xml`.
 *
 * [logo] is rendered with [Image], not `Icon`: classic `nav_header_main.xml` shows the app logo in
 * an untinted `ImageView`, and `res/drawable/ic_logo.xml` is a five-colour vector (`#4d4c4c`,
 * `#7b4639`, `#FEBA2A`, `#d97e35`, `#df983b`). `Icon` would flatten it to a single tint colour — a
 * real visual regression — so the logo uses [Image] while the single-colour 24dp menu-row glyphs
 * below keep using `Icon` (matching both the classic `NavigationView` item tint and the M3
 * `NavigationDrawerItem` default).
 */
@Composable
fun ReadingDrawerHeader(appName: String, logo: Painter?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
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
