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

package net.bible.android.database.migrations

import androidx.room.migration.Migration

private val addEditBeforeRun = makeMigration(1..2) { db ->
    db.execSQL("ALTER TABLE `AgentPrompt` ADD COLUMN `editBeforeRun` INTEGER NOT NULL DEFAULT 0")
}

private val addNoDocumentCreation = makeMigration(2..3) { db ->
    db.execSQL("ALTER TABLE `AgentPrompt` ADD COLUMN `noDocumentCreation` INTEGER NOT NULL DEFAULT 0")
}

private val addGlobalAiSettingsAndUsage = makeMigration(3..4) { db ->
    db.execSQL("""CREATE TABLE IF NOT EXISTS `GlobalAiSettings` (
        `id` BLOB NOT NULL PRIMARY KEY,
        `agentPermissionMode` TEXT DEFAULT NULL,
        `permanentlyAllowedTools` TEXT DEFAULT NULL,
        `permanentlyDeniedTools` TEXT DEFAULT NULL,
        `aiExcludedDocuments` TEXT NOT NULL,
        `commentaryMaxResponseTokens` INTEGER NOT NULL DEFAULT 0
    )""")
    db.execSQL("""CREATE TABLE IF NOT EXISTS `LlmUsageRecord` (
        `id` BLOB NOT NULL PRIMARY KEY,
        `providerConfigId` BLOB NOT NULL,
        `deviceId` TEXT NOT NULL,
        `inputTokens` INTEGER NOT NULL DEFAULT 0,
        `outputTokens` INTEGER NOT NULL DEFAULT 0,
        `cacheCreationTokens` INTEGER NOT NULL DEFAULT 0,
        `cacheReadTokens` INTEGER NOT NULL DEFAULT 0,
        `estimatedCostUsd` REAL NOT NULL DEFAULT 0.0,
        FOREIGN KEY(`providerConfigId`) REFERENCES `LlmProviderConfig`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
    )""")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_LlmUsageRecord_providerConfigId` ON `LlmUsageRecord` (`providerConfigId`)")
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_LlmUsageRecord_providerConfigId_deviceId` ON `LlmUsageRecord` (`providerConfigId`, `deviceId`)")
    db.execSQL("ALTER TABLE `LlmProviderConfig` ADD COLUMN `customInputPrice` REAL NOT NULL DEFAULT 0.0")
    db.execSQL("ALTER TABLE `LlmProviderConfig` ADD COLUMN `customOutputPrice` REAL NOT NULL DEFAULT 0.0")
}

private val setCommentaryTokenDefault = makeMigration(4..5) { db ->
    db.execSQL("""CREATE TABLE IF NOT EXISTS `GlobalAiSettings_new` (
        `id` BLOB NOT NULL PRIMARY KEY,
        `agentPermissionMode` TEXT DEFAULT NULL,
        `permanentlyAllowedTools` TEXT DEFAULT NULL,
        `permanentlyDeniedTools` TEXT DEFAULT NULL,
        `aiExcludedDocuments` TEXT NOT NULL,
        `commentaryMaxResponseTokens` INTEGER NOT NULL DEFAULT 4000
    )""")
    db.execSQL("""INSERT INTO `GlobalAiSettings_new` (`id`, `agentPermissionMode`, `permanentlyAllowedTools`, `permanentlyDeniedTools`, `aiExcludedDocuments`, `commentaryMaxResponseTokens`)
        SELECT `id`, `agentPermissionMode`, `permanentlyAllowedTools`, `permanentlyDeniedTools`, `aiExcludedDocuments`,
            CASE WHEN `commentaryMaxResponseTokens` = 0 THEN 4000 ELSE `commentaryMaxResponseTokens` END
        FROM `GlobalAiSettings`""")
    db.execSQL("DROP TABLE `GlobalAiSettings`")
    db.execSQL("ALTER TABLE `GlobalAiSettings_new` RENAME TO `GlobalAiSettings`")
}

private val addHiddenBuiltInPrompts = makeMigration(5..6) { db ->
    db.execSQL("ALTER TABLE `GlobalAiSettings` ADD COLUMN `hiddenBuiltInPrompts` TEXT NOT NULL DEFAULT ''")
}

private val addMaxIterations = makeMigration(6..7) { db ->
    db.execSQL("ALTER TABLE `GlobalAiSettings` ADD COLUMN `maxIterations` INTEGER NOT NULL DEFAULT 10")
    db.execSQL("ALTER TABLE `AgentPrompt` ADD COLUMN `maxIterations` INTEGER DEFAULT NULL")
}

private val addCommentaryDeselected = makeMigration(7..8) { db ->
    db.execSQL("ALTER TABLE `GlobalAiSettings` ADD COLUMN `commentaryDeselected` TEXT NOT NULL DEFAULT ''")
}

private val addConfiguredModels = makeMigration(8..9) { db ->
    // 1. Create LlmConfiguredModel table
    db.execSQL("""CREATE TABLE IF NOT EXISTS `LlmConfiguredModel` (
        `id` BLOB NOT NULL PRIMARY KEY,
        `providerConfigId` BLOB NOT NULL,
        `modelId` TEXT NOT NULL,
        `orderNumber` INTEGER NOT NULL DEFAULT 0,
        `inputPricePerMillion` REAL NOT NULL DEFAULT 0.0,
        `outputPricePerMillion` REAL NOT NULL DEFAULT 0.0,
        `cacheCreationPricePerMillion` REAL NOT NULL DEFAULT 0.0,
        `cacheReadPricePerMillion` REAL NOT NULL DEFAULT 0.0,
        FOREIGN KEY(`providerConfigId`) REFERENCES `LlmProviderConfig`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
    )""")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_LlmConfiguredModel_providerConfigId` ON `LlmConfiguredModel` (`providerConfigId`)")
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_LlmConfiguredModel_providerConfigId_modelId` ON `LlmConfiguredModel` (`providerConfigId`, `modelId`)")

    // 2. Rebuild LlmProviderConfig without removed columns (defaultModel, isDefault, customInputPrice, customOutputPrice)
    db.execSQL("""CREATE TABLE IF NOT EXISTS `LlmProviderConfig_new` (
        `id` BLOB NOT NULL PRIMARY KEY,
        `providerType` TEXT NOT NULL,
        `displayName` TEXT NOT NULL,
        `endpoint` TEXT DEFAULT NULL,
        `apiFormat` TEXT DEFAULT NULL,
        `orderNumber` INTEGER NOT NULL DEFAULT 0
    )""")
    db.execSQL("""INSERT INTO `LlmProviderConfig_new` (`id`, `providerType`, `displayName`, `endpoint`, `apiFormat`, `orderNumber`)
        SELECT `id`, `providerType`, `displayName`, `endpoint`, `apiFormat`, `orderNumber` FROM `LlmProviderConfig`""")
    db.execSQL("DROP TABLE `LlmProviderConfig`")
    db.execSQL("ALTER TABLE `LlmProviderConfig_new` RENAME TO `LlmProviderConfig`")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_LlmProviderConfig_orderNumber` ON `LlmProviderConfig` (`orderNumber`)")

    // 3. Rebuild AgentPrompt: replace providerConfigId + modelOverride with configuredModelId
    db.execSQL("""CREATE TABLE IF NOT EXISTS `AgentPrompt_new` (
        `id` BLOB NOT NULL PRIMARY KEY,
        `name` TEXT NOT NULL,
        `description` TEXT DEFAULT NULL,
        `promptTemplate` TEXT NOT NULL,
        `showIn` TEXT NOT NULL,
        `orderNumber` INTEGER NOT NULL DEFAULT 0,
        `createdAt` INTEGER NOT NULL DEFAULT 0,
        `strictContextMatching` INTEGER NOT NULL DEFAULT 1,
        `permissionMode` TEXT DEFAULT NULL,
        `allowedTools` TEXT DEFAULT NULL,
        `deniedTools` TEXT DEFAULT NULL,
        `configuredModelId` BLOB DEFAULT NULL,
        `editBeforeRun` INTEGER NOT NULL DEFAULT 0,
        `noDocumentCreation` INTEGER NOT NULL DEFAULT 0,
        `maxIterations` INTEGER DEFAULT NULL,
        FOREIGN KEY(`configuredModelId`) REFERENCES `LlmConfiguredModel`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
    )""")
    db.execSQL("""INSERT INTO `AgentPrompt_new` (`id`, `name`, `description`, `promptTemplate`, `showIn`, `orderNumber`, `createdAt`, `strictContextMatching`, `permissionMode`, `allowedTools`, `deniedTools`, `configuredModelId`, `editBeforeRun`, `noDocumentCreation`, `maxIterations`)
        SELECT `id`, `name`, `description`, `promptTemplate`, `showIn`, `orderNumber`, `createdAt`, `strictContextMatching`, `permissionMode`, `allowedTools`, `deniedTools`, NULL, `editBeforeRun`, `noDocumentCreation`, `maxIterations` FROM `AgentPrompt`""")
    db.execSQL("DROP TABLE `AgentPrompt`")
    db.execSQL("ALTER TABLE `AgentPrompt_new` RENAME TO `AgentPrompt`")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_AgentPrompt_orderNumber` ON `AgentPrompt` (`orderNumber`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_AgentPrompt_createdAt` ON `AgentPrompt` (`createdAt`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_AgentPrompt_configuredModelId` ON `AgentPrompt` (`configuredModelId`)")

    // 4. Rebuild LlmUsageRecord: replace providerConfigId with configuredModelId
    db.execSQL("""CREATE TABLE IF NOT EXISTS `LlmUsageRecord_new` (
        `id` BLOB NOT NULL PRIMARY KEY,
        `configuredModelId` BLOB NOT NULL,
        `deviceId` TEXT NOT NULL,
        `inputTokens` INTEGER NOT NULL DEFAULT 0,
        `outputTokens` INTEGER NOT NULL DEFAULT 0,
        `cacheCreationTokens` INTEGER NOT NULL DEFAULT 0,
        `cacheReadTokens` INTEGER NOT NULL DEFAULT 0,
        `estimatedCostUsd` REAL NOT NULL DEFAULT 0.0
    )""")
    // Old usage records are dropped (no way to map providerConfigId → configuredModelId)
    db.execSQL("DROP TABLE `LlmUsageRecord`")
    db.execSQL("ALTER TABLE `LlmUsageRecord_new` RENAME TO `LlmUsageRecord`")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_LlmUsageRecord_configuredModelId` ON `LlmUsageRecord` (`configuredModelId`)")
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_LlmUsageRecord_configuredModelId_deviceId` ON `LlmUsageRecord` (`configuredModelId`, `deviceId`)")

    // 5. Add defaultModelId to GlobalAiSettings
    db.execSQL("ALTER TABLE `GlobalAiSettings` ADD COLUMN `defaultModelId` BLOB DEFAULT NULL")
}

private val raiseCommentaryTokenDefault = makeMigration(9..10) { db ->
    db.execSQL("""CREATE TABLE IF NOT EXISTS `GlobalAiSettings_new` (
        `id` BLOB NOT NULL PRIMARY KEY,
        `agentPermissionMode` TEXT DEFAULT NULL,
        `permanentlyAllowedTools` TEXT DEFAULT NULL,
        `permanentlyDeniedTools` TEXT DEFAULT NULL,
        `aiExcludedDocuments` TEXT NOT NULL,
        `commentaryMaxResponseTokens` INTEGER NOT NULL DEFAULT 15000,
        `hiddenBuiltInPrompts` TEXT NOT NULL,
        `maxIterations` INTEGER NOT NULL DEFAULT 10,
        `commentaryDeselected` TEXT NOT NULL,
        `defaultModelId` BLOB DEFAULT NULL
    )""")
    db.execSQL("""INSERT INTO `GlobalAiSettings_new` (`id`, `agentPermissionMode`, `permanentlyAllowedTools`, `permanentlyDeniedTools`, `aiExcludedDocuments`, `commentaryMaxResponseTokens`, `hiddenBuiltInPrompts`, `maxIterations`, `commentaryDeselected`, `defaultModelId`)
        SELECT `id`, `agentPermissionMode`, `permanentlyAllowedTools`, `permanentlyDeniedTools`, `aiExcludedDocuments`,
            CASE WHEN `commentaryMaxResponseTokens` = 4000 THEN 15000 ELSE `commentaryMaxResponseTokens` END,
            `hiddenBuiltInPrompts`, `maxIterations`, `commentaryDeselected`, `defaultModelId`
        FROM `GlobalAiSettings`""")
    db.execSQL("DROP TABLE `GlobalAiSettings`")
    db.execSQL("ALTER TABLE `GlobalAiSettings_new` RENAME TO `GlobalAiSettings`")
}

private val addAiLanguage = makeMigration(10..11) { db ->
    db.execSQL("ALTER TABLE `GlobalAiSettings` ADD COLUMN `aiLanguage` TEXT DEFAULT NULL")
}

private val addAutoIncludeFields = makeMigration(11..12) { db ->
    db.execSQL("ALTER TABLE `AgentPrompt` ADD COLUMN `autoIncludeDocuments` INTEGER NOT NULL DEFAULT 0")
    db.execSQL("ALTER TABLE `AgentPrompt` ADD COLUMN `autoIncludeCommentaries` INTEGER NOT NULL DEFAULT 0")
}

private val addAskModelBeforeRun = makeMigration(12..13) { db ->
    db.execSQL("ALTER TABLE `GlobalAiSettings` ADD COLUMN `askModelBeforeRun` INTEGER NOT NULL DEFAULT 0")
}

private val addBibleOnly = makeMigration(13..14) { db ->
    db.execSQL("ALTER TABLE `AgentPrompt` ADD COLUMN `bibleOnly` INTEGER NOT NULL DEFAULT 0")
}

private val addIsTextTransformation = makeMigration(14..15) { db ->
    db.execSQL("ALTER TABLE `AgentPrompt` ADD COLUMN `isTextTransformation` INTEGER NOT NULL DEFAULT 0")
}

private val addAiDisclaimerAccepted = makeMigration(15..16) { db ->
    db.execSQL("ALTER TABLE `GlobalAiSettings` ADD COLUMN `aiDisclaimerAccepted` INTEGER NOT NULL DEFAULT 0")
}

private val addPromptCategories = makeMigration(16..17) { db ->
    db.execSQL("""CREATE TABLE IF NOT EXISTS `PromptCategory` (
        `id` BLOB NOT NULL PRIMARY KEY,
        `name` TEXT NOT NULL,
        `orderNumber` INTEGER NOT NULL DEFAULT 0
    )""")
    db.execSQL("ALTER TABLE `AgentPrompt` ADD COLUMN `categoryId` BLOB DEFAULT NULL")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_AgentPrompt_categoryId` ON `AgentPrompt` (`categoryId`)")
}

private val addCategoryHidden = makeMigration(17..18) { db ->
    db.execSQL("ALTER TABLE `PromptCategory` ADD COLUMN `hidden` INTEGER NOT NULL DEFAULT 0")
    db.execSQL("ALTER TABLE `GlobalAiSettings` ADD COLUMN `hiddenBuiltInCategories` TEXT NOT NULL DEFAULT ''")
}

private val addCustomSystemPrompts = makeMigration(18..19) { db ->
    db.execSQL("ALTER TABLE `GlobalAiSettings` ADD COLUMN `customAgentSystemPrompt` TEXT DEFAULT NULL")
    db.execSQL("ALTER TABLE `GlobalAiSettings` ADD COLUMN `customTextTransformationSystemPrompt` TEXT DEFAULT NULL")
}

private val addFavoritePrompts = makeMigration(19..20) { db ->
    db.execSQL("ALTER TABLE `GlobalAiSettings` ADD COLUMN `favoritePrompts` TEXT NOT NULL DEFAULT ''")
}

private val addRawLogTable = makeMigration(20..21) { db ->
    db.execSQL("""CREATE TABLE IF NOT EXISTS `LlmRawLogRecord` (
        `id` BLOB NOT NULL PRIMARY KEY,
        `promptId` BLOB DEFAULT NULL,
        `promptName` TEXT NOT NULL DEFAULT '',
        `promptDescription` TEXT DEFAULT NULL,
        `configuredModelId` BLOB DEFAULT NULL,
        `modelName` TEXT NOT NULL DEFAULT '',
        `providerType` TEXT NOT NULL DEFAULT '',
        `timestamp` INTEGER NOT NULL DEFAULT 0,
        `totalInputTokens` INTEGER NOT NULL DEFAULT 0,
        `totalOutputTokens` INTEGER NOT NULL DEFAULT 0,
        `estimatedCostUsd` REAL NOT NULL DEFAULT 0.0,
        `logData` BLOB NOT NULL,
        `iterationCount` INTEGER NOT NULL DEFAULT 0,
        `wasError` INTEGER NOT NULL DEFAULT 0
    )""")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_LlmRawLogRecord_timestamp` ON `LlmRawLogRecord` (`timestamp`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_LlmRawLogRecord_promptId` ON `LlmRawLogRecord` (`promptId`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_LlmRawLogRecord_configuredModelId` ON `LlmRawLogRecord` (`configuredModelId`)")
    db.execSQL("ALTER TABLE `GlobalAiSettings` ADD COLUMN `rawLogRetentionDays` INTEGER DEFAULT 30")
}

private val addBuiltinPromptOverride = makeMigration(21..22) { db ->
    db.execSQL("""CREATE TABLE IF NOT EXISTS `BuiltinPromptOverride` (
        `id` BLOB NOT NULL PRIMARY KEY,
        `configuredModelId` BLOB DEFAULT NULL,
        FOREIGN KEY(`configuredModelId`) REFERENCES `LlmConfiguredModel`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
    )""")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_BuiltinPromptOverride_configuredModelId` ON `BuiltinPromptOverride` (`configuredModelId`)")
}

private val addAutoHideAgentLog = makeMigration(22..23) { db ->
    db.execSQL("ALTER TABLE `GlobalAiSettings` ADD COLUMN `autoHideAgentLogOnCompletion` INTEGER NOT NULL DEFAULT 0")
}

val aiSettingsMigrations: Array<Migration> = arrayOf(addEditBeforeRun, addNoDocumentCreation, addGlobalAiSettingsAndUsage, setCommentaryTokenDefault, addHiddenBuiltInPrompts, addMaxIterations, addCommentaryDeselected, addConfiguredModels, raiseCommentaryTokenDefault, addAiLanguage, addAutoIncludeFields, addAskModelBeforeRun, addBibleOnly, addIsTextTransformation, addAiDisclaimerAccepted, addPromptCategories, addCategoryHidden, addCustomSystemPrompts, addFavoritePrompts, addRawLogTable, addBuiltinPromptOverride, addAutoHideAgentLog)
