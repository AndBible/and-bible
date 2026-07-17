package net.bible.sharedcore.ai

/**
 * A configured provider row. id = stable provider id (String of `IdType`).
 *
 * Mirrors `net.bible.service.llm.LlmProviderConfig` (`id`, `providerType`, `displayName`,
 * `endpoint`, `apiFormat`) plus its derived/resolved accessors — `resolveProvider()`,
 * `resolveEndpoint()`, `resolveApiFormat()` — and the SharedPreferences-backed API key
 * (`getApiKey()`), flattened to portable primitives (no Room/enum types leaked).
 */
data class ProviderVd(
    val id: String,
    val displayName: String,
    val providerTypeId: String,   // LlmProviderConfig.providerType / LlmProvider.name or "CUSTOM"
    val apiKeySet: Boolean,        // getApiKey().isNotBlank() — masked in the row summary
    val isCustom: Boolean,         // resolveProvider() == LlmProvider.CUSTOM
    val endpoint: String,          // resolveEndpoint() — custom only, else ""
    val apiFormatId: String,       // resolveApiFormat().name ("OPENAI"/"ANTHROPIC") — custom only
)

/**
 * A configured model row. id = stable model id.
 *
 * Mirrors `net.bible.service.llm.LlmConfiguredModel` (`id`, `providerConfigId`, `modelId`,
 * `displayName`) plus `GlobalAiSettings.defaultModelId` (`isDefault`),
 * `LlmProvider.isModelSupported()` (`supported`), and a pre-formatted pricing line built from
 * `inputPricePerMillion`/`outputPricePerMillion`/`cacheCreationPricePerMillion`/
 * `cacheReadPricePerMillion`.
 */
data class ModelVd(
    val id: String,
    val modelId: String,           // e.g. "gpt-4o" or "openrouter/anthropic/..."
    val displayName: String,
    val providerId: String,
    val isDefault: Boolean,
    val supported: Boolean,
    val pricingSummary: String,    // pre-formatted price/cost line
)

/**
 * A provider *type* choice for the add-provider picker.
 *
 * Mirrors one `net.bible.service.llm.LlmProvider` enum entry (or the synthetic "CUSTOM" choice):
 * `displayName`, `tier` (`ProviderTier.name`), `apiKeyUrl`, `endpoint` (as the default),
 * `supportsDynamicModels`.
 */
data class ProviderTypeVd(
    val id: String,                // LlmProvider.name or "CUSTOM"
    val displayName: String,
    val tier: String,              // ProviderTier.name: "RECOMMENDED" | "COMMUNITY" | "UNCATEGORIZED"
    val apiKeyUrl: String?,        // shown as a link if present
    val defaultEndpoint: String,
    val supportsDynamicModels: Boolean,
    val isCustom: Boolean,
)

/**
 * A discovered/available model for the add-model picker.
 *
 * `modelId`/`label` come from `LlmProviderConfig.resolveAvailableModels()` (enum-declared or
 * dynamically fetched via `DynamicModelService`); `supported`/`knownPricing` come from
 * `LlmProvider.isModelSupported(modelId)` / `LlmProvider.hasKnownPricing(modelId)`.
 */
data class AvailableModelVd(val modelId: String, val label: String, val supported: Boolean, val knownPricing: Boolean)

/** One entry in the easy-setup wizard's recommended-setup picker (step 1). */
data class RecommendedSetupVd(val id: String, val label: String, val providerTypeId: String, val modelId: String, val apiKeyUrl: String?)
