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

val aiSettingsMigrations: Array<Migration> = arrayOf(addEditBeforeRun, addNoDocumentCreation, addGlobalAiSettingsAndUsage, setCommentaryTokenDefault)
