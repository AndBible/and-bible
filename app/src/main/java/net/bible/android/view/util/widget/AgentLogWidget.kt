/*
 * Copyright (c) 2024-2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
import android.animation.PropertyValuesHolder
import android.graphics.Color
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import net.bible.android.activity.R
import net.bible.android.activity.databinding.AgentLogWidgetBinding
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.IdType
import net.bible.android.view.activity.ai.AgentLogAdapter
import net.bible.service.device.ScreenSettings
import net.bible.android.view.util.UiUtils
import net.bible.service.common.CommonUtils
import net.bible.service.common.CommonUtils.buildActivityComponent
import net.bible.android.view.activity.ai.RawLlmLogActivity
import net.bible.service.common.AiSettings
import net.bible.service.common.DefaultModelChangedEvent
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.LlmCostTracker
import net.bible.service.llm.agent.AgentLogEntry
import net.bible.service.llm.agent.AgentLogUpdatedEvent
import net.bible.service.llm.agent.AgentSessionManager
import net.bible.service.llm.agent.AgentSessionStatusChangedEvent
import net.bible.service.llm.agent.AgentStopReason
import net.bible.service.llm.agent.LogEntryType
import net.bible.service.llm.agent.shouldAutoHideAgentLog
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
    private var iconAnimator: ObjectAnimator? = null

    private var isUserVisible: Boolean
        get() = CommonUtils.settings.getBoolean(PREF_AGENT_LOG_VISIBLE, false)
        set(value) = CommonUtils.settings.setBoolean(PREF_AGENT_LOG_VISIBLE, value)

    /** Always reads the current workspace ID so it stays correct after workspace switches. */
    private val workspaceId: IdType get() = windowControl.windowRepository.id

    init {
        buildActivityComponent().inject(this)

        binding.apply {
            logRecyclerView.layoutManager = LinearLayoutManager(context)
            logRecyclerView.adapter = adapter
            if (CommonUtils.settings.disableAnimations) {
                logRecyclerView.itemAnimator = null
            }

            // expand- and closeButton are wrapped in FrameLayout containers with a
            // small horizontal padding. Binding the same handler to both the button and
            // its container gives a slightly wider hit zone so off-by-a-few-pixel taps
            // (more common on tablets / e-ink digitizers) still trigger the intended
            // action instead of being captured by the adjacent toggle-clickable
            // statusIcon/statusText.
            val toggleListener = View.OnClickListener { toggleExpanded() }
            expandButton.setOnClickListener(toggleListener)
            expandButtonContainer.setOnClickListener(toggleListener)
            // Toggle is bound to the middle area only — NOT to headerLayout — so
            // taps that miss the expand/close buttons by a few dp don't get
            // hijacked into toggling the log expand/collapse state.
            statusIcon.setOnClickListener(toggleListener)
            statusText.setOnClickListener(toggleListener)
            headerCostText.setOnClickListener(toggleListener)
        }

        adapter.onRawLogClick = { openRawLog() }

        adapter.onModelSelectorClick = { showModelSelector() }
        updateModelSelectorText()
        updateExpandIcon()
    }

    override fun onDetachedFromWindow() {
        ABEventBus.unregister(this)
        stopStatusAnimation()
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        ABEventBus.safelyRegister(this)
        super.onAttachedToWindow()

        refreshLogEntries()
        updateBackgroundColor()

        val isRunning = AgentSessionManager.isRunning(workspaceId)
        if (isRunning) {
            startStatusAnimation()
            if (visibility != View.VISIBLE) {
                show()
            }
        } else if (isUserVisible) {
            // Restore visibility from previous session
            visibility = View.VISIBLE
            updateBackgroundColor()
            notifyVisibilityChanged()
        }
        updateCloseStopButton(isRunning)
    }

    /**
     * Update background color to match the bottom window's background.
     */
    fun updateBackgroundColor() {
        val monochromeMode = CommonUtils.settings.monochromeMode
        val lastWindow = windowControl.windowRepository.visibleWindows.lastOrNull()
        val backgroundColor = if (lastWindow != null) {
            val colors = lastWindow.pageManager.actualTextDisplaySettings.colors
            val nightBackground = if (monochromeMode) Color.BLACK else colors?.nightBackground
            val dayBackground = if (monochromeMode) Color.WHITE else colors?.dayBackground
            (if (ScreenSettings.nightMode) nightBackground else dayBackground)
                ?: UiUtils.bibleViewDefaultBackgroundColor
        } else {
            UiUtils.bibleViewDefaultBackgroundColor
        }
        binding.rootLayout.setBackgroundColor(backgroundColor)

        if (monochromeMode) {
            val tint = if (ScreenSettings.nightMode) Color.WHITE else Color.BLACK
            binding.statusIcon.setColorFilter(tint)
            binding.expandButton.setColorFilter(tint)
            binding.closeButton.setColorFilter(tint)
            binding.statusText.setTextColor(tint)
            binding.headerCostText.setTextColor(tint)
            binding.headerCostText.alpha = 1.0f
            binding.rootLayout.elevation = 0f
        }
    }

    /**
     * Toggle the expanded state of the log.
     */
    private fun toggleExpanded() {
        isExpanded = !isExpanded
        binding.logRecyclerView.visibility = if (isExpanded) View.VISIBLE else View.GONE
        updateExpandIcon()
        notifyVisibilityChanged()
        if (isExpanded && adapter.itemCount > 0) {
            binding.logRecyclerView.scrollToPosition(adapter.itemCount - 1)
        }
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
        isUserVisible = true
        updateBackgroundColor()
        notifyVisibilityChanged()
    }

    /**
     * Hide the widget.
     */
    fun hide() {
        visibility = View.GONE
        isUserVisible = false
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
        ABEventBus.post(AgentLogVisibilityChanged(visibility == View.VISIBLE, totalHeight))
    }

    /**
     * Refresh the log entries from the session manager.
     */
    private fun refreshLogEntries(scrollToBottom: Boolean = false) {
        val entries = AgentSessionManager.getLogEntries(workspaceId)
        // Copy each entry so DiffUtil detects changes to mutable fields (status, costInfo).
        adapter.submitList(entries.map { it.copy() }) {
            if (scrollToBottom && adapter.itemCount > 0 && isExpanded) {
                binding.logRecyclerView.scrollToPosition(adapter.itemCount - 1)
            }
        }

        // Update status text with latest meaningful entry
        val latestMessage = getLatestMeaningfulMessage(entries)
        updateStatusText(latestMessage)
        updateHeaderCost()
    }

    /**
     * Show session-cumulative cost in the header.
     */
    private fun updateHeaderCost() {
        val session = AgentSessionManager.getSession(workspaceId)
        val totalCost = session?.sessionCostUsd ?: 0.0
        if (totalCost > 0) {
            binding.headerCostText.text = LlmCostTracker.formatCost(totalCost)
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
        val latestAction = entries.lastOrNull { it.type == LogEntryType.ACTION || it.type == LogEntryType.LLM_COMMENT }
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
        binding.statusText.text = latestMessage ?: context.getString(R.string.agent_log_idle)
    }

    /**
     * Start a pulsating "thinking" animation on the status icon while the agent is running.
     * Respects the disable_animations setting (e.g., for e-ink devices).
     */
    private fun startStatusAnimation() {
        if (CommonUtils.settings.disableAnimations) return
        if (iconAnimator?.isRunning == true) return

        val scaleX = PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.15f)
        val scaleY = PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.15f)
        iconAnimator = ObjectAnimator.ofPropertyValuesHolder(binding.statusIcon, scaleX, scaleY).apply {
            duration = 600
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    /**
     * Stop the status icon animation and reset its scale.
     */
    private fun stopStatusAnimation() {
        iconAnimator?.cancel()
        iconAnimator = null
        binding.statusIcon.scaleX = 1.0f
        binding.statusIcon.scaleY = 1.0f
    }

    /**
     * Handle log update events.
     */
    fun onEventMainThread(event: AgentLogUpdatedEvent) {
        if (event.workspaceId == workspaceId) {
            refreshLogEntries(scrollToBottom = true)
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

            // Start/stop status icon animation based on running state
            if (event.isRunning) {
                startStatusAnimation()
            } else {
                stopStatusAnimation()
            }

            // Update close/stop button based on running state
            updateCloseStopButton(event.isRunning)

            // Auto-show when agent starts
            if (event.isRunning && visibility != View.VISIBLE) {
                show()
            }

            // Auto-hide on a terminal state (unless it errored), if enabled by the user.
            if (!event.isRunning &&
                visibility == View.VISIBLE &&
                shouldAutoHideAgentLog(CommonUtils.aiSettings.autoHideAgentLogOnCompletion, event.stopReason)
            ) {
                hide()
                if (event.stopReason == AgentStopReason.COMPLETED) {
                    ABEventBus.post(ToastEvent(R.string.ai_task_completed))
                }
            }
        }
    }

    /**
     * Refresh the model selector text when the default model changes elsewhere.
     */
    fun onEventMainThread(@Suppress("UNUSED_PARAMETER") event: DefaultModelChangedEvent) {
        updateModelSelectorText()
    }

    /**
     * Update the model selector text to show the current default model.
     */
    private fun updateModelSelectorText() {
        val modelDao = DatabaseContainer.instance.aiSettingsDb.llmConfiguredModelDao()
        val defaultId = AiSettings.defaultModelId
        val model = defaultId?.let { modelDao.getById(it) }
        adapter.modelSelectorText = context.getString(
            R.string.agent_log_model_selector,
            model?.modelId ?: context.getString(R.string.agent_log_model_not_configured)
        )
    }

    /**
     * Show a dialog to quickly switch the default model.
     */
    private fun showModelSelector() {
        val modelDao = DatabaseContainer.instance.aiSettingsDb.llmConfiguredModelDao()
        val providerDao = DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao()
        val currentDefault = AiSettings.defaultModelId
        val allModels = modelDao.all().sortedByDescending { it.id == currentDefault }
        if (allModels.isEmpty()) return

        val providers = providerDao.all().associateBy { it.id }
        val items = allModels.map { model ->
            val providerName = providers[model.providerConfigId]?.displayName ?: "?"
            val prefix = if (model.id == currentDefault) "★ " else ""
            val pricing = if (model.inputPricePerMillion > 0 || model.outputPricePerMillion > 0) {
                " (${LlmCostTracker.formatPriceCompact(model.inputPricePerMillion)}/${LlmCostTracker.formatPriceCompact(model.outputPricePerMillion)})"
            } else ""
            "$prefix${model.modelId} — $providerName$pricing"
        }.toTypedArray()

        android.app.AlertDialog.Builder(context)
            .setTitle(R.string.agent_log_select_model)
            .setItems(items) { _, which ->
                AiSettings.defaultModelId = allModels[which].id
                updateModelSelectorText()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Open the raw LLM log activity.
     */
    private fun openRawLog() {
        val intent = android.content.Intent(context, RawLlmLogActivity::class.java).apply {
            putExtra(RawLlmLogActivity.EXTRA_WORKSPACE_ID, workspaceId.toString())
        }
        context.startActivity(intent)
    }

    /**
     * Update close/stop button based on running state.
     * When running: shows stop icon, clicking cancels the agent.
     * When idle: shows close icon, clicking hides the widget.
     */
    private fun updateCloseStopButton(isRunning: Boolean) {
        val onClick: View.OnClickListener
        if (isRunning) {
            binding.closeButton.setImageResource(R.drawable.ic_stop_black_24dp)
            val stopColor = if (CommonUtils.settings.monochromeMode) {
                if (ScreenSettings.nightMode) Color.WHITE else Color.BLACK
            } else CommonUtils.getResourceColor(R.color.grey_500)
            binding.closeButton.setColorFilter(stopColor)
            binding.closeButton.contentDescription = context.getString(R.string.agent_log_stop)
            onClick = View.OnClickListener {
                AgentSessionManager.stopAgent(workspaceId)
            }
        } else {
            binding.closeButton.setImageResource(R.drawable.ic_baseline_close_24)
            binding.closeButton.clearColorFilter()
            binding.closeButton.contentDescription = context.getString(R.string.agent_log_close)
            onClick = View.OnClickListener { hide() }
        }
        binding.closeButton.setOnClickListener(onClick)
        // Mirror handler on the container so taps within the slightly wider FrameLayout
        // hit zone (but outside the visible button) still trigger the action.
        binding.closeButtonContainer.setOnClickListener(onClick)
        binding.closeButton.isEnabled = true
        binding.closeButton.alpha = 1.0f
    }

    companion object {
        private const val PREF_AGENT_LOG_VISIBLE = "agent_log_widget_visible"
    }
}
