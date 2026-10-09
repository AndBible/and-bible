package net.bible.service.readingplan

import androidx.room3.Room
import kotlinx.coroutines.runBlocking
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TestBibleApplication
import net.bible.android.database.ReadingPlanDatabase
import net.bible.service.db.readingplan.ReadingPlanRepository
import net.bible.service.db.sqliteDriverFactory
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.readingplan.ReadingPlanSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Pins how plan files are parsed, with the bundled plans coming through a fake [ReadingPlanSource]. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [35]) // 35: same in-memory Room setup as ReadingPlanRepositoryStartDateTest
class ReadingPlanTextFileDaoTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: ReadingPlanDatabase
    private lateinit var userDir: File

    private class FakeSource(val plans: Map<String, String>) : ReadingPlanSource {
        var opened = mutableListOf<String>()
        override fun builtInPlanCodes() = plans.keys.toList()
        override fun openBuiltInPlan(code: String): String? { opened += code; return plans[code] }
    }

    private val strings = object : CoreStrings {
        override val labelAll = "All"
        override val errorOccurred = "Error"
        override fun somethingWithParenthesis(a: String, b: String) = "$a ($b)"
        override fun readingPlanDay(day: String) = "Day $day"
    }

    /** The real file format: `#` header comment lines (name, then description), `day=Book.chapter, ...` lines. */
    private val twoDayPlan = "# Two day plan\n# A short description\n1=Gen.1, Matt.1\n2=Gen.2-Gen.3\n"

    /** A bundled file's bytes the way the Android source hands them over: Latin-1 decoded raw bytes. */
    private fun asFileText(utf8Text: String) = String(utf8Text.toByteArray(Charsets.UTF_8), Charsets.ISO_8859_1)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(application, ReadingPlanDatabase::class.java)
            .setDriver(sqliteDriverFactory()).build()
        userDir = tmp.newFolder("user")
    }

    @After fun tearDown() { db.close() }

    private fun dao(
        source: ReadingPlanSource,
        distributed: List<DistributedPlanDetails> = emptyList(),
    ) = ReadingPlanTextFileDao(
        source = source,
        repository = ReadingPlanRepository(daoProvider = { db.readingPlanDao() }),
        coreStrings = strings,
        userPlanFolder = { userDir },
        providedPlans = { emptyMap() },
        distributedPlans = { distributed },
    )

    @Test fun parsesDaysAndReadingsOfABundledPlan() = runBlocking {
        val dao = dao(FakeSource(mapOf("two" to twoDayPlan)))
        val list = dao.getReadingList("two")
        assertEquals(listOf(1, 2), list.map { it.day })
        assertEquals(2, list[0].numReadings)
        assertEquals("Genesis 1, Matthew 1", list[0].readingsDesc)
        assertEquals(1, list[1].numReadings)
        assertEquals("Day 1", list[0].dayDesc)
        assertEquals(2, dao.getNumberOfPlanDays("two"))
        val day2 = dao.getReading("two", 2)
        assertEquals(2, day2.day)
        assertTrue(day2.getReadingKey(1).cardinality > 30) // Gen.2-Gen.3
    }

    @Test fun planNameAndDescriptionComeFromTheHeaderComments() = runBlocking {
        val info = dao(FakeSource(mapOf("two" to twoDayPlan))).getReadingPlanInfoDto("two")
        assertEquals("Two day plan", info.planName)
        assertEquals("A short description", info.planDescription)
        assertEquals(2, info.numberOfPlanDays)
        assertFalse(info.isDateBasedPlan)
    }

    @Test fun nonAsciiHeaderCommentsSurviveTheTextHandover() = runBlocking {
        val text = asFileText("# Vuoden lukuohjelma äöå ✝\n# Päivittäinen kuvaus\n1=Gen.1\n")
        val info = dao(FakeSource(mapOf("fi" to text))).getReadingPlanInfoDto("fi")
        assertEquals("Vuoden lukuohjelma äöå ✝", info.planName)
        assertEquals("Päivittäinen kuvaus", info.planDescription)
    }

    @Test fun aDateBasedPlanIsRecognisedAndDatesParsed() = runBlocking {
        val dao = dao(FakeSource(mapOf("dated" to "# Dated\n1=Jan-1;Gen.1\n2=Jan-2;Gen.2\n")))
        assertTrue(dao.getReadingPlanInfoDto("dated").isDateBasedPlan)
        assertEquals(2, dao.getReadingList("dated").size)
        assertTrue(dao.getReading("dated", 1).readingDate != null)
    }

    @Test fun aDistributedPlanKeepsItsLocalizedNameAndDescription() = runBlocking {
        val info = dao(FakeSource(mapOf("two" to twoDayPlan)), listOf(DistributedPlanDetails("two", "Localized", "Localized desc")))
            .getReadingPlanInfoDto("two")
        assertEquals("Localized", info.planName)
        assertEquals("Localized desc", info.planDescription)
    }

    @Test fun aUserFileWithTheSameCodeWinsOverTheBundledPlan() = runBlocking {
        File(userDir, "two.properties").writeText("# Mine\n1=Exod.1\n")
        val source = FakeSource(mapOf("two" to twoDayPlan))
        val dao = dao(source)
        assertEquals("Mine", dao.getReadingPlanInfoDto("two").planName)
        assertEquals("Exodus 1", dao.getReading("two", 1).readingsDesc)
        assertTrue(source.opened.isEmpty())
    }

    @Test fun planListHoldsBundledThenUserPlansWithoutDuplicates() = runBlocking {
        File(userDir, "two.properties").writeText("# Mine\n1=Exod.1\n")
        File(userDir, "own.properties").writeText("# Own\n1=Lev.1\n")
        File(userDir, "notes.txt").writeText("ignored")
        val dao = dao(FakeSource(mapOf("two" to twoDayPlan, "three" to twoDayPlan)))
        assertEquals(listOf("two", "three", "own"), dao.readingPlanList().map { it.planCode })
        assertEquals(listOf("two", "three"), dao.internalPlanCodes)
        assertEquals(listOf("own"), dao.userPlanCodes())
        assertEquals(setOf("two", "own"), dao.userPlanCodes(filterDuplicates = false)!!.toSet())
    }
}
