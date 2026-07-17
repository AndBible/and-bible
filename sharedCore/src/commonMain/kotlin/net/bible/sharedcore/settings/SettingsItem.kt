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
        override val visible: Boolean = true,
        val enabled: Boolean = true,
    ) : SettingsItem

    /** Clickable row that navigates elsewhere (host decides where by [key]). */
    data class NavigationRow(
        override val key: String,
        val title: String,
        val summary: String? = null,
        override val visible: Boolean = true,
        val enabled: Boolean = true,
    ) : SettingsItem

    /** Non-selectable informational row. */
    data class InfoRow(
        override val key: String,
        val title: String,
        val summary: String? = null,
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
