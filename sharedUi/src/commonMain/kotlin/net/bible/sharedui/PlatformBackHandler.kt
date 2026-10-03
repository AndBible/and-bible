package net.bible.sharedui

import androidx.compose.runtime.Composable

/**
 * Multiplatform back-press seam. `androidx.activity.compose.BackHandler` is Android-only, so a
 * shared screen that needs to intercept Back cannot call it directly — which is what
 * `components/AbQuickSheet.kt` documents as the reason one sheet can only be assembled in `:app`.
 *
 * Do NOT replace this with `org.jetbrains.compose.ui:ui-backhandler`: it is on the classpath with
 * iOS variants and looks like a drop-in, but it does not resolve on the Android target at CMP
 * 1.11.1 (AndroidMidiRecorder hit exactly this and wrote the same seam).
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
