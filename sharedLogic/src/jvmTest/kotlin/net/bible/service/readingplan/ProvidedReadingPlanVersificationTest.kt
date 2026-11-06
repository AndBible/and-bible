package net.bible.service.readingplan

import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import net.bible.android.database.readingplan.ReadingPlanDao
import net.bible.service.db.readingplan.ReadingPlanRepository
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.readingplan.ReadingPlanSource
import net.bible.sharedlogic.platform.AddonReadingPlan
import net.bible.sharedlogic.platform.LocalCalendar
import okio.Path.Companion.toPath
import org.crosswire.jsword.book.Book
import java.io.File
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

/** A module-supplied plan sets its versification only in the book's conf, not in the plan file. */
class ProvidedReadingPlanVersificationTest {
    @Test
    fun infoUsesVersificationFromProvidedModuleConf() = runBlocking {
        val dir = File.createTempFile("provided-plan", "").apply { delete(); mkdirs() }
        val planFile = File(dir, "modplan.properties").apply { writeText("# Mod plan\n1=Joel.1\n2=Joel.2\n") }
        val book = Proxy.newProxyInstance(Book::class.java.classLoader, arrayOf(Book::class.java)) { _, m, a ->
            when (m.name) {
                "getProperty" -> if (a[0] == "Versification") "German" else null
                "getName" -> "Module plan"
                else -> error("Unexpected book call: ${m.name}")
            }
        } as Book
        val plan = AddonReadingPlan(book, "modplan.properties", false, planFile.absolutePath.toPath())
        val source = object : ReadingPlanSource {
            override fun builtInPlanCodes() = emptyList<String>()
            override fun openBuiltInPlan(code: String): String? = null
        }
        val scope = AppCoroutineScope()
        val calendar = unused<LocalCalendar>()
        val repo = ReadingPlanRepository({ unused<ReadingPlanDao>() }, scope, calendar)
        val dao = ReadingPlanTextFileDao(source, repo, unused<CoreStrings>(), calendar,
            { "/nonexistent-provided-plans".toPath() }, { mapOf("modplan" to plan) })
        try {
            val reading = dao.getReading("modplan", 1)
            assertEquals("German", reading.readingPlanInfo.versification!!.name)
        } finally {
            scope.coroutineContext[Job]?.cancel()
            dir.deleteRecursively()
        }
    }

    private inline fun <reified T> unused(): T = Proxy.newProxyInstance(
        T::class.java.classLoader, arrayOf(T::class.java),
    ) { _, method, _ -> if (method.name == "getPlan") null else error("Unexpected fixture call: ${method.name}") } as T
}
