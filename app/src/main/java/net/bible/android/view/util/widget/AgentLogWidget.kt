/*
 * Copyright (c) 2024-2026 Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.view.util.widget

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import net.bible.android.activity.R
import net.bible.android.activity.databinding.AgentLogWidgetBinding
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.IdType
import net.bible.android.view.activity.ai.AgentLogAdapter
import net.bible.service.device.ScreenSettings
import net.bible.android.view.util.UiUtils
import net.bible.service.common.CommonUtils
import net.bible.service.common.CommonUtils.buildActivityComponent
import net.bible.service.llm.agent.AgentLogEntry
import net.bible.service.llm.agent.AgentLogUpdatedEvent
import net.bible.service.llm.agent.AgentSessionManager
import net.bible.service.llm.agent.AgentSessionStatusChangedEvent
import net.bible.service.llm.agent.LogEntryType
import javax.inject.Inject

/**
 * Event posted when the agent log widget visibility changes.
 *
 * @param visible Whether the widget is now visible
 * @param height The height of the widget (for offset calculation)
 */
class AgentLogVisibilityChanged(val visible: Boolean, val height: Int)

/**
 * Widget for displaying the agent execution log.
 *
 * Shows a collapsible list of log entries from the current agent session.
 * The widget is shown/hidden based on agent activity and user interaction.
 */
class AgentLogWidget(context: Context, attributeSet: AttributeSet) : LinearLayout(context, attributeSet) {

    private val binding = AgentLogWidgetBinding.inflate(
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater,
        this, true
    )

    @Inject
    lateinit var windowControl: WindowControl

    private val adapter = AgentLogAdapter()
    private var isExpanded = false
    private var workspaceId: IdType? = null
    private var spinAnimator: ObjectAnimator? = null

    init {
        buildActivityComponent().inject(this)

        binding.apply {
            logRecyclerView.layoutManager = LinearLayoutManager(context)
            logRecyclerView.adapter = adapter

            expandButton.setOnClickListener { toggleExpanded() }
            closeButton.setOnClickListener { hide() }
            headerLayout.setOnClickListener { toggleExpanded() }
        }

        updateExpandIcon()
    }

    override fun onDetachedFromWindow() {
        ABEventBus.unregister(this)
        stopSpinAnimation()
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        ABEventBus.safelyRegister(this)
        super.onAttachedToWindow()

        // Get current workspace ID and load entries
        workspaceId = windowControl.windowRepository.id
        refreshLogEntries()
        updateBackgroundColor()

        // Check if agent is already running (handles race condition where
        // AgentSessionStatusChangedEvent was posted before widget was attached)
        val wsId = workspaceId
        val isRunning = wsId != null && AgentSessionManager.isRunning(wsId)
        if (isRunning) {
            startSpinAnimation()
            if (visibility != View.VISIBLE) {
                show()
            }
        }
        // Update close/stop button state
        updateCloseStopButton(isRunning)
    }

    /**
     * Update background color to match the bottom window's background.
     */
    fun updateBackgroundColor() {
        val lastWindow = windowControl.windowRepository.visibleWindows.lastOrNull()
        val backgroundColor = if (lastWindow != null) {
            val colors = lastWindow.pageManager.actualTextDisplaySettings.colors
            val monochromeMode = CommonUtils.settings.monochromeMode
            val nightBackground = if (monochromeMode) -16777216 else colors?.nightBackground
            val dayBackground = if (monochromeMode) -1 else colors?.dayBackground
            (if (ScreenSettings.nightMode) nightBackground else dayBackground)
                ?: UiUtils.bibleViewDefaultBackgroundColor
        } else {
            UiUtils.bibleViewDefaultBackgroundColor
        }
        binding.rootLayout.setBackgroundColor(backgroundColor)
    }

    /**
     * Toggle the expanded state of the log.
     */
    private fun toggleExpanded() {
        isExpanded = !isExpanded
        binding.logRecyclerView.visibility = if (isExpanded) View.VISIBLE else View.GONE
        updateExpandIcon()
        notifyVisibilityChanged()
    }

    /**
     * Update the expand/collapse icon based on current state.
     */
    private fun updateExpandIcon() {
        val iconRes = if (isExpanded) {
            R.drawable.ic_expand_more_24
        } else {
            R.drawable.ic_expand_less_24
        }
        binding.expandButton.setImageResource(iconRes)
    }

    /**
     * Show the widget.
     */
    fun show() {
        visibility = View.VISIBLE
        updateBackgroundColor()
        notifyVisibilityChanged()
    }

    /**
     * Hide the widget.
     */
    fun hide() {
        visibility = View.GONE
        notifyVisibilityChanged()
    }

    /**
     * Post visibility change event for offset calculation.
     */
    private fun notifyVisibilityChanged() {
        val totalHeight = if (visibility == View.VISIBLE) {
            // Measure the widget height
            measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            )
            measuredHeight
        } else {
            0
        }
        Log.i(TAG, "notifyVisibilityChanged: visible=${visibility == View.VISIBLE}, height=$totalHeight, expanded=$isExpanded")
        ABEventBus.post(AgentLogVisibilityChanged(visibility == View.VISIBLE, totalHeight))
    }

    /**
     * Refresh the log entries from the session manager.
     */
    private fun refreshLogEntries() {
        val wsId = workspaceId ?: return
        val entries = AgentSessionManager.getLogEntries(wsId)
        adapter.submitList(entries.toList())

        // Update status text with latest meaningful entry
        val latestMessage = getLatestMeaningfulMessage(entries)
        updateStatusText(latestMessage)
        updateHeaderCost(entries)
    }

    /**
     * Show session-cumulative cost in the header.
     * Prefers the total cost entry; falls back to the latest per-call cost.
     */
    private fun updateHeaderCost(entries: List<AgentLogEntry>) {
        val costEntry = entries.lastOrNull { it.isTotalCost }
            ?: entries.lastOrNull { it.costInfo != null }
        val costInfo = costEntry?.costInfo
        if (costInfo != null) {
            binding.headerCostText.text = costInfo
            binding.headerCostText.visibility = View.VISIBLE
        } else {
            binding.headerCostText.visibility = View.GONE
        }
    }

    /**
     * Get the latest meaningful message for status display.
     * Prefers ACTION entries (tool calls) over INFO entries (iterations, etc.).
     */
    private fun getLatestMeaningfulMessage(entries: List<AgentLogEntry>): String? {
        // Prefer the latest ACTION entry (tool calls are more informative)
        val latestAction = entries.lastOrNull { it.type == LogEntryType.ACTION }
        if (latestAction != null) {
            return latestAction.message
        }
        // Fall back to the latest non-INFO entry, or the very last entry
        return entries.lastOrNull { it.type != LogEntryType.INFO }?.message
            ?: entries.lastOrNull()?.message
    }

    /**
     * Update the status text.
     * Always shows the latest log entry message, or empty if none.
     */
    private fun updateStatusText(latestMessage: String?) {
        binding.statusText.text = latestMessage ?: ""
    }

    /**
     * Start spinning animation on the status icon.
     * Respects the disable_animations setting (e.g., for e-ink devices).
     */
    private fun startSpinAnimation() {
        if (CommonUtils.settings.disableAnimations) return
        if (spinAnimator?.isRunning == true) return

        spinAnimator = ObjectAnimator.ofFloat(binding.statusIcon, "rotation", 0f, 360f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    /**
     * Stop spinning animation on the status icon.
     */
    private fun stopSpinAnimation() {
        spinAnimator?.cancel()
        spinAnimator = null
        binding.statusIcon.rotation = 0f
    }

    /**
     * Handle log update events.
     */
    fun onEventMainThread(event: AgentLogUpdatedEvent) {
        if (event.workspaceId == workspaceId) {
            refreshLogEntries()
            // Auto-scroll to bottom when new entries are added
            if (adapter.itemCount > 0 && isExpanded) {
                binding.logRecyclerView.smoothScrollToPosition(adapter.itemCount - 1)
            }
        }
    }

    /**
     * Handle session status change events.
     */
    fun onEventMainThread(event: AgentSessionStatusChangedEvent) {
        if (event.workspaceId == workspaceId) {
            val entries = AgentSessionManager.getLogEntries(event.workspaceId)
            val latestMessage = getLatestMeaningfulMessage(entries)
            updateStatusText(latestMessage)

            // Start/stop spin animation based on running state
            if (event.isRunning) {
                startSpinAnimation()
            } else {
                stopSpinAnimation()
            }

            // Update close/stop button based on running state
            updateCloseStopButton(event.isRunning)

            // Auto-show when agent starts
            if (event.isRunning && visibility != View.VISIBLE) {
                show()
            }
        }
    }

    /**
     * Update close/stop button based on running state.
     * When running: shows stop icon, clicking cancels the agent.
     * When idle: shows close icon, clicking hides the widget.
     */
    private fun updateCloseStopButton(isRunning: Boolean) {
        if (isRunning) {
            binding.closeButton.setImageResource(R.drawable.ic_stop_black_24dp)
            binding.closeButton.setColorFilter(CommonUtils.getResourceColor(R.color.grey_500))
            binding.closeButton.contentDescription = context.getString(R.string.agent_log_stop)
            binding.closeButton.setOnClickListener {
                workspaceId?.let { AgentSessionManager.stopAgent(it) }
            }
        } else {
            binding.closeButton.setImageResource(R.drawable.ic_baseline_close_24)
            binding.closeButton.clearColorFilter()
            binding.closeButton.contentDescription = context.getString(R.string.agent_log_close)
            binding.closeButton.setOnClickListener { hide() }
        }
        binding.closeButton.isEnabled = true
        binding.closeButton.alpha = 1.0f
    }

    companion object {
        const val TAG = "AgentLogWidget"
    }
}
