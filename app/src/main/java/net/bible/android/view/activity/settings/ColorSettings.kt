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

package net.bible.android.view.activity.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import net.bible.android.activity.R
import net.bible.android.activity.databinding.SettingsDialogBinding
import net.bible.android.database.SettingsBundle
import net.bible.android.database.WorkspaceEntities
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.AndBibleAddons

class ColorSettingsDataStore(val activity: ColorSettingsActivity): PreferenceDataStore() {
    val colors get() = activity.colors

    /** Keys of the color pickers whose values a [ColorThemePreset] stamps. Editing any of
     * these by hand clears the selected theme (→ "Custom"). */
    private val presetPaletteKeys = setOf(
        "text_color_day", "text_color_night", "background_color_day", "background_color_night",
    )

    override fun putInt(key: String?, value: Int) {
        when(key) {
            "text_color_day" -> colors.dayTextColor = value
            "text_color_night" -> colors.nightTextColor = value
            "background_color_day" -> colors.dayBackground = value
            "background_color_night" -> colors.nightBackground = value
            "noise_day" -> colors.dayNoise = value
            "noise_night" -> colors.nightNoise = value
            "background_image_opacity_day" -> colors.dayBackgroundImageOpacity = value
            "background_image_opacity_night" -> colors.nightBackgroundImageOpacity = value
            "workspace_color" -> colors.workspaceColor = value
        }
        // Editing one of the palette colors a preset stamps means the colors no longer match a
        // built-in preset, so switch the selector to "Custom". Only these keys are part of the
        // preset palette; noise, background-image opacity and workspace_color are not, so editing
        // them leaves the selected theme intact.
        if (key in presetPaletteKeys && colors.themeName != null) {
            colors.themeName = null
            // Flip the theme selector to "Custom" right away, rather than leaving it showing
            // the stale preset name until the screen is reopened.
            activity.refreshColorPreferences()
        }
        activity.setDirty()
    }

    override fun getInt(key: String?, defValue: Int): Int {
        return when(key) {
            "text_color_day" -> colors.dayTextColor?: defValue
            "text_color_night" -> colors.nightTextColor?: defValue
            "background_color_day" -> colors.dayBackground?: defValue
            "background_color_night" -> colors.nightBackground?: defValue
            "noise_day" -> colors.dayNoise?: defValue
            "noise_night" -> colors.nightNoise?: defValue
            "background_image_opacity_day" -> colors.dayBackgroundImageOpacity ?: defValue
            "background_image_opacity_night" -> colors.nightBackgroundImageOpacity ?: defValue
            "workspace_color" -> colors.workspaceColor?: defValue
            else -> defValue
        }
    }

    override fun putString(key: String?, value: String?) {
        if (key == "color_theme") {
            val preset = ColorThemePreset.byId(value)
            if (preset != null) preset.applyTo(colors) else colors.themeName = null
            activity.setDirty()
            activity.refreshColorPreferences()
        }
    }

    override fun getString(key: String?, defValue: String?): String? {
        return if (key == "color_theme") colors.themeName ?: "" else defValue
    }
}

class ColorSettingsActivity: ActivityBase() {
    private lateinit var binding: SettingsDialogBinding

    private lateinit var settingsBundle: SettingsBundle

    internal lateinit var colors: WorkspaceEntities.Colors
    internal var workspaceSettings: WorkspaceEntities.WorkspaceSettings? = null
    private var dirty = false
    private var reset = false
    internal var fragment: ColorSettingsFragment? = null

    /** Called by [ColorSettingsDataStore] after a theme preset has stamped new colors,
     * so the on-screen ListPreference and color pickers reflect the new values. */
    fun refreshColorPreferences() {
        fragment?.refreshSummariesAndTheme()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.text_options_opts, menu)
        menu.findItem(R.id.help).isVisible = false
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var isHandled = true
        when(item.itemId) {
            R.id.reset -> reset()
            android.R.id.home -> onBackPressed()
            else -> isHandled = false
        }
        if (!isHandled) {
            isHandled = super.onOptionsItemSelected(item)
        }
        return isHandled
    }

    fun cancel() {
        dirty = false
        setResult()
        finish()
    }

    fun reset() {
        AlertDialog.Builder(this)
            .setPositiveButton(R.string.yes) {_, _ ->
                reset = true
                setResult()
                finish()
            }
            .setNegativeButton(R.string.no,null)
            .setMessage(getString(R.string.reset_are_you_sure))
            .create()
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        settingsBundle = SettingsBundle.fromJson(intent.extras?.getString("settingsBundle")!!)
        colors = settingsBundle.actualSettings.colors!!
        colors.workspaceColor = settingsBundle.workspaceSettings.colors?.workspaceColor

        super.onCreate(savedInstanceState)

        binding = SettingsDialogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        super.buildActivityComponent().inject(this)
        dirty = false
        reset = false

        val frag = ColorSettingsFragment(isWindow = settingsBundle.windowId != null)
        fragment = frag
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, frag)
            .commit()

        if(settingsBundle.windowId != null) {
            title = getString(R.string.window_color_settings_title)
        } else {
            title = getString(R.string.workspace_color_settings_title)
        }

        setResult()
    }

    fun setDirty() {
        dirty = true
        setResult()
    }

    fun setResult() {
        val resultIntent = Intent(this, ColorSettingsActivity::class.java)

        resultIntent.putExtra("edited", dirty)
        resultIntent.putExtra("reset", reset)
        resultIntent.putExtra("windowId", settingsBundle.windowId?.toString())
        resultIntent.putExtra("colors", colors.toJson())
        resultIntent.putExtra("workspaceColor", workspaceSettings?.workspaceColor)

        setResult(Activity.RESULT_OK, resultIntent)
    }
}


class ColorSettingsFragment(val isWindow: Boolean = false): PreferenceFragmentCompat() {
    private var editingNight = false

    private val chooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val activity = activity as ColorSettingsActivity
        // extras contains "selectedInitials" (may be absent → treat as null = None)
        val initials = if (result.data?.hasExtra("selectedInitials") == true)
            result.data?.getStringExtra("selectedInitials") else null
        if (editingNight) activity.colors.nightBackgroundImage = initials
        else activity.colors.dayBackgroundImage = initials
        activity.setDirty()
        updateImageSummaries()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val activity = activity as ColorSettingsActivity
        preferenceManager.preferenceDataStore = ColorSettingsDataStore(activity)
        setPreferencesFromResource(R.xml.color_settings, rootKey)
        if(isWindow) {
            findPreference<Preference>("workspace_color")?.isVisible = false
        }
        findPreference<Preference>("background_image_day")?.setOnPreferenceClickListener {
            editingNight = false
            chooserLauncher.launch(Intent(activity, BackgroundImageChooserActivity::class.java))
            true
        }
        findPreference<Preference>("background_image_night")?.setOnPreferenceClickListener {
            editingNight = true
            chooserLauncher.launch(Intent(activity, BackgroundImageChooserActivity::class.java))
            true
        }
        updateImageSummaries()
    }

    private fun nameFor(initials: String?): String {
        if (initials == null) return getString(R.string.background_image_none)
        return AndBibleAddons.providedBackgroundImages[initials]?.name ?: initials
    }

    private fun updateImageSummaries() {
        val activity = activity as ColorSettingsActivity
        findPreference<Preference>("background_image_day")?.summary = nameFor(activity.colors.dayBackgroundImage)
        findPreference<Preference>("background_image_night")?.summary = nameFor(activity.colors.nightBackgroundImage)
    }

    /** Keys of the color pickers whose value can be changed "from underneath them" by
     * applying a [ColorThemePreset], i.e. without the user interacting with that specific
     * picker. */
    private val colorPickerKeys = listOf(
        "text_color_day", "text_color_night",
        "background_color_day", "background_color_night",
    )

    /**
     * Re-syncs the UI after [ColorSettingsDataStore] has applied (or cleared) a color theme
     * preset directly onto the underlying [WorkspaceEntities.Colors] object.
     *
     * `ColorPreferenceCompat` caches the color it displays in a private field that is only
     * populated from the `PreferenceDataStore` when the preference is (re)attached to its
     * `PreferenceGroup` (`Preference.onAttachedToHierarchy` -> `dispatchSetInitialValue` ->
     * `onSetInitialValue` -> `getPersistedInt`). Calling `notifyChanged()` alone would just
     * redraw the *stale* cached value, since it doesn't cause that re-read. There is no public
     * API on `ColorPreferenceCompat` to pull a fresh value from the data store without also
     * persisting/callback side effects, so the correct, side-effect-free way to force a re-read
     * is to detach and reattach each affected preference from its `PreferenceGroup`.
     */
    fun refreshSummariesAndTheme() {
        val activity = activity as? ColorSettingsActivity ?: return
        // Re-sync the ListPreference selection with the (possibly changed) themeName.
        findPreference<ListPreference>("color_theme")?.value = activity.colors.themeName ?: ""
        // Force each color picker to re-read its value from the datastore (see kdoc above).
        for (key in colorPickerKeys) {
            val preference = findPreference<Preference>(key) ?: continue
            val parent = preference.parent ?: continue
            parent.removePreference(preference)
            parent.addPreference(preference)
        }
        updateImageSummaries()
    }
}
