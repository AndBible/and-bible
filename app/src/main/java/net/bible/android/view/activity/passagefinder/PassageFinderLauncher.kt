/*
 * Copyright (c) 2026 Andreas Brauchli and the AndBible contributors.
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

package net.bible.android.view.activity.passagefinder

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.bible.android.control.navigation.NavigationControl
import net.bible.android.control.page.PageControl
import net.bible.android.control.passagefinder.PassageFinderDataSource
import net.bible.android.view.activity.page.MainBibleActivity

/**
 * Manages the ComposeView lifecycle for the PassageFinder widget overlay.
 * Added as a child of the DrawerLayout so it floats above all content including toolbar.
 *
 * Per D-15: Built directly in MainBibleActivity, no standalone test activity.
 * Uses ViewCompositionStrategy.DisposeOnDetachedFromWindow to handle Activity lifecycle correctly.
 */
class PassageFinderLauncher(
    private val activity: MainBibleActivity,
    private val navigationControl: NavigationControl,
    private val pageControl: PageControl,
) {
    private var composeView: ComposeView? = null
    private var viewModel: PassageFinderViewModel? = null
    private var navigationJob: Job? = null

    fun show() {
        ensureComposeView()
        val vm = viewModel ?: PassageFinderViewModel(
            PassageFinderDataSource(navigationControl, pageControl)
        ).also { viewModel = it }

        vm.show()

        composeView?.setContent {
            PassageFinderWidget(
                viewModel = vm,
                onDismiss = { hide() },
            )
        }
        composeView?.visibility = View.VISIBLE
        composeView?.bringToFront()

        // Collect confirmed verse selections and navigate the Bible view
        navigationJob?.cancel()
        navigationJob = activity.lifecycleScope.launch {
            vm.selectionConfirmed.collect { verse ->
                pageControl.currentPageManager.currentBible.setKey(verse)
                hide()
            }
        }
    }

    fun hide() {
        navigationJob?.cancel()
        navigationJob = null
        composeView?.visibility = View.GONE
    }

    val isVisible: Boolean
        get() = composeView?.visibility == View.VISIBLE

    private fun ensureComposeView() {
        if (composeView != null) return
        composeView = ComposeView(activity).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnDetachedFromWindow
            )
        }
        // Add as overlay to drawerLayout (the DrawerLayout root) so it floats above
        // all content including toolbar and navigation drawer.
        val params = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        activity.binding.drawerLayout.addView(composeView, 1, params)
    }
}
