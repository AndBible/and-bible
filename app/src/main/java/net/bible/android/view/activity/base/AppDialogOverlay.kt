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
package net.bible.android.view.activity.base

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.bible.android.activity.R
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.ai.AgentPermissionController
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedui.AbAppTheme
import net.bible.sharedui.components.AbLinkRouting
import net.bible.sharedui.components.AppDialogHost
import net.bible.sharedui.components.LocalNoticeIcons
import net.bible.sharedui.components.NoticeIcons
import org.koin.java.KoinJavaComponent

/**
 * Which host draws [AppDialogController]'s queue (spec D6, plan correction 3): the host that most
 * recently RESUMED, for as long as it stays STARTED. Exactly one owner at a time, so two live hosts
 * never both draw the same request, and a paused host keeps its dialog (and a half-typed field).
 */
object AppDialogRendering {
    private val mutableOwner = MutableStateFlow<Any?>(null)
    val owner: StateFlow<Any?> = mutableOwner.asStateFlow()
    fun claim(owner: Any) {
        mutableOwner.value = owner
    }
    fun release(owner: Any) {
        mutableOwner.compareAndSet(owner, null)
    }
}

/**
 * Every dialog-capable host renders this once (Task 5: `NavHostComposeActivity`,
 * `CalculatorComposeActivity`, and — via [mountAppDialogOverlay] — `StartupActivity` and
 * `ErrorActivity`). Draws nothing unless this host currently owns rendering (see
 * [AppDialogRendering]).
 *
 * `koinInject` (koin-compose) is not on `:app`'s classpath, so the two controllers are looked up
 * with `KoinJavaComponent.get` instead, same as everywhere else in `:app`.
 */
@Composable
fun AppDialogOverlay(onSheetOpening: () -> Unit = {}) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) = AppDialogRendering.claim(context)
            override fun onStop(owner: LifecycleOwner) = AppDialogRendering.release(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            AppDialogRendering.release(context)
        }
    }
    val renderingOwner by AppDialogRendering.owner.collectAsState()
    if (renderingOwner !== context) return

    val dialogs = remember { KoinJavaComponent.get<AppDialogController>(AppDialogController::class.java) }
    val permissions = remember { KoinJavaComponent.get<AgentPermissionController>(AgentPermissionController::class.java) }
    val shown by dialogs.pending.collectAsState()
    val progress by dialogs.progress.collectAsState()
    val permission by permissions.pending.collectAsState()
    // The two drawables an AppDialogRequest.Notice can draw (AbNoticeDialog's title logo, body
    // Logo block and money IconLine) -- sharedUi stays resource-free, so this is the one place
    // that resolves them for real via painterResource.
    val noticeIcons = NoticeIcons(
        logo = painterResource(R.drawable.ic_logo),
        money = painterResource(R.drawable.baseline_attach_money_24),
    )
    CompositionLocalProvider(LocalNoticeIcons provides noticeIcons) {
        AppDialogHost(
            shown = shown,
            permission = permission,
            onRespond = dialogs::respond,
            onPermissionChoice = permissions::respond,
            onPermissionDismiss = permissions::dismiss,
            onOpenExternal = { CommonUtils.openLinkNow(it) },
            askBeforeOpeningLink = CommonUtils.isDiscrete,
            onSheetOpening = onSheetOpening,
            progress = progress,
        )
    }
}

/**
 * Task 31 addendum item 3 (correction 12, parked in run 2): provided once at every Compose host
 * root, so a FUTURE dialog/sheet that forgets its own [AbLinkRouting] wrap still asks before leaving
 * the app in discrete mode — fail CLOSED — instead of silently falling through to the platform's
 * bare `LocalUriHandler` (see [net.bible.sharedui.components.LocalAbLinkOpener]'s kdoc). A dialog
 * that already wraps itself (`ComposeReadingViewHost`'s `SpeakSettingsSlot`/`ReadingDialogSlot`, or
 * `AppDialogHost` itself via `askBeforeOpeningLink`) simply re-provides [LocalAbLinkOpener] again,
 * one level deeper — harmless, since only the innermost provider for a given link's composition
 * governs.
 */
@Composable
fun FailClosedLinkRouting(content: @Composable () -> Unit) =
    AbLinkRouting(askFirst = CommonUtils.isDiscrete, onOpenExternal = { CommonUtils.openLinkNow(it) }, content = content)

/**
 * For the two hosts with no Compose content of their own — the View-based `StartupActivity` and the
 * UI-less `ErrorActivity`: a transparent `ComposeView` over the content. Call after
 * `setContentView` (or, with no content view, anywhere in `onCreate`). Draws nothing and has no
 * clickable node while the queue is empty, so it never intercepts touches meant for the content
 * beneath it.
 */
fun ComponentActivity.mountAppDialogOverlay() {
    addContentView(
        ComposeView(this).apply { setContent { AbAppTheme { FailClosedLinkRouting { AppDialogOverlay() } } } },
        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
    )
}
