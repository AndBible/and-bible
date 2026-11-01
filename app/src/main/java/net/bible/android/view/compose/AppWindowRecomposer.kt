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

package net.bible.android.view.compose

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.WindowRecomposerFactory
import androidx.compose.ui.platform.WindowRecomposerPolicy
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import net.bible.service.common.CommonUtils
import net.bible.sharedui.theme.AppMotionDurationScale

/**
 * Makes every Android Compose root honour the app's "Disable animations" setting (osTicket 3499).
 *
 * Each `ComposeView` / `setContent` host gets its recomposer from [WindowRecomposerPolicy]. The factory installed here
 * is Compose's own lifecycle-aware one, with an [AppMotionDurationScale] in the recomposer's coroutine context. That
 * context is the effect context of the whole composition, so `LaunchedEffect`, `rememberCoroutineScope`,
 * `animate*AsState`, transitions and the `ModalBottomSheet`/dialog/popup windows (which compose through the parent's
 * composition context) all see a zero duration scale while the setting is on. The setting is read live, so toggling
 * it needs no window rebuild.
 *
 * Providing our own scale replaces Compose's built-in observer of the system "Animator duration scale"; while the
 * app setting is off the system scale is still followed through [ValueAnimator.getDurationScale] (API 26+).
 */
object AppWindowRecomposer {
    val motionDurationScale = AppMotionDurationScale(
        animationsDisabled = { CommonUtils.settings.disableAnimations },
        systemScale = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ValueAnimator.getDurationScale() else 1f
        },
    )

    @OptIn(InternalComposeUiApi::class)
    private val factory = object : WindowRecomposerFactory {
        override fun createRecomposer(windowRootView: android.view.View) =
            windowRootView.createLifecycleAwareWindowRecomposer(motionDurationScale)
    }

    /** Installs the factory process-wide; call once from `Application.onCreate`. Idempotent. */
    @OptIn(InternalComposeUiApi::class)
    fun install() {
        WindowRecomposerPolicy.setFactory(factory)
    }
}
