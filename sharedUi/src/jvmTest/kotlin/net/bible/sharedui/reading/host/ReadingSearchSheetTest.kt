package net.bible.sharedui.reading.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3Api::class)
class ReadingSearchSheetTest {
    @Test
    fun newHandoffDuringPreviousSheetHideStaysOpen() {
        for (frames in 0..4) {
            var visible by mutableStateOf(true)
            var hasContent by mutableStateOf(true)
            lateinit var scaffold: BottomSheetScaffoldState
            runComposeUiTest {
                mainClock.autoAdvance = false
                setContent {
                    AbTheme(darkTheme = false, disableAnimations = true) {
                        scaffold = rememberBottomSheetScaffoldState(
                            bottomSheetState = rememberStandardBottomSheetState(
                                initialValue = SheetValue.Hidden, skipHiddenState = false,
                            ),
                        )
                        DriveSearchSheet(scaffold, visible) {
                            visible = false
                        }
                        BottomSheetScaffold(
                            scaffoldState = scaffold, sheetPeekHeight = 0.dp,
                            sheetContent = { Box(Modifier.height(if (hasContent) 200.dp else 1.dp)) },
                        ) { Box(Modifier.fillMaxSize()) }
                    }
                }
                mainClock.advanceTimeBy(2_000)
                runOnIdle { visible = false; hasContent = false }
                repeat(frames) { mainClock.advanceTimeByFrame() }
                runOnIdle { visible = true; hasContent = true }
                mainClock.advanceTimeBy(2_000)
                runOnIdle {
                    assertTrue(visible, "new handoff during hide after $frames frames")
                    assertEquals(SheetValue.Expanded, scaffold.bottomSheetState.currentValue)
                }
            }
        }
    }

    @Test
    fun searchHandoffPresentWhenHostMountsIsNotDismissedByInitialHiddenState() {
        for (disableAnimations in listOf(false, true)) {
            val visibility = MutableStateFlow(false)
            var dismissals = 0
            lateinit var scaffold: BottomSheetScaffoldState
            lateinit var scope: CoroutineScope
            runComposeUiTest {
                mainClock.autoAdvance = false
                setContent {
                    AbTheme(darkTheme = false, disableAnimations = disableAnimations) {
                        scaffold = rememberBottomSheetScaffoldState(
                            bottomSheetState = rememberStandardBottomSheetState(
                                initialValue = SheetValue.Hidden, skipHiddenState = false,
                            ),
                        )
                        scope = rememberCoroutineScope()
                        // The handoff is accepted from the host effect before the sheet's observer starts.
                        LaunchedEffect(Unit) { visibility.value = true }
                        val visible by visibility.collectAsState()
                        DriveSearchSheet(scaffold, visible) {
                            dismissals++
                            visibility.value = false
                        }
                        BottomSheetScaffold(
                            scaffoldState = scaffold,
                            sheetPeekHeight = 0.dp,
                            sheetContent = { Text("Occurrence results", Modifier.height(200.dp)) },
                        ) { Box(Modifier.fillMaxSize()) }
                    }
                }
                mainClock.advanceTimeBy(2_000)
                runOnIdle {
                    assertTrue(visibility.value, "initial Hidden is not a user dismissal (animations disabled=$disableAnimations)")
                    assertEquals(0, dismissals)
                    assertEquals(SheetValue.Expanded, scaffold.bottomSheetState.currentValue)
                }
                // Settling a user swipe at the zero-height peek must still dismiss the session sheet.
                runOnIdle { scope.launch { scaffold.bottomSheetState.partialExpand() } }
                mainClock.advanceTimeBy(2_000)
                runOnIdle {
                    assertEquals(false, visibility.value)
                    assertTrue(dismissals > 0)
                }
                val settledDismissals = dismissals
                runOnIdle { visibility.value = true }
                mainClock.advanceTimeBy(2_000)
                runOnIdle {
                    assertTrue(visibility.value)
                    assertEquals(SheetValue.Expanded, scaffold.bottomSheetState.currentValue)
                    assertEquals(settledDismissals, dismissals, "reopening must not report another dismissal")
                }
            }
        }
    }

    @Test
    fun dragAwayDuringExpandAnimationStillCountsAsDismissal() {
        val visibility = MutableStateFlow(false)
        var dismissals = 0
        lateinit var scaffold: BottomSheetScaffoldState
        lateinit var scope: CoroutineScope
        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent {
                AbTheme(darkTheme = false, disableAnimations = false) {
                    scaffold = rememberBottomSheetScaffoldState(
                        bottomSheetState = rememberStandardBottomSheetState(
                            initialValue = SheetValue.Hidden, skipHiddenState = false,
                        ),
                    )
                    scope = rememberCoroutineScope()
                    val visible by visibility.collectAsState()
                    DriveSearchSheet(scaffold, visible) {
                        dismissals++
                        visibility.value = false
                    }
                    BottomSheetScaffold(
                        scaffoldState = scaffold,
                        sheetPeekHeight = 0.dp,
                        sheetContent = { Text("Occurrence results", Modifier.height(200.dp)) },
                    ) { Box(Modifier.fillMaxSize()) }
                }
            }
            mainClock.advanceTimeBy(500)
            runOnIdle { visibility.value = true }
            // A few frames into the expand animation: the sheet has not settled at Expanded yet.
            repeat(3) { mainClock.advanceTimeByFrame() }
            runOnIdle {
                assertTrue(scaffold.bottomSheetState.currentValue != SheetValue.Expanded, "still animating")
                scope.launch { scaffold.bottomSheetState.hide() }
            }
            mainClock.advanceTimeBy(3_000)
            runOnIdle {
                assertTrue(scaffold.bottomSheetState.currentValue != SheetValue.Expanded)
                assertEquals(1, dismissals)
                assertEquals(false, visibility.value)
            }
        }
    }
}
