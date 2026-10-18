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

    @Test fun booksAreQueriedOnceUntilCachesAreCleared() {
        var calls = 0
        val scanner = SharedAddonScanner({ calls++; listOf(fakeAddonBook("A", "/a", plans = listOf("p.properties"))) }, fs)
        scanner.readingPlans(); scanner.readingPlans(); scanner.promptPacks()
        assertEquals(1, calls)
        scanner.clearCaches()
        scanner.readingPlans()
        assertEquals(2, calls)
    }

    @Test fun catalogClearCachesRequeriesBooksAndFiresPromptReloadedOnce() {
        var installed = listOf(fakeAddonBook("A", "/a", plans = listOf("one.properties")))
        val scanner = SharedAddonScanner({ installed }, fs)
        val promptSource = SharedPromptPackSource(scanner)
        val catalog = SharedAddonCatalog(scanner, promptSource)
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

    @Test fun promptPackSourceReadsTheScannerLive() {
        touch("/m/p.csv")
        val source = SharedPromptPackSource(SharedAddonScanner({ listOf(fakeAddonBook("M", "/m", prompts = "p.csv")) }, fs))
        assertEquals(listOf(net.bible.sharedlogic.platform.PromptPackRef("M", "/m/p.csv")), source.packs)
    }
}
