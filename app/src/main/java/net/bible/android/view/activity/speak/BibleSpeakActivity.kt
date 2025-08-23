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

import android.annotation.SuppressLint
import androidx.appcompat.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import net.bible.android.activity.R
import net.bible.android.activity.databinding.SpeakBibleBinding
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import net.bible.android.control.navigation.NavigationControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.speak.*
import net.bible.android.database.bookmarks.PlaybackSettings
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.android.database.bookmarks.VoiceSelectionMode
import net.bible.android.view.activity.ActivityScope
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.navigation.GridChoosePassageBook
import net.bible.service.common.htmlToSpan
import net.bible.service.common.speakHelpVideo
import net.bible.service.device.speak.VoiceManager
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseFactory
import org.crosswire.jsword.passage.VerseRange
import javax.inject.Inject

@ActivityScope
class BibleSpeakActivity : AbstractSpeakActivity() {
    @Inject lateinit var windowControl: WindowControl
    @Inject lateinit var navigationControl: NavigationControl

    lateinit var binding: SpeakBibleBinding
    
    private var voiceAdapter: ArrayAdapter<String>? = null
    private var availableVoices: List<VoiceManager.VoiceInfo> = emptyList()
    private var currentLanguageCode: String? = null
    private var lastDocumentLanguageCode: String? = null

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SpeakBibleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        buildActivityComponent().inject(this)
        ABEventBus.register(this)
        binding.apply {
            speakSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    speedStatus.text = "$progress %"
                    if (fromUser) {
                        updateSettings()
                    }
                }
            })
            speakChapterChanges.setOnClickListener { updateSettings() }
            speakTitles.setOnClickListener { updateSettings() }
            speakFootnotes.setOnClickListener { updateSettings() }
            repeatPassageCheckbox.setOnClickListener { setRepeatPassage() }
            sleepTimer.setOnClickListener { setSleepTime() }
            
            // Custom voice checkbox listener
            customVoiceCheckbox.setOnClickListener { 
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
        
        setupVoiceSelection()
        resetView(SpeakSettings.load())
    }

    override val sleepTimer: CheckBox get() = binding.sleepTimer

    override fun onDestroy() {
        ABEventBus.unregister(this)
        super.onDestroy()
    }

    override fun resetView(settings: SpeakSettings) = binding.run {
        speakChapterChanges.isChecked = settings.playbackSettings.speakChapterChanges
        speakTitles.isChecked = settings.playbackSettings.speakTitles
        speakFootnotes.isChecked = settings.playbackSettings.speakFootnotes
        speakSpeed.progress = settings.playbackSettings.speed
        speedStatus.text = "${settings.playbackSettings.speed} %"
        sleepTimer.isChecked = settings.sleepTimer > 0
        sleepTimer.text = if(settings.sleepTimer>0) getString(R.string.sleep_timer_set, settings.sleepTimer) else getString(R.string.conf_speak_sleep_timer)
        repeatPassageCheckbox.text = settings.playbackSettings.verseRange?.name?: getString(R.string.speak_verse_range_to_repeat)
        repeatPassageCheckbox.isChecked = settings.playbackSettings.verseRange != null
        
        // Set voice selection mode 
        val voiceMode = if (settings.playbackSettings.useSystemDefaultVoice) {
            // Backwards compatibility: old system default becomes language-specific
            VoiceSelectionMode.LANGUAGE_SPECIFIC
        } else {
            settings.playbackSettings.voiceSelectionMode
        }
        
        // Set custom voice checkbox based on mode
        customVoiceCheckbox.isChecked = (voiceMode == VoiceSelectionMode.MANUAL_SELECTION)
        
        // Check for language change that should disable custom voice
        checkAndHandleLanguageChange()
        
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
            val currentBook = speakControl.ttsServiceManager.currentlyPlayingBook
            currentLanguageCode = currentBook?.language?.code ?: "en"
            
            // Get available voices for this language
            availableVoices = speakControl.ttsServiceManager.getAvailableVoicesForLanguage(currentLanguageCode!!)
            
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
        val showVoiceSelection = binding.customVoiceCheckbox.isChecked
        binding.voiceSelectionLayout.visibility = if (showVoiceSelection) View.VISIBLE else View.GONE
    }
    
    private fun checkAndHandleLanguageChange() {
        try {
            // Get current document language
            val currentBook = speakControl.ttsServiceManager.currentlyPlayingBook
            val newLanguageCode = currentBook?.language?.code ?: "en"
            
            // If language changed and custom voice is enabled, check if voice is compatible
            if (lastDocumentLanguageCode != null && lastDocumentLanguageCode != newLanguageCode && binding.customVoiceCheckbox.isChecked) {
                val settings = SpeakSettings.load()
                val selectedVoiceName = settings.playbackSettings.selectedVoiceName
                
                if (selectedVoiceName != null) {
                    val voiceLanguage = speakControl.ttsServiceManager.getVoiceLanguage(selectedVoiceName)
                    
                    // If voice language doesn't match document language, disable custom voice
                    if (voiceLanguage != null && voiceLanguage != newLanguageCode) {
                        Log.i(TAG, "Document language changed from $lastDocumentLanguageCode to $newLanguageCode, voice language is $voiceLanguage - disabling custom voice")
                        binding.customVoiceCheckbox.isChecked = false
                        updateVoiceSelectionUI()
                        // Update settings will be called by the checkbox listener
                        
                        // Show toast to inform user
                        Toast.makeText(this, "Custom voice disabled due to language change", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            
            lastDocumentLanguageCode = newLanguageCode
            currentLanguageCode = newLanguageCode
            
            // Reload voices for new language
            loadAvailableVoices()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking language change", e)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.speak_bible_actionbar_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.advancedSettings -> {
                startActivity(Intent(this, SpeakSettingsActivity::class.java))
                return true
            }
            R.id.systemSettings -> {
                startActivity(Intent("com.android.settings.TTS_SETTINGS"))
                return true
            }
            R.id.help -> {
                onHelpButtonClick()
                return true
            }
        }
        return false
    }

    fun onEventMainThread(ev: SpeakSettingsChangedEvent) {
        currentSettings = ev.speakSettings
        resetView(ev.speakSettings)
    }

    fun onHelpButtonClick() {
        val htmlMessage = (
                "<b>${getString(R.string.speak)}</b><br><br>"
                + "<b><a href=\"$speakHelpVideo\">"
                + "${getString(R.string.watch_tutorial_video)}</a></b>"
                )

        val spanned = htmlToSpan(htmlMessage)

        val d = AlertDialog.Builder(this)
                .setMessage(spanned)
                .setPositiveButton(android.R.string.ok) { _, _ ->  }
                .create()

        d.show()
        d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
    }

    fun setRepeatPassage() {
        val s = SpeakSettings.load()
        if(s.playbackSettings.verseRange != null) {
            s.playbackSettings.verseRange = null
            s.save(updateBookmark = true)
        }
        else {
            val intent = Intent(this, GridChoosePassageBook::class.java)
            intent.putExtra("isScripture", true)
            intent.putExtra("navigateToVerse", true)
            intent.putExtra("title", getString(R.string.speak_beginning_of_passage))
            startVerse = null
            endVerse = null
            binding.repeatPassageCheckbox.isChecked = false // not yet!
            startActivityForResult(intent, ActivityBase.STD_REQUEST_CODE)
        }
    }

    private var startVerse: Verse? = null
    private var endVerse: Verse? = null

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.i(TAG, "Activity result:$resultCode")
        val verseStr = data?.extras?.getString("verse")
        val v11n = navigationControl.versification
        if(verseStr != null) {
            val verse = VerseFactory.fromString(v11n, verseStr)
            if(startVerse == null) {
                startVerse = verse
                val intent = Intent(this, GridChoosePassageBook::class.java)
                intent.putExtra("isScripture", true)
                intent.putExtra("navigateToVerse", true)
                intent.putExtra("title", getString(R.string.speak_ending_of_passage))
                startActivityForResult(intent, STD_REQUEST_CODE)
            }
            else {
                endVerse = verse
                val settings = SpeakSettings.load()
                if(endVerse!!.ordinal > startVerse!!.ordinal){
                    val verseRange = VerseRange(v11n, startVerse, endVerse)
                    settings.playbackSettings.verseRange = verseRange
                    settings.save(updateBookmark = true)
                }
                else {
                    startVerse = null
                    endVerse = null
                    ABEventBus.post(ToastEvent(R.string.speak_ending_verse_must_be_later))
                    resetView(settings)
                }
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }


    fun updateSettings() {
        val voiceSelectionMode = if (binding.customVoiceCheckbox.isChecked) {
            VoiceSelectionMode.MANUAL_SELECTION
        } else {
            VoiceSelectionMode.LANGUAGE_SPECIFIC
        }
        
        // Get selected voice for manual selection
        val selectedVoiceName = if (voiceSelectionMode == VoiceSelectionMode.MANUAL_SELECTION 
            && binding.voiceSpinner.selectedItemPosition >= 0 
            && binding.voiceSpinner.selectedItemPosition < availableVoices.size) {
            availableVoices[binding.voiceSpinner.selectedItemPosition].name
        } else {
            null
        }
        
        val settings = SpeakSettings.load()
        settings.apply {
            playbackSettings = PlaybackSettings(
                speakChapterChanges = binding.speakChapterChanges.isChecked,
                speakTitles = binding.speakTitles.isChecked,
                speakFootnotes = binding.speakFootnotes.isChecked,
                speed = binding.speakSpeed.progress,
                verseRange = settings.playbackSettings.verseRange,
                useSystemDefaultVoice = false, // Always false now - backwards compatibility
                voiceSelectionMode = voiceSelectionMode,
                selectedVoiceName = selectedVoiceName
            )
            sleepTimer = currentSettings.sleepTimer
            lastSleepTimer = currentSettings.lastSleepTimer
            save(updateBookmark = true)
        }
    }
}
