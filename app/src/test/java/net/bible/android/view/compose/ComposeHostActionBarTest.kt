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
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Device-independent verification of the Batch-2..4 shared-host-template finding
 * (2026-07-13): the Compose host Activities that declare no `android:theme` in the
 * manifest inherit the app default theme `AppTheme`
 * (`Theme.AppCompat.DayNight.DarkActionBar`), which carries a *native* AppCompat
 * ActionBar. Because those hosts draw their own Compose `TopAppBar` on top, the
 * on-device result is a **double app bar** — a defect the golden-screenshot harness
 * cannot see (it renders the `:sharedUi` composable in isolation, never the hosted
 * Activity window).
 *
 * This probe reproduces the mechanism faithfully: [ActionBarProbeActivity] extends the
 * exact same [ActivityBase] every real host extends, is not declared in the manifest
 * (so Robolectric applies the app default `AppTheme` — identical to a theme-less host),
 * and calls `setContent {}` exactly as the real hosts do. Whether a native ActionBar is
 * created depends only on the window theme, not on app init or the Compose content, so
 * `doNotInitializeApp = true` keeps the test light without affecting what is measured.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ComposeHostActionBarTest {

    /** A theme-less Compose host stand-in: same base class + default theme as the real ones. */
    class ActionBarProbeActivity : ActivityBase() {
        override val doNotInitializeApp = true
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContent { /* real hosts put AbTheme { AbScaffold { ... } } here */ }
        }
    }

    /**
     * A Compose host stand-in themed with `Theme.AbCompose` — the shared NoActionBar window
     * theme now applied to every top-bar-bearing Compose host in the manifest. `setTheme` runs
     * before `super.onCreate` so AppCompat reads it exactly as it reads a manifest `android:theme`.
     */
    class AbComposeThemedProbeActivity : ActivityBase() {
        override val doNotInitializeApp = true
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.Theme_AbCompose)
            super.onCreate(savedInstanceState)
            setContent { }
        }
    }

    private inline fun <reified A : ActivityBase> build(): AppCompatActivity =
        Robolectric.buildActivity(A::class.java).create().get()

    @Test
    fun `theme-less ActivityBase host shows a native ActionBar (confirms double-app-bar finding)`() {
        val activity = build<ActionBarProbeActivity>()
        // A NoActionBar theme would make supportActionBar null; DarkActionBar makes it non-null.
        assertNotNull(
            "Theme-less Compose host inherits AppTheme (DarkActionBar) → a native ActionBar exists " +
                "above the Compose TopAppBar. If this is null, AppTheme changed to NoActionBar.",
            activity.supportActionBar,
        )
        assertTrue(
            "The inherited native ActionBar is actually shown, producing the double app bar on device.",
            activity.supportActionBar!!.isShowing,
        )
    }

    @Test
    fun `Theme_AbCompose host has no native ActionBar (guards the fix)`() {
        val activity = build<AbComposeThemedProbeActivity>()
        assertNull(
            "Theme.AbCompose sets windowActionBar=false → no native ActionBar, so the Compose " +
                "TopAppBar is the only bar. If this regresses to non-null, a host lost the theme " +
                "or the style's windowActionBar override was removed.",
            activity.supportActionBar,
        )
    }

    /** Stand-in for MyDocumentsComposeActivity's window theme (Theme.AbCompose, NoActionBar). */
    class MyDocumentsHostProbe : ActivityBase() {
        override val doNotInitializeApp = true
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.Theme_AbCompose); super.onCreate(savedInstanceState); setContent { }
        }
    }
    /** Stand-in for MyDocumentPagesComposeActivity's window theme. */
    class MyDocumentPagesHostProbe : ActivityBase() {
        override val doNotInitializeApp = true
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.Theme_AbCompose); super.onCreate(savedInstanceState); setContent { }
        }
    }

    @Test fun `MyDocuments host theme has no native ActionBar`() {
        assertNull(build<MyDocumentsHostProbe>().supportActionBar)
    }
    @Test fun `MyDocumentPages host theme has no native ActionBar`() {
        assertNull(build<MyDocumentPagesHostProbe>().supportActionBar)
    }

    // Nav-graph slice 4 Task 9: CloudDocumentsComposeActivity was deleted -- its `CloudDocuments`
    // destination has been hosted inside NavHostComposeActivity's own graph (via
    // `downloadNavGraph`) since Task 8, so its dedicated no-native-ActionBar probe folds into
    // `NavHostComposeActivity has no native ActionBar (Theme_AbCompose)` below, the same
    // consolidation nav-graph Task 10 did for the ten AI-cluster hosts.

    // Round 13a T4: BibleSpeakComposeActivity and SpeakSettingsComposeActivity were deleted (the
    // Compose Speak entry point moves to a bottom sheet over the reading view, Task 13) — their
    // no-native-ActionBar checks went with them.

    /** Stand-in for WorkspaceSelectorComposeActivity's window theme (Theme.AbCompose, NoActionBar). */
    class WorkspaceSelectorHostProbe : ActivityBase() {
        override val doNotInitializeApp = true
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.Theme_AbCompose); super.onCreate(savedInstanceState); setContent { }
        }
    }

    @Test fun `WorkspaceSelector host theme has no native ActionBar`() {
        assertNull(build<WorkspaceSelectorHostProbe>().supportActionBar)
    }

    /**
     * Nav-graph Task 10 deleted the ten classic AI-cluster host Activities that used to each carry
     * their own no-native-ActionBar test here (`AiConnectionSettingsComposeActivity` through
     * `RawLlmLogComposeActivity`, ten test functions). All ten AI screens now render as destinations
     * inside this ONE host, so their ten narrow probes collapse into this single one -- a coverage
     * CONSOLIDATION, not a coverage drop: every one of those ten screens is reachable only through
     * `NavHostComposeActivity`'s manifest-declared `Theme.AbCompose` now, so proving IT has no
     * native ActionBar proves the property for all ten (and every other nav-graph destination,
     * `CloudDocuments` among them since nav-graph slice 4 Task 9) at once, the same way
     * `InstallZipComposeActivity` below proves it for its own single manifest entry. Built with a
     * real route
     * ([NavRoutes.AI_CONNECTION_SETTINGS]) via [NavHostComposeActivity.intentFor] because `onCreate`
     * requires `EXTRA_ROUTE` and throws without it.
     */
    @Test fun `NavHostComposeActivity has no native ActionBar (Theme_AbCompose)`() {
        val activity = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(
                ApplicationProvider.getApplicationContext(),
                NavRoutes.AI_CONNECTION_SETTINGS,
            ),
        ).create().get()
        assertNull(
            "NavHostComposeActivity must use Theme.AbCompose (NoActionBar); a non-null " +
                "supportActionBar means the manifest entry lost the theme -> double app bar for " +
                "every nav-graph destination it hosts (all ten former AI-cluster screens included).",
            activity.supportActionBar,
        )
    }

    // Slice 8 D3: the `InstallZipComposeActivity has no native ActionBar (Theme_AbCompose)` probe above
    // this comment was deleted here (not rehosted). Deletion reason: it measured the InstallZip HOST's
    // own window theme, but `InstallZipComposeActivity` is now a plain `ComponentActivity` redirect that
    // draws nothing at all (no `setContent`, no theme-bearing content) -- there is no window left to have
    // an ActionBar in. The InstallZip UI is now a destination of `NavHostComposeActivity`, whose
    // action-bar-free `Theme.AbCompose` window is already pinned by
    // `NavHostComposeActivity has no native ActionBar (Theme_AbCompose)` above, which covers this
    // destination the same way it covers the ten former AI-cluster screens and CloudDocuments.
}
