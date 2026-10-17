package net.bible.android.platform

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.sharedlogic.platform.LocaleValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.text.BreakIterator
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Oracle tests: each Android port must behave exactly like the inline JDK code it replaces. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class G3PortsOracleTest {
    private fun v(l: Locale) = LocaleValue(l.language, l.country, l.toLanguageTag(), l.toString())

    @Test fun localeSupportMatchesJavaLocale() {
        val saved = Locale.getDefault()
        try {
            for (tag in listOf("en-US", "fi-FI", "tr-TR", "ar-EG", "he-IL", "zh-Hant-TW")) {
                val d = Locale.forLanguageTag(tag)
                Locale.setDefault(d)
                val s = AndroidLocaleSupport
                assertEquals(tag, v(d), s.default)
                assertEquals(tag, v(Locale("he")), s.of("he"))
                assertEquals(tag, v(Locale("iw")), s.of("iw"))
                assertEquals(tag, v(Locale("en", "GB")), s.of("en", "GB"))
                assertEquals(tag, v(Locale("en_GB")), s.parse("en_GB"))
                assertEquals(tag, v(Locale("fi")), s.parse("fi"))
                assertEquals("suomi", s.displayLanguage("fi"))
                val unk = Locale.forLanguageTag("xx-unknown")
                assertEquals(unk.getDisplayLanguage(unk), s.displayLanguage("xx-unknown"))
                assertEquals(d.getDisplayLanguage(d), s.defaultDisplayLanguage())
                assertEquals(tag, "%.2f".format(0.125), s.formatFixed(0.125, 2))
                assertEquals(tag, "%.1f".format(1234.5), s.formatFixed(1234.5, 1))
                assertEquals(tag, "%,d".format(1234567L), s.formatGrouped(1234567))
            }
        } finally { Locale.setDefault(saved) }
    }

    private fun cal(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }
    private fun Calendar.zero() = apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    @Test fun localCalendarMatchesCalendarAcrossDst() {
        val savedTz = TimeZone.getDefault()
        val savedLocale = Locale.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Helsinki"))
            for (loc in listOf("en-US", "fi-FI")) {
                Locale.setDefault(Locale.forLanguageTag(loc))
                val c = AndroidLocalCalendar()
                for ((from, to) in listOf("2026-03-27" to "2026-04-01", "2026-10-23" to "2026-10-28")) {
                    var t = java.time.LocalDate.parse(from).atStartOfDay(java.time.ZoneId.of("UTC")).toInstant().toEpochMilli()
                    val end = java.time.LocalDate.parse(to).atStartOfDay(java.time.ZoneId.of("UTC")).toInstant().toEpochMilli()
                    while (t <= end) {
                        val m = "$loc $t"
                        assertEquals(m, cal(t).zero().timeInMillis, c.startOfLocalDay(t))
                        for (n in listOf(-1, 1, 7)) {
                            assertEquals(m, cal(t).apply { add(Calendar.DAY_OF_YEAR, n) }.timeInMillis, c.plusLocalDays(t, n))
                            assertEquals(m, cal(t).apply { add(Calendar.WEEK_OF_YEAR, n) }.timeInMillis, c.plusWeeks(t, n))
                        }
                        assertEquals(m, cal(t).apply { set(Calendar.DAY_OF_WEEK, firstDayOfWeek); zero() }.timeInMillis, c.startOfLocalWeek(t))
                        assertEquals(m, cal(t).get(Calendar.MONTH), c.localMonth(t))
                        assertEquals(m, cal(t).getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) ?: "", c.shortMonthName(t))
                        t += 3_600_000L
                    }
                }
            }
            Locale.setDefault(Locale.forLanguageTag("en-US"))
            val wed = cal(0).apply { set(2026, Calendar.OCTOBER, 14, 12, 0, 0) }.timeInMillis
            assertEquals(Calendar.SUNDAY, cal(AndroidLocalCalendar().startOfLocalWeek(wed)).get(Calendar.DAY_OF_WEEK))
            Locale.setDefault(Locale.forLanguageTag("fi-FI"))
            assertEquals(Calendar.MONDAY, cal(AndroidLocalCalendar().startOfLocalWeek(wed)).get(Calendar.DAY_OF_WEEK))
        } finally {
            TimeZone.setDefault(savedTz)
            Locale.setDefault(savedLocale)
        }
    }

    @Test fun sentenceBreakerMatchesBreakIterator() {
        val bible = (1..40).joinToString(" ") { "In the beginning God created the heaven and the earth number $it. And the earth was without form! Was it void?" }
            .take(3000)
        for (text in listOf("First one. Second two! Third?", bible)) {
            for (o in 1 until text.length) {
                val bi = BreakIterator.getSentenceInstance().apply { setText(text) }
                assertEquals(bi.preceding(o), AndroidSentenceBreaker.preceding(text, o))
                val bi2 = BreakIterator.getSentenceInstance().apply { setText(text) }
                assertEquals(bi2.following(o), AndroidSentenceBreaker.following(text, o))
            }
            val bi = BreakIterator.getSentenceInstance().apply { setText(text) }
            // DONE (-1) at both ends.
            assertEquals(BreakIterator.DONE, bi.following(text.length))
            assertEquals(BreakIterator.DONE, AndroidSentenceBreaker.following(text, text.length))
            val bi0 = BreakIterator.getSentenceInstance().apply { setText(text) }
            assertEquals(BreakIterator.DONE, bi0.preceding(0))
            assertEquals(BreakIterator.DONE, AndroidSentenceBreaker.preceding(text, 0))
            assertThrows(IllegalArgumentException::class.java) { bi.following(text.length + 1) }
            assertThrows(IllegalArgumentException::class.java) { AndroidSentenceBreaker.following(text, text.length + 1) }
            assertThrows(IllegalArgumentException::class.java) { AndroidSentenceBreaker.preceding(text, text.length + 1) }
        }
    }

    @Test fun labelNameStringsReadResources() {
        val ctx = RuntimeEnvironment.getApplication()
        val s = AndroidLabelNameStrings(ctx)
        fun check() {
            assertEquals(ctx.getString(R.string.speak), s.speak)
            assertEquals(ctx.getString(R.string.label_unlabelled), s.unlabelled)
            assertEquals(ctx.getString(R.string.add_paragraph_break), s.paragraphBreak)
            assertEquals(ctx.getString(R.string.ai_label), s.ai)
        }
        check()
        val before = s.unlabelled
        val savedConfig = android.content.res.Configuration(ctx.resources.configuration)
        try {
            // Flip the app locale: a value cached at construction or first read would now be stale.
            ctx.resources.updateConfiguration(
                android.content.res.Configuration(savedConfig).apply { setLocale(Locale.forLanguageTag("fi")) },
                ctx.resources.displayMetrics
            )
            check()
            assertEquals(ctx.getString(R.string.label_unlabelled), s.unlabelled)
            assert(before != s.unlabelled) { "fi translation of label_unlabelled expected to differ from English" }
        } finally {
            ctx.resources.updateConfiguration(savedConfig, ctx.resources.displayMetrics)
        }
    }
}
