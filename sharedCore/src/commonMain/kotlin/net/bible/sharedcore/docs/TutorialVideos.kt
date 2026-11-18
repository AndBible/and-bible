package net.bible.sharedcore.docs

/**
 * The 6.0 video series and the shared URL/caption policy for every in-app tutorial link.
 *
 * Video ids and uploaded subtitle languages come from `website/data/tutorials.yaml`, through the generated
 * [TutorialVideoCatalog]. After publishing, regenerate it with `cd website && uv run python -m sitegen.app_tutorials`.
 * While an episode has no id, [url] opens the "videos coming soon" placeholder with its own subtitle tracks.
 *
 * The enum names are the episode folder names under `website/video/episodes/`, upper-cased with `_` for `-`
 * ([slug] gives the folder name back); `TutorialVideosEpisodesTest` keeps both sides in step.
 */
object TutorialVideos {
    /** The AndBible YouTube channel: what the placeholder falls back to until its own video is uploaded. */
    const val CHANNEL_URL = "https://www.youtube.com/@AndBible"

    /** Id of the "Videos coming soon" placeholder video; null until uploaded. */
    val PLACEHOLDER_VIDEO_ID: String? = TutorialVideoCatalog.placeholderId

    /** Uploaded translated captions of the placeholder, used until an episode is published. */
    val PLACEHOLDER_SUBTITLES: Set<String> = TutorialVideoCatalog.placeholderSubtitles

    /** Id of the tutorials playlist ("AndBible tutorials"); null until the playlist is public. */
    val TUTORIAL_PLAYLIST_ID: String? = TutorialVideoCatalog.playlistId

    /**
     * One video of the series; [youtubeId] is null until it is published. [subtitles] are the YouTube caption
     * languages (BCP-47, sorted) uploaded besides English. Both are generated from the website catalog.
     */
    enum class Episode {
        WHATS_NEW_6_0,
        TUTORIAL_01_GETTING_STARTED,
        TUTORIAL_02_FINDING_YOUR_WAY,
        TUTORIAL_03_VERSE_MENU,
        TUTORIAL_04_STRONGS_DICTIONARIES_CROSSREFS,
        TUTORIAL_05_WINDOWS_1_OPEN_AND_ARRANGE,
        TUTORIAL_06_WINDOWS_2_PIN_SYNC_LINKS,
        TUTORIAL_07_WORKSPACES,
        TUTORIAL_08_BOOKMARKS,
        TUTORIAL_09_LABELS_AND_HIGHLIGHTS,
        TUTORIAL_10_NOTES,
        TUTORIAL_11_STUDY_PADS,
        TUTORIAL_12_SEARCH_1_BASICS,
        TUTORIAL_13_SEARCH_2_ADVANCED,
        TUTORIAL_14_SPEAK,
        TUTORIAL_15_READING_PLANS_AND_PROGRESS,
        TUTORIAL_16_BACKUP_AND_RESTORE,
        TUTORIAL_17_SYNC_1_GOOGLE_DRIVE,
        TUTORIAL_18_SYNC_2_MORE_BACKENDS_AND_DOCUMENTS,
        TUTORIAL_19_APPEARANCE_1_NIGHT_MODE_FONTS_COLOURS,
        TUTORIAL_20_APPEARANCE_2_GLOBAL_WORKSPACE_WINDOW,
        TUTORIAL_21_DOCUMENT_LIBRARY,
        TUTORIAL_22_LOAD_FILES_AND_CUSTOM_REPOSITORIES,
        TUTORIAL_23_MY_DOCUMENTS_AND_EPUB,
        TUTORIAL_24_SHARING_AND_BIBLE_LINKS,
        TUTORIAL_25_MEMORIZE,
        TUTORIAL_26_DESKTOP,
        TUTORIAL_27_STUDY_WORKFLOWS_1_TOPICAL_AND_SERMON,
        TUTORIAL_28_STUDY_WORKFLOWS_2_WORD_STUDY_AND_DEVOTIONS,
        TUTORIAL_29_AI_1_GETTING_STARTED,
        TUTORIAL_30_AI_2_PROMPTS,
        TUTORIAL_31_AI_3_TOOLS_DATA_PERMISSIONS,
        TUTORIAL_32_AI_4_CUSTOM_PROMPTS_COSTS_SYNC;

        /** The episode's folder name under `website/video/episodes/`. */
        val slug: String get() = name.lowercase().replace('_', '-')

        val youtubeId: String? get() = TutorialVideoCatalog.episodes.getValue(slug).youtubeId
        val subtitles: Set<String> get() = TutorialVideoCatalog.episodes.getValue(slug).subtitles
    }

    /** The episodes that have a YouTube id, in series order. */
    fun published(): List<Episode> = Episode.entries.filter { it.youtubeId != null }

    /** The video to open for the placeholder: its own watch URL, else the channel. */
    fun placeholderUrl(): String = resolve(null, PLACEHOLDER_VIDEO_ID)

    /** The video to open for [episode]: its own watch URL when published, else the [placeholderUrl]. */
    fun url(episode: Episode): String = resolve(episode.youtubeId, PLACEHOLDER_VIDEO_ID)

    /**
     * The YouTube caption language to request for an app in [appLanguage], or null when none applies: the
     * opened video's subtitles (the placeholder's when unpublished) are matched by exact tag (case-insensitive, `_` read as `-`), then by the
     * app tag's primary subtag (app `pt-BR` finds subtitle `pt`; app `pt` does not find `pt-BR`). English never
     * matches, because the video's own audio and captions are English.
     */
    fun subtitleLanguage(episode: Episode, appLanguage: String): String? = matchSubtitle(effectiveSubtitles(episode.youtubeId, episode.subtitles), appLanguage)

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
    fun hasPlainLabel(episode: Episode, appLanguage: String): Boolean =
        plainLabel(effectiveSubtitles(episode.youtubeId, episode.subtitles), appLanguage)

    /** The tutorials playlist when it is public, else the [placeholderUrl]. */
    fun playlistUrl(): String = resolvePlaylist(TUTORIAL_PLAYLIST_ID, PLACEHOLDER_VIDEO_ID)

    /** The fallback chain episode, then placeholder, then channel; separate from the ids so a test can drive it. */
    internal fun resolve(episodeId: String?, placeholderId: String?, channel: String = CHANNEL_URL): String =
        (episodeId ?: placeholderId)?.let(::watchUrl) ?: channel

    internal fun resolvePlaylist(playlistId: String?, placeholderId: String?, channel: String = CHANNEL_URL): String =
        playlistId?.let { "https://www.youtube.com/playlist?list=$it" } ?: resolve(null, placeholderId, channel)

    internal fun plainLabel(subtitles: Set<String>, appLanguage: String): Boolean =
        isEnglish(appLanguage) || matchSubtitle(subtitles, appLanguage) != null

    private fun effectiveSubtitles(youtubeId: String?, subtitles: Set<String>): Set<String> =
        if (youtubeId == null) PLACEHOLDER_SUBTITLES else subtitles

    internal fun buildUrl(youtubeId: String?, subtitles: Set<String>, appLanguage: String): String {
        val videoId = youtubeId ?: PLACEHOLDER_VIDEO_ID
        val match = matchSubtitle(effectiveSubtitles(youtubeId, subtitles), appLanguage)
        if (videoId == null || match == null) return resolve(youtubeId, PLACEHOLDER_VIDEO_ID)
        return "${watchUrl(videoId)}&cc_load_policy=1&cc_lang_pref=${encode(match)}&hl=${encode(normalise(appLanguage))}"
    }

    internal fun matchSubtitle(subtitles: Set<String>, appLanguage: String): String? {
        val app = normalise(appLanguage)
        if (app.isEmpty() || isEnglish(app)) return null
        for (candidate in candidates(app)) {
            subtitles.firstOrNull { it.replace('_', '-').equals(candidate, ignoreCase = true) }?.let { return it }
        }
        return null
    }

    /**
     * The tags to try for an app tag, most specific first: the tag itself, then language+script, language+region
     * (`zh-Hant-TW` finds `zh-TW`), the bare language; each also with the legacy alias of the language.
     */
    private fun candidates(app: String): List<String> {
        val parts = app.split('-')
        val lang = parts[0]
        val rest = parts.drop(1)
        val script = rest.firstOrNull { it.length == 4 && it.all(Char::isLetter) }
        val region = rest.firstOrNull { it != script && (it.length == 2 && it.all(Char::isLetter) || it.length == 3 && it.all(Char::isDigit)) }
        // YouTube uses regional Chinese tracks even when the app resolves a script-only locale.
        // An explicit script wins over a conflicting region; exact/script tracks still win first.
        val chineseRegion = if (lang.equals("zh", ignoreCase = true)) {
            when {
                script.equals("Hans", ignoreCase = true) -> "CN"
                script.equals("Hant", ignoreCase = true) -> "TW"
                region.equals("SG", ignoreCase = true) -> "CN"
                region.equals("HK", ignoreCase = true) -> "TW"
                else -> null
            }
        } else null
        val compatibleRegion = region.takeUnless {
            lang.equals("zh", ignoreCase = true) && (
                script.equals("Hans", ignoreCase = true) && (it.equals("TW", true) || it.equals("HK", true)) ||
                    script.equals("Hant", ignoreCase = true) && (it.equals("CN", true) || it.equals("SG", true))
                )
        }
        val tails = listOfNotNull(rest.joinToString("-"), script, chineseRegion, compatibleRegion, "").distinct()
        val langs = listOf(lang) + listOfNotNull(LANGUAGE_ALIASES[lang.lowercase()])
        return langs.flatMap { l -> tails.map { t -> if (t.isEmpty()) l else "$l-$t" } }.distinct()
    }

    /** Legacy and platform-specific language codes and their YouTube equivalents (both directions). */
    private val LANGUAGE_ALIASES = mapOf(
        "iw" to "he", "he" to "iw", "in" to "id", "id" to "in", "ji" to "yi", "yi" to "ji",
        "no" to "nb", "nb" to "no", "tl" to "fil", "fil" to "tl",
    )

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
