/*
 * Copyright (c) 2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.android.view.activity.discrete

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.service.common.CommonUtils.removeLeadingZeroes
import net.bible.sharedcore.calculator.CalculatorController
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.calculator.CalculatorScreen
import net.bible.sharedui.theme.AbTheme
import net.objecthunter.exp4j.ExpressionBuilder
import java.math.BigDecimal

/**
 * Compose host for the calculator disguise — the new-path twin of the classic [CalculatorActivity].
 * Renders the shared [CalculatorScreen] and drives it with a [CalculatorController], keeping the
 * arithmetic engine (exp4j + BigDecimal) and the real PIN on the `:app` side of the injected seams.
 * Behaviour (arithmetic formatting, PIN-vs-operation unlock decision, RESULT_OK/finish) mirrors
 * [CalculatorActivity] exactly. Like it, [doNotInitializeApp] is true (no workspace init behind
 * the disguise). Selected by [net.bible.android.view.ScreenLauncher] when `use_compose_ui` is on.
 */
class CalculatorComposeActivity : ActivityBase() {
    override val doNotInitializeApp: Boolean = true

    private val controller by lazy {
        CalculatorController(
            evaluate = ::evaluate,
            isPin = { input ->
                val pin = removeLeadingZeroes(
                    CommonUtils.realSharedPreferences.getString("calculator_pin", "1234")!!
                )
                input == pin || pin == ""
            },
            onUnlock = {
                Log.i(TAG, "Calculator (Compose): PIN OK!")
                setResult(RESULT_OK)
                finish()
            },
        )
    }

    /**
     * Mirrors [CalculatorActivity.calculate]'s arithmetic path: evaluate the normalised expression,
     * scale to 8 half-up decimals, then strip trailing zeros. Any error (bad format, division by
     * zero → "Infinity", which `BigDecimal` rejects) returns null, leaving the display unchanged.
     */
    private fun evaluate(expr: String): String? = try {
        val raw = ExpressionBuilder(expr).build().evaluate().toString()
        val scaled = BigDecimal(raw).setScale(8, BigDecimal.ROUND_HALF_UP).toPlainString()
        scaled.replace(Regex("\\.?0*$"), "")
    } catch (e: Exception) {
        null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "Calculator (Compose): onCreate")
        super.onCreate(savedInstanceState)
        setContent {
            ProvideAppLocals {
                AbTheme(
                    colorMode = CommonUtils.settings.displayColorMode,
                    disableAnimations = CommonUtils.settings.disableAnimations,
                ) {
                    val display by controller.display.collectAsState()
                    CalculatorScreen(display, controller::onKey)
                }
            }
        }
    }

    override fun onBackPressed() {
        Log.i(TAG, "Calculator (Compose): onBackPressed")
        setResult(RESULT_CANCELED)
        finish()
    }
}
