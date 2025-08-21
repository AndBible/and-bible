/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.view.activity.speak

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import net.bible.android.activity.R
import net.bible.android.activity.databinding.SpeakSettingsBinding
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.speak.*
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.android.database.bookmarks.VoiceSelectionMode
import net.bible.android.view.activity.ActivityScope
import net.bible.service.common.AdvancedSpeakSettings
import net.bible.service.common.automaticSpeakBookmarkingVideo
import net.bible.service.common.htmlToSpan
import net.bible.service.device.speak.VoiceManager
import javax.inject.Inject

@ActivityScope
class SpeakSettingsActivity : AbstractSpeakActivity() {
    companion object {
        const val TAG = "SpeakSettingsActivity"
    }

    lateinit var binding: SpeakSettingsBinding
    private var voiceAdapter: ArrayAdapter<String>? = null
    private var availableVoices: List<VoiceManager.VoiceInfo> = emptyList()
    private var currentLanguageCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SpeakSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        super.buildActivityComponent().inject(this)
        ABEventBus.register(this)
        
        setupVoiceSelection()
        resetView(SpeakSettings.load())
        
        binding.apply {
            synchronize.setOnClickListener { updateSettings() }
            replaceDivineName.setOnClickListener { updateSettings() }
            useSystemDefaultVoice.setOnClickListener { updateSettings() }
            autoBookmark.setOnClickListener { updateSettings() }
            restoreSettingsFromBookmarks.setOnClickListener { updateSettings() }
            
            // Voice selection listeners
            voiceSelectionGroup.setOnCheckedChangeListener { _, _ -> 
                updateVoiceSelectionUI()
                updateSettings()
            }
            
            voiceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    updateSettings()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
    }
    
    private fun setupVoiceSelection() {
        // Initialize voice spinner
        voiceAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mutableListOf<String>())
        voiceAdapter?.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.voiceSpinner.adapter = voiceAdapter
        
        // Load available voices for current document language
        loadAvailableVoices()
    }
    
    private fun loadAvailableVoices() {
        try {
            // Get current book language
            val currentBook = speakControl.ttsServiceManager?.currentlyPlayingBook
            currentLanguageCode = currentBook?.language?.code ?: "en"
            
            // Get available voices for this language
            availableVoices = speakControl.ttsServiceManager?.getAvailableVoicesForLanguage(currentLanguageCode!!) ?: emptyList()
            
            // Update spinner with voice names
            val voiceNames = availableVoices.map { it.displayName }
            voiceAdapter?.clear()
            voiceAdapter?.addAll(voiceNames)
            voiceAdapter?.notifyDataSetChanged()
            
            // Show message if no voices available
            if (availableVoices.isEmpty()) {
                voiceAdapter?.add(getString(R.string.no_voices_available))
                binding.voiceSpinner.isEnabled = false
            } else {
                binding.voiceSpinner.isEnabled = true
            }
        } catch (e: Exception) {
            // Handle gracefully - TTS might not be initialized yet
            voiceAdapter?.clear()
            voiceAdapter?.add(getString(R.string.no_voices_available))
            binding.voiceSpinner.isEnabled = false
        }
    }
    
    private fun updateVoiceSelectionUI() {
        val showVoiceSelection = binding.manualVoiceSelectionOption.isChecked
        binding.voiceSelectionLayout.visibility = if (showVoiceSelection) View.VISIBLE else View.GONE
    }

    override val sleepTimer: CheckBox? = null

    override fun onDestroy() {
        ABEventBus.unregister(this)
        super.onDestroy()
    }

    override fun resetView(settings: SpeakSettings) {
        binding.apply {
            synchronize.isChecked = AdvancedSpeakSettings.synchronize
            replaceDivineName.isChecked = AdvancedSpeakSettings.replaceDivineName
            useSystemDefaultVoice.isChecked = settings.playbackSettings.useSystemDefaultVoice
            restoreSettingsFromBookmarks.isChecked = AdvancedSpeakSettings.restoreSettingsFromBookmarks
            autoBookmark.isChecked = AdvancedSpeakSettings.autoBookmark
            
            // Set voice selection mode
            val voiceMode = if (settings.playbackSettings.useSystemDefaultVoice) {
                VoiceSelectionMode.SYSTEM_DEFAULT
            } else {
                settings.playbackSettings.voiceSelectionMode
            }
            
            when (voiceMode) {
                VoiceSelectionMode.SYSTEM_DEFAULT -> systemDefaultVoiceOption.isChecked = true
                VoiceSelectionMode.LANGUAGE_SPECIFIC -> languageSpecificVoiceOption.isChecked = true
                VoiceSelectionMode.MANUAL_SELECTION -> manualVoiceSelectionOption.isChecked = true
            }
            
            // Set selected voice in spinner
            val selectedVoiceName = settings.playbackSettings.selectedVoiceName
            if (selectedVoiceName != null) {
                val voiceIndex = availableVoices.indexOfFirst { it.name == selectedVoiceName }
                if (voiceIndex >= 0) {
                    voiceSpinner.setSelection(voiceIndex)
                }
            }
            
            updateVoiceSelectionUI()
        }
    }

    fun onEventMainThread(ev: SpeakSettingsChangedEvent) {
        currentSettings = ev.speakSettings
        resetView(ev.speakSettings)
    }

    fun updateSettings() {
        val voiceSelectionMode = when {
            binding.systemDefaultVoiceOption.isChecked -> VoiceSelectionMode.SYSTEM_DEFAULT
            binding.languageSpecificVoiceOption.isChecked -> VoiceSelectionMode.LANGUAGE_SPECIFIC
            binding.manualVoiceSelectionOption.isChecked -> VoiceSelectionMode.MANUAL_SELECTION
            else -> VoiceSelectionMode.SYSTEM_DEFAULT
        }
        
        // Get selected voice for manual selection
        val selectedVoiceName = if (voiceSelectionMode == VoiceSelectionMode.MANUAL_SELECTION 
            && binding.voiceSpinner.selectedItemPosition >= 0 
            && binding.voiceSpinner.selectedItemPosition < availableVoices.size) {
            availableVoices[binding.voiceSpinner.selectedItemPosition].name
        } else {
            null
        }
        
        val settings = SpeakSettings.load().apply {
            sleepTimer = currentSettings.sleepTimer
            lastSleepTimer = currentSettings.lastSleepTimer
            playbackSettings = playbackSettings.copy(
                useSystemDefaultVoice = voiceSelectionMode == VoiceSelectionMode.SYSTEM_DEFAULT, // For backwards compatibility
                voiceSelectionMode = voiceSelectionMode,
                selectedVoiceName = selectedVoiceName
            )
        }
        
        binding.apply {
            AdvancedSpeakSettings.synchronize = synchronize.isChecked
            AdvancedSpeakSettings.autoBookmark = autoBookmark.isChecked
            AdvancedSpeakSettings.replaceDivineName = replaceDivineName.isChecked
            AdvancedSpeakSettings.restoreSettingsFromBookmarks = restoreSettingsFromBookmarks.isChecked
        }
        
        settings.save(updateBookmark = true)
        resetView(settings)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.speak_bible_actionbar_menu, menu)
        menu.findItem(R.id.systemSettings).isVisible = false
        menu.findItem(R.id.advancedSettings).isVisible = false
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.help -> {
                onHelpButtonClick()
                return true
            }
        }
        return false
    }

    fun onHelpButtonClick() {
        val htmlMessage = (
                "<b>${getString(R.string.conf_speak_auto_bookmark)}</b><br><br>"
                + "<b><a href=\"$automaticSpeakBookmarkingVideo\">"
                + "${getString(R.string.watch_tutorial_video)}</a></b><br><br>"
                + getString(R.string.speak_help_auto_bookmark)
                + "<br><br><b>${getString(R.string.conf_save_playback_settings_to_bookmarks)}</b><br><br>"
                + getString(R.string.speak_help_playback_settings)
                + "<br><br>"
                + getString(R.string.speak_help_playback_settings_example)
                )

        val spanned = htmlToSpan(htmlMessage)

        val d = AlertDialog.Builder(this)
                .setMessage(spanned)
                .setPositiveButton(android.R.string.ok) { _, _ ->  }
                .create()

        d.show()
        d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
    }
}
