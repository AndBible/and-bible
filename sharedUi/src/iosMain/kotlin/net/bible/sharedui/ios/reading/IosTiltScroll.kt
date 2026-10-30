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
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.ios.reading

import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.bible.android.control.page.PageTiltScrollControl
import net.bible.android.control.page.TiltScrollDriver
import net.bible.android.control.page.TiltSensor
import net.bible.android.control.page.window.WindowControl
import net.bible.sharedcore.log.nsLog
import net.bible.sharedui.reading.TiltScreenRotation
import net.bible.sharedui.reading.tiltPitchDegrees
import net.bible.sharedui.webview.IosReadingWebViews
import org.koin.core.Koin
import platform.CoreGraphics.CGPointMake
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationState
import platform.UIKit.UIInterfaceOrientationLandscapeLeft
import platform.UIKit.UIInterfaceOrientationLandscapeRight
import platform.UIKit.UIInterfaceOrientationPortraitUpsideDown
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIWindowScene
import kotlin.math.max
import net.bible.sharedui.host.reading.TiltScrollSession

/** The app's one `CMMotionManager` (Apple: create a single instance per app; the overflow menu probes it too). */
internal val iosMotionManager: CMMotionManager by lazy { CMMotionManager() }

/**
 * The orientation of the foreground window scene's interface, or portrait when there is none. Read per sample, so a
 * rotation switches the tilt axis at once ([tiltPitchDegrees]).
 */
private fun currentTiltScreenRotation(): TiltScreenRotation {
    val scene = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
        .firstOrNull { it.activationState == UISceneActivationStateForegroundActive }
        ?: UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>().firstOrNull()
    return when (scene?.interfaceOrientation) {
        UIInterfaceOrientationPortraitUpsideDown -> TiltScreenRotation.PortraitUpsideDown
        UIInterfaceOrientationLandscapeLeft -> TiltScreenRotation.LandscapeLeft
        UIInterfaceOrientationLandscapeRight -> TiltScreenRotation.LandscapeRight
        else -> TiltScreenRotation.Portrait
    }
}

/**
 * [TiltSensor] over CoreMotion device motion. iOS reports the pitch as +90 degrees for a device held upright in
 * portrait and 0 flat; the common control expects Android's convention (upright = -90, tilting the top of the device
 * forward = more negative), and a pitch already corrected for the screen rotation. [tiltPitchDegrees] does both: the sign
 * flip, and in landscape the `roll` axis instead of the `pitch` (the device's x axis then no longer runs along the
 * reading direction). The rotation is read from the window scene per sample ([rotation]).
 *
 * Reading device motion needs no authorisation prompt on iOS (only activity/pedometer data does), so a missing
 * `NSMotionUsageDescription` cannot crash this; the key is still declared in Info.plist. [stop] is idempotent and
 * is what the lifecycle hook calls when the app resigns active, so no motion updates run in the background.
 */
@kotlin.native.HiddenFromObjC
class IosTiltSensor(
    private val manager: CMMotionManager = iosMotionManager,
    private val rotation: () -> TiltScreenRotation = ::currentTiltScreenRotation,
) : TiltSensor {
    override val isAvailable: Boolean get() = manager.deviceMotionAvailable

    override fun start(onSample: (pitch: Float) -> Unit) {
        if (!manager.deviceMotionAvailable) return
        if (manager.deviceMotionActive) manager.stopDeviceMotionUpdates()
        manager.deviceMotionUpdateInterval = UPDATE_INTERVAL_SECONDS
        manager.startDeviceMotionUpdatesToQueue(NSOperationQueue.mainQueue) { motion, _ ->
            val attitude = motion?.attitude ?: return@startDeviceMotionUpdatesToQueue
            onSample(tiltPitchDegrees(attitude.pitch, attitude.roll, rotation()))
        }
    }

    override fun stop() {
        if (manager.deviceMotionActive) manager.stopDeviceMotionUpdates()
    }

    private companion object {
        const val UPDATE_INTERVAL_SECONDS = 1.0 / 20.0
    }
}

/**
 * Tilt scroll for the reading view: one [PageTiltScrollControl] on an [IosTiltSensor], driving the active window's
 * WebView scroll view one point at a time. [setAppActive] must follow the app lifecycle so the sensor is off in the
 * background. Android parity gap: no touch-to-recalibrate yet (Android resets the neutral pose on touch).
 */
@kotlin.native.HiddenFromObjC
class IosTiltScroll(private val koin: Koin, sensor: TiltSensor = IosTiltSensor()) : TiltScrollSession {
    private val control = PageTiltScrollControl(sensor) {
        koin.get<WindowControl>().windowRepository.workspaceSettings.enableTiltToScroll
    }
    private val driver = TiltScrollDriver(control, ::scrollActiveWindow)
    private var job: Job? = null

    override fun setAppActive(active: Boolean) = driver.setAppActive(active)

    /** Starts the polling loop on [scope] (the main dispatcher). */
    override fun start(scope: CoroutineScope) {
        job?.cancel()
        driver.setAppActive(UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive)
        job = scope.launch {
            while (isActive) {
                val wait = try {
                    driver.step()
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    nsLog("IosTiltScroll: step failed: ${e.stackTraceToString()}")
                    POLL_AFTER_FAILURE_MS
                }
                delay(wait)
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        driver.setAppActive(false)
    }

    private fun scrollActiveWindow(forward: Boolean, pixels: Int): Boolean {
        val id = koin.get<WindowControl>().activeWindow.id.toString()
        if (!IosReadingWebViews.hasEntry(id)) return false
        val scrollView = IosReadingWebViews.webViewFor(id).scrollView
        val x = scrollView.contentOffset.useContents { x }
        val y = scrollView.contentOffset.useContents { y }
        val content = scrollView.contentSize.useContents { height }
        val visible = scrollView.bounds.useContents { size.height }
        val maxY = max(0.0, content - visible)
        val target = if (forward) y + pixels else y - pixels
        if (target < 0.0 || target > maxY) return false
        scrollView.setContentOffset(CGPointMake(x, target), animated = false)
        return true
    }

    private companion object {
        const val POLL_AFTER_FAILURE_MS = 500L
    }
}
