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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
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
    private var navigationJob: Job? = null

    /**
     * Obtain the ViewModel from the activity's ViewModelStore so its [androidx.lifecycle.viewModelScope]
     * (and the verse-text flow collector started in `init`) are cancelled when the activity is
     * destroyed. Manually instantiating the ViewModel — as an earlier draft did — would skip
     * `onCleared()` and leak the collector across activity recreation.
     *
     * The launcher itself is recreated alongside the activity, so this `lazy` only ever wraps
     * one activity's ViewModelStore.
     */
    private val viewModel: PassageFinderViewModel by lazy {
        ViewModelProvider(
            activity,
            PassageFinderViewModelFactory(
                PassageFinderDataSource(navigationControl, pageControl)
            ),
        )[PassageFinderViewModel::class.java]
    }

    /**
     * Open the passage finder overlay.
     *
     * @return true if the widget was actually shown; false if the active module has no
     *   books to navigate (in which case the caller should fall back to the legacy chooser).
     */
    fun show(): Boolean {
        val vm = viewModel
        vm.show()
        if (!vm.uiState.value.visible) {
            // ViewModel.show() refused — typically because the active module yields no books.
            // Skip showing the ComposeView so the caller can fall back to the legacy chooser
            // rather than presenting an empty overlay.
            return false
        }

        ensureComposeView()
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
        return true
    }

    fun hide() {
        navigationJob?.cancel()
        navigationJob = null
        // Sync the ViewModel state with the hidden view. In normal flow the widget
        // already calls dismiss()/confirmSelection() before invoking onDismiss, but
        // hide() can also be called externally (e.g. on back press), so be defensive.
        viewModel.dismiss()
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
