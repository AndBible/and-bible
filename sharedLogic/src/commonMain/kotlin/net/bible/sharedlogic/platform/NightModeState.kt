package net.bible.sharedlogic.platform

/** Whether night mode is active now. iOS provides this from its appearance/light-sensor setting. */
interface NightModeState { val nightMode: Boolean }
