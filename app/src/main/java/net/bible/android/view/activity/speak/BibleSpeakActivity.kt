package net.bible.android.view.activity.speak

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Html
import androidx.appcompat.app.AlertDialog
import android.util.Log
import android.view.View
import android.widget.*
import kotlinx.android.synthetic.main.speak_bible.*
import net.bible.android.activity.R
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.speak.*
import net.bible.android.view.activity.ActivityScope
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.service.db.bookmark.BookmarkDto
import net.bible.service.device.speak.BibleSpeakTextProvider.Companion.FLAG_SHOW_ALL
import net.bible.service.device.speak.event.SpeakEvent
import net.bible.service.device.speak.event.SpeakProgressEvent
import javax.inject.Inject

@ActivityScope
class BibleSpeakActivity : AbstractSpeakActivity() {
	@Inject lateinit var speakControl: SpeakControl
	@Inject lateinit var bookmarkControl: BookmarkControl

	companion object {
		const val TAG = "BibleSpeakActivity"
	}

	@SuppressLint("SetTextI18n")
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.speak_bible)
		super.buildActivityComponent().inject(this)
		ABEventBus.getDefault().register(this)

		speakSpeed.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
			override fun onStopTrackingTouch(seekBar: SeekBar?) {}
			override fun onStartTrackingTouch(seekBar: SeekBar?) {}
			override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
				speedStatus.text = "$progress %"
				if(fromUser) {
					updateSettings()
				}
			}
		})

		resetView(SpeakSettings.load())
	}

	override fun onDestroy() {
		ABEventBus.getDefault().unregister(this)
		super.onDestroy()
	}

	override fun resetView(settings: SpeakSettings) {
		statusText.text = speakControl.getStatusText(FLAG_SHOW_ALL)
		synchronize.isChecked = settings.synchronize
		speakChapterChanges.isChecked = settings.playbackSettings.speakChapterChanges
		speakTitles.isChecked = settings.playbackSettings.speakTitles
		speakFootnotes.isChecked = settings.playbackSettings.speakFootnotes

		replaceDivineName.isChecked = settings.replaceDivineName
		restoreSettingsFromBookmarks.isChecked = settings.restoreSettingsFromBookmarks

		autoBookmark.isChecked = settings.autoBookmark
		if(!autoBookmark.isChecked) {
			restoreSettingsFromBookmarks.isChecked = false;
			restoreSettingsFromBookmarks.isEnabled = false;
		}
		else {
			restoreSettingsFromBookmarks.isEnabled = true;
		}
		speakSpeed.progress = settings.playbackSettings.speed
		speedStatus.text = "${settings.playbackSettings.speed} %"
		sleepTimer.isChecked = settings.sleepTimer > 0
		sleepTimer.text = if(settings.sleepTimer>0) getString(R.string.sleep_timer_timer_set, settings.sleepTimer) else getString(R.string.conf_sleep_timer)
		speakPauseButton.setImageResource(
				if(speakControl.isSpeaking)
					android.R.drawable.ic_media_pause
				else
				android.R.drawable.ic_media_play
		)
	}

	fun onEventMainThread(ev: SpeakProgressEvent) {
		statusText.text = speakControl.getStatusText(FLAG_SHOW_ALL)
	}


	fun onEventMainThread(ev: SpeakSettingsChangedEvent) {
		currentSettings = ev.speakSettings;
		resetView(ev.speakSettings)
	}

	fun onEventMainThread(ev: SpeakEvent) {
		speakPauseButton.setImageResource(
				if(ev.isSpeaking)
					android.R.drawable.ic_media_pause
				else
				android.R.drawable.ic_media_play
		)
	}

	fun onSettingsChange(widget: View) = updateSettings()

	fun updateSettings() {
		val settings = SpeakSettings(
				synchronize = synchronize.isChecked,
				playbackSettings = PlaybackSettings(
						speakChapterChanges = speakChapterChanges.isChecked,
						speakTitles = speakTitles.isChecked,
						speakFootnotes = speakFootnotes.isChecked,
						speed = speakSpeed.progress
						),
				autoBookmark = autoBookmark.isChecked,
				replaceDivineName = replaceDivineName.isChecked,
				restoreSettingsFromBookmarks = restoreSettingsFromBookmarks.isChecked,
				sleepTimer = currentSettings.sleepTimer,
				lastSleepTimer = currentSettings.lastSleepTimer
		)
		settings.save(updateBookmark = true)
	}

	fun onButtonClick(button: View) {
		try {
			when (button) {
				prevButton -> speakControl.rewind(SpeakSettings.RewindAmount.ONE_VERSE)
				nextButton -> speakControl.forward(SpeakSettings.RewindAmount.ONE_VERSE)
				rewindButton -> speakControl.rewind()
				stopButton -> speakControl.stop()
				speakPauseButton ->
					if (speakControl.isPaused) {
						speakControl.continueAfterPause()
					} else if (speakControl.isSpeaking) {
						speakControl.pause()
					} else {
						speakControl.speakBible()
					}
				forwardButton -> speakControl.forward()
			}
		} catch (e: Exception) {
			Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e)
			Log.e(TAG, "Error: ", e)
		}
		statusText.text = speakControl.getStatusText(FLAG_SHOW_ALL)
	}

	fun onHelpButtonClick(button: View) {
		val htmlMessage = ("<b>${getString(R.string.conf_speak_auto_bookmark)}</b><br><br>"
				+ getString(R.string.speak_help_auto_bookmark)
				+ "<br><br><b>${getString(R.string.restore_settings_from_bookmarks)}</b><br><br>"
				+ getString(R.string.speak_help_playback_settings)
				+ "<br><br>"
				+ getString(R.string.speak_help_playback_settings_example)
				)

		val spanned = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			Html.fromHtml(htmlMessage, Html.FROM_HTML_MODE_LEGACY)
		} else {
			Html.fromHtml(htmlMessage)
		}

		AlertDialog.Builder(this)
				.setMessage(spanned)
				.setPositiveButton(android.R.string.ok) { _, _ ->  }
				.show()
	}

	fun onBookmarkButtonClick(button: View) {
		val bookmarkTitles = ArrayList<String>()
		val bookmarkDtos = ArrayList<BookmarkDto>()
		val labelDto = bookmarkControl.getOrCreateSpeakLabel()
		for (b in bookmarkControl.getBookmarksWithLabel(labelDto).sortedWith(
				Comparator<BookmarkDto> { o1, o2 -> o1.verseRange.start.compareTo(o2.verseRange.start) })) {

			bookmarkTitles.add("${b.verseRange.start.name} (${b.playbackSettings?.bookAbbreviation?:"?"})")
			bookmarkDtos.add(b)
		}

		val adapter = ArrayAdapter<String>(this, android.R.layout.select_dialog_item, bookmarkTitles)
		AlertDialog.Builder(this)
				.setTitle(R.string.speak_bookmarks_menu_title)
				.setAdapter(adapter) { _, which ->
					speakControl.speakFromBookmark(bookmarkDtos[which])
					if(currentSettings.synchronize) {
						startActivity(Intent(this, MainBibleActivity::class.java))
					}
				}
				.setNegativeButton(R.string.cancel, null)
				.show()
		Log.d(TAG, "Showing! $bookmarkTitles");
	}
}
