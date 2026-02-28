/*
 * Copyright (c) 2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

private val addAgentPromptTable = makeMigration(1..2) { db ->
    db.execSQL("""
        CREATE TABLE IF NOT EXISTS `AgentPrompt` (
            `id` BLOB NOT NULL,
            `name` TEXT NOT NULL,
            `description` TEXT DEFAULT NULL,
            `promptTemplate` TEXT NOT NULL,
            `showIn` TEXT NOT NULL,
            `orderNumber` INTEGER NOT NULL DEFAULT 0,
            `createdAt` INTEGER NOT NULL DEFAULT 0,
            PRIMARY KEY(`id`)
        )
    """.trimIndent())
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_AgentPrompt_orderNumber` ON `AgentPrompt` (`orderNumber`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_AgentPrompt_createdAt` ON `AgentPrompt` (`createdAt`)")
}

private val addStrictContextMatching = makeMigration(2..3) { db ->
    db.execSQL("ALTER TABLE `AgentPrompt` ADD COLUMN `strictContextMatching` INTEGER NOT NULL DEFAULT 1")
}

private val addPermissionMode = makeMigration(3..4) { db ->
    db.execSQL("ALTER TABLE `AgentPrompt` ADD COLUMN `permissionMode` TEXT DEFAULT NULL")
}

private val addPromptToolPermissions = makeMigration(4..5) { db ->
    db.execSQL("ALTER TABLE `AgentPrompt` ADD COLUMN `allowedTools` TEXT DEFAULT NULL")
    db.execSQL("ALTER TABLE `AgentPrompt` ADD COLUMN `deniedTools` TEXT DEFAULT NULL")
}

private val addModelOverride = makeMigration(5..6) { db ->
    db.execSQL("ALTER TABLE `AgentPrompt` ADD COLUMN `modelOverride` TEXT DEFAULT NULL")
}

private val addLanguageCode = makeMigration(6..7) { db ->
    db.execSQL("ALTER TABLE `LlmProcessingCacheEntry` ADD COLUMN `languageCode` TEXT DEFAULT NULL")
}

private val addProviderConfig = makeMigration(7..8) { db ->
    db.execSQL("""
        CREATE TABLE IF NOT EXISTS `LlmProviderConfig` (
            `id` BLOB NOT NULL,
            `providerType` TEXT NOT NULL,
            `displayName` TEXT NOT NULL,
            `endpoint` TEXT DEFAULT NULL,
            `apiFormat` TEXT DEFAULT NULL,
            `defaultModel` TEXT DEFAULT NULL,
            `isDefault` INTEGER NOT NULL DEFAULT 0,
            `orderNumber` INTEGER NOT NULL DEFAULT 0,
            PRIMARY KEY(`id`)
        )
    """.trimIndent())
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_LlmProviderConfig_orderNumber` ON `LlmProviderConfig` (`orderNumber`)")

    // SQLite does not support ALTER TABLE ADD FOREIGN KEY, so we must recreate
    // the AgentPrompt table to add the FK constraint on providerConfigId.
    db.execSQL("""
        CREATE TABLE IF NOT EXISTS `AgentPrompt_new` (
            `id` BLOB NOT NULL,
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
            `modelOverride` TEXT DEFAULT NULL,
            `providerConfigId` BLOB DEFAULT NULL,
            PRIMARY KEY(`id`),
            FOREIGN KEY(`providerConfigId`) REFERENCES `LlmProviderConfig`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
        )
    """.trimIndent())
    db.execSQL("""
        INSERT INTO `AgentPrompt_new` (`id`, `name`, `description`, `promptTemplate`, `showIn`,
            `orderNumber`, `createdAt`, `strictContextMatching`, `permissionMode`,
            `allowedTools`, `deniedTools`, `modelOverride`, `providerConfigId`)
        SELECT `id`, `name`, `description`, `promptTemplate`, `showIn`,
            `orderNumber`, `createdAt`, `strictContextMatching`, `permissionMode`,
            `allowedTools`, `deniedTools`, `modelOverride`, NULL
        FROM `AgentPrompt`
    """.trimIndent())
    db.execSQL("DROP TABLE `AgentPrompt`")
    db.execSQL("ALTER TABLE `AgentPrompt_new` RENAME TO `AgentPrompt`")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_AgentPrompt_orderNumber` ON `AgentPrompt` (`orderNumber`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_AgentPrompt_createdAt` ON `AgentPrompt` (`createdAt`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_AgentPrompt_providerConfigId` ON `AgentPrompt` (`providerConfigId`)")
}

val llmProcessingMigrations: Array<Migration> = arrayOf(
    addAgentPromptTable,
    addStrictContextMatching,
    addPermissionMode,
    addPromptToolPermissions,
    addModelOverride,
    addLanguageCode,
    addProviderConfig,
)
