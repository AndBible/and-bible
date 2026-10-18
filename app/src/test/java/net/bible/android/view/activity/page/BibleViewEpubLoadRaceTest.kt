package net.bible.android.view.activity.page

import okio.Path.Companion.toOkioPath
import android.net.Uri
import com.nhaarman.mockitokotlin2.*
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.*
import net.bible.android.control.page.window.*
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.link.LinkControl
import net.bible.android.view.activity.page.screen.PageTiltScroller
import net.bible.service.common.CommonUtils
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordGenBook
import net.bible.service.sword.epub.EpubBackend
import org.crosswire.jsword.passage.DefaultLeafKeyList
import org.koin.core.context.GlobalContext
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class BibleViewEpubLoadRaceTest {
    private lateinit var repo: WindowRepository
    private lateinit var originalRepo: WindowRepository
    private lateinit var view: BibleView
    private lateinit var window: Window
    private lateinit var host: ReadingHostActivity
    private var mockLifecycle: LifecycleRegistry? = null
    private var mainScheduler: TestCoroutineScheduler? = null
    private var mockHostScope: CoroutineScope? = null
    private val epubOwners = mutableListOf<EpubOwner>()
    // A failed bounded drain must outlive the JUnit instance and block graph replacement.
    companion object {
        private var retainedCleanup: BibleViewEpubLoadRaceTest? = null
        private fun requireNoRetainedCleanup() {
            check(retainedCleanup == null) { "HARNESS FAILURE: previous EPUB cleanup must be retried before setup" }
        }
    }
    private var restored = false
    private var drainScopes: (Array<CoroutineScope>) -> Unit = { DatabaseResetter.resetDatabase(*it) }

    private class Failures {
        var primary: Throwable? = null
        fun attempt(block: () -> Unit) {
            try { block() } catch (e: Throwable) {
                val first = primary
                if (first == null) primary = e else if (first !== e) first.addSuppressed(e)
            }
        }
        fun throwIfAny() { primary?.let { throw it } }
    }

    private class EpubOwner(val fixture: net.bible.service.sword.epub.EpubBackendStateTest) {
        var book: SwordGenBook? = null
        var beforeDelete: (() -> Unit)? = null
        var outcome: (() -> Unit)? = null
        var bookRemoved = false
        var fixtureTornDown = false
        var removeBook: (SwordGenBook) -> Unit = { Books.installed().removeBook(it) }
        val finished get() = bookRemoved && fixtureTornDown
        fun finish() {
            if (finished) return
            // Unsafe deletion intent is a hard barrier; completed-user outcome errors are not.
            beforeDelete?.invoke()
            val failures = Failures()
            failures.attempt { outcome?.invoke() }
            failures.attempt {
                if (!bookRemoved) {
                    book?.let { target ->
                        if (Books.installed().getBooks().any { it === target }) removeBook(target)
                    }
                    bookRemoved = true
                }
            }
            failures.attempt {
                if (!fixtureTornDown) {
                    fixture.tearDown()
                    fixtureTornDown = true
                }
            }
            failures.throwIfAny()
        }
    }
    private val control = WindowControl()
    private val links get() = GlobalContext.get().get<LinkControl>()
    private val bookmarks get() = GlobalContext.get().get<BookmarkControl>()

    @Before fun setup() {
        requireNoRetainedCleanup()
        initializeFixture()
    }

    private fun initializeFixture() = runTest {
        mainScheduler = testScheduler
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        repo = WindowRepository(CoroutineScope(SupervisorJob() + Dispatchers.Main))
        control.windowRepository = repo
        originalRepo = CommonUtils.windowControl.windowRepository
        CommonUtils.windowControl.windowRepository = repo
        repo.initializeAtEdge()
        window = repo.activeWindow
        host = mock()
        whenever(host.hostContext).thenReturn(RuntimeEnvironment.getApplication())
        val lifecycle = LifecycleRegistry.createUnsafe(host)
        mockLifecycle = lifecycle
        lifecycle.currentState = Lifecycle.State.STARTED
        whenever(host.lifecycle).thenReturn(lifecycle)
        mockHostScope = host.lifecycleScope
        val callbacks = BibleViewHostCallbacks(mock(), {}, {}, { _, _ -> }, { _, _ -> false }, { false }, {}, { null }, { _, _ -> }, {}, { false }, { 0 }, { 0 }, { 0 }, { error("Unused") })
        view = BibleView(host, callbacks, WeakReference(window), control, GlobalContext.get().get(), PageTiltScrollControl(), links, bookmarks, GlobalContext.get().get(), GlobalContext.get().get())
        window.bibleView = view
        view.lastUpdated = 1
        BibleView::class.java.getDeclaredField("pageTiltScroller").apply { isAccessible = true; set(view, PageTiltScroller(view, PageTiltScrollControl())) }
    }

    @After fun cleanup() {
        if (restored) return
        check(retainedCleanup == null || retainedCleanup === this) { "HARNESS FAILURE: another EPUB owner is retained" }
        retainedCleanup = this
        val failures = Failures()
        failures.attempt { if (::view.isInitialized) view.destroy() }
        val scopes = listOfNotNull(if (::repo.isInitialized) repo.scope else null, mockHostScope)
        failures.attempt { mockLifecycle?.currentState = Lifecycle.State.DESTROYED }
        failures.attempt {
            mainScheduler?.let { Dispatchers.setMain(UnconfinedTestDispatcher(it)) }
            scopes.forEach { it.cancel() }
            mainScheduler?.runCurrent()
        }
        failures.attempt { drainScopes(scopes.toTypedArray()) }
        val complete = scopes.all { it.coroutineContext[Job]?.isCompleted != false }
        if (complete) {
            epubOwners.forEach { owner -> failures.attempt { owner.finish() } }
            // Failed removal/teardown remains retryable with this exact binding and Main.
            if (epubOwners.all { it.finished }) {
                var bindingRestored = false
                var mainRestored = false
                failures.attempt {
                    if (::originalRepo.isInitialized) CommonUtils.windowControl.windowRepository = originalRepo
                    bindingRestored = true
                }
                failures.attempt { Dispatchers.resetMain(); mainRestored = true }
                if (bindingRestored && mainRestored) {
                    restored = true
                    retainedCleanup = null
                }
            }
        } else {
            failures.attempt { error("HARNESS FAILURE: retain EPUB owners while render owners incomplete") }
        }
        failures.throwIfAny()
    }

    private fun preservingFailure(body: () -> Unit, vararg finalizers: () -> Unit) {
        val failures = Failures()
        failures.attempt(body)
        finalizers.forEach { failures.attempt(it) }
        failures.throwIfAny()
    }

    private fun field(name: String): Any? = BibleView::class.java.getDeclaredField(name).run { isAccessible = true; get(view) }
    private fun doc(id: String, entered: CountDownLatch? = null, release: CountDownLatch? = null): Document = object : Document {
        override suspend fun contentMap(): Map<String, String> {
            entered?.countDown()
            check(release?.await(10, TimeUnit.SECONDS) != false)
            return mapOf("id" to "'$id'")
        }
    }

    @Test fun overlappingIoLoadsPublishOnlyTheNewerLocationAndDocument() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val serialEntered = CountDownLatch(1)
        val serialRelease = CountDownLatch(1)
        val oldKey = DefaultLeafKeyList("old", "old")
        val newKey = DefaultLeafKeyList("new", "new")
        val old = launch(Dispatchers.IO) { view.loadDocument(doc("old", serialEntered, serialRelease), key = oldKey, anchorOrdinal = OrdinalRange(1), htmlId = "old") }
        try {
            withContext(Dispatchers.Default) { withTimeout(10_000) { while (serialEntered.count != 0L) { runCurrent(); delay(10) } } }
            assertNull("Pending serialization must not publish location", view.initialKey)
            val newer = launch(Dispatchers.IO) { view.loadDocument(doc("new"), key = newKey, anchorOrdinal = OrdinalRange(9), htmlId = "new") }
            while (newer.isActive) { runCurrent(); withContext(Dispatchers.IO) { delay(10) } }
            serialRelease.countDown()
            while (old.isActive) { runCurrent(); withContext(Dispatchers.IO) { delay(10) } }
            assertEquals(newKey, view.initialKey)
            assertEquals(9, view.documentLoader.initialAnchorOrdinal!!.start)
            assertEquals("new", view.documentLoader.initialHtmlId)
            assertEquals("{'id': 'new'}", view.documentLoader.latestDocumentStr)
        } finally {
            serialRelease.countDown(); old.cancel()
        }
    }
    @Test fun concurrentIoGenerationsAreUniqueAndStaleMainPublicationIsRejected() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val count = 8
        val entered = CountDownLatch(count)
        val release = CountDownLatch(1)
        val loads = (1..count).map { i ->
            launch(Dispatchers.IO) {
                view.loadDocument(doc("$i", entered, release), key = DefaultLeafKeyList("$i", "$i"), anchorOrdinal = OrdinalRange(i), htmlId = "$i")
            }
        }
        val page = window.pageManager.currentPage as CurrentPageBase
        val original = page.currentDocument
        try {
            withContext(Dispatchers.Default) { withTimeout(10_000) { while (entered.count != 0L) { runCurrent(); delay(10) } } }
            assertEquals(count.toLong(), view.documentLoader.loadGeneration)
            assertNull(view.initialKey)
            assertNull(view.firstDocument)
            page.onlySetCurrentDocument(Books.installed().getBooks().first { it != original })
            release.countDown()
            loads.forEach { it.join() }
            assertNull(view.firstDocument)
            assertNull(view.initialKey)
            assertNull(view.documentLoader.initialAnchorOrdinal)
            assertNull(view.documentLoader.initialHtmlId)
        } finally {
            page.onlySetCurrentDocument(original)
            release.countDown()
            loads.forEach { it.cancel() }
        }
    }

    @Test fun firstReaderConfigurationIncludesPersistedFavouriteLabels() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val label = net.bible.android.database.bookmarks.BookmarkEntities.Label(name = "Persisted favourite", favourite = true)
        val dao = net.bible.service.db.DatabaseContainer.instance.bookmarkDb.bookmarkDao()
        dao.insert(label)
        view.jsChannel.markClientReady()
        val load = launch(Dispatchers.IO) { view.loadDocument(doc("first")) }
        try {
            load.join()
            // The detached real WebView queues its first reader JS; flush then inspect actual evaluation.
            view.jsChannel.flush()
            val script = org.robolectric.Shadows.shadowOf(view).lastEvaluatedJavascript
            assertTrue(script.contains("set_config"))
            assertTrue("First configuration must contain persisted favourites: $script", script.contains("favouriteLabels: [\"${label.id}\"]"))
        } finally {
            load.cancel()
        }
    }

    @Test fun controlledRepoOwnedEpubFileSurvivesUntilProducerCompletion() {
        val fixture = net.bible.service.sword.epub.EpubBackendStateTest()
        val owner = EpubOwner(fixture).also { epubOwners += it }
        fixture.setUp()
        val dir = fixture.buildEpub("owned-lifetime")
        val state = net.bible.service.sword.epub.EpubBackendState(dir.toOkioPath(), net.bible.test.testCoreStrings(), net.bible.android.platform.AndroidAppEnvironment(RuntimeEnvironment.getApplication()))
        val readDb = state.javaClass.getDeclaredField("readDb").apply { isAccessible = true }.get(state)
            as net.bible.android.database.EpubDatabase
        runBlocking { assertNull("Real lazy metadata INSERT has not run", readDb.epubDao().getMeta()) }
        val file = RuntimeEnvironment.getApplication().getDatabasePath(state.appDbFilename)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val producerFailure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        val parent = checkNotNull(repo.scope.coroutineContext[Job])
        val child = repo.scope.launch(Dispatchers.IO) {
            try {
                // Test-only gate models work already running across cancellation, before any query.
                withContext(NonCancellable) {
                    entered.countDown()
                    check(release.await(10, TimeUnit.SECONDS)) { "HARNESS FAILURE: EPUB gate timeout" }
                    val chars = state.totalCharacters()
                    assertTrue(chars > 0)
                    assertEquals(chars, readDb.epubDao().getMeta()?.totalCharacters)
                    println("EPUB-OWNED real-totalCharacters-complete db=${System.identityHashCode(readDb)} file=${file.path}")
                }
            } catch (e: Throwable) { producerFailure.set(e) }
        }
        val previous = DatabaseResetter.drainObserver
        val observer: (List<Job>, Deferred<Unit>) -> Unit = { jobs, drain ->
            assertTrue(jobs.any { it === parent })
            assertFalse(drain.isCompleted)
            assertFalse(child.isCompleted)
            assertTrue("exact EPUB file remains at suspended drain", file.exists())
            release.countDown()
        }
        owner.beforeDelete = {
            assertTrue("INVARIANT deletion intent must wait exact repo EPUB child", child.isCompleted)
        }
        owner.outcome = {
            assertTrue(file.exists())
            producerFailure.get()?.let { throw it }
        }
        preservingFailure({
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertTrue(parent.children.any { it === child })
            DatabaseResetter.drainObserver = observer
            cleanup()
            assertTrue(child.isCompleted)
            assertFalse(file.exists())
        }, { release.countDown() }, {
            if (DatabaseResetter.drainObserver === observer) DatabaseResetter.drainObserver = previous
        }, { DatabaseResetter.runResetBlocking { withTimeout(5_000) { child.join() } } })
    }

    private fun ownedEpub(name: String): Pair<EpubOwner, net.bible.service.sword.epub.EpubBackendState> {
        check(!restored) { "Cannot register a fixture on a retired EPUB owner" }
        val fixture = net.bible.service.sword.epub.EpubBackendStateTest()
        val owner = EpubOwner(fixture).also { epubOwners += it }
        fixture.setUp()
        val state = net.bible.service.sword.epub.EpubBackendState(fixture.buildEpub(name).toOkioPath(),
            net.bible.test.testCoreStrings(), net.bible.android.platform.AndroidAppEnvironment(RuntimeEnvironment.getApplication()))
        return owner to state
    }

    private fun caught(block: () -> Unit): Throwable {
        try { block() } catch (failure: Throwable) { return failure }
        error("Expected injected failure")
    }

    @Test fun incompleteOwnerBlocksRestartAndRetainsBindingUntilReleaseRetry() {
        val (owner, state) = ownedEpub("incomplete-retry")
        val file = RuntimeEnvironment.getApplication().getDatabasePath(state.appDbFilename)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val child = repo.scope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
            }
        }
        val realDrain = drainScopes
        val injected = IllegalStateException("injected bounded drain refusal")
        preservingFailure({
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            drainScopes = { throw injected }
            assertSame(injected, caught { cleanup() })
            assertFalse(child.isCompleted)
            assertFalse(owner.fixtureTornDown)
            assertTrue(file.exists())
            assertSame(repo, CommonUtils.windowControl.windowRepository)
            assertSame(this, retainedCleanup)
            val nextInstance = BibleViewEpubLoadRaceTest()
            val restart = caught { nextInstance.setup() }
            assertTrue(restart.message!!.contains("previous EPUB cleanup"))
            assertFalse(nextInstance::repo.isInitialized)
            assertSame(mainScheduler, TestScope().testScheduler)
            release.countDown()
            drainScopes = realDrain
            cleanup()
            assertTrue(child.isCompleted)
            assertTrue(owner.finished)
            assertFalse(file.exists())
            assertSame(originalRepo, CommonUtils.windowControl.windowRepository)
            assertNull(retainedCleanup)
        }, { release.countDown() }, {
            drainScopes = realDrain
            cleanup()
        })
    }

    @Test fun completedProducerFailureStillDeletesFilesAndRestoresDownloadPath() {
        val originalPath = org.crosswire.jsword.book.sword.SwordBookPath.getSwordDownloadDir()
        val (owner, state) = ownedEpub("failed-producer")
        val file = RuntimeEnvironment.getApplication().getDatabasePath(state.appDbFilename)
        val injected = AssertionError("injected completed producer outcome")
        val result = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        val child = repo.scope.launch(Dispatchers.IO) {
            try { state.totalCharacters(); throw injected } catch (failure: Throwable) { result.set(failure) }
        }
        DatabaseResetter.runResetBlocking { child.join() }
        assertTrue(child.isCompleted)
        owner.outcome = { result.get()?.let { throw it } }
        preservingFailure({
            val failure = caught { cleanup() }
            assertSame(injected, failure)
            assertTrue(owner.finished)
            assertFalse(file.exists())
            assertEquals(originalPath, org.crosswire.jsword.book.sword.SwordBookPath.getSwordDownloadDir())
            assertNull(retainedCleanup)
        }, { owner.outcome = null; cleanup() })
    }

    @Test fun failedRegisteredBookRemovalRetriesWithoutRepeatingFixtureTeardown() {
        val (owner, state) = ownedEpub("removal-retry")
        val book = SwordGenBook(state.getBookMetaData(), EpubBackend(state, state.getBookMetaData()))
        owner.book = book
        Books.installed().addBook(book)
        val actualRemoval = owner.removeBook
        val injected = IllegalStateException("injected non-native registry removal failure")
        owner.removeBook = { throw injected }
        preservingFailure({
            assertSame(injected, caught { cleanup() })
            assertFalse(owner.bookRemoved)
            assertTrue(owner.fixtureTornDown)
            assertFalse(owner.finished)
            assertTrue(Books.installed().getBooks().any { it === book })
            assertSame(this, retainedCleanup)
            owner.removeBook = actualRemoval
            cleanup()
            assertTrue(owner.finished)
            assertFalse(Books.installed().getBooks().any { it === book })
            assertNull(retainedCleanup)
        }, { owner.removeBook = actualRemoval; cleanup() })
    }

    @Test fun retiredOwnerRejectsRegistrationBeforeResourceMutation() {
        cleanup()
        val path = org.crosswire.jsword.book.sword.SwordBookPath.getSwordDownloadDir()
        val owners = epubOwners.toList()
        preservingFailure({
            val failure = caught { ownedEpub("rejected-late-fixture") }
            assertTrue("Registration must reject retirement before setup", failure.message?.contains("retired EPUB owner") == true)
            assertEquals(owners, epubOwners)
            assertEquals(path, org.crosswire.jsword.book.sword.SwordBookPath.getSwordDownloadDir())
        }, {
            // Mutation calibration must clean any mistakenly admitted real fixture independently.
            epubOwners.filter { it !in owners }.forEach { it.finish() }
        })
    }

    @Test fun neverRegisteredBookOnExistingFixtureCleansAfterInjectedOutcomeFailure() {
        val path = org.crosswire.jsword.book.sword.SwordBookPath.getSwordDownloadDir()
        val (owner, state) = ownedEpub("unregistered-existing")
        val file = RuntimeEnvironment.getApplication().getDatabasePath(state.appDbFilename)
        owner.book = SwordGenBook(state.getBookMetaData(), EpubBackend(state, state.getBookMetaData()))
        owner.removeBook = { error("Unregistered book must not be removed") }
        val injected = AssertionError("injected unregistered-book outcome")
        owner.outcome = { throw injected }
        preservingFailure({
            assertSame(injected, caught { cleanup() })
            assertTrue(owner.finished)
            assertFalse(file.exists())
            assertEquals(path, org.crosswire.jsword.book.sword.SwordBookPath.getSwordDownloadDir())
            assertNull(retainedCleanup)
        }, { owner.outcome = null; cleanup() })
    }

    @Test fun bodyFailureRemainsPrimaryWhenReleaseObserverAndJoinAlsoFail() {
        val primary = AssertionError("primary admission failure")
        val releaseFailure = IllegalStateException("release failure")
        val observerFailure = IllegalStateException("observer restoration failure")
        val joinFailure = IllegalStateException("bounded join failure")
        val attempted = mutableListOf<String>()
        val actual = caught {
            preservingFailure({ throw primary },
                { attempted += "release"; throw releaseFailure },
                { attempted += "observer"; throw observerFailure },
                { attempted += "join"; throw joinFailure })
        }
        assertSame(primary, actual)
        assertEquals(listOf("release", "observer", "join"), attempted)
        assertEquals(listOf(releaseFailure, observerFailure, joinFailure), actual.suppressed.toList())
        val admission = AssertionError("primary before bounded join")
        val joined = caught { preservingFailure({ throw admission }, {}, {}, { throw joinFailure }) }
        assertSame(admission, joined)
        assertEquals(listOf(joinFailure), joined.suppressed.toList())
    }

    @Test fun openLinkNeverLetsTheWebViewNavigateKnownOrUnknownSchemes() {
        // Handled-or-not, the WebView must not navigate: known schemes with missing parameters and unknown ones.
        for (url in listOf("osis://?v11n=KJV", "journal://", "multi://", "ab-find-all://?type=g", "epub-ref://?book=x",
            "mailto:a@b.c", "no-scheme")) {
            assertTrue(url, view.openLink(Uri.parse(url)))
        }
    }

    @Test fun epubUriFromAnotherDocumentOpensTarget() = checkEpubUri(false)

    @Test fun epubUriRejectsChangedSourceDocument() = checkEpubUri(true)

    private fun checkEpubUri(changeSource: Boolean) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = net.bible.service.sword.epub.EpubBackendStateTest()
        val owner = EpubOwner(fixture).also { epubOwners += it }
        fixture.setUp()
        val dir = fixture.buildEpub("uri-target")
        val state = net.bible.service.sword.epub.EpubBackendState(dir.toOkioPath(), net.bible.test.testCoreStrings(), net.bible.android.platform.AndroidAppEnvironment(RuntimeEnvironment.getApplication()))
        val target = SwordGenBook(state.getBookMetaData(), EpubBackend(state, state.getBookMetaData()))
        owner.book = target
        val oldMode = links.windowMode
        try {
            Books.installed().addBook(target)
            links.windowMode = net.bible.android.control.link.WindowMode.WINDOW_MODE_THIS
            val source = window.pageManager.currentPage.currentDocument
            assertNotEquals(target, source)
            assertTrue(view.openLink(Uri.parse("epub-ref://?book=${target.initials}&toKey=ch1&toId=")))
            if (changeSource) {
                val page = window.pageManager.currentPage
                (page as CurrentPageBase).onlySetCurrentDocument(target)
                // Changing source to another document before the deferred lookup must suppress navigation.
                // The EPUB link queue moved with the link family into the shared ReadingBridge (I2 Stream B Task 6).
                fun Any.lazyField(name: String): Any? = javaClass.getDeclaredField(name).run { isAccessible = true; (get(this@lazyField) as Lazy<*>).value }
                val linkFamily = view.readingBridge.lazyField("links\$delegate")!!
                val queue = linkFamily.lazyField("epubLinks\$delegate") as net.bible.sharedcore.platform.OrderedLauncher
                queue.join("epub-links")
                assertEquals(target, page.currentDocument)
                assertNotEquals(state.keys.first().getOsisRef(), page.key?.getOsisRef())
            } else {
                withContext(Dispatchers.Default) {
                    withTimeout(10_000) {
                        while (window.pageManager.currentPage.currentDocument != target) { runCurrent(); delay(10) }
                    }
                }
                assertEquals(target, window.pageManager.currentPage.currentDocument)
            }
        } finally {
            links.windowMode = oldMode
            // Exact render/link scopes are drained by class cleanup before deleting this owned fixture.
        }
    }

}
