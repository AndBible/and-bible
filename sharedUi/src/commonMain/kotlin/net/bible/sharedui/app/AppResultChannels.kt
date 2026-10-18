package net.bible.sharedui.app

import net.bible.sharedcore.download.RepositoryResult
import net.bible.sharedcore.nav.BookmarkResult
import net.bible.sharedcore.nav.DocumentResult
import net.bible.sharedcore.nav.InstallZipResult
import net.bible.sharedcore.nav.KeyChooserResult
import net.bible.sharedcore.nav.LabelEditResult
import net.bible.sharedcore.nav.ManageLabelsResult
import net.bible.sharedcore.nav.MyDocumentPagesResult
import net.bible.sharedcore.nav.MyDocumentsResult
import net.bible.sharedcore.nav.PassageResult
import net.bible.sharedcore.nav.ReadingProgressResult
import net.bible.sharedcore.nav.TextSettingsResult
import net.bible.sharedcore.nav.WorkspaceResult
import net.bible.sharedui.nav.NavResultChannel

/**
 * The result channels (and the one shell callback) that the platform host still declares itself and hands to
 * [AppHosts.graphs]. The platform host keeps them because its reading destination's collectors read them (and the
 * channel guards read their declarations); the feature hosts only deliver into them. The manage-labels channel is
 * shared by the bookmark and workspace graphs.
 *
 * [beforeSwitchMyDocument] runs when the My Documents pages editor switches document; it needs the platform host's
 * reading request bookkeeping.
 */
class AppResultChannels(
    val readingProgress: NavResultChannel<ReadingProgressResult>,
    val bookmark: NavResultChannel<BookmarkResult>,
    val manageLabels: NavResultChannel<ManageLabelsResult>,
    val labelEdit: NavResultChannel<LabelEditResult>,
    val repositoryEditor: NavResultChannel<RepositoryResult>,
    val myDocumentPages: NavResultChannel<MyDocumentPagesResult>,
    val myDocuments: NavResultChannel<MyDocumentsResult>,
    val keyChooser: NavResultChannel<KeyChooserResult>,
    val passage: NavResultChannel<PassageResult>,
    val document: NavResultChannel<DocumentResult>,
    val installZip: NavResultChannel<InstallZipResult>,
    val workspace: NavResultChannel<WorkspaceResult>,
    val textSettings: NavResultChannel<TextSettingsResult>,
    val beforeSwitchMyDocument: () -> Unit,
)
