package net.bible.sharedui.strings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import net.bible.sharedcore.docs.TutorialVideos

/**
 * The app's UI language tag (BCP-47, `_` or `-`), provided next to [LocalStrings] by each host. It picks the caption
 * language of tutorial video links ([tutorialVideoUrl]) and the "(English)" qualifier of their labels
 * ([tutorialVideoLabel]). A system language the app has no translation for resolves to English before it gets here;
 * a language with a caption track but no app translation is still honoured when the host passes its tag.
 */
val LocalAppLanguage = staticCompositionLocalOf { "en" }

/** The watch URL of [episode] for the current [LocalAppLanguage] (captions in that language when the episode has them). */
@Composable
fun tutorialVideoUrl(episode: TutorialVideos.Episode): String = TutorialVideos.url(episode, LocalAppLanguage.current)

/** "Watch tutorial video", with "(English)" only when [episode] has no caption track in the current app language. */
@Composable
fun tutorialVideoLabel(episode: TutorialVideos.Episode): String {
    val strings = LocalStrings.current
    return if (TutorialVideos.hasPlainLabel(episode, LocalAppLanguage.current)) strings.watchTutorialVideoPlain else strings.watchTutorialVideo
}
