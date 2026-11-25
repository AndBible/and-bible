package net.bible.sharedui.strings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import net.bible.sharedcore.docs.TutorialVideos

/**
 * The app's UI language tag (BCP-47, `_` or `-`), provided next to [LocalStrings] by each host. It picks the caption
 * language of tutorial video links ([tutorialVideoUrl]). A system language the app has no translation for resolves to
 * English before it gets here; a language with a caption track but no app translation is still honoured when the host
 * passes its tag.
 */
val LocalAppLanguage = staticCompositionLocalOf { "en" }

/** The watch URL of [episode] for the current [LocalAppLanguage] (captions in that language when the episode has them). */
@Composable
fun tutorialVideoUrl(episode: TutorialVideos.Episode): String = TutorialVideos.url(episode, LocalAppLanguage.current)

/** The localized tutorial-video label, independent of the caption track selected for [episode]. */
@Composable
fun tutorialVideoLabel(episode: TutorialVideos.Episode): String = LocalStrings.current.watchTutorialVideoPlain
