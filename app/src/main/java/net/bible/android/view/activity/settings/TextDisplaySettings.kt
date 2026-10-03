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

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.ImageSpan
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import kotlinx.serialization.Serializable
import net.bible.android.activity.R
import net.bible.android.activity.databinding.SettingsDialogBinding
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.InheritedFrom
import net.bible.android.database.SettingsBundle
import net.bible.android.database.SettingsLevel
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings.Types
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.defaultWorkspaceColor
import net.bible.android.view.activity.page.Preference as ItemPreference
import net.bible.android.database.json
import net.bible.android.view.activity.ActivityScope
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.page.ColorPreference
import net.bible.android.view.activity.page.CommandPreference
import net.bible.android.view.activity.page.ExpandXrefsPreference
import net.bible.android.view.activity.page.FontFamilyPreference
import net.bible.android.view.activity.page.FontSizePreference
import net.bible.android.view.activity.page.HideLabelsPreference
import net.bible.android.view.activity.page.LineSpacingPreference
import net.bible.android.view.activity.page.MainBibleActivity.Companion.COLORS_CHANGED
import net.bible.android.view.activity.page.MarginSizePreference
import net.bible.android.view.activity.page.FootnotesInlinePreference
import net.bible.android.view.activity.page.MorphologyPreference
import net.bible.android.view.activity.page.NonStrongsWordItalicPreference
import net.bible.android.view.activity.page.AiDocMarkersPreference
import net.bible.android.view.activity.page.OrdinalsPreference
import net.bible.android.view.activity.page.MyNotesPreference
import net.bible.android.view.activity.page.OptionsMenuItemInterface
import net.bible.android.view.activity.page.RedLettersPreference
import net.bible.android.view.activity.page.ScrollHelperLinesPreference
import net.bible.android.view.activity.page.ScrollHelperLineStylePreference
import net.bible.android.view.activity.page.PageButtonsPreference
import net.bible.android.view.activity.page.PageScrollAmountPreference
import net.bible.android.view.activity.page.StrongsPreference
import net.bible.android.view.activity.page.TopMarginPreference
import net.bible.android.view.activity.page.InfiniteScrollPreference
import net.bible.android.view.activity.page.buyDevelopmentLink
import net.bible.service.common.CommonUtils
import net.bible.service.common.CommonUtils.getTintedDrawable
import net.bible.service.common.getPreferenceList
import net.bible.service.common.htmlToSpan
import net.bible.service.common.setupPreferenceSearch
import net.bible.service.common.textDisplaySettingsVideo
import java.lang.IllegalArgumentException
import java.lang.RuntimeException
import javax.inject.Inject


class TextDisplaySettingsDataStore(
    private val activity: TextDisplaySettingsActivity,
    private val settingsBundle: SettingsBundle
): PreferenceDataStore() {
    override fun putBoolean(key: String, value: Boolean) {
        val type = Types.valueOf(key)
        val prefItem = getPrefItem(settingsBundle, type)
        val oldValue = prefItem.value
        prefItem.value = value
        if(oldValue != value) {
            activity.setDirty(type)
        }
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        val type = Types.valueOf(key)
        val settings = TextDisplaySettings.actual(settingsBundle.pageManagerSettings, settingsBundle.workspaceSettings, settingsBundle.globalSettings)

        return (settings.getValue(type) ?: TextDisplaySettings.default.getValue(type)) as Boolean
    }
}

fun getPrefItem(settings: SettingsBundle, key: String): OptionsMenuItemInterface {
    return try {
        val type = Types.valueOf(key)
        getPrefItem(settings, type)
    } catch (e: IllegalArgumentException) {
        when(key) {
            "apply_to_all_workspaces" -> CommandPreference()
            else -> throw RuntimeException("Unsupported item key $key")
        }
    }
}

fun getPrefItem(settings: SettingsBundle, type: Types): OptionsMenuItemInterface =
    when(type) {
        Types.BOOKMARKS_SHOW -> ItemPreference(settings, Types.BOOKMARKS_SHOW)
        Types.REDLETTERS -> RedLettersPreference(settings)
        Types.SECTIONTITLES -> ItemPreference(settings, Types.SECTIONTITLES)
        Types.VERSENUMBERS -> ItemPreference(settings, Types.VERSENUMBERS)
        Types.VERSEPERLINE -> ItemPreference(settings, Types.VERSEPERLINE)
        Types.FOOTNOTES -> ItemPreference(settings, Types.FOOTNOTES)
        Types.FOOTNOTES_INLINE -> FootnotesInlinePreference(settings)
        Types.EXPAND_XREFS -> ExpandXrefsPreference(settings)
        Types.XREFS -> ItemPreference(settings, Types.XREFS)
        Types.MYNOTES -> MyNotesPreference(settings)
        Types.STRONGS -> StrongsPreference(settings)
        Types.MORPH -> MorphologyPreference(settings)
        Types.FONTSIZE -> FontSizePreference(settings)
        Types.FONTFAMILY -> FontFamilyPreference(settings)
        Types.MARGINSIZE -> MarginSizePreference(settings)
        Types.COLORS -> ColorPreference(settings)
        Types.JUSTIFY -> ItemPreference(settings, Types.JUSTIFY)
        Types.HYPHENATION -> ItemPreference(settings, Types.HYPHENATION)
        Types.TOPMARGIN -> TopMarginPreference(settings)
        Types.LINE_SPACING -> LineSpacingPreference(settings)
        Types.BOOKMARKS_HIDELABELS -> HideLabelsPreference(settings, Types.BOOKMARKS_HIDELABELS)
        Types.PAGENUMBER -> ItemPreference(settings, Types.PAGENUMBER)
        Types.INFINITE_SCROLL -> InfiniteScrollPreference(settings)
        Types.NON_STRONGS_WORD_ITALIC -> NonStrongsWordItalicPreference(settings)
        Types.MARK_AS_READ_BUTTON -> ItemPreference(settings, Types.MARK_AS_READ_BUTTON)
        Types.TITLE_SCROLL_BUTTON -> ItemPreference(settings, Types.TITLE_SCROLL_BUTTON)
        Types.MEMORIZATION_INDICATORS -> ItemPreference(settings, Types.MEMORIZATION_INDICATORS)
        Types.AUTO_TRACK_READING -> ItemPreference(settings, Types.AUTO_TRACK_READING)
        Types.AI_DOC_MARKERS -> AiDocMarkersPreference(settings)
        Types.ORDINALS -> OrdinalsPreference(settings)
        Types.PAGE_SCROLL_AMOUNT -> PageScrollAmountPreference(settings)
        Types.SCROLL_HELPER_LINES -> ScrollHelperLinesPreference(settings)
        Types.SCROLL_HELPER_LINE_STYLE -> ScrollHelperLineStylePreference(settings)
        Types.PAGE_BUTTONS -> PageButtonsPreference(settings)
        Types.SHOW_READING_PROGRESS -> ItemPreference(settings, Types.SHOW_READING_PROGRESS)
    }

class TextDisplaySettingsFragment: PreferenceFragmentCompat() {
    private val settingsBundle get() = (activity as TextDisplaySettingsActivity).settingsBundle
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val activity = activity as TextDisplaySettingsActivity
        preferenceManager.preferenceDataStore = TextDisplaySettingsDataStore(activity, settingsBundle)
        setPreferencesFromResource(R.xml.text_display_settings, rootKey)
        setupParentSettingsLinks()
        updateItems()
    }

    private fun setupParentSettingsLinks() {
        val parentCategory = findPreference<androidx.preference.PreferenceCategory>("parent_settings_category")
        val workspaceLink = findPreference<Preference>("open_workspace_settings")
        val globalLink = findPreference<Preference>("open_global_settings")

        workspaceLink?.icon = CommonUtils.makeLarger(getTintedDrawable(R.drawable.ic_workspace_overlay_24dp), 1.5F)
        globalLink?.icon = CommonUtils.makeLarger(getTintedDrawable(R.drawable.ic_settings_black_24dp), 1.5F)

        when (settingsBundle.level) {
            SettingsLevel.WINDOW -> {
                workspaceLink?.title = getString(R.string.workspace_text_options_link, settingsBundle.workspaceName)
            }
            SettingsLevel.WORKSPACE -> {
                workspaceLink?.isVisible = false
            }
            SettingsLevel.GLOBAL -> {
                parentCategory?.isVisible = false
            }
        }
    }

    private fun openWorkspaceSettings() {
        val intent = Intent(context, TextDisplaySettingsActivity::class.java)
        val wsBundle = SettingsBundle(
            level = SettingsLevel.WORKSPACE,
            workspaceId = settingsBundle.workspaceId,
            workspaceName = settingsBundle.workspaceName,
            workspaceSettings = settingsBundle.workspaceSettings,
            globalSettings = settingsBundle.globalSettings,
        )
        intent.putExtra("settingsBundle", wsBundle.toJson())
        startActivity(intent)
    }

    private fun openGlobalSettings() {
        val intent = Intent(context, TextDisplaySettingsActivity::class.java)
        val globalBundle = SettingsBundle(
            level = SettingsLevel.GLOBAL,
            globalSettings = settingsBundle.globalSettings,
        )
        intent.putExtra("settingsBundle", globalBundle.toJson())
        startActivity(intent)
    }

    private val parentSettingsKeys = setOf("open_workspace_settings", "open_global_settings")

    internal fun updateItems() {
        for(p in getPreferenceList()) {
            if (p.key in parentSettingsKeys) continue
            updateItem(p)
        }
    }

    private val windowId get() = settingsBundle.windowId

    private fun updateItem(p: Preference) {
        val itmOptions = getPrefItem(settingsBundle, p.key)
        val itemPref = itmOptions as? ItemPreference
        p.icon = CommonUtils.iconWithInheritance(
            itmOptions.icon!!,
            itemPref?.inheritedFrom ?: InheritedFrom.NONE,
            settingsBundle.level,
            1.5F
        )
        if(itmOptions.title != null) {
            p.title = itmOptions.title
        }
        if(itmOptions.summary != null) {
            p.summary = itmOptions.summary
        }
        p.isEnabled = itmOptions.enabled
        p.isVisible = itmOptions.visible

        if(itmOptions is StrongsPreference) {
            updateItem(findPreference(Types.MORPH.name)!!)
            updateItem(findPreference(Types.NON_STRONGS_WORD_ITALIC.name)!!)
        }
    }


    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            "open_workspace_settings" -> { openWorkspaceSettings(); return true }
            "open_global_settings" -> { openGlobalSettings(); return true }
        }
        var returnValue = true
        val prefItem = getPrefItem(settingsBundle, preference.key)
        val type = try {Types.valueOf(preference.key)} catch (e: IllegalArgumentException) { null }
        val activity = activity as TextDisplaySettingsActivity
        val resetFunc = {
            if(prefItem is ItemPreference) {
                prefItem.setNonSpecific()
                activity.setDirty(prefItem.type)
            }
            updateItem(preference)
        }
        val handled = prefItem.openDialog(activity, {
            updateItem(preference)
            if(type != null)
                activity.setDirty(type)
        }, resetFunc)

        if(!handled) {
            returnValue = super.onPreferenceTreeClick(preference)
            updateItems()
        }

        return returnValue
    }
}

@Serializable
data class DirtyTypesSerializer(val dirtyTypes: MutableSet<Types>) {
    fun toJson(): String {
        return json.encodeToString(serializer(), this)
    }
    companion object {
        fun fromJson(jsonString: String): DirtyTypesSerializer {
            return json.decodeFromString(serializer(), jsonString)
        }
    }
}

@ActivityScope
class TextDisplaySettingsActivity: ActivityBase() {
    private lateinit var fragment: TextDisplaySettingsFragment
    private var requiresReload = false
    private var reset = false
    private val dirtyTypes = mutableSetOf<Types>()
    private val bundleStack = ArrayDeque<SettingsBundle>()

    internal lateinit var settingsBundle: SettingsBundle
    private lateinit var binding: SettingsDialogBinding

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.text_options_opts, menu)
        fragment.setupPreferenceSearch(menu!!, this)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var isHandled = true
        when(item.itemId) {
            R.id.reset -> reset()
            R.id.help -> help()
            android.R.id.home -> onBackPressed()
            else -> isHandled = false
        }
        if (!isHandled) {
            isHandled = super.onOptionsItemSelected(item)
        }
        return isHandled
    }

    fun reset() {
        AlertDialog.Builder(this)
            .setPositiveButton(R.string.yes) {_, _ ->
                reset = true
                requiresReload = true
                if (settingsBundle.level == SettingsLevel.GLOBAL) {
                    // GLOBAL settings are launched without a result handler, so the reset cannot be
                    // applied by MainBibleActivity.workspaceSettingsChanged like WINDOW/WORKSPACE.
                    // Clear all global overrides in-place and commit here, mirroring the empty-object
                    // reset used for the other levels (commitDirtyToInMemoryState handles GLOBAL).
                    settingsBundle = settingsBundle.copy(globalSettings = TextDisplaySettings())
                    dirtyTypes.addAll(Types.values())
                    commitDirtyToInMemoryState()
                }
                setResult()
                finish()
            }
            .setNegativeButton(R.string.no,null)
            .setMessage(getString(R.string.reset_are_you_sure))
            .create()
            .show()
    }

    fun help() {
        val resetIcon = ImageSpan(getTintedDrawable(R.drawable.ic_baseline_undo_24))
        val length = 9

        val videoSpan = htmlToSpan("<i><a href=\"$textDisplaySettingsVideo\">${getString(R.string.watch_tutorial_video)}</a></i><br><br>")

        val buy = getString(R.string.buy_development)
        val support = getString(R.string.buy_development2)
        val heartIcon = ImageSpan(getTintedDrawable(R.drawable.baseline_attach_money_24))
        val buyMessage = "<b>$support</b>: <a href=\"$buyDevelopmentLink\">$buy</a>"
        val iconStr = SpannableString("* ")
        iconStr.setSpan(heartIcon, 0, 1, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        val spannedBuy = TextUtils.concat(htmlToSpan("<br><br>"), iconStr, htmlToSpan(buyMessage))

        val text = when {
            isGlobal -> {
                val h1 = getString(R.string.global_text_options_help1)
                val h3 = getString(R.string.text_options_reset_help, "__ICON1__", getString(R.string.reset_defaults))
                val text = "$h1\n\n$h3"
                val start1 = text.indexOf("__ICON1__")
                val span = SpannableString(text)
                span.setSpan(resetIcon, start1, start1 + length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
                TextUtils.concat(videoSpan, span, spannedBuy)
            }
            isWindow -> {
                val w1 = getString(R.string.window_text_options_help1, "__ICON1__")
                val w4 = getString(R.string.text_options_reset_help, "__ICON3__", getString(R.string.reset_workspace_defaults))
                val icon1 = ImageSpan(getTintedDrawable(R.drawable.ic_workspace_overlay_24dp))

                val text = "$w1\n\n$w4"
                val start1 = text.indexOf("__ICON1__")
                val start3 = text.indexOf("__ICON3__")
                val span = SpannableString(text)
                span.setSpan(icon1, start1, start1 + length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
                span.setSpan(resetIcon, start3, start3 + length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
                TextUtils.concat(videoSpan, span, spannedBuy)
            }
            else -> {
                val h1 = getString(R.string.workspace_text_options_help1)
                val h2 = getString(R.string.workspace_text_options_help2)
                val h3 = getString(R.string.text_options_reset_help, "__ICON1__", getString(R.string.reset_defaults))
                val text = "$h1 $h2 \n\n$h3"
                val start1 = text.indexOf("__ICON1__")
                val span = SpannableString(text)
                span.setSpan(resetIcon, start1, start1 + length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
                TextUtils.concat(videoSpan, span, spannedBuy)
            }
        }

        val title = when {
            isGlobal -> getString(R.string.global_text_options_help_title)
            isWindow -> getString(R.string.window_text_options_help_title)
            else -> getString(R.string.workspace_text_options_help_title)
        }

        val d = AlertDialog.Builder(this)
            .setPositiveButton(R.string.okay, null)
            .setTitle(title)
            .setMessage(text)
            .create()

        d.show()
        d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val newBundleJson = intent?.getStringExtra("settingsBundle") ?: return
        // Commit current-level dirty changes to in-memory state before navigating up the stack,
        // so they aren't lost when this bundle later pops back.
        commitDirtyToInMemoryState()
        bundleStack.addLast(settingsBundle)
        loadSettingsBundle(SettingsBundle.fromJson(newBundleJson))
    }

    override fun onBackPressed() {
        // Commit current-level dirty changes. GLOBAL must commit even when exiting (the activity
        // may have been launched without a result handler). WORKSPACE/WINDOW commit only when
        // popping back to a stacked level; on full exit they propagate via onActivityResult.
        val popping = bundleStack.isNotEmpty()
        if (dirtyTypes.isNotEmpty() &&
            (settingsBundle.level == SettingsLevel.GLOBAL || popping)
        ) {
            commitDirtyToInMemoryState()
        }
        if (popping) {
            val previous = bundleStack.removeLast()
            // Refresh the popped bundle from now-current in-memory state so the popped view shows
            // the changes the user just made deeper in the stack (e.g. window-level icons/values
            // reflect workspace edits that were just committed above).
            loadSettingsBundle(refreshFromInMemoryState(previous))
            return
        }
        finish()
    }

    /**
     * Persist the currently-edited [settingsBundle]'s dirty changes into the in-memory
     * `windowRepository`/`CommonUtils` state so that subsequent navigation and reads see them.
     * Mirrors the per-level branches of `MainBibleActivity.workspaceSettingsChanged`.
     */
    private fun commitDirtyToInMemoryState() {
        if (dirtyTypes.isEmpty()) return
        val repo = windowControl.windowRepository
        when (settingsBundle.level) {
            SettingsLevel.GLOBAL -> {
                CommonUtils.globalTextDisplaySettings = settingsBundle.globalSettings
                repo.propagateGlobalTextDisplaySettingsChange(
                    dirtyTypes, settingsBundle.globalSettings
                )
                repo.updateAllWindowsTextDisplaySettings()
            }
            SettingsLevel.WORKSPACE -> {
                repo.textDisplaySettings = settingsBundle.workspaceSettings
                repo.workspaceSettings.workspaceColor =
                    settingsBundle.workspaceSettings.colors?.workspaceColor ?: defaultWorkspaceColor
                repo.updateWindowTextDisplaySettingsValues(dirtyTypes, settingsBundle.workspaceSettings)
                repo.updateAllWindowsTextDisplaySettings()
            }
            SettingsLevel.WINDOW -> {
                val window = settingsBundle.windowId?.let { repo.getWindow(it) } ?: return
                window.pageManager.textDisplaySettings = settingsBundle.pageManagerSettings!!
                window.bibleView?.updateTextDisplaySettings()
            }
        }
    }

    /** Refresh inherited (and own-window) settings on a popped bundle from in-memory state. */
    private fun refreshFromInMemoryState(bundle: SettingsBundle): SettingsBundle {
        val repo = windowControl.windowRepository
        val refreshedWindow = bundle.windowId?.let { repo.getWindow(it) }
        return bundle.copy(
            globalSettings = CommonUtils.globalTextDisplaySettings,
            workspaceSettings = repo.textDisplaySettings,
            pageManagerSettings = refreshedWindow?.pageManager?.textDisplaySettings
                ?: bundle.pageManagerSettings,
        )
    }

    private fun loadSettingsBundle(bundle: SettingsBundle) {
        settingsBundle = bundle
        dirtyTypes.clear()
        requiresReload = false
        reset = false

        val windowId = settingsBundle.windowId
        title = when (settingsBundle.level) {
            SettingsLevel.GLOBAL -> getString(R.string.global_text_display_settings_title)
            SettingsLevel.WINDOW -> getString(R.string.window_text_display_settings_title, windowControl.windowPosition(windowId!!) + 1)
            SettingsLevel.WORKSPACE -> getString(R.string.workspace_text_display_settings_title, settingsBundle.workspaceName)
        }

        val fragment = TextDisplaySettingsFragment()
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, fragment)
            .commit()
        this.fragment = fragment
        setResult()
    }

    private val isWindow get() = settingsBundle.level == SettingsLevel.WINDOW
    private val isGlobal get() = settingsBundle.level == SettingsLevel.GLOBAL

    @Inject lateinit var windowControl: WindowControl

    override fun onCreate(savedInstanceState: Bundle?) {
        val settingsBundleJson = intent.extras?.getString("settingsBundle")
        if(settingsBundleJson == null) {
            // Same as in ColorSettingsActivity: without a bundle there is nothing to edit, so
            // finish rather than throwing out of onCreate (#3867).
            Log.e(TAG, "No settingsBundle in intent, finishing")
            super.onCreate(savedInstanceState)
            finish()
            return
        }
        settingsBundle = SettingsBundle.fromJson(settingsBundleJson)
        super.onCreate(savedInstanceState)

        binding = SettingsDialogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        super.buildActivityComponent().inject(this)
        loadSettingsBundle(settingsBundle)
    }

    fun setDirty(type: Types) {
        dirtyTypes.add(type)
        setResult()
    }

    fun setResult() {
        val resultIntent = Intent(this, TextDisplaySettingsActivity::class.java)

        resultIntent.putExtra("settingsBundle", settingsBundle.toJson())
        resultIntent.putExtra("reset", reset)
        resultIntent.putExtra("edited", dirtyTypes.isNotEmpty())
        resultIntent.putExtra("dirtyTypes", DirtyTypesSerializer(dirtyTypes).toJson())

        setResult(Activity.RESULT_OK, resultIntent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            COLORS_CHANGED -> {
                val extras = data?.extras!!
                val edited = extras.getBoolean("edited")
                val reset = extras.getBoolean("reset")
                val prefItem = getPrefItem(settingsBundle, Types.COLORS)
                if(reset) {
                    prefItem.setNonSpecific()
                    setDirty(Types.COLORS)
                    fragment.updateItems()
                }
                else if(edited) {
                    val colors = WorkspaceEntities.Colors.fromJson(data.extras?.getString("colors")!!)
                    prefItem.value = colors
                    setDirty(Types.COLORS)
                    fragment.updateItems()
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
