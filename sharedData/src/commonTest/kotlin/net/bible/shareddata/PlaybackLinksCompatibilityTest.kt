package net.bible.shareddata

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import net.bible.android.database.bookmarks.PlaybackSettings
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.android.database.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** Field shape checked against current-stable and beta-1260/beta-1280 before adding speakLinks. */
@Serializable
private data class OldPlaybackSettings(
    val speakChapterChanges: Boolean = true,
    val speakTitles: Boolean = true,
    val speakFootnotes: Boolean = false,
    val speed: Int = 100,
    val bookId: String? = null,
    val bookmarkWasCreated: Boolean? = null,
    val verseRange: String? = null,
)

class PlaybackLinksCompatibilityTest {
    @Test
    fun defaultLinksPreserveOldJsonIncludingOtherPlaybackSettings() {
        val settings = PlaybackSettings(speed = 145, speakFootnotes = true, bookId = "KJV")
        val encoded = settings.toJson()
        assertFalse("speakLinks" in encoded)
        assertEquals(json.encodeToString(OldPlaybackSettings(speed = 145, speakFootnotes = true, bookId = "KJV")), encoded)
        assertEquals(OldPlaybackSettings(speed = 145, speakFootnotes = true, bookId = "KJV"), json.decodeFromString<OldPlaybackSettings>(encoded))
        assertFalse("speakLinks" in SpeakSettings(settings).toJson())
    }

    @Test
    fun disabledLinksRoundTripInPlaybackAndWorkspaceSettings() {
        val settings = PlaybackSettings.fromJson("""{"speed":145,"speakLinks":false}""")
        assertEquals(145, settings.speed)
        assertFalse(settings.speakLinks)
        assertEquals(settings, PlaybackSettings.fromJson(settings.toJson()))
        val workspace = SpeakSettings(settings, sleepTimer = 7)
        assertEquals(workspace, SpeakSettings.fromJson(workspace.toJson()))
        // Older clients fall back to ALL defaults for OFF; this is a documented sync limitation.
        assertFailsWith<SerializationException> { json.decodeFromString<OldPlaybackSettings>(settings.toJson()) }
    }
}
