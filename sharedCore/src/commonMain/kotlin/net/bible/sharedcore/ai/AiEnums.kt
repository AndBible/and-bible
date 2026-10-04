package net.bible.sharedcore.ai

import net.bible.sharedcore.settings.SettingsItem.Choice

/**
 * Stable value ids for the Android `PermissionMode` enum
 * (`net.bible.service.llm.agent.AgentPermissions.PermissionMode`), which controls when the user
 * is asked for permission before an AI agent's write tools execute. Mirrors only its `name`s
 * (declaration order); labels are host-resolved (see [agentPermissionModeChoices]).
 */
object AgentPermissionModeIds {
    val ordered: List<String> = listOf(
        "ALWAYS_ASK",
        "ASK_ONCE_PER_RUN",
        "ALLOW_ALL",
        "DENY_ALL",
    )
}

/** Build the choice list given host-resolved labels keyed by value id. */
fun agentPermissionModeChoices(labels: Map<String, String>): List<Choice> =
    AgentPermissionModeIds.ordered.map { Choice(it, labels[it] ?: it) }
