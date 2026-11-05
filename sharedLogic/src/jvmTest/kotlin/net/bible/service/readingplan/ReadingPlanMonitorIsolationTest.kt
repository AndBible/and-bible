package net.bible.service.readingplan

import net.bible.android.database.readingplan.ReadingPlanDao
import net.bible.service.db.readingplan.ReadingPlanRepository
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.readingplan.ReadingPlanSource
import net.bible.sharedlogic.platform.LocalCalendar
import okio.Path.Companion.toPath
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadingPlanMonitorIsolationTest {
    @Test fun aSlowPlanReadDoesNotBlockAnotherPlanOnTheSameDao() {
        val slowStarted = CountDownLatch(1)
        val releaseSlow = CountDownLatch(1)
        val fastDone = CountDownLatch(1)
        val source = object : ReadingPlanSource {
            override fun builtInPlanCodes() = listOf("slow", "fast")
            override fun openBuiltInPlan(code: String): String {
                if (code == "slow") {
                    slowStarted.countDown()
                    check(releaseSlow.await(10, TimeUnit.SECONDS))
                }
                return "# $code\n1=Gen.1\n2=Gen.2\n"
            }
        }
        val scope = AppCoroutineScope()
        val calendar = unused<LocalCalendar>()
        val dao = ReadingPlanTextFileDao(source,
            ReadingPlanRepository({ unused<ReadingPlanDao>() }, scope, calendar), unused<CoreStrings>(), calendar,
            { "/nonexistent-monitor-test-plans".toPath() }, { emptyMap() })
        var slowDays = 0
        var fastDays = 0
        val slow = thread { slowDays = dao.getNumberOfPlanDays("slow") }
        var fast: Thread? = null
        try {
            assertTrue(slowStarted.await(5, TimeUnit.SECONDS))
            fast = thread {
                fastDays = dao.getNumberOfPlanDays("fast")
                fastDone.countDown()
            }
            assertTrue(fastDone.await(2, TimeUnit.SECONDS), "unrelated plan read blocked behind file I/O")
        } finally {
            releaseSlow.countDown()
            slow.join(10_000)
            fast?.join(10_000)
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
        assertEquals(2, slowDays)
        assertEquals(2, fastDays)
    }

    private inline fun <reified T> unused(): T = Proxy.newProxyInstance(
        T::class.java.classLoader, arrayOf(T::class.java),
    ) { _, method, _ -> error("Unexpected fixture call: ${method.name}") } as T
}
