package net.bible.sharedui.reading

import androidx.compose.ui.input.key.KeyEvent

/** Command is a find modifier only on Apple hardware, never a Windows/Linux Meta shortcut. */
internal expect val searchFieldUsesCommandKeys: Boolean

/** A committed printable character; platform key-down and text events differ. */
internal expect fun searchResultKeyCharacter(event: KeyEvent): String?

/** False for key events an on-screen keyboard synthesizes (Android IMEs send some keys as events). */
internal expect fun isHardwareKeyEvent(event: KeyEvent): Boolean
