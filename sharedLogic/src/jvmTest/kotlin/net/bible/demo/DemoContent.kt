package net.bible.demo

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The demo content a store-screenshot run installs into the app: a YAML file that [DemoBackupGenerator] turns into an
 * ordinary AndBible database backup (`.abdb.zip`), restorable through the normal restore UI on Android, iOS and desktop.
 * The format is parsed strictly: an unknown field, an unknown enum value or a reference to an undefined id fails with a
 * message naming the offending item.
 *
 * ```yaml
 * labels:
 *   - id: char-god              # local key, only used inside this file (bookmarks, studypads)
 *     name: "Character of God"
 *     color: "#E57373"          # #RRGGBB or #AARRGGBB
 *     favourite: true           # optional, default false
 *     displayStyle: HIGHLIGHT   # optional: HIGHLIGHT | UNDERLINE | MARKER | HIDDEN
 *     wholeVerseStyle: UNDERLINE  # optional, same values; default null = inherit displayStyle
 * bookmarks:
 *   - id: jer-9-24
 *     module: KJV               # optional; documentation only, see the note on book below
 *     v11n: KJV                 # optional, default KJV; the versification the ref is read in
 *     ref: "Jer.9.24"           # OSIS verse or range "Jer.9.23-Jer.9.24"
 *     labels: [char-god, love]  # optional
 *     primaryLabel: char-god    # optional, must be one of labels
 *     note: "<p>HTML allowed</p>"  # optional; stored as the bookmark's note (HTML content type)
 * studypads:                    # a label's journal: ordered entries, bookmark entries and text entries
 *   - label: sower
 *     entries:
 *       - bookmark: mat-13-3    # must carry that label
 *       - text: "<p>HTML text entry</p>"
 * workspaces:                   # order = order in the app; the FIRST one is the active one after restore
 *   - name: bookmarks-labels    # by convention the shot id
 *     windows:                  # order = window order
 *       - category: BIBLE       # BIBLE | COMMENTARY | DICTIONARY | GENERAL_BOOK | MAPS | MY_NOTE | STUDYPAD
 *         document: KJV         # module initials; omitted for MY_NOTE and STUDYPAD
 *         key: "Jer.9.24"       # BIBLE/COMMENTARY/MY_NOTE: OSIS verse; DICTIONARY/GENERAL_BOOK/MAPS: the module key
 *         bibleDocument: KJV    # optional: the window's Bible module when category is COMMENTARY or MY_NOTE
 *         label: sower          # STUDYPAD only: the label id
 *         weight: 1.0           # optional
 *         synchronized: true    # optional, default true
 *         pinned: false         # optional, default false
 *         state: VISIBLE        # VISIBLE | MINIMISED (default VISIBLE; SPLIT is accepted as VISIBLE)
 *         maximized: false      # optional: makes this the workspace's maximized window
 *     textDisplaySettings:      # optional workspace-level overrides, by TextDisplaySettings field name
 *       strongsMode: 1
 *       showXrefs: true
 * ```
 *
 * Notes on what the generated backup contains:
 * - Bookmarks are written with the real DAOs. Ordinals come from the versification of `v11n` (KJV by default) and the
 *   KJVA ordinals the app also stores. The `book` column stays empty, because the generator has no Sword modules
 *   installed; the app then treats the bookmark as one that applies to any Bible module, which is what the shots need.
 * - Window documents are stored by initials only, so the modules must be installed on the device that restores the
 *   backup (their absence is the app's normal "document not installed" state, not a restore failure).
 * - A STUDYPAD window is stored as the app stores it: the pseudo document `MyStudyPads` with key `journal:<label id>`.
 */
@Serializable
data class DemoContent(
    val labels: List<DemoLabel> = emptyList(),
    val bookmarks: List<DemoBookmark> = emptyList(),
    val studypads: List<DemoStudyPad> = emptyList(),
    val workspaces: List<DemoWorkspace> = emptyList(),
) {
    companion object {
        fun parse(yaml: String): DemoContent = Yaml.default.decodeFromString(serializer(), yaml)
    }
}

@Serializable
data class DemoLabel(
    val id: String,
    val name: String,
    val color: String,
    val favourite: Boolean = false,
    val displayStyle: String? = null,
    val wholeVerseStyle: String? = null,
)

@Serializable
data class DemoBookmark(
    val id: String,
    val module: String? = null,
    val v11n: String = "KJV",
    val ref: String,
    val labels: List<String> = emptyList(),
    val primaryLabel: String? = null,
    val note: String? = null,
)

@Serializable
data class DemoStudyPad(
    val label: String,
    val entries: List<DemoStudyPadEntry> = emptyList(),
)

/** Exactly one of [bookmark] and [text] is set. */
@Serializable
data class DemoStudyPadEntry(
    val bookmark: String? = null,
    val text: String? = null,
)

@Serializable
data class DemoWorkspace(
    val name: String,
    val windows: List<DemoWindow> = emptyList(),
    val textDisplaySettings: Map<String, String> = emptyMap(),
)

@Serializable
data class DemoWindow(
    val category: String,
    val document: String? = null,
    val key: String? = null,
    val bibleDocument: String? = null,
    val label: String? = null,
    val weight: Float = 1.0f,
    val synchronized: Boolean = true,
    val pinned: Boolean = false,
    val state: String = "VISIBLE",
    val maximized: Boolean = false,
)
