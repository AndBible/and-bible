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

val llmProcessingMigrations: Array<Migration> = arrayOf(
    addAgentPromptTable,
    addStrictContextMatching,
    addPermissionMode,
    addPromptToolPermissions,
    addModelOverride,
)
