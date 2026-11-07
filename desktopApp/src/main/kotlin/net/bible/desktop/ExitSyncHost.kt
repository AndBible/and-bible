package net.bible.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.sharedcore.platform.AppSettings
import net.bible.service.common.THEME_PALETTE_STYLE_KEY
import net.bible.sharedlogic.platform.NightModeState
import net.bible.sharedui.assembly.HostStringTableHolder
import net.bible.sharedui.desktop.assembly.DesktopUiLanguage
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbHostTheme
import org.koin.mp.KoinPlatform
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import net.bible.sharedcore.platform.desktop.DesktopExitTasks
import net.bible.sharedui.desktop.exit.ExitSyncStatus

private data class ExitAppearance(val settings: AppSettings, val dark: Boolean, val language: String)

/** Cache theme inputs off the UI thread before persistence starts. */
@Composable
internal fun ExitSyncWindow(closing: Boolean, language: String, skipRequested: Boolean, onSkip: () -> Unit, content: @Composable () -> Unit) {
    val appearance by produceState<ExitAppearance?>(null, closing) {
        if (!closing) {
            while (true) {
                val next = withContext(Dispatchers.IO) {
                    runCatching {
                        val koin = KoinPlatform.getKoin()
                        val source = koin.get<AppSettings>()
                        val color = source.getString("display_color_mode", null)
                        val palette = source.getString(THEME_PALETTE_STYLE_KEY, null)
                        val animations = source.getBoolean("disable_animations", false)
                        val cached = object : AppSettings by source {
                            override fun getString(key: String, default: String?): String? = when (key) {
                                "display_color_mode" -> color ?: default
                                THEME_PALETTE_STYLE_KEY -> palette ?: default
                                else -> default
                            }
                            override fun getBoolean(key: String, default: Boolean): Boolean =
                                if (key == "disable_animations") animations else default
                        }
                        ExitAppearance(cached, koin.get<NightModeState>().nightMode,
                            koin.getOrNull<HostStringTableHolder>()?.current?.tag ?: language)
                    }.getOrNull()
                }
                if (next != null) value = next
                delay(500)
            }
        }
    }
    if (!closing) content() else {
        val current = appearance
        if (current != null) {
            val strings = remember(current.language) { iosStrings(current.language) }
            val direction = if (DesktopUiLanguage.isRtl(current.language)) LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(LocalStrings provides strings, LocalLayoutDirection provides direction) {
                AbHostTheme(current.dark, current.settings, false, { null }) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        ExitSyncHost(KoinPlatform.getKoinOrNull()?.getOrNull(), skipRequested = skipRequested, onSkip = onSkip)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ExitSyncHost(tasks: DesktopExitTasks?, showAfterMs: Long = 400, skipRequested: Boolean = false, onSkip: () -> Unit = { tasks?.skipRemaining() }) {
    if (tasks == null) return
    val syncing by tasks.syncing.collectAsState()
    var visible by remember(tasks) { mutableStateOf(false) }
    var quitting by remember(tasks) { mutableStateOf(false) }
    LaunchedEffect(skipRequested) { if (skipRequested) quitting = true }
    LaunchedEffect(tasks, syncing) {
        if (syncing) {
            delay(showAfterMs)
            visible = true
        } else if (!quitting) {
            visible = false
        }
    }
    if ((visible && syncing) || quitting) ExitSyncStatus(closing = quitting, onQuitNow = {
        if (!quitting) {
            quitting = true
            onSkip()
        }
    })
}
