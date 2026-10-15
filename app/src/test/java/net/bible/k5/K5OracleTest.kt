package net.bible.k5

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.SharedConstants
import net.bible.android.control.page.CommentaryBlockResolver
import net.bible.android.control.page.SwordCommentaryWalker
import net.bible.android.control.search.SearchHighlighter
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.android.misc.OsisFragment
import net.bible.android.platform.AndroidAppEnvironment
import net.bible.android.platform.androidSaxTextPolicy
import net.bible.service.installzip.DefaultInstallCommitter
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.read.GetDictionaryEntryTool
import net.bible.service.llm.tools.read.GetGenBookContentTool
import net.bible.service.llm.tools.read.GetVerseContentTool
import net.bible.service.sword.ShareSelection
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.epub.*
import net.bible.test.*
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookPathJvm
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRangeFactory
import org.jdom2.input.SAXBuilder
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Opt-in capture of the actual app producers, while production still uses jdom2. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class K5OracleTest {
    private val strings get() = testCoreStrings()
    private val env get() = AndroidAppEnvironment(ApplicationProvider.getApplicationContext())
    private val facade get() = testSwordContentFacade()

    @After fun cleanup() { SwordContentFacade.clearCaches(); DatabaseResetter.resetDatabase() }

    @Test fun fixtureHasDeterministicBytesAndLongNamespacedChapter() {
        val tmp = kotlin.io.path.createTempDirectory("k5-fixture").toFile()
        try {
            val first = K5EpubFixture.create(File(tmp, "a")).readBytes()
            assertArrayEquals(first, K5EpubFixture.create(File(tmp, "b")).readBytes())
            ZipFile(File(tmp, "a/k5.epub")).use { zip ->
                val doc = SAXBuilder().build(zip.getInputStream(zip.getEntry("ch2.xhtml")))
                assertEquals(1200, doc.rootElement.getChild("body", doc.rootElement.namespace).getChildren("p", doc.rootElement.namespace).size)
            }
        } finally { tmp.deleteRecursively() }
    }

    @Test fun fixtureInstallsOptimizesAndRendersThroughApp() = runBlocking {
        val output = epub()
        assertTrue(output.take(3000), output.contains("toc\tK5 Oracle: One\t"))
        assertTrue(output.contains("id\t"))
        assertTrue(output.contains("long-1200"))
        assertTrue(output.contains("rendered\t"))
        assertEquals("Fresh installations must yield identical oracle rows", output, epub())
    }

    @Test fun comparisonReportsAllChangedAndMissingRows() {
        val tmp = kotlin.io.path.createTempDirectory("k5-compare").toFile()
        try {
            File(tmp, "copy.tsv").writeText("KJV\tGen.1.1\told\nKJV\tJohn.3.16\told\n")
            val failures = differences(tmp, mapOf("copy.tsv" to "KJV\tGen.1.1\tnew\nKJV\tJohn.3.16\tnew\n", "canonical.tsv" to "KJV\tGen.1.1\ttext\n"))
            assertEquals(failures.toString(), 3, failures.size)
            assertTrue(failures.any { "module=KJV key=Gen.1.1 section=copy.tsv offset=12" in it })
            assertTrue(failures.any { "key=John.3.16" in it })
            assertTrue(differences(tmp, mapOf("copy.tsv" to File(tmp, "copy.tsv").readText() + "\n")).any { "section=copy.tsv" in it })
        } finally { tmp.deleteRecursively() }
    }

    @Test fun syntheticJosephusRootHasExplicitClientPageProvenance() = withExtraModules {
        val book = Books.installed().getBook("Josephus")
        if (System.getProperty("k5.oracle.write") != null || System.getProperty("k5.oracle.check") != null) requireNotNull(book) { "Full oracle requires Josephus regression coverage" }
        assumeTrue("Optional Josephus module not installed for normal CI", book != null)
        requireNotNull(book)
        val keys = sample(book, 300)
        val root = keys.first()
        assertEquals("", root.getOsisID())
        val captured = clientPages(mapOf(book to listOf(root, keys.first { it.getOsisID().isNotBlank() })))
        assertTrue(captured.contains("synthetic-genbook-root"))
        assertTrue(captured.contains("bookInitials"))
        assertThrows(Exception::class.java) { clientPages(mapOf(book to listOf(org.crosswire.jsword.passage.TreeKey("DefinitelyMissingEntry")))) }
    }

    @Test fun epubQueryInventoryRetainsFallbacksAndContextDifferences() = runBlocking {
        val output = epub()
        assertTrue(output.contains("query\tbackend.toc.ncx"))
        assertTrue(output.contains("query\tbackend.toc.id"))
        assertTrue(output.contains("query\tbackend.toc.nav"))
        assertTrue(output.contains("query\tfacade.text\tchapter1"))
        assertTrue(output.contains("query\tbackend.index.bva\tdetached-body"))
        assertTrue(output.contains("query\tfacade.bva\tsub-element"))
        assertTrue(output.contains("query\toptimizer.split"))
        val queries = output.lineSequence().filter { it.startsWith("query\t") }.map { it.split('\t') }.toList()
        assertTrue(queries.any { it[1] == "backend.toc.id" && it[2] == "opf" && it[4] == "0" })
        assertTrue(queries.any { it[1] == "backend.toc.id" && it[2] == "opf-id-fallback" && it[4] == "1" })
        val absolute = queries.single { it[1] == "backend.index.bva" && it[2] == "sub-element-chapter2" }[4].toInt()
        val relative = queries.single { it[1] == "facade.bva" && it[2] == "sub-element-chapter2" }[4].toInt()
        assertTrue(absolute > relative)
        val text = queries.single { it[1] == "facade.text" && it[2] == "chapter1" }[5]
        assertTrue(text.contains("XHTML note"))
        assertFalse(text.contains("Plain note"))
    }

    @Test fun titleProbeReportsAvailabilityWithoutForcingMissingEntries() {
        val finpr = Books.installed().getBook("FinPR") as SwordBook
        val kjv = Books.installed().getBook("KJV") as SwordBook
        assertFalse(verses(finpr).any { it.getOsisID() == "Ps.3.0" })
        assertEquals(200, verses(finpr).size)
        assertTrue(titleCoverage(finpr).contains("unavailable"))
        assertTrue(verses(kjv).any { it.getOsisID() == "Ps.3.0" })
        assertTrue(titleCoverage(kjv).contains("available"))
        for (book in listOf(finpr, kjv)) for (ref in listOf("Gen.1.1", "Ps.3.1", "Matt.5.3", "John.3.16", "Rev.22.21")) assertTrue(verses(book).any { it.getOsisID() == ref })
    }

    @Test fun everyInstalledBibleSampleIsStrictlyReadableAndRequiredCoverageIsExplicit() {
        val bibles = Books.installed().getBooks().filterIsInstance<SwordBook>().filter { it.bookCategory == BookCategory.BIBLE }.sortedBy { it.initials }
        assertTrue(bibles.any { it.initials == "ISV" })
        assertTrue(bibles.any { it.initials == "FinPR" })
        for (book in bibles.sortedBy { if (it.initials == "ISV") 0 else 1 }) {
            val selected = verses(book)
            // Read the actual strict producer before planner assertions: this reproduces partial-module failure.
            for (verse in selected) SwordContentFacade.readOsisFragment(book, verse, strings)
            assertEquals(book.initials, 200, selected.size)
            assertEquals(book.initials, 200, selected.map { it.getOsisID() }.distinct().size)
            for (verse in selected) assertTrue("${book.initials}/${verse.getOsisID()}", book.contains(verse))
            for (ref in probes) {
                val verse = VerseRangeFactory.fromString(book.versification, ref).start
                val available = book.contains(verse)
                assertEquals("${book.initials}/$ref", available, selected.any { it.getOsisID() == ref })
                assertTrue(probeCoverage(book).contains("$ref\tforced-reference\t${if (available) "available" else "unavailable"}\t"))
            }
        }
        validateRequiredCoverage(bibles.associateWith { verses(it) })
        val partial = bibles.single { it.initials == "ISV" }
        assertThrows(IllegalStateException::class.java) { validateRequiredCoverage(mapOf(partial to verses(partial))) }
    }

    private val probes = listOf("Gen.1.1", "Ps.3.1", "Matt.5.3", "John.3.16", "Rev.22.21", "Ps.3.0")
    private fun probeCoverage(book: SwordBook): String = probes.joinToString("") { ref ->
        val available = book.contains(VerseRangeFactory.fromString(book.versification, ref).start)
        row(book.initials, ref, "forced-reference", if (available) "available" else "unavailable", if (available) "Included in verse sample" else "Module has no entry")
    }
    private fun validateRequiredCoverage(plans: Map<SwordBook, List<Verse>>) {
        for (ref in probes.take(5)) check(plans.values.any { verses -> verses.any { it.getOsisID() == ref } }) { "Required reference $ref has no supporting installed Bible" }
    }

    private fun titleCoverage(book: SwordBook): String {
        val title = VerseRangeFactory.fromString(book.versification, "Ps.3.0").start
        return row(book.initials, "Ps.3.0", "extra-title-probe", if (book.contains(title)) "available" else "unavailable", if (book.contains(title)) "Included in verse sample" else "Module has no entry")
    }

    @Test fun realProducerSmokeReadsOwnedContentWhenAmbientGeneralBookIsEmpty() {
        val registry = Books.installed()
        val previous = registry.getBooks().filter { it.bookCategory == BookCategory.GENERAL_BOOK }
        val empty = net.bible.service.sword.mydocument.createMyDocumentBook(
            net.bible.android.database.mydocument.MyDocument(name = "Empty smoke document", initials = "MyDoc_K5EmptySmoke")
        )
        try {
            previous.forEach { registry.removeBook(it) }
            registry.addBook(empty)
            assertFalse((empty.getGlobalKeyList() as Iterable<Key>).any { it.getOsisID().isNotBlank() })
            realProducerSmoke()
        } finally {
            registry.removeBook(empty)
            previous.forEach { registry.addBook(it) }
        }
    }

    @Test fun realProducerSmoke() = runBlocking {
        val book = Books.installed().getBook("KJV") as SwordBook
        val verse = VerseRangeFactory.fromString(book.versification, "John.3.16").start
        assertTrue(clientPages(mapOf(book to listOf(verse))).contains("bookInitials"))
        assertTrue(facade.getBibleSpeakCommands(SpeakSettings(), book, verse).isNotEmpty())
        assertTrue(SearchHighlighter.highlight(SwordContentFacade.readOsisFragment(book, verse, strings), listOf("God"), emptyList()).toString().contains("God"))
        assertEquals(200, verses(book).size)
        epub(capture = false) { genBook ->
            val key = genBook.getGlobalKeyList().iterator().next().getOsisID()
            for (format in listOf("text", "xml")) {
                val result = tool(GetGenBookContentTool, genBook.initials, key, format, "book", "key")
                assertTrue(result.contains("success"))
                assertTrue("Owned EPUB $format result must retain chapter content", result.contains("Grace and peace"))
            }
        }
        val isv = Books.installed().getBook("ISV") as SwordBook
        val block = CommentaryBlockResolver(SwordCommentaryWalker(isv, org.koin.core.context.GlobalContext.get().get())).resolveBlock(verse)
        assertNotNull(block.content)
        val selection = object : ShareSelection {
            override val bookInitials = "KJV"
            override val verseRange = VerseRangeFactory.fromString(book.versification, "John.3.16")
            override val startOffset: Int? = null
            override val endOffset: Int? = null
            override val notes = "<p>Oracle note.</p>"
        }
        assertTrue(facade.getSelectionText(selection, true, false, showNotes = true).contains("Oracle note."))
        assertFalse(facade.getSelectionText(selection, true, false, showNotes = false).contains("Oracle note."))
        for (format in listOf("text", "xml")) {
            assertTrue(tool(GetVerseContentTool, "KJV", "John.3.16", format, "book", "verseRef").contains("success"))
            assertTrue(tool(GetDictionaryEntryTool, "StrongsGreek", "G25", format, "dictionary", "key").contains("success"))
            assertTrue(tool(GetDictionaryEntryTool, "StrongsHebrew", "H430", format, "dictionary", "key").contains("success"))
        }
    }

    @Test fun oracle() = runBlocking {
        val write = System.getProperty("k5.oracle.write")
        val check = System.getProperty("k5.oracle.check")
        val timing = System.getProperty("k5.timing.out")
        assumeTrue("Set k5.oracle.write, k5.oracle.check or k5.timing.out", listOfNotNull(write, check, timing).isNotEmpty())
        require(listOfNotNull(write, check, timing).size == 1) { "Choose exactly one oracle/timing mode" }
        val previous = SwordBookPathJvm.getAugmentPath()
        try {
            val root = File(requireNotNull(System.getProperty("k5.project.root")))
            val extras = File(root, ".local/k5-sword").walkTopDown().filter { it.isDirectory && File(it, "mods.d").isDirectory }.toList()
            SwordBookPathJvm.setAugmentPath((previous.toList() + extras).distinct().toTypedArray())
            val books = Books.installed().getBooks().sortedBy { it.initials }
            require(books.any { it.initials == "KJV" }) { "Install the test Sword modules first" }
            val sample = books.associateWith { sample(it, 300) }
            if (timing != null) {
                repeat(5) { round ->
                    val start = System.nanoTime(); clientPages(sample); val middle = System.nanoTime()
                    epub(capture = false); val end = System.nanoTime()
                    if (round >= 2) File(timing).apply { parentFile?.mkdirs() }.appendText(row(System.getProperty("k5.timing.label", "jdom2"), (round - 1).toString(), ((middle - start) / 1_000_000.0).toString(), ((end - middle) / 1_000_000.0).toString()))
                }
                return@runBlocking
            }
            val sections = linkedMapOf("clientpage.tsv" to clientPages(sample))
            val speak = StringBuilder(); val copy = StringBuilder(); val canonical = StringBuilder(); val highlight = StringBuilder(); val llm = StringBuilder(); val commentary = StringBuilder()
            val bibles = books.filterIsInstance<SwordBook>().filter { it.bookCategory == BookCategory.BIBLE }
            val versePlans = books.filterIsInstance<SwordBook>().filter { it.bookCategory == BookCategory.BIBLE || it.bookCategory == BookCategory.COMMENTARY }.associateWith { verses(it) }
            validateRequiredCoverage(bibles.associateWith { versePlans.getValue(it) })
            // Validate real entries before producers that intentionally degrade read errors to empty output.
            for ((book, selected) in versePlans) for (verse in selected) SwordContentFacade.readOsisFragment(book, verse, strings)
            for (book in bibles) {
                val verses = versePlans.getValue(book)
                for (verse in verses) {
                    speak.append(row(book.initials, verse.getOsisID(), speakText(book, verse)))
                    canonical.append(row(book.initials, verse.getOsisID(), SwordContentFacade.getCanonicalText(book, verse, strings = strings, saxTextPolicy = androidSaxTextPolicy())))
                    for (notes in listOf(false, true)) {
                        val selection = object : ShareSelection {
                            override val bookInitials = book.initials
                            override val verseRange = VerseRangeFactory.fromString(book.versification, verse.getOsisID())
                            override val startOffset: Int? = null
                            override val endOffset: Int? = null
                            override val notes = "<p>Oracle selection note.</p>"
                        }
                        copy.append(row(book.initials, verse.getOsisID(), "notes=$notes", facade.getSelectionText(selection, showVerseNumbers = true, advertiseApp = false, showNotes = notes)))
                    }
                }
                for (query in listOf("God", "love", "beginning", "blessed", "peace")) for (verse in verses.take(20)) {
                    highlight.append(row(book.initials, verse.getOsisID(), query, SearchHighlighter.highlight(SwordContentFacade.readOsisFragment(book, verse, strings), listOf(query), emptyList()).toString()))
                }
                for (format in listOf("text", "xml")) for (verse in verses.take(5)) llm.append(tool(GetVerseContentTool, book.initials, verse.getOsisID(), format, "book", "verseRef"))
            }
            for ((module, key) in listOf("StrongsGreek" to "G25", "StrongsHebrew" to "H430")) {
                require(books.any { it.initials == module }) { "Missing required dictionary $module" }
                for (format in listOf("text", "xml")) llm.append(tool(GetDictionaryEntryTool, module, key, format, "dictionary", "key"))
            }
            val genBooks = books.filter { it.bookCategory == BookCategory.GENERAL_BOOK }
            if (genBooks.isEmpty()) llm.append(row("SKIP", "genbook", "No GenBook installed"))
            for (book in genBooks) {
                for (key in sample.getValue(book).filter { syntheticRoot(book, it) }) llm.append(row(book.initials, key.getOsisID(), "SKIP", "synthetic-genbook-root", "Read tool requires a content entry"))
                for (key in sample.getValue(book).filterNot { syntheticRoot(book, it) }.take(5)) for (format in listOf("text", "xml")) llm.append(tool(GetGenBookContentTool, book.initials, key.getOsisID(), format, "book", "key"))
            }
            val commentaries = books.filterIsInstance<SwordBook>().filter { it.initials == "ISV" || it.bookCategory == BookCategory.COMMENTARY }
            if (commentaries.isEmpty()) commentary.append(row("SKIP", "commentary", "No commentary-capable module installed"))
            for (book in commentaries) {
                val resolver = CommentaryBlockResolver(SwordCommentaryWalker(book, org.koin.core.context.GlobalContext.get().get()))
                for (verse in versePlans.getValue(book).take(20)) {
                    val block = resolver.resolveBlock(verse)
                    commentary.append(row(book.initials, verse.getOsisID(), block.start.getOsisID(), block.end.getOsisID(), block.content.orEmpty()))
                }
            }
            sections["speak.tsv"] = speak.toString(); sections["copy.tsv"] = copy.toString(); sections["canonical.tsv"] = canonical.toString()
            sections["search-highlight.tsv"] = highlight.toString(); sections["llm.tsv"] = llm.toString(); sections["commentary-blocks.tsv"] = commentary.toString(); sections["epub.tsv"] = epub()
            sections["coverage.tsv"] = books.joinToString("") { row(it.initials, it.bookCategory.name, sample.getValue(it).size.toString()) } + bibles.joinToString("") { probeCoverage(it) + row(it.initials, "verse-sample", versePlans.getValue(it).size.toString()) }
            val dir = File(write ?: check!!)
            if (write != null) { dir.mkdirs(); sections.forEach { (name, text) -> File(dir, name).writeText(text) } }
            else { val failures = differences(dir, sections); assertTrue(failures.joinToString("\n"), failures.isEmpty()) }
        } finally { SwordBookPathJvm.setAugmentPath(previous) }
    }

    @Test fun speakCaptureIsStableAcrossFreshCommandInstances() {
        val book = Books.installed().getBook("KJV") as SwordBook
        val verse = VerseRangeFactory.fromString(book.versification, "John.3.16").start
        val captured = speakText(book, verse)
        assertEquals(captured, speakText(book, verse))
        assertTrue(captured.contains("text=For God so loved"))
        assertTrue(captured.contains("type=NORMAL"))
        val other = VerseRangeFactory.fromString(book.versification, "Gen.1.1").start
        assertNotEquals(captured, speakText(book, other))
    }

    private fun speakText(book: SwordBook, verse: Verse): String = facade.getBibleSpeakCommands(SpeakSettings(), book, verse).joinToString("\n") { command ->
        // Object.toString identities are not speech data. Capture the actual ordered command payloads.
        val fields = generateSequence(command.javaClass as Class<*>?) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) || it.isSynthetic }
            .sortedBy { it.name }
            .joinToString(";") { field -> field.isAccessible = true; "${field.name}=${field.get(command)}" }
        "${command.javaClass.name}:$fields"
    }

    private fun clientPages(sample: Map<Book, List<Key>>) = buildString {
        for ((book, keys) in sample) for ((position, key) in keys.withIndex()) {
            if (syntheticRoot(book, key)) {
                append(row(book.initials, key.getOsisID(), "SKIP", "synthetic-genbook-root", "sample-position=$position", "Non-content index container; name empty, parent absent, children present"))
                continue
            }
            val fragment = OsisFragment(SwordContentFacade.readOsisFragment(book, key, strings), key, book)
            append(row(book.initials, key.getOsisID(), fragment.toHashMap.entries.joinToString(",", "{", "}") { "${it.key}:${it.value}" }))
        }
    }

    private suspend fun tool(tool: Tool, module: String, key: String, format: String, moduleArg: String, keyArg: String): String {
        val result = tool.execute(buildJsonObject { put(moduleArg, module); put(keyArg, key); put("format", format) }, AgentContext(IdType("00000000-0000-0000-0000-000000000001")))
        check(result is ToolResult.Success) { "$module $key $format: ${result.toJson()}" }
        return row(module, key, tool.javaClass.simpleName, format, result.toJson())
    }

    private suspend fun epub(capture: Boolean = true, inspect: suspend (Book) -> Unit = {}): String {
        val tmp = kotlin.io.path.createTempDirectory("k5-epub").toFile()
        val name = "K5OracleFixture"
        val dir = File(File(SharedConstants.modulesDir, "epub"), name)
        require(!dir.exists()) { "Refusing to overwrite existing fixture $dir" }
        var book: Book? = null
        try {
            val source = K5EpubFixture.create(tmp)
            val querySnapshots = if (capture) {
                val originals = File(tmp, "originals")
                net.bible.service.common.ArchiveUtils.unzipFile(source, originals)
                K5EpubQueries.capture(originals)
            } else ""
            check(DefaultInstallCommitter(testBackupControl(), strings, env).commitEpub(source, name, false))
            book = Books.installed().getBook(epubInitials(name)) ?: error("Fixture EPUB did not register")
            val backend = book.epubBackend ?: error("Not an EPUB backend")
            // EpubBackendState performs optimization on construction when the source has no optimized data.
            check(backend.state.keys.size > 2) { "Long chapter did not split" }
            inspect(book)
            if (!capture) return ""
            return buildString {
                append(row("fixture", "source-sha256", digest(source.readBytes())))
                ZipFile(source).use { zip ->
                    for (entry in zip.entries().toList()) append(row("source", entry.name, java.util.Base64.getEncoder().encodeToString(zip.getInputStream(entry).use { it.readBytes() })))
                }
                append(querySnapshots)
                for (key in backend.tocKeys()) append(row("toc", key.getName(), key.getOsisRef()))
                for (key in backend.state.keys) {
                    val text = book.getRawText(key)
                    val range = backend.getOrdinalRange(key)
                    val fragment = File(dir, "optimized/${key.getOsisRef().toLong().toString().padStart(3, '0')}.xhtml.gz")
                    append(row("fragment", fragment.name, digest(fragment.readBytes()), range.first.toString(), range.last.toString()))
                    val xml = SAXBuilder().build(java.io.StringReader("<root>$text</root>"))
                    fun ids(element: org.jdom2.Element) { element.getAttributeValue("id")?.let { append(row("id", key.getOsisRef(), it)) }; element.children.forEach { ids(it) } }
                    ids(xml.rootElement)
                    append(row("rendered", key.getOsisRef(), OsisFragment(SwordContentFacade.readOsisFragment(book, key, strings), key, book).xmlStr))
                }
            }
        } finally {
            book?.let { Books.installed().removeBook(it) }
            deleteEpubModule(dir)
            tmp.deleteRecursively()
        }
    }

    private fun verses(book: SwordBook): List<Verse> {
        val forced = probes.map { VerseRangeFactory.fromString(book.versification, it).start }.filter { book.contains(it) }
        val available = (book.getGlobalKeyList() as Iterable<Key>).filterIsInstance<Verse>().filter { book.contains(it) }.distinctBy { it.getOsisID() }
        val step = maxOf(1, (available.size + 199) / 200)
        val spaced = available.filterIndexed { index, _ -> index % step == 0 }
        // Preserve the evenly spaced sample first; refill from available entries if spacing rounded down.
        return (forced + spaced + available).distinctBy { it.getOsisID() }.take(200)
    }

    private fun withExtraModules(action: () -> Unit) {
        val previous = SwordBookPathJvm.getAugmentPath()
        try {
            val root = File(requireNotNull(System.getProperty("k5.project.root")))
            val extras = File(root, ".local/k5-sword").walkTopDown().filter { it.isDirectory && File(it, "mods.d").isDirectory }.toList()
            SwordBookPathJvm.setAugmentPath((previous.toList() + extras).distinct().toTypedArray())
            action()
        } finally { SwordBookPathJvm.setAugmentPath(previous) }
    }

    private fun syntheticRoot(book: Book, key: Key): Boolean = book is org.crosswire.jsword.book.sword.SwordGenBook &&
        book.backend is org.crosswire.jsword.book.sword.GenBookBackend && key.getParent() == null &&
        key.getName().isEmpty() && key.getOsisID().isEmpty() && key.getChildCount() > 0

    private fun sample(book: Book, cap: Int): List<Key> {
        val keys = (book.getGlobalKeyList() as Iterable<Key>).toList()
        val must = when (book.initials) { "NASB" -> setOf("Mark.10.34", "Luke.4.43", "Luke.18.42", "John.13.26"); "ISV" -> setOf("1Cor.14.33"); else -> emptySet() }
        val mandatory = keys.filter { it.getOsisID() in must }
        val step = maxOf(1, (keys.size + cap - 1) / cap)
        return (mandatory + keys.filterIndexed { i, _ -> i % step == 0 }).distinctBy { it.getOsisID() }.take(cap)
    }

    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun row(vararg cells: String) = cells.joinToString("\t") { it.replace("\\", "\\\\").replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n") } + "\n"

    private fun differences(dir: File, actual: Map<String, String>): List<String> = (actual.keys + dir.listFiles().orEmpty().filter { it.isFile }.map { it.name }).distinct().sorted().flatMap { section ->
        val baseline = File(dir, section)
        if (!baseline.exists() || section !in actual) return@flatMap listOf("module=? key=? section=$section offset=0 missing=${if (!baseline.exists()) "baseline" else "actual"}")
        val old = baseline.readText().split('\n')
        val new = actual.getValue(section).split('\n')
        (0 until maxOf(old.size, new.size)).mapNotNull { index ->
            val a = old.getOrNull(index); val b = new.getOrNull(index)
            if (a == b) null else {
                val offset = (0 until minOf(a.orEmpty().length, b.orEmpty().length)).firstOrNull { a!![it] != b!![it] } ?: minOf(a.orEmpty().length, b.orEmpty().length)
                val cells = (b ?: a).orEmpty().split('\t')
                "module=${cells.firstOrNull()} key=${cells.getOrNull(1)} section=$section offset=$offset row=${index + 1}"
            }
        }
    }
}
