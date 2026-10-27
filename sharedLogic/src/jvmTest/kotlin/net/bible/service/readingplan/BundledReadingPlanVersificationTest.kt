package net.bible.service.readingplan

import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import net.bible.android.database.readingplan.ReadingPlanDao
import net.bible.service.db.readingplan.ReadingPlanRepository
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.readingplan.ReadingPlanSource
import net.bible.sharedlogic.platform.LocalCalendar
import okio.Path.Companion.toPath
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.VersificationConverter
import org.crosswire.jsword.versification.system.Versifications
import java.io.File
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Uses the reported plan and the FinRK module from the standard testmods.zip fixture. */
class BundledReadingPlanVersificationTest {
    @Test
    fun twoYearPlanCoversAllFourFinRkJoelChapters() = runBlocking {
        val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .first { File(it, "app/src/main/assets/readingplan").isDirectory }
        val source = object : ReadingPlanSource {
            override fun builtInPlanCodes() = listOf("y2ot1ntps2")
            override fun openBuiltInPlan(code: String) =
                File(root, "app/src/main/assets/readingplan/$code.properties").readText(Charsets.ISO_8859_1)
        }
        val scope = AppCoroutineScope()
        val calendar = unused<LocalCalendar>()
        val dao = ReadingPlanTextFileDao(
            source,
            ReadingPlanRepository({ unused<ReadingPlanDao>() }, scope, calendar),
            unused<CoreStrings>(), calendar,
            userPlanFolder = { "/nonexistent-reading-plans".toPath() },
            providedPlans = { emptyMap() },
        )
        try {
            val bible = assertNotNull(Books.installed().getBook("FinRK"), "Install .local/testmods.zip") as AbstractPassageBook
            assertEquals("German", bible.versification.name)
            val days = (678..680).map { dao.getReading("y2ot1ntps2", it) }
            assertTrue(days.all { it.readingPlanInfo.versification!!.name == "KJV" })
            val displayedDays = days.map { it.forVersification(bible.versification) }
            assertEquals("Joel 4", displayedDays.last().getReadingKey(2).getName())
            val middleDayVerses = displayedDays[1].getReadingKey(2).map { it as Verse }
            assertEquals("Joel.2.1", middleDayVerses.first().getOsisID())
            assertEquals("Joel.3.5", middleDayVerses.last().getOsisID())
            assertEquals(32, middleDayVerses.size)
            assertEquals("Joel 3", days.last().getReadingKey(2).getName())
            assertEquals(2, displayedDays.last().numReadings)
            assertTrue(displayedDays.last().readingsDesc.contains("Joel 4"))
            val converter = VersificationConverter()
            val readings = days.map { day ->
                KeyUtil.getPassage(converter.convert(day.getReadingKey(2), bible.versification))!!
            }

            assertEquals("Joel.4.1", readings.last().getVerseAt(0).getOsisID())
            assertEquals("Joel.4.21", readings.last().getVerseAt(20).getOsisID())
            for ((chapter, lastVerse) in listOf(20, 27, 5, 21).withIndex()) {
                for (verse in 1..lastVerse) {
                    val expected = Verse(bible.versification, BibleBook.JOEL, chapter + 1, verse)
                    assertTrue(readings.any { it.contains(expected) }, "Missing ${expected.getOsisID()}")
                }
            }
        } finally {
            scope.coroutineContext[Job]?.cancel()
        }
    }

    @Test
    fun forVersificationReturnsSameInstanceForOwnVersificationAndKeepsDayData() {
        val kjv = Versifications.instance().getVersification("KJV")!!
        val german = Versifications.instance().getVersification("German")!!
        val info = ReadingPlanInfoDto("p").apply { versification = kjv }
        val strings = object : CoreStringsStub() {}.proxy()
        val day = OneDaysReadingsDto(5, "Joel.1,Joel.3", info, strings, unused<LocalCalendar>())

        assertTrue(day.forVersification(kjv) === day)

        val mapped = day.forVersification(german)
        assertEquals(5, mapped.day)
        assertEquals(day.dayDesc, mapped.dayDesc)
        assertEquals(2, mapped.numReadings)
        assertEquals("Joel 4", mapped.getReadingKey(2).getName())
        assertEquals("Joel 3", day.getReadingKey(2).getName())
    }

    private open class CoreStringsStub {
        fun proxy(): CoreStrings = Proxy.newProxyInstance(
            CoreStrings::class.java.classLoader, arrayOf(CoreStrings::class.java),
        ) { _, m, a -> if (m.name == "readingPlanDay") "Day ${a[0]}" else error("Unexpected ${m.name}") } as CoreStrings
    }

    private inline fun <reified T> unused(): T = Proxy.newProxyInstance(
        T::class.java.classLoader, arrayOf(T::class.java),
    ) { _, method, _ ->
        if (method.name == "getPlan") null else error("Unexpected fixture call: ${method.name}")
    } as T
}
