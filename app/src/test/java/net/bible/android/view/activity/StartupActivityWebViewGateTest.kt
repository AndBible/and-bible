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
package net.bible.android.view.activity

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Task 29 (Startup:112): [StartupActivity] itself could not be built under Robolectric (run-1
 * note), so [webViewTooOldRequest] -- the pure branch-mapping `checkWebView` now delegates to --
 * is tested directly instead, covering exactly the branches the old `AlertDialog.Builder` version
 * had: Huawei's non-Chromium versioning, an unparseable version string, at/above the minimum, and
 * below it (the request that reaches [net.bible.sharedcore.ui.dialog.AppDialogController]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class StartupActivityWebViewGateTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun huaweiWebViewIsNeverChecked() {
        assertNull(webViewTooOldRequest(context, "com.huawei.webview", "60.0"))
    }

    @Test
    fun anUnparseableVersionIsNotChecked() {
        assertNull(webViewTooOldRequest(context, "com.google.android.webview", null))
        assertNull(webViewTooOldRequest(context, "com.google.android.webview", "not-a-version"))
    }

    @Test
    fun atOrAboveTheMinimumVersionProceedsSilently() {
        assertNull(webViewTooOldRequest(context, "com.google.android.webview", "$MINIMUM_WEBVIEW_MAJOR_VERSION.0.1234.0"))
        assertNull(webViewTooOldRequest(context, "com.google.android.webview", "${MINIMUM_WEBVIEW_MAJOR_VERSION + 5}.0.1234.0"))
    }

    /**
     * Below the minimum: the same [R.string.old_webview] HTML (with the Play Store link), the same
     * "Proceed anyway"/"Close" button texts as the old dialog, and `cancellable = false` (the old
     * `setCancelable(false)`) so back/scrim can't silently bypass the warning.
     */
    @Test
    fun belowTheMinimumVersionRaisesTheWarning() {
        val oldVersion = MINIMUM_WEBVIEW_MAJOR_VERSION - 1
        val request = webViewTooOldRequest(context, "com.google.android.webview", "$oldVersion.0.1234.0")!!

        assertFalse(request.cancellable)
        assertEquals(context.getString(R.string.proceed_anyway), request.confirmText)
        assertEquals(context.getString(R.string.close), request.dismissText)
        assertTrue(
            "message must carry the Play Store link for the WebView package",
            request.message!!.contains("https://play.google.com/store/apps/details?id=com.google.android.webview"),
        )
        assertTrue(request.message!!.contains("$oldVersion.0.1234.0"))
    }

    @Test
    fun aVersionWithATrailingSuffixParsesItsLeadingNumber() {
        // e.g. "83 (a beta build)" -- the old code split on the first "." then the first " ".
        assertNull(webViewTooOldRequest(context, "com.google.android.webview", "$MINIMUM_WEBVIEW_MAJOR_VERSION (stable)"))
    }
}
