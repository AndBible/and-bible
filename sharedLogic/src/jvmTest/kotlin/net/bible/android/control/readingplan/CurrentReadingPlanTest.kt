package net.bible.android.control.readingplan

import java.lang.reflect.Proxy
import kotlinx.coroutines.test.runTest
import net.bible.android.database.DbDate
import net.bible.android.database.readingplan.ReadingPlanDao
import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlan
import net.bible.sharedcore.platform.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class CurrentReadingPlanTest {
    private val values = mutableMapOf<String, String?>()
    private val settings = Proxy.newProxyInstance(AppSettings::class.java.classLoader, arrayOf(AppSettings::class.java)) { _, method, args ->
        when (method.name) {
            "getString" -> values[args[0]] ?: args[1]
            "setString", "removeString" -> { values[args[0] as String] = args.getOrNull(1) as String?; Unit }
            else -> error("Unexpected settings call: ${method.name}")
        }
    } as AppSettings
    private val selection = CurrentReadingPlan(settings)

    private fun dao(vararg plans: String) = Proxy.newProxyInstance(
        ReadingPlanDao::class.java.classLoader, arrayOf(ReadingPlanDao::class.java),
    ) { _, method, args ->
        check(method.name == "getPlan")
        (args[0] as String).takeIf { it in plans }?.let { ReadingPlan(it, DbDate(1_800_000_000_000L)) }
    } as ReadingPlanDao

    @Test fun syncedResetForgetsTheSelectedPlan() = runTest {
        selection.select("plan")
        selection.reconcile(dao())
        assertEquals("", selection.code)
    }

    @Test fun resetAndRestartKeepsTheSelectedPlan() = runTest {
        selection.select("plan")
        selection.reconcile(dao("plan"))
        assertEquals("plan", selection.code)
    }

    @Test fun localResetOnlyForgetsTheResetPlan() {
        selection.select("plan")
        selection.forgetReset("other")
        assertEquals("plan", selection.code)
        selection.forgetReset("plan")
        assertEquals("", selection.code)
    }
}
