package net.bible.sharedui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState

/**
 * Exposes an icon-only control's on/off state to accessibility services without changing its look
 * (F98: the Labels-list icons carried their state in glyph and tint only). `IconToggleButton` would do
 * the same but restyles the checked state, which moves goldens.
 */
fun Modifier.toggleStateSemantics(on: Boolean): Modifier = semantics {
    role = Role.Switch
    toggleableState = ToggleableState(on)
}

/** The pick-one-of-several counterpart of [toggleStateSemantics] (the primary-label bookmark). */
fun Modifier.selectionStateSemantics(selected: Boolean): Modifier = semantics {
    role = Role.RadioButton
    this.selected = selected
}
