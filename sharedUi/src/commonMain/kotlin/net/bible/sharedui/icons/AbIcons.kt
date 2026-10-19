@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.icons

import org.jetbrains.compose.resources.DrawableResource
import net.bible.sharedui.generated.resources.Res
import net.bible.sharedui.generated.resources.file_export
import net.bible.sharedui.generated.resources.ic_add_circle_outline_white_24dp
import net.bible.sharedui.generated.resources.ic_baseline_bookmark_24
import net.bible.sharedui.generated.resources.ic_baseline_headphones_24
import net.bible.sharedui.generated.resources.ic_baseline_menu_book_24
import net.bible.sharedui.generated.resources.ic_baseline_refresh_24
import net.bible.sharedui.generated.resources.ic_baseline_share_24
import net.bible.sharedui.generated.resources.ic_baseline_studypads_24
import net.bible.sharedui.generated.resources.ic_baseline_undo_24
import net.bible.sharedui.generated.resources.ic_backup_black_24dp
import net.bible.sharedui.generated.resources.ic_bible_24dp
import net.bible.sharedui.generated.resources.ic_calculator_color
import net.bible.sharedui.generated.resources.ic_check_24dp
import net.bible.sharedui.generated.resources.ic_commentary
import net.bible.sharedui.generated.resources.ic_delete_24dp
import net.bible.sharedui.generated.resources.ic_error_outline_black_24dp
import net.bible.sharedui.generated.resources.ic_file_download_24dp
import net.bible.sharedui.generated.resources.ic_help_white_24dp
import net.bible.sharedui.generated.resources.ic_label_24dp
import net.bible.sharedui.generated.resources.ic_logo
import net.bible.sharedui.generated.resources.ic_menu
import net.bible.sharedui.generated.resources.ic_more_vert_black_24dp
import net.bible.sharedui.generated.resources.ic_save_24dp
import net.bible.sharedui.generated.resources.ic_search_24dp
import net.bible.sharedui.generated.resources.ic_strongs_hebrew
import net.bible.sharedui.generated.resources.ic_sync_white_24dp
import net.bible.sharedui.generated.resources.ic_syncdb_24dp
import net.bible.sharedui.generated.resources.ic_text_format_white_24dp
import net.bible.sharedui.generated.resources.ic_unarchive_white_24dp
import net.bible.sharedui.generated.resources.ic_workspace_solid_24dp
import net.bible.sharedui.generated.resources.icon_bell
import net.bible.sharedui.generated.resources.icon_book
import net.bible.sharedui.generated.resources.icon_book_bible
import net.bible.sharedui.generated.resources.icon_calendar
import net.bible.sharedui.generated.resources.icon_church
import net.bible.sharedui.generated.resources.icon_circle_exclamation
import net.bible.sharedui.generated.resources.icon_circle_info
import net.bible.sharedui.generated.resources.icon_circle_question
import net.bible.sharedui.generated.resources.icon_clock
import net.bible.sharedui.generated.resources.icon_comment
import net.bible.sharedui.generated.resources.icon_cross
import net.bible.sharedui.generated.resources.icon_crown
import net.bible.sharedui.generated.resources.icon_envelope
import net.bible.sharedui.generated.resources.icon_flag
import net.bible.sharedui.generated.resources.icon_globe
import net.bible.sharedui.generated.resources.icon_handshake
import net.bible.sharedui.generated.resources.icon_heart
import net.bible.sharedui.generated.resources.icon_heart_crack
import net.bible.sharedui.generated.resources.icon_key
import net.bible.sharedui.generated.resources.icon_landmark
import net.bible.sharedui.generated.resources.icon_lightbulb
import net.bible.sharedui.generated.resources.icon_link
import net.bible.sharedui.generated.resources.icon_location_dot
import net.bible.sharedui.generated.resources.icon_microphone
import net.bible.sharedui.generated.resources.icon_music
import net.bible.sharedui.generated.resources.icon_person_praying
import net.bible.sharedui.generated.resources.icon_robot
import net.bible.sharedui.generated.resources.icon_share_nodes
import net.bible.sharedui.generated.resources.icon_star
import net.bible.sharedui.generated.resources.icon_star_of_david
import net.bible.sharedui.generated.resources.icon_tag
import net.bible.sharedui.generated.resources.icon_user

/** Shared Compose resource counterparts for Android's drawable inventories. */
@kotlin.native.HiddenFromObjC
object AbIcons {
    val labelIconResources: Map<String, DrawableResource> = mapOf(
        "book" to Res.drawable.icon_book,
        "book-bible" to Res.drawable.icon_book_bible,
        "cross" to Res.drawable.icon_cross,
        "church" to Res.drawable.icon_church,
        "star-of-david" to Res.drawable.icon_star_of_david,
        "person-praying" to Res.drawable.icon_person_praying,
        "info" to Res.drawable.icon_circle_info,
        "question" to Res.drawable.icon_circle_question,
        "exclamation" to Res.drawable.icon_circle_exclamation,
        "lightbulb" to Res.drawable.icon_lightbulb,
        "bell" to Res.drawable.icon_bell,
        "flag" to Res.drawable.icon_flag,
        "star" to Res.drawable.icon_star,
        "tag" to Res.drawable.icon_tag,
        "envelope" to Res.drawable.icon_envelope,
        "comment" to Res.drawable.icon_comment,
        "share-nodes" to Res.drawable.icon_share_nodes,
        "link" to Res.drawable.icon_link,
        "handshake" to Res.drawable.icon_handshake,
        "clock" to Res.drawable.icon_clock,
        "map-marker" to Res.drawable.icon_location_dot,
        "globe" to Res.drawable.icon_globe,
        "landmark" to Res.drawable.icon_landmark,
        "calendar" to Res.drawable.icon_calendar,
        "user" to Res.drawable.icon_user,
        "music" to Res.drawable.icon_music,
        "microphone" to Res.drawable.icon_microphone,
        "key" to Res.drawable.icon_key,
        "crown" to Res.drawable.icon_crown,
        "heart" to Res.drawable.icon_heart,
        "heart-crack" to Res.drawable.icon_heart_crack,
        "robot" to Res.drawable.icon_robot,
    )

    val bookmarkIconResources: Map<String, DrawableResource> = mapOf(
        "ic_baseline_bookmark_24" to Res.drawable.ic_baseline_bookmark_24,
        "ic_baseline_studypads_24" to Res.drawable.ic_baseline_studypads_24,
        "ic_label_24dp" to Res.drawable.ic_label_24dp,
        "ic_baseline_share_24" to Res.drawable.ic_baseline_share_24,
        "ic_save_24dp" to Res.drawable.ic_save_24dp,
    )

    val bookmarkActionIconResources: Map<String, DrawableResource> = mapOf(
        "ic_search_24dp" to Res.drawable.ic_search_24dp,
        "ic_add_circle_outline_white_24dp" to Res.drawable.ic_add_circle_outline_white_24dp,
        "ic_help_white_24dp" to Res.drawable.ic_help_white_24dp,
        "ic_baseline_refresh_24" to Res.drawable.ic_baseline_refresh_24,
        "ic_text_format_white_24dp" to Res.drawable.ic_text_format_white_24dp,
        "ic_baseline_undo_24" to Res.drawable.ic_baseline_undo_24,
        "file_export" to Res.drawable.file_export,
        "ic_file_download_24dp" to Res.drawable.ic_file_download_24dp,
        "ic_check_24dp" to Res.drawable.ic_check_24dp,
        "ic_delete_24dp" to Res.drawable.ic_delete_24dp,
    )

    val chooserOverflowIconResources: Map<String, DrawableResource> = mapOf(
        "ic_file_download_24dp" to Res.drawable.ic_file_download_24dp,
        "ic_backup_black_24dp" to Res.drawable.ic_backup_black_24dp,
        "ic_unarchive_white_24dp" to Res.drawable.ic_unarchive_white_24dp,
    )

    val downloadOverflowIconResources: Map<String, DrawableResource> = mapOf(
        "ic_error_outline_black_24dp" to Res.drawable.ic_error_outline_black_24dp,
        "ic_unarchive_white_24dp" to Res.drawable.ic_unarchive_white_24dp,
        "ic_syncdb_24dp" to Res.drawable.ic_syncdb_24dp,
    )

    val cloudDocumentsOverflowIconResources: Map<String, DrawableResource> = mapOf(
        "ic_sync_white_24dp" to Res.drawable.ic_sync_white_24dp,
        "ic_baseline_refresh_24" to Res.drawable.ic_baseline_refresh_24,
        "ic_help_white_24dp" to Res.drawable.ic_help_white_24dp,
    )

    val readingToolbarIconResources: Map<String, DrawableResource> = mapOf(
        "ic_menu" to Res.drawable.ic_menu,
        "ic_search_24dp" to Res.drawable.ic_search_24dp,
        "ic_baseline_headphones_24" to Res.drawable.ic_baseline_headphones_24,
        "ic_strongs_hebrew" to Res.drawable.ic_strongs_hebrew,
        "ic_bible_24dp" to Res.drawable.ic_bible_24dp,
        "ic_baseline_menu_book_24" to Res.drawable.ic_baseline_menu_book_24,
        "ic_commentary" to Res.drawable.ic_commentary,
        "ic_workspace_solid_24dp" to Res.drawable.ic_workspace_solid_24dp,
        "ic_more_vert_black_24dp" to Res.drawable.ic_more_vert_black_24dp,
        "ic_syncdb_24dp" to Res.drawable.ic_syncdb_24dp,
    )

    val identityIconResources: Map<String, DrawableResource> = mapOf(
        "ic_logo" to Res.drawable.ic_logo,
        "ic_calculator_color" to Res.drawable.ic_calculator_color,
    )

    /** Every drawable above; de-duplicated so shared icons are rendered once by the resource test. */
    val all: Map<String, DrawableResource> = buildMap {
        putAll(labelIconResources)
        putAll(bookmarkIconResources)
        putAll(bookmarkActionIconResources)
        putAll(chooserOverflowIconResources)
        putAll(downloadOverflowIconResources)
        putAll(cloudDocumentsOverflowIconResources)
        putAll(readingToolbarIconResources)
        putAll(identityIconResources)
    }
}

internal fun readingBibleIconKey(discrete: Boolean): String =
    if (discrete) "ic_baseline_menu_book_24" else "ic_bible_24dp"

/** The custom label glyph resources, keyed exactly like Android's `customIconMap`. */
@kotlin.native.HiddenFromObjC
val labelIconResources: Map<String, DrawableResource> get() = AbIcons.labelIconResources
