/*
 * Copyright (c) 2020-2026 Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.common

import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.bible.android.BibleApplication

private const val TAG = "SecureStorage"

object SecureStorage {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(BibleApplication.application)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            BibleApplication.application,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getString(key: String, default: String? = null): String? = prefs.getString(key, default)
    fun setString(key: String, value: String?) {
        if (value == null) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putString(key, value).apply()
        }
    }
    fun remove(key: String) { prefs.edit().remove(key).apply() }

    /**
     * One-time migration from plain-text storage to encrypted storage.
     * Moves secrets from CommonUtils.settings (SettingsDatabase) and realSharedPreferences
     * to SecureStorage, and renames gdrive_ settings keys to cloud_sync_ / sync_enable_ prefixes.
     * Idempotent — safe to call on every app start.
     */
    fun migrateFromPlainStorage() {
        val sharedPrefs = CommonUtils.realSharedPreferences
        val settingsDb = CommonUtils.settings

        // Secrets: move from SettingsDatabase/SharedPreferences → SecureStorage (encrypted)
        val secretMigrations = mapOf(
            "gdrive_password" to "cloud_sync_password",
            "gdrive_username" to "cloud_sync_username",
            "gdrive_server_url" to "cloud_sync_server_url",
            "gdrive_folder_path" to "cloud_sync_folder_path",
        )
        for ((oldKey, newKey) in secretMigrations) {
            if (getString(newKey) != null) continue
            // Check SettingsDatabase first (current storage), then SharedPreferences (legacy)
            val value = settingsDb.getString(oldKey) ?: sharedPrefs.getString(oldKey, null)
            if (value != null) {
                Log.i(TAG, "Migrating secret '$oldKey' → '$newKey' to SecureStorage")
                setString(newKey, value)
                settingsDb.removeString(oldKey)
                sharedPrefs.edit().remove(oldKey).apply()
            }
        }

        // lastAccount: move from SharedPreferences → SecureStorage
        if (getString("cloud_sync_last_account") == null) {
            val lastAccount = sharedPrefs.getString("lastAccount", null)
            if (lastAccount != null) {
                Log.i(TAG, "Migrating 'lastAccount' → 'cloud_sync_last_account' to SecureStorage")
                setString("cloud_sync_last_account", lastAccount)
                sharedPrefs.edit().remove("lastAccount").apply()
            }
        }

        // Boolean settings: rename gdrive_* → sync_enable_* (stay in SettingsDatabase)
        val boolRenames = mapOf(
            "gdrive_bookmarks" to "sync_enable_bookmarks",
            "gdrive_workspaces" to "sync_enable_workspaces",
            "gdrive_readingplans" to "sync_enable_readingplans",
            "gdrive_mydocuments" to "sync_enable_mydocuments",
            "gdrive_llmprocessing" to "sync_enable_llmprocessing",
        )
        for ((oldKey, newKey) in boolRenames) {
            val value = settingsDb.getBoolean(oldKey, false)
            if (value) {
                Log.i(TAG, "Renaming boolean setting '$oldKey' → '$newKey'")
                settingsDb.setBoolean(newKey, true)
            }
            settingsDb.removeBoolean(oldKey)
        }

        // Long settings: rename gdrive_sync_interval → cloud_sync_interval
        val oldInterval = settingsDb.getLong("gdrive_sync_interval", Long.MIN_VALUE)
        if (oldInterval != Long.MIN_VALUE) {
            Log.i(TAG, "Renaming long setting 'gdrive_sync_interval' → 'cloud_sync_interval'")
            settingsDb.setLong("cloud_sync_interval", oldInterval)
            settingsDb.removeLong("gdrive_sync_interval")
        }

        // LLM API keys: move from SettingsDatabase → SecureStorage
        // Legacy single key
        val legacyApiKey = settingsDb.getString("llm_api_key")
        if (legacyApiKey != null && legacyApiKey.isNotBlank()) {
            if (getString("llm_api_key") == null) {
                Log.i(TAG, "Migrating legacy 'llm_api_key' to SecureStorage")
                setString("llm_api_key", legacyApiKey)
            }
            settingsDb.removeString("llm_api_key")
        }
    }
}
