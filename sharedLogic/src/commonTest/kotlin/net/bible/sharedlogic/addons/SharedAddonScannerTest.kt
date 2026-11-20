package net.bible.sharedlogic.addons

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SharedAddonScannerTest {
    private val fs = FakeFileSystem()

    private fun touch(path: String) {
        val p = path.toPath()
        fs.createDirectories(p.parent!!)
        fs.write(p) { writeUtf8("x") }
    }

    @Test fun planKeyIsTheFileNameWithoutExtensionAndPathIsResolvedAgainstTheBookLocation() {
        val book = fakeAddonBook("PlanMod", "/mods/plan", plans = listOf("myplan.properties", "sub/other.v2.properties"))
        val plans = SharedAddonScanner({ listOf(book) }, fs).readingPlans()
        assertEquals(setOf("myplan", "other.v2"), plans.keys)
        assertEquals("/mods/plan/myplan.properties".toPath(), plans.getValue("myplan").path)
        assertEquals("/mods/plan/sub/other.v2.properties".toPath(), plans.getValue("other.v2").path)
        assertEquals("sub/other.v2.properties", plans.getValue("other.v2").fileName)
        assertSame(book, plans.getValue("myplan").book)
    }

    @Test fun dateBasedFlagIsCaseInsensitiveTrueAndEverythingElseIsFalse() {
        fun flag(value: String?) = SharedAddonScanner(
            { listOf(fakeAddonBook("B", "/m", plans = listOf("p.properties"), dateBased = value)) }, fs,
        ).readingPlans().getValue("p").isDateBased
        assertTrue(flag("True")); assertTrue(flag("true")); assertTrue(flag("TRUE"))
        assertFalse(flag("False")); assertFalse(flag("yes")); assertFalse(flag(null))
    }

    @Test fun theLaterBookWinsWhenTwoBooksProvideTheSameCode() {
        val a = fakeAddonBook("A", "/a", plans = listOf("same.properties"))
        val b = fakeAddonBook("B", "/b", plans = listOf("same.properties"))
        assertSame(b, SharedAddonScanner({ listOf(a, b) }, fs).readingPlans().getValue("same").book)
    }

    @Test fun booksWithoutTheProvidesPropertiesContributeNothing() {
        val plain = fakeAddonBook("Plain", "/p")
        val scanner = SharedAddonScanner({ listOf(plain) }, fs)
        assertTrue(scanner.readingPlans().isEmpty())
        assertTrue(scanner.promptPacks().isEmpty())
    }

    @Test fun promptPackPathIsResolvedAgainstTheLocationAndOnlyReadableFilesAreListed() {
        touch("/mods/ok/prompts/pack.csv")
        val ok = fakeAddonBook("Ok", "/mods/ok", prompts = "prompts/pack.csv")
        val missing = fakeAddonBook("Missing", "/mods/missing", prompts = "pack.csv")
        fs.createDirectories("/mods/dir/pack.csv".toPath()) // a directory is not a readable pack
        val dir = fakeAddonBook("Dir", "/mods/dir", prompts = "pack.csv")
        val packs = SharedAddonScanner({ listOf(ok, missing, dir) }, fs).promptPacks()
        assertEquals(listOf("Ok"), packs.map { it.moduleName })
        assertEquals("/mods/ok/prompts/pack.csv", packs.single().path)
    }

    @Test fun collectionsResolveTheDeclaredFileAndRejectUnsafeOrMissingPaths() {
        touch("/mods/ok/db/manna.sqlite3")
        touch("/mods/nover/c.db")
        touch("/outside.db")
        val ok = fakeAddonBook("Manna", "/mods/ok", labels = listOf("db/manna.sqlite3", "other.db"), version = "1.2")
        val nover = fakeAddonBook("NoVer", "/mods/nover", labels = listOf("c.db"))
        val abs = fakeAddonBook("Abs", "/mods/ok", labels = listOf("/outside.db"))
        val dotdot = fakeAddonBook("Dots", "/mods/ok", labels = listOf("../../outside.db"))
        val missing = fakeAddonBook("Gone", "/mods/ok", labels = listOf("gone.db"))
        val none = fakeAddonBook("None", "/mods/ok")
        val refs = SharedAddonScanner({ listOf(ok, nover, abs, dotdot, missing, none) }, fs).collections()
        assertEquals(listOf("Manna", "NoVer"), refs.map { it.moduleInitials })
        assertEquals("/mods/ok/db/manna.sqlite3".toPath(), refs[0].file)
        assertEquals("1.2", refs[0].version)
        assertEquals(ok.name, refs[0].moduleName)
        assertEquals("0", refs[1].version)
    }

    @Test fun collectionSourceReadsLiveAndFiresReloadedFromCatalogClearCaches() {
        touch("/mods/ok/m.db")
        val scanner = SharedAddonScanner({ listOf(fakeAddonBook("M", "/mods/ok", labels = listOf("m.db"))) }, fs)
        val source = SharedCollectionPackSource(scanner)
        val catalog = SharedAddonCatalog(scanner, SharedPromptPackSource(scanner), source)
        var fired = 0
        source.reloaded.subscribe { fired++ }
        assertEquals(1, source.collections().size)
        catalog.clearCaches()
        assertEquals(1, fired)
    }

    @Test fun booksAreQueriedOnceUntilCachesAreCleared() {
        var calls = 0
        val scanner = SharedAddonScanner({ calls++; listOf(fakeAddonBook("A", "/a", plans = listOf("p.properties"))) }, fs)
        scanner.readingPlans(); scanner.readingPlans(); scanner.promptPacks()
        assertEquals(1, calls)
        scanner.clearCaches()
        scanner.readingPlans()
        assertEquals(2, calls)
    }

    @Test fun fontsAreServedFromTheSameCachedBookListAsEveryOtherView() {
        var calls = 0
        val scanner = SharedAddonScanner({ calls++; listOf(fakeAddonBook("A", "/a", plans = listOf("p.properties"))) }, fs)
        repeat(3) { scanner.fonts() }
        scanner.readingPlans()
        assertEquals(1, calls, "repeated font scans must not re-query the installed books")
        scanner.clearCaches()
        scanner.fonts()
        assertEquals(2, calls)
    }

    @Test fun catalogClearCachesRequeriesBooksAndFiresPromptReloadedOnce() {
        var installed = listOf(fakeAddonBook("A", "/a", plans = listOf("one.properties")))
        val scanner = SharedAddonScanner({ installed }, fs)
        val promptSource = SharedPromptPackSource(scanner)
        val catalog = SharedAddonCatalog(scanner, promptSource, SharedCollectionPackSource(scanner))
        var fired = 0
        promptSource.reloaded.subscribe { fired++ }
        assertEquals(setOf("one"), catalog.readingPlans.keys)

        installed = listOf(fakeAddonBook("A", "/a", plans = listOf("two.properties")))
        assertEquals(setOf("one"), catalog.readingPlans.keys) // still cached
        assertEquals(0, fired)
        catalog.clearCaches()
        assertEquals(setOf("two"), catalog.readingPlans.keys)
        assertEquals(1, fired)
    }

    @Test fun backgroundInventoryResolvesModuleTokensAndRejectsMissingFiles() {
        touch("/mods/photo/images/leaf.jpg")
        val photo = FakeAddonBook(FakeAddonMetaData("Photo", "/mods/photo",
            mapOf("AndBibleProvidesBackgroundImage" to listOf("Leaf;images/leaf.jpg", "Ignored;other.jpg")), emptyMap()))
        val missing = FakeAddonBook(FakeAddonMetaData("Missing", "/mods/missing",
            mapOf("AndBibleProvidesBackgroundImage" to listOf("Absent;none.jpg")), emptyMap()))
        val inventory = SharedAddonScanner({ listOf(photo, missing) }, fs).backgroundImages()
        assertEquals(setOf("Photo"), inventory.keys)
        assertEquals("Leaf", inventory.getValue("Photo").name)
        assertEquals("/mods/photo/images/leaf.jpg".toPath(), inventory.getValue("Photo").path)
    }

    @Test fun fontCatalogRejectsMalformedMissingAndEscapingFilesAndUsesTheLastFamily() {
        touch("/mods/a/one.ttf")
        touch("/mods/b/two.ttf")
        touch("/outside.ttf")
        fs.allowSymlinks = true
        fs.createSymlink("/mods/a/link.ttf".toPath(), "/outside.ttf".toPath())
        val a = FakeAddonBook(FakeAddonMetaData("A", "/mods/a", mapOf("AndBibleProvidesFont" to listOf(
            "Study;one.ttf", "Malformed", ";one.ttf", "Missing;absent.ttf", "Outside;../../outside.ttf", "Link;link.ttf",
        )), emptyMap()))
        val b = FakeAddonBook(FakeAddonMetaData("B", "/mods/b", mapOf("AndBibleProvidesFont" to listOf("Study;two.ttf")), emptyMap()))
        var installed = listOf(a)
        val scanner = SharedAddonScanner({ installed }, fs)
        assertEquals(listOf(net.bible.sharedlogic.platform.AddonFont("A", "Study", "one.ttf", "/mods/a/one.ttf")), scanner.fonts())
        installed = listOf(a, b)
        scanner.clearCaches()
        assertEquals(listOf(net.bible.sharedlogic.platform.AddonFont("B", "Study", "two.ttf", "/mods/b/two.ttf")), scanner.fonts())
        fs.delete("/mods/b/two.ttf".toPath())
        assertEquals(listOf(net.bible.sharedlogic.platform.AddonFont("A", "Study", "one.ttf", "/mods/a/one.ttf")), scanner.fonts())
    }

    @Test fun promptPackSourceReadsTheScannerLive() {
        touch("/m/p.csv")
        val source = SharedPromptPackSource(SharedAddonScanner({ listOf(fakeAddonBook("M", "/m", prompts = "p.csv")) }, fs))
        assertEquals(listOf(net.bible.sharedlogic.platform.PromptPackRef("M", "/m/p.csv")), source.packs)
    }
}
