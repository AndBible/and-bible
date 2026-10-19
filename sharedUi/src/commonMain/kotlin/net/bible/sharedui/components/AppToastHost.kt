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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import net.bible.sharedui.theme.LocalDisableAnimations

/**
 * The in-app toast source on platforms without a system toast (iOS): [show] from any thread, and an [AppToastHost]
 * in the app's composition draws it. One toast at a time; a newer one replaces the shown one and restarts the timer.
 */
@kotlin.native.HiddenFromObjC
object AppToasts {
    /** How long a short toast stays up (Android's `Toast.LENGTH_SHORT` is about 2 s; a little longer here for reading). */
    const val SHORT_MILLIS: Long = 2500L

    /** How long a long toast stays up. */
    const val LONG_MILLIS: Long = 4000L

    /** A toast; [id] is unique per [show] so that the same text twice still restarts the timer. */
    data class Toast(val id: Long, val text: String, val long: Boolean)

    private val current = MutableStateFlow<Toast?>(null)

    /** The toast to draw now, or null. */
    val state: StateFlow<Toast?> get() = current

    /** Shows [text]; blank text is ignored. Safe from any thread. */
    fun show(text: String, long: Boolean = false) {
        if (text.isBlank()) return
        current.update { Toast(id = (it?.id ?: 0L) + 1L, text = text, long = long) }
    }

    /** Removes [toast] if it is still the one shown (the host calls this when its time is up). */
    fun dismiss(toast: Toast) {
        current.compareAndSet(toast, null)
    }

    /** Drops any toast (tests). */
    fun clear() {
        current.value = null
    }
}

/**
 * Draws [AppToasts]: a snackbar-style pill at the bottom of the screen, announced to the screen reader as a polite live
 * region. Colours come from the theme's inverse pair, so monochrome mode stays grayscale. With animations disabled
 * ([LocalDisableAnimations]) it appears and disappears without a fade.
 */
@kotlin.native.HiddenFromObjC
@Composable
fun AppToastHost(modifier: Modifier = Modifier) {
    val toast by AppToasts.state.collectAsState()
    val disableAnimations = LocalDisableAnimations.current
    // Kept while fading out so the text does not vanish before the fade ends.
    var shown by remember { mutableStateOf<AppToasts.Toast?>(null) }
    val visible = toast != null
    LaunchedEffect(toast) { toast?.let { shown = it } }

    LaunchedEffect(toast?.id) {
        val t = toast ?: return@LaunchedEffect
        delay(if (t.long) AppToasts.LONG_MILLIS else AppToasts.SHORT_MILLIS)
        AppToasts.dismiss(t)
    }

    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 72.dp), contentAlignment = Alignment.BottomCenter) {
        val content = toast ?: shown
        if (content != null) {
            if (disableAnimations) {
                if (visible) ToastPill(content.text)
            } else {
                AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) { ToastPill(content.text) }
            }
        }
    }
}

@Composable
private fun ToastPill(text: String) {
    Surface(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp,
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
