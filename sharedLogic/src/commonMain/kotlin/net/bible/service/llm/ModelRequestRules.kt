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

package net.bible.service.llm

/**
 * Model-specific request parameters for models whose API defaults break the agent's tool loop.
 *
 * Every other model gets the plain request. A rule matches the bare model id and its dated
 * snapshots (`<id>-<suffix>`), not provider-prefixed ids such as OpenRouter's `openai/...`:
 * routers have their own parameter names and defaults.
 */
object ModelRequestRules {
    /**
     * OpenAI models whose Chat Completions function calling works only with
     * `reasoning_effort: "none"` (their default, `medium`, breaks tool calls).
     */
    private val OPENAI_REASONING_EFFORT_NONE = listOf("gpt-6-luna")

    /**
     * Anthropic models with adaptive thinking on by default. Thinking is turned off: the adapter does
     * not send thinking blocks back in tool loops, and these models accept only the default temperature.
     * `thinking: disabled` is valid while the effort stays at its default (`medium`).
     */
    private val ANTHROPIC_THINKING_DISABLED = listOf("claude-haiku-5-5")

    private fun List<String>.matches(model: String) = any { model == it || model.startsWith("$it-") }

    /** The `reasoning_effort` to send on an OpenAI-compatible request, or null to send none. */
    fun openAiReasoningEffort(model: String): String? =
        if (OPENAI_REASONING_EFFORT_NONE.matches(model)) "none" else null

    /** Whether an Anthropic request must carry `thinking: {type: "disabled"}`. */
    fun anthropicDisableThinking(model: String): Boolean = ANTHROPIC_THINKING_DISABLED.matches(model)

    /** Whether the model accepts a non-default temperature (false: the request omits it). */
    fun acceptsTemperature(model: String): Boolean = !ANTHROPIC_THINKING_DISABLED.matches(model)
}
