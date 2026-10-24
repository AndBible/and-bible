package net.bible.sharedui.ios.discrete

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import net.bible.sharedcore.calculator.CalculatorController
import net.bible.sharedcore.calculator.CalculatorEvaluator
import net.bible.sharedcore.calculator.CalculatorUnlock
import net.bible.sharedcore.platform.IosDeviceSettings
import net.bible.sharedui.calculator.CalculatorScreen

/**
 * U7 hides app content before PIN unlock; ADR 0007 does not fake the app name.
 * Device-local PIN access deliberately does not resolve Koin or database-backed AppSettings.
 */
@OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)
@kotlin.native.HiddenFromObjC
@Composable
fun IosCalculatorGate(settings: IosDeviceSettings, onUnlock: () -> Unit) {
    val unlock by rememberUpdatedState(onUnlock)
    val controller = remember(settings) {
        CalculatorController(
            CalculatorEvaluator::evaluate,
            { CalculatorUnlock.allows(it, settings.getString("calculator_pin")) },
            { unlock() },
        )
    }
    val display by controller.display.collectAsState()
    val error by controller.error.collectAsState()
    CalculatorScreen(display, error, controller::onKey)
}
