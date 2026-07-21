package net.bible.sharedcore.settings

sealed interface SettingsItem {
    val key: String
    val visible: Boolean

    /** Group header (non-interactive). */
    data class Category(
        override val key: String,
        val title: String,
        override val visible: Boolean = true,
    ) : SettingsItem

    /** Boolean toggle. */
    data class SwitchRow(
        override val key: String,
        val title: String,
        val summary: String? = null,
        val checked: Boolean,
        /** Optional key into the host's [net.bible.sharedui.settings.LocalSettingsIcon] seam for a leading icon. */
        val iconKey: String? = null,
        override val visible: Boolean = true,
        val enabled: Boolean = true,
    ) : SettingsItem

    /** Single-choice from [entries] (label shown, value is the stable id). */
    data class ListChoiceRow(
        override val key: String,
        val title: String,
        val summary: String? = null,
        val entries: List<Choice>,
        val selectedValue: String,
        /** Optional key into the host's [net.bible.sharedui.settings.LocalSettingsIcon] seam for a leading icon. */
        val iconKey: String? = null,
        override val visible: Boolean = true,
        val enabled: Boolean = true,
    ) : SettingsItem

    /** Opens a text/numeric editor dialog. [value] is the current display value. */
    data class TextInputRow(
        override val key: String,
        val title: String,
        val summary: String? = null,
        val value: String,
        val numeric: Boolean = false,
        /** Obscure characters (password field). Renders AbTextInputDialog with PasswordVisualTransformation. */
        val masked: Boolean = false,
        /** Optional key into the host's [net.bible.sharedui.settings.LocalSettingsIcon] seam for a leading icon. */
        val iconKey: String? = null,
        override val visible: Boolean = true,
        val enabled: Boolean = true,
    ) : SettingsItem

    /** Integer slider (SeekBar analogue). [valueLabel] is the pre-formatted readout (e.g. "150 %"). */
    data class SliderRow(
        override val key: String,
        val title: String,
        val value: Int,
        val min: Int,
        val max: Int,
        val valueLabel: String,
        val iconKey: String? = null,
        override val visible: Boolean = true,
        val enabled: Boolean = true,
    ) : SettingsItem

    /**
     * Multi-selection from [options]; [selectedValues] holds the currently-selected stable ids.
     * Inverse semantics (classic InverseMultiSelectListPreference, where an empty stored set means
     * "all selected") are resolved by the controller BEFORE building this row — it always presents
     * the positive "selected" set, so the framework stays semantics-free.
     */
    data class MultiSelectRow(
        override val key: String,
        val title: String,
        val summary: String? = null,
        val options: List<Choice>,
        val selectedValues: Set<String>,
        val iconKey: String? = null,
        override val visible: Boolean = true,
        val enabled: Boolean = true,
    ) : SettingsItem

    /** Clickable row that navigates elsewhere (host decides where by [key]). */
    data class NavigationRow(
        override val key: String,
        val title: String,
        val summary: String? = null,
        /** Optional key into the host's [net.bible.sharedui.settings.LocalSettingsIcon] seam for a leading icon. */
        val iconKey: String? = null,
        override val visible: Boolean = true,
        val enabled: Boolean = true,
    ) : SettingsItem

    /**
     * Non-selectable informational row, optionally made interactive via [onClickKey] (e.g. to open
     * an info/disclaimer dialog) — when null the row stays plain/non-clickable, unchanged behaviour.
     */
    data class InfoRow(
        override val key: String,
        val title: String,
        val summary: String? = null,
        /** Optional key into the host's [net.bible.sharedui.settings.LocalSettingsIcon] seam for a leading icon. */
        val iconKey: String? = null,
        /** When non-null, the row is clickable and the host's navigate/click callback fires with this key. */
        val onClickKey: String? = null,
        override val visible: Boolean = true,
    ) : SettingsItem

    data class Choice(val value: String, val label: String)
}

/** What the framework composable renders. Dialog state is screen-local, not here. */
data class SettingsScreenState(
    val title: String,
    val items: List<SettingsItem>,
) {
    val visibleItems: List<SettingsItem> get() = items.filter { it.visible }
}
