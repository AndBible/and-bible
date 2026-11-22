package net.bible.sharedui.reading

import androidx.compose.ui.input.key.KeyEvent

/** Command is a find modifier only on Apple hardware, never a Windows/Linux Meta shortcut. */
internal expect val searchFieldUsesCommandKeys: Boolean

/** A committed printable character; platform key-down and text events differ. */
internal expect fun searchResultKeyCharacter(event: KeyEvent): String?

/** False for key events an on-screen keyboard synthesizes (Android IMEs send some keys as events). */
internal expect fun isHardwareKeyEvent(event: KeyEvent): Boolean

/**
 * Desktop: the find results list takes the focus when results appear even if the last input was a pointer press
 * (Compose desktop switches to Touch mode on every click), so Enter and the arrows work; touch platforms keep
 * their focus and on-screen keyboard (#4139, #4197).
 */
internal expect val searchResultsAlwaysTakeFocus: Boolean
