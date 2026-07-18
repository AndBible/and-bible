package net.bible.sharedcore.ai

/**
 * A prompt row for the prompt manager list.
 *
 * Mirrors `net.bible.service.llm.AgentPrompt` (`id`, `name`, `description`, `categoryId`) plus
 * derived state from `PromptRepository`/`ToolRegistry`/`GlobalAiSettings`:
 * `isBuiltIn` (`PromptRepository.isBuiltIn`), `isReadOnly` (`PromptRepository.isReadOnly`,
 * true for built-in AND add-on prompts), `isFavorite` (`PromptRepository.isFavorite` /
 * `GlobalAiSettings.favoritePrompts`), `isHidden` (`PromptRepository.isBuiltInPromptHidden` for
 * built-ins; user prompts are never hidden this way).
 */
data class PromptVd(
    val id: String,
    val name: String,
    val description: String,
    val categoryId: String?,      // null = uncategorized
    val isBuiltIn: Boolean,
    val isReadOnly: Boolean,
    val isFavorite: Boolean,
    val isHidden: Boolean,
)

/**
 * A prompt category (folder) row.
 *
 * Mirrors `net.bible.service.llm.PromptCategory` (`id`, `name`) plus `isBuiltIn`
 * (`PromptRepository.allCategories()` = `BuiltInPrompts.defaultCategories()` + DB categories)
 * and `isHidden` (`PromptRepository.isCategoryHidden`: `PromptCategory.hidden` OR
 * `GlobalAiSettings.hiddenBuiltInCategories`).
 */
data class PromptCategoryVd(val id: String, val name: String, val isBuiltIn: Boolean, val isHidden: Boolean)

/** Grouped view for the manager list: categories (+ a virtual Favorites group) each with their prompts, in display order. */
data class PromptGroupVd(val category: PromptCategoryVd?, val isFavorites: Boolean, val prompts: List<PromptVd>)

/**
 * Tool with its permission state, for the shared ToolPermissionList.
 *
 * Mirrors one registered `net.bible.service.llm.tools.Tool` (via `ToolRegistry`): `id` =
 * `AgentTool.name` (`tool.agentTool.name`, the stable enum id — NOT the LLM-facing
 * `camelCaseName`), `displayName` = `ToolRegistry.getDisplayName(tool)`, `description` =
 * `tool.description`, `requiresPermission` = `tool.requiresPermission` (write tools),
 * `categoryId` = `tool.category.name` (`ToolCategory.name`).
 */
data class ToolVd(val id: String, val displayName: String, val description: String, val requiresPermission: Boolean, val categoryId: String)

/** Mirrors `net.bible.service.llm.ToolCategory`: `id` = `.name`, `displayName` = `ToolRegistry.getCategoryDisplayName`. */
data class ToolCategoryVd(val id: String, val displayName: String)

/** read tools: ENABLED/DISABLED(+DEFAULT); write tools: ALLOW/DENY(+DEFAULT) */
enum class ToolPermission { DEFAULT, ALLOW, DENY, ENABLED, DISABLED }

/** Mirrors `net.bible.service.llm.PromptContext` `.name` order verbatim (String ids). */
object PromptContextIds {
    val ordered: List<String> = listOf(
        "VERSE_SELECTION",
        "TEXT_SELECTION",
        "WINDOW_MENU",
        "WORKSPACE_MENU",
        "NOTE_EDITOR",
    )
}

/**
 * Full editable prompt data for the PromptEdit screen.
 *
 * Mirrors `net.bible.service.llm.AgentPrompt` fields: `id`, `name`, `description` (nullable in
 * the entity, flattened to `""` here), `promptTemplate` -> `template`, `showIn` (`Set<PromptContext>`
 * -> `Set<String>` of `.name`s) -> `contexts`, `isTextTransformation`, `permissionMode`
 * (`PermissionMode?.name`), `allowedTools`/`deniedTools` (`Set<AgentTool>?` -> `Set<String>` of
 * `.name`s, empty set = no override), `configuredModelId` -> `modelOverrideId`, `maxIterations`,
 * `strictContextMatching`, `specifyBeforeRun`, `noDocumentCreation`, `autoIncludeDocuments`,
 * `autoIncludeCommentaries`, plus derived `isReadOnly`/`isBuiltIn` (`PromptRepository`).
 */
data class PromptEditData(
    val id: String?, val name: String, val description: String, val template: String,
    val categoryId: String?, val contexts: Set<String>, val isTextTransformation: Boolean,
    val permissionMode: String?, val allowedTools: Set<String>, val deniedTools: Set<String>,
    val modelOverrideId: String?, val maxIterations: Int?,
    val strictContextMatching: Boolean, val specifyBeforeRun: Boolean, val noDocumentCreation: Boolean,
    val autoIncludeDocuments: Boolean, val autoIncludeCommentaries: Boolean,
    val isReadOnly: Boolean, val isBuiltIn: Boolean,
)
