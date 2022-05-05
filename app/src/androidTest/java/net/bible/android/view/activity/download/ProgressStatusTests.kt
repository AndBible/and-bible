package net.bible.android.view.activity.download

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import net.bible.android.activity.R
import org.crosswire.common.progress.JobManager
import org.hamcrest.Matchers.anything
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class ProgressStatusTests
    {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before
    fun setup() {
        // Mark all (leftover) jobs as done to clear the JobManager's state
        JobManager.iterator().forEach { it.done() }
    }

    @After
    fun tearDown() {
        JobManager.iterator().forEach { it.done() }
    }


    private fun waitForNoTasksDelay() {
        // See ProgressActivityBase.initialiseView()
        // It waits 4s before configuring the "No Tasks Running" view
        SystemClock.sleep(4100)
    }

    @Test
    fun testDisplayNoTasksMessage() {
        launch(ProgressStatus::class.java).use {
            instrumentation.waitForIdleSync()
            waitForNoTasksDelay()
            onView(withId(R.id.noTasksRunning))
                .check(matches(isDisplayed()))
        } // scenario
    }

    @Test
    fun testHideNoTasksMessage() {
        val job = JobManager.createJob("Test Job")
        launch(ProgressStatus::class.java).use {
            instrumentation.waitForIdleSync()
            waitForNoTasksDelay()
            onView(withId(R.id.noTasksRunning))
                .check(matches(withEffectiveVisibility(Visibility.GONE)))
        } // scenario
        job.done()
    }

    @Test
    fun testTasksViewIsVisible() {
        val job = JobManager.createJob("Test Job")
        launch(ProgressStatus::class.java).use {
            instrumentation.waitForIdleSync()
            onView(withId(R.id.progressControlContainer))
                    .check(matches(isDisplayed()))
        } // scenario
        job.done()
    }

    @Test
    fun testScrollToEnd() {
        // create some placeholder jobs to be displayed
        for (i in 1..50) {
            JobManager.createJob("Test Job #${i}")
        }

        launch(ProgressStatus::class.java).use {
            instrumentation.waitForIdleSync()
            // validate that the views extend beyond the visible screen
            onView(withText("Test Job #1"))
                .check(matches(isDisplayed()))
            val lastViewMatcher = withText("Test Job #50")
            onView(lastViewMatcher)
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))

            // Verify that the last one can be scrolled into view
            onView(lastViewMatcher).perform(scrollTo())
            onView(lastViewMatcher).check(matches(isDisplayed()))
        } // scenario
    } // testScrollToEnd()


    @Test
    fun testFinishedJobIsNotDisplayed() {
        // create some placeholder jobs to be displayed
        for (i in 1..3) {
            JobManager.createJob("Test Job #${i}")
        }
        val jobname = "Job Will Be Completed"
        val job = JobManager.createJob(jobname)
        val matcher = withText(jobname)

        launch(ProgressStatus::class.java).use {
            instrumentation.waitForIdleSync()

            // Verify that the job is displayed
            onView(matcher).check(matches(isDisplayed()))

            // the job has finished
            job.done()

            // Verify that it is no longer displayed
            Assert.assertThrows(
                NoMatchingViewException::class.java,
                { onView(matcher).check(matches(anything())) })
        } // scenario
    } // testFinishedJobIsNotDisplayed()

    } // class
