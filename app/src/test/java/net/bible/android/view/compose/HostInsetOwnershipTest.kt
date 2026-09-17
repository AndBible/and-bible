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

import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.ActivityBase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The content-root inset padding that `ActivityBase.setupUi()` applies on API 35+, and the two
 * hosts' ownership of it.
 *
 * `@Config(sdk = [35])` is load-bearing and must not be relaxed to `TEST_SDK` (33): the padding
 * listener at `ActivityBase.kt:144` is inside an `SDK_INT >= VANILLA_ICE_CREAM` branch, so at 33
 * there is nothing to measure and every assertion here would pass on a tree with the defect.
 *
 * Robolectric does not synthesise system-bar insets, so these tests dispatch their own. The numbers
 * (80 top / 39 bottom) are the ones measured on the maintainer's device in the spec's §1.2; any
 * non-zero pair would do.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [35])
class HostInsetOwnershipTest {

    /** A host that takes `ActivityBase`'s default: `setupUi()` runs, including the padding. */
    class BasePaddedProbeActivity : ActivityBase() {
        override val doNotInitializeApp = true
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContent { }
        }
    }

    private fun dispatchSystemBars(root: ViewGroup) {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 80, 0, 39))
            .build()
        ViewCompat.dispatchApplyWindowInsets(root, insets)
    }

    private fun contentRootOf(activity: ActivityBase): ViewGroup =
        activity.findViewById(android.R.id.content)

    @Test
    fun baseSetupUiPadsTheContentRootOnApi35() {
        val activity = Robolectric.buildActivity(BasePaddedProbeActivity::class.java).setup().get()
        val root = contentRootOf(activity)
        dispatchSystemBars(root)
        assertEquals("ActivityBase.setupUi() must pad the content root by the status bar", 80, root.paddingTop)
        assertEquals("ActivityBase.setupUi() must pad the content root by the navigation bar", 39, root.paddingBottom)
    }
}
