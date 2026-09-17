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
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.backup.BackupComposeActivity
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.applyComposeHostWindowSetup
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.navigation.ChooseDictionaryWordComposeActivity
import net.bible.android.view.activity.navigation.ChooseDocumentComposeActivity
import net.bible.android.view.activity.navigation.GridChoosePassageComposeActivity
import net.bible.android.view.activity.navigation.genbookmap.ChooseGeneralBookKeyComposeActivity
import net.bible.android.view.activity.navigation.genbookmap.ChooseMapKeyComposeActivity
import net.bible.android.view.activity.settings.TextDisplaySettingsComposeActivity
import net.bible.android.view.activity.workspaces.WorkspaceSelectorComposeActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The content-root inset padding that `ActivityBase.setupUi()` applies on API 35+, and the nine
 * Compose hosts' (host-inset-ownership fix round 1, Critical 1) ownership of it.
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

    /** A host shaped like the two real Compose hosts after this task. */
    class ComposeHostProbeActivity : ActivityBase() {
        override val doNotInitializeApp = true
        override val disableBaseSetupUi = true
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            applyComposeHostWindowSetup()
            setContent { }
        }
    }

    @Test
    fun aComposeHostDoesNotPadItsContentRoot() {
        val activity = Robolectric.buildActivity(ComposeHostProbeActivity::class.java).setup().get()
        val root = contentRootOf(activity)
        dispatchSystemBars(root)
        assertEquals("a Compose host must not pad its content root -- the scaffolds own the inset", 0, root.paddingTop)
        assertEquals("a Compose host must not pad its content root -- the scaffolds own the inset", 0, root.paddingBottom)
    }

    /**
     * `applyComposeHostWindowSetup()`'s effects are mostly not independently observable under
     * Robolectric. Two were tried and rejected before this one:
     * - `window.attributes.layoutInDisplayCutoutMode` -- the app theme
     *   (`values-v27/barstyles.xml`'s `android:windowLayoutInDisplayCutoutMode = shortEdges`)
     *   already sets this to `SHORT_EDGES` on every window, helper or not, so the assertion
     *   cannot fail; confirmed by running it with the helper call physically removed, which still
     *   passed.
     * - `enableEdgeToEdge()` / `WindowCompat.setDecorFitsSystemWindows()` reach into framework
     *   internals Robolectric does not shadow in an inspectable way.
     *
     * The one that IS genuinely observable: the O+ branch ORs `SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR`
     * into `window.decorView.systemUiVisibility` directly (day mode only) -- nothing else in this
     * probe's path sets that legacy flag, so its absence/presence tracks the helper having run.
     * Confirmed failing with the helper call removed (see the fix report's RED evidence).
     */
    @Test
    fun aComposeHostAppliesTheSharedWindowSetup() {
        val activity = Robolectric.buildActivity(ComposeHostProbeActivity::class.java).setup().get()
        assertEquals(
            "applyComposeHostWindowSetup() must request a light (dark-icon) navigation bar in day mode",
            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR,
            activity.window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR,
        )
    }

    /**
     * The registry of every `ActivityBase` subclass whose Compose content reaches `AbScaffold` /
     * `AbSelectionScaffold` (directly, or through a wrapper like `AbDocumentListScaffold` or
     * `AbSettingsScreen`) -- fix round 1, Critical 1's full include list. `StartupComposeActivity`,
     * `CalculatorComposeActivity` and `InstallZipComposeActivity` are the excluded Compose hosts:
     * their content uses no `Ab*` scaffold (the first two render a bare screen, the third only
     * `AbConfirmDialog`/`AbErrorDialog`), so they still want `ActivityBase`'s content-root padding
     * and must NOT appear here.
     */
    private val composeHostsOwningTheirInsets: List<Pair<String, ActivityBase>> = listOf(
        "NavHostComposeActivity" to NavHostComposeActivity(),
        "BackupComposeActivity" to BackupComposeActivity(),
        "ChooseDocumentComposeActivity" to ChooseDocumentComposeActivity(),
        "ChooseDictionaryWordComposeActivity" to ChooseDictionaryWordComposeActivity(),
        "GridChoosePassageComposeActivity" to GridChoosePassageComposeActivity(),
        "ChooseGeneralBookKeyComposeActivity" to ChooseGeneralBookKeyComposeActivity(),
        "ChooseMapKeyComposeActivity" to ChooseMapKeyComposeActivity(),
        "TextDisplaySettingsComposeActivity" to TextDisplaySettingsComposeActivity(),
        "WorkspaceSelectorComposeActivity" to WorkspaceSelectorComposeActivity(),
    )

    @Test
    fun everyComposeHostOnTheIncludeListDisablesTheBaseSetup() {
        for ((name, host) in composeHostsOwningTheirInsets) {
            assertTrue("$name must own its window setup", host.disableBaseSetupUi)
        }
    }
}
