/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.view.activity.settings

import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.view.MenuItem
import android.webkit.URLUtil
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.activity.databinding.SettingsDialogBinding
import net.bible.android.control.event.ABEventBus
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.cloud.CloudDocumentsActivity
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.android.view.util.Hourglass
import net.bible.service.common.CommonUtils
import net.bible.service.cloudsync.CloudAdapters
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.cloudsync.CloudSync
import net.bible.service.cloudsync.documents.DocumentSync
import net.bible.service.cloudsync.documents.DocumentSyncService
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.service.cloudsync.documents.DocumentSyncSummary
import net.bible.service.cloudsync.documents.computeDocumentSyncSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SyncSettingsActivity: ActivityBase() {
    private lateinit var binding: SettingsDialogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = SettingsDialogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        super.buildActivityComponent().inject(this)

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, SyncSettingsFragment())
            .commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}

class SyncSettingsFragment: PreferenceFragmentCompat() {
    private fun setupDrivePref(pref: SwitchPreferenceCompat) {
        val category = SyncableDatabaseDefinition.nameToCategory[pref.key.removePrefix("sync_enable_").uppercase()]!!
        pref.setOnPreferenceChangeListener { _, newValue ->
            val enableSync = newValue as Boolean
            if(enableSync) {
                lifecycleScope.launch {
                    val hourglass = Hourglass(requireContext())
                    hourglass.show(R.string.synchronizing)
                    
                    var signInSuccess = CloudSync.signedIn
                    if (!signInSuccess) {
                        signInSuccess = CloudSync.signIn(activity as ActivityBase) == true
                    }
                    
                    if (signInSuccess) {
                        category.syncEnabled = true
                        CloudSync.waitUntilFinished()
                        CloudSync.start()
                        CloudSync.waitUntilFinished()
                        ABEventBus.post(MainBibleActivity.MainBibleAfterRestore())
                    }
                    
                    hourglass.dismiss()
                    activity?.recreate()
                }
                // Return false to prevent the toggle from being updated now
                // The recreate() will refresh the UI with the correct state
                return@setOnPreferenceChangeListener false
            } else {
                // If turning sync off, we can do it immediately
                category.syncEnabled = false
                return@setOnPreferenceChangeListener true
            }
        }
        val lastSyncStr = category.lastSynchronized?.let {
            val sdf = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
            val date = Date(it)
            ".\n\n" + getString(R.string.last_updated, sdf.format(date))
        }?: ""
        pref.summary = "${getString(category.contentDescription)}$lastSyncStr"
    }

    /** Guards the document-sync enable flow so rapid repeated toggles can't launch it twice. */
    private var documentEnableInProgress = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = PreferenceStore()
        setPreferencesFromResource(R.xml.sync_settings, rootKey)
        preferenceScreen.findPreference<SwitchPreferenceCompat>("sync_enable_bookmarks")!!.run { setupDrivePref(this) }
        preferenceScreen.findPreference<SwitchPreferenceCompat>("sync_enable_readingplans")!!.run {
            //setupDrivePref(this!!)
            isVisible = false
        }
        preferenceScreen.findPreference<SwitchPreferenceCompat>("sync_enable_workspaces")!!.run { setupDrivePref(this) }
        preferenceScreen.findPreference<SwitchPreferenceCompat>("sync_enable_mydocuments")!!.run { setupDrivePref(this) }
        preferenceScreen.findPreference<SwitchPreferenceCompat>("sync_enable_ai_settings")!!.run { setupDrivePref(this) }
        preferenceScreen.findPreference<SwitchPreferenceCompat>("sync_enable_progress")!!.run { setupDrivePref(this) }
        preferenceScreen.findPreference<SwitchPreferenceCompat>("sync_enable_documents")!!.run {
            setOnPreferenceChangeListener { _, newValue ->
                val enable = newValue as Boolean
                if (enable) {
                    // Set the guard synchronously so a second tap before the hourglass shows is
                    // ignored — otherwise repeated taps launch the sign-in/scan/dialog flow (and
                    // its background transfer) multiple times in parallel.
                    if (!documentEnableInProgress) {
                        documentEnableInProgress = true
                        lifecycleScope.launch {
                            val hourglass = Hourglass(requireContext())
                            hourglass.show()   // blocks the UI until the dialog appears
                            try {
                                var signedIn = CloudSync.signedIn
                                if (!signedIn) signedIn = CloudSync.signIn(activity as ActivityBase) == true
                                if (signedIn) {
                                    val items = withContext(Dispatchers.IO) { DocumentSync.scan() }
                                    val summary = computeDocumentSyncSummary(items, DocumentSyncSettings.blockList.all())
                                    showEnableDocumentsDialog(summary)
                                }
                            } finally {
                                hourglass.dismiss()
                                documentEnableInProgress = false
                            }
                        }
                    }
                    false   // committed in the dialog's positive button
                } else {
                    DocumentSyncSettings.enabled = false
                    updateDocumentSyncVisibility()
                    true
                }
            }
        }
        preferenceScreen.findPreference<Preference>("document_sync_manage")!!.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), CloudDocumentsActivity::class.java))
            true
        }
        // The auto-operation and Wi-Fi-only toggles persist into DocumentSyncSettings (the
        // DocumentSyncDatabase singleton the sync engine reads), NOT the generic PreferenceStore
        // SharedPreferences — otherwise the UI and the engine would read/write different stores
        // and the toggles would silently have no effect.
        val documentSyncDataStore = DocumentSyncPrefsDataStore()
        for (key in DOCUMENT_SYNC_TOGGLE_KEYS) {
            preferenceScreen.findPreference<SwitchPreferenceCompat>(key)?.preferenceDataStore = documentSyncDataStore
        }
        preferenceScreen.findPreference<SwitchPreferenceCompat>("sync_enable_documents")?.isChecked =
            DocumentSyncSettings.enabled
        updateDocumentSyncVisibility()
        preferenceScreen.findPreference<Preference>("cloud_sync_reset")!!.run {
            if(!CommonUtils.isCloudSyncEnabled || !CloudSync.signedIn) {
                isVisible = false
            }
            setOnPreferenceClickListener {
                lifecycleScope.launch {
                    if(Dialogs.simpleQuestion(requireContext(), message =getString(R.string.sync_confirmation))) {
                        val hourglass = Hourglass(requireContext())
                        hourglass.show()
                        CloudSync.signOut()
                        hourglass.dismiss()
                        activity?.recreate()
                    }
                }
                true
            }
        }
        preferenceScreen.findPreference<Preference>("cloud_sync_info")!!.run {
            if(!CommonUtils.isCloudSyncEnabled || !CloudSync.signedIn) {
                isVisible = false
            } else {
                lifecycleScope.launch {
                    val bytesUsed = CloudSync.bytesUsed()
                    val megaBytesUsed = bytesUsed / (1024.0 * 1024)
                    summary = getString(R.string.cloud_info_summary, String.format("%.2f", megaBytesUsed))
                }
            }
        }
        val secureDataStore = SharedPrefsDataStore()
        val usernamePref = preferenceScreen.findPreference<EditTextPreference>("cloud_sync_username")!!.apply {
            preferenceDataStore = secureDataStore
        }
        val passwordPref = preferenceScreen.findPreference<EditTextPreference>("cloud_sync_password")!!.apply {
            preferenceDataStore = secureDataStore
        }
        val serverUrlPref = preferenceScreen.findPreference<EditTextPreference>("cloud_sync_server_url")!!.apply {
            preferenceDataStore = secureDataStore
        }
        val folderPathPref = preferenceScreen.findPreference<EditTextPreference>("cloud_sync_folder_path")!!.apply {
            preferenceDataStore = secureDataStore
        }

        serverUrlPref.setOnPreferenceChangeListener { _, newValue ->
            val newUrl = newValue as String
            val isHttpOrHttps = newUrl.startsWith("http://") || newUrl.startsWith("https://")
            val hasValidStructure = URLUtil.isValidUrl(newUrl) && isHttpOrHttps && !newUrl.endsWith("/login") && !newUrl.contains(" ")
            
            if (hasValidStructure) {
                true
            } else {
                Dialogs.showErrorMsg(R.string.invalid_url_message)
                false
            }
        }

        preferenceScreen.findPreference<ListPreference>("sync_adapter")!!.run {
            if(CloudSync.signedIn) {
                isEnabled = false
            }
            fun setSummary(newValue: CloudAdapters) {
                val sum1 = getString(R.string.prefs_sync_introduction_summary1)
                val driveSum = getString(R.string.prefs_sync_introduction_summary2, getString(R.string.app_name_medium))
                var result = sum1
                val isGoogleDrive = newValue == CloudAdapters.GOOGLE_DRIVE
                if(isGoogleDrive) {
                    result += " $driveSum"
                }
                usernamePref.isVisible = !isGoogleDrive
                passwordPref.isVisible = !isGoogleDrive
                serverUrlPref.isVisible = !isGoogleDrive
                folderPathPref.isVisible = !isGoogleDrive
                if(CloudSync.signedIn) {
                    usernamePref.isEnabled = false
                    passwordPref.isEnabled = false
                    serverUrlPref.isEnabled = false
                    folderPathPref.isEnabled = false
                }
                result += " " + getString(R.string.sync_adapter_summary, newValue.displayName)
                summary = result
            }
            setSummary(CloudAdapters.current)
            entryValues = CloudAdapters.allEnabled.map { it.name }.toTypedArray()
            entries = CloudAdapters.allEnabled.map { it.displayName }.toTypedArray()
            setOnPreferenceChangeListener { _, newValue ->
                setSummary(CloudAdapters.valueOf(newValue as String))
                true
            }
        }
    }

    private fun showEnableDocumentsDialog(summary: DocumentSyncSummary) {
        val ctx = requireContext()
        val message = if (summary.isEmpty) {
            getString(R.string.document_sync_enable_dialog_nothing)
        } else {
            // Only mention a direction that has items, and only show its size when known
            // (a local-only document with no declared install size reports 0 bytes).
            val parts = buildList {
                if (summary.uploadCount > 0) add(
                    if (summary.uploadBytes > 0)
                        getString(R.string.cloud_doc_summary_upload_size, summary.uploadCount, Formatter.formatShortFileSize(ctx, summary.uploadBytes))
                    else getString(R.string.cloud_doc_summary_upload, summary.uploadCount)
                )
                if (summary.downloadCount > 0) add(
                    if (summary.downloadBytes > 0)
                        getString(R.string.cloud_doc_summary_download_size, summary.downloadCount, Formatter.formatShortFileSize(ctx, summary.downloadBytes))
                    else getString(R.string.cloud_doc_summary_download, summary.downloadCount)
                )
            }
            var m = parts.joinToString("\n")
            if (DocumentSyncSettings.wifiOnly && CommonUtils.isMeteredNetwork) {
                m += "\n" + getString(R.string.cloud_doc_wifi_waiting)
            }
            m
        }
        AlertDialog.Builder(ctx)
            .setTitle(R.string.document_sync_enable_dialog_title)
            .setMessage(message)
            .setPositiveButton(R.string.okay) { _, _ ->
                DocumentSyncSettings.enabled = true
                DocumentSyncService.start(ctx, summary.uploadInitials, summary.downloadInitials)
                updateDocumentSyncVisibility()
                preferenceScreen.findPreference<SwitchPreferenceCompat>("sync_enable_documents")?.isChecked = true
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateDocumentSyncVisibility() {
        // signedIn is used as a proxy for "a cloud adapter is configured": CloudSync.cloudAdapter
        // is internal and there is no persistent configured-but-signed-out adapter state.
        // The whole Document sync category shows only when signed in, so Synced documents stays
        // reachable for manual sync even when automatic sync is off. Wi-Fi-only is relevant only
        // while automatic sync is enabled.
        preferenceScreen.findPreference<PreferenceCategory>("document_sync_category")?.isVisible =
            CloudSync.signedIn
        preferenceScreen.findPreference<SwitchPreferenceCompat>("sync_documents_wifi_only")?.isVisible =
            DocumentSyncSettings.enabled
        for (key in listOf("sync_documents_auto_download", "sync_documents_auto_upload", "sync_documents_auto_delete")) {
            preferenceScreen.findPreference<SwitchPreferenceCompat>(key)?.isVisible = DocumentSyncSettings.enabled
        }
    }

    override fun onResume() {
        super.onResume()
        preferenceScreen.findPreference<SwitchPreferenceCompat>("sync_enable_documents")?.isChecked =
            DocumentSyncSettings.enabled
        updateDocumentSyncVisibility()
    }
}

private class SharedPrefsDataStore : PreferenceDataStore() {
    private val prefs get() = CommonUtils.realSharedPreferences
    override fun putString(key: String, value: String?) {
        if (value == null) prefs.edit().remove(key).apply()
        else prefs.edit().putString(key, value).apply()
    }
    override fun getString(key: String, defValue: String?): String? = prefs.getString(key, defValue)
}

/** Preference keys whose value lives in [DocumentSyncSettings], not the generic PreferenceStore. */
private val DOCUMENT_SYNC_TOGGLE_KEYS = listOf(
    "sync_documents_auto_download",
    "sync_documents_auto_upload",
    "sync_documents_auto_delete",
    "sync_documents_wifi_only",
)

/**
 * Routes the document-sync toggle preferences to [DocumentSyncSettings] (backed by the
 * DocumentSyncDatabase singleton), so the switches in the UI and the values the sync engine
 * reads are one and the same store.
 */
private class DocumentSyncPrefsDataStore : PreferenceDataStore() {
    override fun getBoolean(key: String, defValue: Boolean): Boolean = when (key) {
        "sync_documents_auto_download" -> DocumentSyncSettings.autoDownload
        "sync_documents_auto_upload" -> DocumentSyncSettings.autoUpload
        "sync_documents_auto_delete" -> DocumentSyncSettings.autoDelete
        "sync_documents_wifi_only" -> DocumentSyncSettings.wifiOnly
        else -> defValue
    }

    override fun putBoolean(key: String, value: Boolean) {
        when (key) {
            "sync_documents_auto_download" -> DocumentSyncSettings.autoDownload = value
            "sync_documents_auto_upload" -> DocumentSyncSettings.autoUpload = value
            "sync_documents_auto_delete" -> DocumentSyncSettings.autoDelete = value
            "sync_documents_wifi_only" -> DocumentSyncSettings.wifiOnly = value
        }
    }
}
