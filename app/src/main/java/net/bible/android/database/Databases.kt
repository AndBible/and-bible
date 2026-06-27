/*
 * Copyright (c) 2023-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.bible.android.database.bookmarks.BookmarkDao
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.migrations.BOOKMARK_DATABASE_VERSION
import net.bible.android.database.migrations.Migration
import net.bible.android.database.migrations.READING_PLAN_DATABASE_VERSION
import net.bible.android.database.migrations.WORKSPACE_DATABASE_VERSION
import net.bible.android.database.readingplan.ReadingPlanDao
import net.bible.android.database.readingplan.ReadingPlanEntities
import net.bible.service.llm.AgentPrompt
import net.bible.service.llm.AgentPromptDao
import net.bible.service.llm.BuiltinPromptOverride
import net.bible.service.llm.BuiltinPromptOverrideDao
import net.bible.service.llm.GlobalAiSettings
import net.bible.service.llm.GlobalAiSettingsDao
import net.bible.service.llm.LlmConfiguredModel
import net.bible.service.llm.LlmConfiguredModelDao
import net.bible.service.llm.LlmProviderConfig
import net.bible.service.llm.LlmProviderConfigDao
import net.bible.service.llm.LlmRawLogRecord
import net.bible.service.llm.LlmRawLogRecordDao
import net.bible.service.llm.LlmUsageRecord
import net.bible.service.llm.LlmUsageRecordDao
import net.bible.service.llm.PromptCategory
import net.bible.service.llm.PromptCategoryDao


@Database(
    entities = [
        BookmarkEntities.BibleBookmark::class,
        BookmarkEntities.BibleBookmarkNotes::class,
        BookmarkEntities.BibleBookmarkToLabel::class,
        BookmarkEntities.GenericBookmark::class,
        BookmarkEntities.GenericBookmarkNotes::class,
        BookmarkEntities.GenericBookmarkToLabel::class,
        BookmarkEntities.Label::class,
        BookmarkEntities.StudyPadTextEntry::class,
        BookmarkEntities.StudyPadTextEntryText::class,
        LogEntry::class,
        SyncConfiguration::class,
        SyncStatus::class,
    ],
    views = [
        BookmarkEntities.BibleBookmarkWithNotes::class,
        BookmarkEntities.GenericBookmarkWithNotes::class,
        BookmarkEntities.StudyPadTextEntryWithText::class,
    ],
    version = BOOKMARK_DATABASE_VERSION
)
@TypeConverters(Converters::class)
abstract class BookmarkDatabase: SyncableRoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    companion object {
        const val dbFileName = "bookmarks.sqlite3"
    }
}

@Database(
    entities = [
        ReadingPlanEntities.ReadingPlan::class,
        ReadingPlanEntities.ReadingPlanStatus::class,
        LogEntry::class,
        SyncConfiguration::class,
        SyncStatus::class,
    ],
    version = READING_PLAN_DATABASE_VERSION
)
@TypeConverters(Converters::class)
abstract class ReadingPlanDatabase: SyncableRoomDatabase() {
    abstract fun readingPlanDao(): ReadingPlanDao
    companion object {
        const val dbFileName = "readingplans.sqlite3"
    }
}

@Database(
    entities = [
        WorkspaceEntities.Workspace::class,
        WorkspaceEntities.Window::class,
        WorkspaceEntities.HistoryItem::class,
        WorkspaceEntities.PageManager::class,
        WorkspaceEntities.WorkspaceLabelOverride::class,
        GlobalTextDisplaySettings::class,
        LogEntry::class,
        SyncConfiguration::class,
        SyncStatus::class,
    ],
    version = WORKSPACE_DATABASE_VERSION
)
@TypeConverters(Converters::class)
abstract class WorkspaceDatabase: SyncableRoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun globalTextDisplaySettingsDao(): GlobalTextDisplaySettingsDao
    companion object {
        const val dbFileName = "workspaces.sqlite3"
    }
}

val temporaryMigrations: Array<Migration> = arrayOf()

const val TEMPORARY_DATABASE_VERSION = 1

/**
 * Ephemeral search-index scratch space. Instantiated against two separate files (the download list
 * and the document-selection list) so their searches don't clobber each other — same schema, two
 * independent data spaces. Never backed up, never synced.
 */
@Database(
    entities = [
        DocumentSearch::class,
    ],
    version = TEMPORARY_DATABASE_VERSION
)
@TypeConverters(Converters::class)
abstract class TemporaryDatabase: RoomDatabase() {
    abstract fun documentSearchDao(): DocumentSearchDao
}

const val DOCUMENT_SYNC_DATABASE_VERSION = 1

/**
 * Home for all per-device document-sync state — settings, the cloud-listing cache, the listing
 * watermark, and per-document sync timestamps. Entirely device-local: never backed up (absent from
 * ALL_DB_FILENAMES), never synced (absent from SyncableDatabaseDefinition), and wiped on cloud
 * sign-out. Document-sync setup is re-established per device (the cloud account itself is
 * device-local), so there is nothing here to back up or sync.
 *
 * Kept separate from [TemporaryDatabase]: that one is single-purpose search scratch with its own
 * (multi-file) lifecycle, and conflating the two schemas in one class would force every file
 * instantiated from it to carry the union of both schemas plus unused DAOs.
 */
@Database(
    entities = [
        CachedCloudDocument::class,
        DocumentSyncPreferences::class,
        CloudListingState::class,
        CloudDocumentSyncTimestamp::class,
    ],
    version = DOCUMENT_SYNC_DATABASE_VERSION
)
@TypeConverters(Converters::class)
abstract class DocumentSyncDatabase: RoomDatabase() {
    abstract fun cloudDocumentCacheDao(): CloudDocumentCacheDao
    abstract fun documentSyncPreferencesDao(): DocumentSyncPreferencesDao
    abstract fun cloudListingStateDao(): CloudListingStateDao
    abstract fun cloudDocumentSyncTimestampDao(): CloudDocumentSyncTimestampDao
}

const val REPO_DATABASE_VERSION = 1

@Database(
    entities = [
        CustomRepository::class,
        SwordDocumentInfo::class,
    ],
    version = REPO_DATABASE_VERSION
)
@TypeConverters(Converters::class)
abstract class RepoDatabase: RoomDatabase() {
    abstract fun swordDocumentInfoDao(): SwordDocumentInfoDao
    abstract fun customRepositoryDao(): CustomRepositoryDao
    companion object {
        const val dbFileName = "repositories.sqlite3"
    }
}

const val SETTINGS_DATABASE_VERSION = 1

@Database(
    entities = [
        BooleanSetting::class,
        StringSetting::class,
        LongSetting::class,
        DoubleSetting::class,
    ],
    version = SETTINGS_DATABASE_VERSION
)
@TypeConverters(Converters::class)
abstract class SettingsDatabase: RoomDatabase() {
    abstract fun booleanSettingDao(): BooleanSettingDao
    abstract fun stringSettingDao(): StringSettingDao
    abstract fun longSettingDao(): LongSettingDao
    abstract fun doubleSettingDao(): DoubleSettingDao
    companion object {
        const val dbFileName = "settings.sqlite3"
    }
}

const val AI_SETTINGS_DATABASE_VERSION = 23

@Database(
    entities = [
        AgentPrompt::class,
        LlmProviderConfig::class,
        LlmConfiguredModel::class,
        GlobalAiSettings::class,
        LlmUsageRecord::class,
        LlmRawLogRecord::class,
        PromptCategory::class,
        BuiltinPromptOverride::class,
        LogEntry::class,
        SyncConfiguration::class,
        SyncStatus::class,
    ],
    version = AI_SETTINGS_DATABASE_VERSION
)
@TypeConverters(Converters::class)
abstract class AiSettingsDatabase: SyncableRoomDatabase() {
    abstract fun agentPromptDao(): AgentPromptDao
    abstract fun llmProviderConfigDao(): LlmProviderConfigDao
    abstract fun llmConfiguredModelDao(): LlmConfiguredModelDao
    abstract fun globalAiSettingsDao(): GlobalAiSettingsDao
    abstract fun llmUsageRecordDao(): LlmUsageRecordDao
    abstract fun llmRawLogRecordDao(): LlmRawLogRecordDao
    abstract fun promptCategoryDao(): PromptCategoryDao
    abstract fun builtinPromptOverrideDao(): BuiltinPromptOverrideDao
    companion object {
        const val dbFileName = "ai_settings.sqlite3"
    }
}
