package net.bible.android.control.page.window

import net.bible.android.database.IdType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.slot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class WindowCommandsImplTest {

    @Test fun setActiveResolvesIdAndSetsActiveWindow() {
        val id = IdType()
        val window = mockk<Window>(relaxed = true) { every { this@mockk.id } returns id }
        val repo = mockk<WindowRepository>(relaxed = true) { every { getWindow(id) } returns window }
        val wc = mockk<WindowControl>(relaxed = true) { every { windowRepository } returns repo }
        val activeSlot = slot<Window>()
        every { wc.activeWindow = capture(activeSlot) } answers {}

        WindowCommandsImpl(wc).setActive(id.toString())

        verify { wc.activeWindow = window }
        assertEquals(window, activeSlot.captured)
    }

    @Test fun setActiveWithUnknownIdIsNoOp() {
        val repo = mockk<WindowRepository>(relaxed = true) { every { getWindow(any()) } returns null }
        val wc = mockk<WindowControl>(relaxed = true) { every { windowRepository } returns repo }
        WindowCommandsImpl(wc).setActive(IdType().toString())
        verify(exactly = 0) { wc.activeWindow = any() }
    }

    @Test fun commitWeightsSetsBothWeightsAndNotifies() {
        val id1 = IdType(); val id2 = IdType()
        val w1 = mockk<Window>(relaxed = true); val w2 = mockk<Window>(relaxed = true)
        val repo = mockk<WindowRepository>(relaxed = true) {
            every { getWindow(id1) } returns w1
            every { getWindow(id2) } returns w2
        }
        val wc = mockk<WindowControl>(relaxed = true) { every { windowRepository } returns repo }

        WindowCommandsImpl(wc).commitWeights(id1.toString(), 1.5f, id2.toString(), 0.5f)

        verify { w1.weight = 1.5f }
        verify { w2.weight = 0.5f }
        verify { wc.windowSizesChanged() }
    }
}
