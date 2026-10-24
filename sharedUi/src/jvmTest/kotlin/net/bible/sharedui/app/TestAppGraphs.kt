package net.bible.sharedui.app

import sun.misc.Unsafe

/**
 * Test fixture for [AppGraphs].
 *
 * The `*NavDeps` classes nest 60+ further deps classes with hundreds of required slots, so no-op lambdas
 * are not a practical way to build them. Every field is therefore allocated WITHOUT running its constructor
 * (`Unsafe.allocateInstance`): the object exists, its fields are null. That is sound only because the
 * tests using it never open a feature destination, and the graph builders only register routes (they read
 * deps inside the composable lambdas). A test that navigates into a feature graph must give that field a
 * real minimal value instead.
 */
object TestAppGraphs {
    private val unsafe: Unsafe = Unsafe::class.java.getDeclaredField("theUnsafe")
        .apply { isAccessible = true }.get(null) as Unsafe

    @Suppress("UNCHECKED_CAST")
    private fun <T> blank(type: Class<T>): T = unsafe.allocateInstance(type) as T

    fun minimal(): AppGraphs = AppGraphs(
        ai = blank(net.bible.sharedui.ai.nav.AiNavDeps::class.java),
        bookmark = blank(net.bible.sharedui.bookmark.nav.BookmarkNavDeps::class.java),
        readingPlan = blank(net.bible.sharedui.readingplan.nav.ReadingPlanNavDeps::class.java),
        search = blank(net.bible.sharedui.search.nav.SearchNavDeps::class.java),
        settings = blank(net.bible.sharedui.settings.nav.SettingsNavDeps::class.java),
        download = blank(net.bible.sharedui.download.nav.DownloadNavDeps::class.java),
        myDocuments = blank(net.bible.sharedui.mydocuments.nav.MyDocumentsNavDeps::class.java),
        chooser = blank(net.bible.sharedui.navigation.nav.ChooserNavDeps::class.java),
        workspace = blank(net.bible.sharedui.workspaces.nav.WorkspaceNavDeps::class.java),
        backup = blank(net.bible.sharedui.backup.nav.BackupNavDeps::class.java),
        installZip = blank(net.bible.sharedui.installzip.nav.InstallZipNavDeps::class.java),
        welcome = blank(net.bible.sharedui.startup.nav.WelcomeNavDeps::class.java),
    )
}
