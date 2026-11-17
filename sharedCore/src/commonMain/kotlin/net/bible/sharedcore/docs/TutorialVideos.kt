package net.bible.sharedcore.docs

/**
 * The 6.0 video series ("What's new" and the 20 tutorials) and the one place the app learns where each video is.
 *
 * **After uploading an episode to YouTube**, replace its `null` below with the video id (the part after `v=` or
 * `youtu.be/`); `youtube_upload.py` prints the exact line to paste. Every link in the app goes through [url], so
 * nothing else changes. While an episode has no id, [url] returns the [placeholderUrl]: during the beta that is
 * the one "videos coming soon" video for every episode.
 *
 * The enum names are the episode folder names under `website/video/episodes/`, upper-cased with `_` for `-`
 * ([slug] gives the folder name back); `TutorialVideosEpisodesTest` keeps both sides in step.
 */
object TutorialVideos {
    /** The AndBible YouTube channel: what the placeholder falls back to until its own video is uploaded. */
    const val CHANNEL_URL = "https://www.youtube.com/@AndBible"

    /** Id of the "Videos coming soon" placeholder video (`placeholder-coming-soon` episode); null until uploaded. */
    val PLACEHOLDER_VIDEO_ID: String? = "RlLkpKZZwTg"

    /** Id of the tutorials playlist ("AndBible tutorials"); null until the playlist is public. */
    val TUTORIAL_PLAYLIST_ID: String? = null

    /**
     * One video of the series; [youtubeId] is null until it is published. [subtitles] are the YouTube caption
     * languages (BCP-47, sorted) uploaded besides English; `youtube_upload.py` prints the matching `setOf(...)`.
     */
    enum class Episode(val youtubeId: String?, val subtitles: Set<String> = emptySet()) {
        WHATS_NEW_6_0(null),
        TUTORIAL_01_GETTING_STARTED(null),
        TUTORIAL_02_FINDING_YOUR_WAY(null),
        TUTORIAL_03_VERSE_MENU(null),
        TUTORIAL_04_STRONGS_DICTIONARIES_CROSSREFS(null),
        TUTORIAL_05_WINDOWS_1_OPEN_AND_ARRANGE(null),
        TUTORIAL_06_WINDOWS_2_PIN_SYNC_LINKS(null),
        TUTORIAL_07_WORKSPACES(null),
        TUTORIAL_08_BOOKMARKS(null),
        TUTORIAL_09_LABELS_AND_HIGHLIGHTS(null),
        TUTORIAL_10_NOTES(null),
        TUTORIAL_11_STUDY_PADS(null),
        TUTORIAL_12_SEARCH_1_BASICS(null),
        TUTORIAL_13_SEARCH_2_ADVANCED(null),
        TUTORIAL_14_SPEAK(null),
        TUTORIAL_15_READING_PLANS_AND_PROGRESS(null),
        TUTORIAL_16_BACKUP_AND_RESTORE(null),
        TUTORIAL_17_SYNC_1_GOOGLE_DRIVE(null),
        TUTORIAL_18_SYNC_2_MORE_BACKENDS_AND_DOCUMENTS(null),
        TUTORIAL_19_APPEARANCE_1_NIGHT_MODE_FONTS_COLOURS(null),
        TUTORIAL_20_APPEARANCE_2_GLOBAL_WORKSPACE_WINDOW(null),
        TUTORIAL_21_DOCUMENT_LIBRARY(null),
        TUTORIAL_22_LOAD_FILES_AND_CUSTOM_REPOSITORIES(null),
        TUTORIAL_23_MY_DOCUMENTS_AND_EPUB(null),
        TUTORIAL_24_SHARING_AND_BIBLE_LINKS(null),
        TUTORIAL_25_MEMORIZE(null),
        TUTORIAL_26_DESKTOP(null),
        TUTORIAL_27_STUDY_WORKFLOWS_1_TOPICAL_AND_SERMON(null),
        TUTORIAL_28_STUDY_WORKFLOWS_2_WORD_STUDY_AND_DEVOTIONS(null),
        TUTORIAL_29_AI_1_GETTING_STARTED(null),
        TUTORIAL_30_AI_2_PROMPTS(null),
        TUTORIAL_31_AI_3_TOOLS_DATA_PERMISSIONS(null),
        TUTORIAL_32_AI_4_CUSTOM_PROMPTS_COSTS_SYNC(null);

        /** The episode's folder name under `website/video/episodes/`. */
        val slug: String get() = name.lowercase().replace('_', '-')
    }

    /** The episodes that have a YouTube id, in series order. */
    fun published(): List<Episode> = Episode.entries.filter { it.youtubeId != null }

    /** The video to open for the placeholder: its own watch URL, else the channel. */
    fun placeholderUrl(): String = resolve(null, PLACEHOLDER_VIDEO_ID)

    /** The video to open for [episode]: its own watch URL when published, else the [placeholderUrl]. */
    fun url(episode: Episode): String = resolve(episode.youtubeId, PLACEHOLDER_VIDEO_ID)

    /**
     * The YouTube caption language to request for an app in [appLanguage], or null when none applies: the
     * episode's [Episode.subtitles] are matched by exact tag (case-insensitive, `_` read as `-`), then by the
     * app tag's primary subtag (app `pt-BR` finds subtitle `pt`; app `pt` does not find `pt-BR`). English never
     * matches, because the video's own audio and captions are English.
     */
    fun subtitleLanguage(episode: Episode, appLanguage: String): String? = matchSubtitle(episode.subtitles, appLanguage)

    /**
     * The video to open for [episode] from an app in [appLanguage]: with a matching caption track the watch URL
     * asks YouTube for those captions and interface language, otherwise it is [url].
     *
     * The form stays `watch?v=`: the caption spike (2026-11-17, Android emulator) found the `/embed/<id>` form unusable as
     * a top-level link (YouTube player error 153), while the `watch` form is accepted by the YouTube app and by Chrome
     * (which turns `cc_lang_pref=fi` into its own `pp` parameter). That no captions appeared is inconclusive: the
     * test video had no translated track. Not verified on iOS or desktop.
     */
    fun url(episode: Episode, appLanguage: String): String = buildUrl(episode.youtubeId, episode.subtitles, appLanguage)

    /** True when the "(English)" qualifier of the video label is not needed: English app, or captions in the app language. */
    fun hasPlainLabel(episode: Episode, appLanguage: String): Boolean = plainLabel(episode.subtitles, appLanguage)

    /** The tutorials playlist when it is public, else the [placeholderUrl]. */
    fun playlistUrl(): String = resolvePlaylist(TUTORIAL_PLAYLIST_ID, PLACEHOLDER_VIDEO_ID)

    /** The fallback chain episode, then placeholder, then channel; separate from the ids so a test can drive it. */
    internal fun resolve(episodeId: String?, placeholderId: String?, channel: String = CHANNEL_URL): String =
        (episodeId ?: placeholderId)?.let(::watchUrl) ?: channel

    internal fun resolvePlaylist(playlistId: String?, placeholderId: String?, channel: String = CHANNEL_URL): String =
        playlistId?.let { "https://www.youtube.com/playlist?list=$it" } ?: resolve(null, placeholderId, channel)

    internal fun plainLabel(subtitles: Set<String>, appLanguage: String): Boolean =
        isEnglish(appLanguage) || matchSubtitle(subtitles, appLanguage) != null

    internal fun buildUrl(youtubeId: String?, subtitles: Set<String>, appLanguage: String): String {
        val match = matchSubtitle(subtitles, appLanguage)
        if (youtubeId == null || match == null) return resolve(youtubeId, PLACEHOLDER_VIDEO_ID)
        return "${watchUrl(youtubeId)}&cc_load_policy=1&cc_lang_pref=${encode(match)}&hl=${encode(normalise(appLanguage))}"
    }

    internal fun matchSubtitle(subtitles: Set<String>, appLanguage: String): String? {
        val app = normalise(appLanguage)
        if (app.isEmpty() || isEnglish(app)) return null
        val primary = app.substringBefore('-')
        return subtitles.firstOrNull { it.replace('_', '-').equals(app, ignoreCase = true) }
            ?: subtitles.firstOrNull { it.replace('_', '-').equals(primary, ignoreCase = true) }
    }

    private fun normalise(tag: String) = tag.trim().replace('_', '-')

    private fun isEnglish(tag: String) = normalise(tag).substringBefore('-').equals("en", ignoreCase = true)

    /** Percent-encodes everything but the unreserved characters (tags are ASCII, but stay safe in a URL). */
    private fun encode(value: String): String = buildString {
        for (b in value.encodeToByteArray()) {
            val c = b.toInt().toChar()
            if (b >= 0 && (c.isLetterOrDigit() || c in "-._~")) append(c)
            else append('%').append((b.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0'))
        }
    }

    private fun watchUrl(id: String) = "https://www.youtube.com/watch?v=$id"
}
