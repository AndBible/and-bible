/*
 * Copyright (c) 2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import net.bible.android.activity.R
import android.graphics.PorterDuff
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import net.bible.android.activity.databinding.CalculatorLayoutBinding
import net.bible.service.common.CommonUtils
import java.lang.Exception
import java.lang.NumberFormatException
import java.math.BigDecimal
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager

// Copied and adapted from https://github.com/eloyzone/android-calculator (5fb1d5e)

class CalculatorActivity : AppCompatActivity() {
    private var openParenthesis = 0
    private var dotUsed = false
    private var equalClicked = false
    private var lastExpression = ""

    lateinit var scriptEngine: ScriptEngine

    private lateinit var calculatorBinding: CalculatorLayoutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        calculatorBinding = CalculatorLayoutBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        setContentView(calculatorBinding.root)
        scriptEngine = ScriptEngineManager().getEngineByName("rhino")
        setOnClickListeners()
        setOnTouchListener()
    }

    private fun setOnClickListeners() = calculatorBinding.run {
        buttonNumber0.setOnClickListener { if (addNumber("0")) equalClicked = false }
        buttonNumber1.setOnClickListener { if (addNumber("1")) equalClicked = false }
        buttonNumber2.setOnClickListener { if (addNumber("2")) equalClicked = false }
        buttonNumber3.setOnClickListener { if (addNumber("3")) equalClicked = false }
        buttonNumber4.setOnClickListener { if (addNumber("4")) equalClicked = false }
        buttonNumber5.setOnClickListener { if (addNumber("5")) equalClicked = false }
        buttonNumber6.setOnClickListener { if (addNumber("6")) equalClicked = false }
        buttonNumber7.setOnClickListener { if (addNumber("7")) equalClicked = false }
        buttonNumber8.setOnClickListener { if (addNumber("8")) equalClicked = false }
        buttonNumber9.setOnClickListener { if (addNumber("9")) equalClicked = false }
        buttonClear.setOnClickListener {
            textViewInputNumbers.text = ""
            openParenthesis = 0
            dotUsed = false
            equalClicked = false
        }
        buttonParentheses.setOnClickListener { if (addParenthesis()) equalClicked = false }
        buttonPercent.setOnClickListener { if (addOperand("%")) equalClicked = false }
        buttonDivision.setOnClickListener { if (addOperand("\u00F7")) equalClicked = false }
        buttonMultiplication.setOnClickListener { if (addOperand("x")) equalClicked = false }
        buttonSubtraction.setOnClickListener { if (addOperand("-")) equalClicked = false }
        buttonAddition.setOnClickListener { if (addOperand("+")) equalClicked = false }
        buttonEqual.setOnClickListener {
            if (textViewInputNumbers.text.toString() != "") calculate(textViewInputNumbers.text.toString())
        }
        buttonDot.setOnClickListener {
            if (addDot()) equalClicked = false
        }
    }

    private fun setOnTouchListener() = calculatorBinding.run {
        val onTouchListener = { view: View, motionEvent: MotionEvent ->
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.background.setColorFilter(Color.DKGRAY, PorterDuff.Mode.SRC_ATOP)
                    view.invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    view.background.clearColorFilter()
                    view.invalidate()
                }
            }
            false
        }
        val buttons = arrayOf(
            buttonNumber0, buttonNumber1, buttonNumber2, buttonNumber3, buttonNumber4,
            buttonNumber5, buttonNumber6, buttonNumber7, buttonNumber8, buttonNumber9,
            buttonClear, buttonParentheses, buttonPercent, buttonDivision,
            buttonMultiplication, buttonSubtraction, buttonAddition, buttonDot
        )

        for(button in buttons) {
            button.setOnTouchListener(onTouchListener)
        }
    }


    private fun addDot(): Boolean = calculatorBinding.run {
        var done = false
        if (textViewInputNumbers.text.isEmpty()) {
            textViewInputNumbers.text = "0."
            dotUsed = true
            done = true
        } else if (dotUsed) {
        } else if (defineLastCharacter(
                textViewInputNumbers.text[textViewInputNumbers.text.length - 1].toString() + ""
            ) == IS_OPERAND
        ) {
            textViewInputNumbers.text = textViewInputNumbers.text.toString() + "0."
            done = true
            dotUsed = true
        } else if (defineLastCharacter(textViewInputNumbers.text[textViewInputNumbers.text.length - 1].toString() + "") == IS_NUMBER) {
            textViewInputNumbers.text = textViewInputNumbers.text.toString() + "."
            done = true
            dotUsed = true
        }
        return done
    }

    private fun addParenthesis(): Boolean = calculatorBinding.run {
        var done = false
        val operationLength = textViewInputNumbers.text.length
        if (operationLength == 0) {
            textViewInputNumbers.text = textViewInputNumbers.text.toString() + "("
            dotUsed = false
            openParenthesis++
            done = true
        } else if (openParenthesis > 0 && operationLength > 0) {
            val lastInput = textViewInputNumbers.text[operationLength - 1].toString() + ""
            when (defineLastCharacter(lastInput)) {
                IS_NUMBER -> {
                    textViewInputNumbers.text = textViewInputNumbers.text.toString() + ")"
                    done = true
                    openParenthesis--
                    dotUsed = false
                }
                IS_OPERAND -> {
                    textViewInputNumbers.text = textViewInputNumbers.text.toString() + "("
                    done = true
                    openParenthesis++
                    dotUsed = false
                }
                IS_OPEN_PARENTHESIS -> {
                    textViewInputNumbers.text = textViewInputNumbers.text.toString() + "("
                    done = true
                    openParenthesis++
                    dotUsed = false
                }
                IS_CLOSE_PARENTHESIS -> {
                    textViewInputNumbers.text = textViewInputNumbers.text.toString() + ")"
                    done = true
                    openParenthesis--
                    dotUsed = false
                }
            }
        } else if (openParenthesis == 0 && operationLength > 0) {
            val lastInput = textViewInputNumbers.text[operationLength - 1].toString() + ""
            if (defineLastCharacter(lastInput) == IS_OPERAND) {
                textViewInputNumbers.text = textViewInputNumbers.text.toString() + "("
                done = true
                dotUsed = false
                openParenthesis++
            } else {
                textViewInputNumbers.text = textViewInputNumbers.text.toString() + "x("
                done = true
                dotUsed = false
                openParenthesis++
            }
        }
        return done
    }

    private fun addOperand(operand: String): Boolean = calculatorBinding.run {
        var done = false
        val operationLength = textViewInputNumbers.text.length
        if (operationLength > 0) {
            val lastInput = textViewInputNumbers.text[operationLength - 1].toString() + ""
            if (lastInput == "+" || lastInput == "-" || lastInput == "*" || lastInput == "\u00F7" || lastInput == "%") {
                Toast.makeText(applicationContext, getString(R.string.calc_wrong_format), Toast.LENGTH_LONG).show()
            } else if (operand == "%" && defineLastCharacter(lastInput) == IS_NUMBER) {
                textViewInputNumbers.text = textViewInputNumbers.text.toString() + operand
                dotUsed = false
                equalClicked = false
                lastExpression = ""
                done = true
            } else if (operand != "%") {
                textViewInputNumbers.text = textViewInputNumbers.text.toString() + operand
                dotUsed = false
                equalClicked = false
                lastExpression = ""
                done = true
            }
        } else {
            Toast.makeText(
                applicationContext,
                getString(R.string.calc_wrong_format_operand),
                Toast.LENGTH_LONG
            ).show()
        }
        return done
    }

    private fun addNumber(number: String): Boolean = calculatorBinding.run {
        var done = false
        val operationLength = textViewInputNumbers.text.length
        if (operationLength > 0) {
            val lastCharacter = textViewInputNumbers.text[operationLength - 1].toString() + ""
            val lastCharacterState = defineLastCharacter(lastCharacter)
            if (operationLength == 1 && lastCharacterState == IS_NUMBER && lastCharacter == "0") {
                textViewInputNumbers.text = number
                done = true
            } else if (lastCharacterState == IS_OPEN_PARENTHESIS) {
                textViewInputNumbers.text = textViewInputNumbers.text.toString() + number
                done = true
            } else if (lastCharacterState == IS_CLOSE_PARENTHESIS || lastCharacter == "%") {
                textViewInputNumbers.text = textViewInputNumbers.text.toString() + "x" + number
                done = true
            } else if (lastCharacterState == IS_NUMBER || lastCharacterState == IS_OPERAND || lastCharacterState == IS_DOT) {
                textViewInputNumbers.text = textViewInputNumbers.text.toString() + number
                done = true
            }
        } else {
            textViewInputNumbers.text = textViewInputNumbers.text.toString() + number
            done = true
        }
        return done
    }

    private fun calculate(input: String) = calculatorBinding.run {
        val pin = CommonUtils.settings.getString("calculator_pin", "1234")
        if(input == pin) {
            setResult(RESULT_OK)
            finish()
        }
        var result = ""
        try {
            var temp = input
            if (equalClicked) {
                temp = input + lastExpression
            } else {
                saveLastExpression(input)
            }
            result = scriptEngine.eval(
                temp.replace("%".toRegex(), "/100").replace("x".toRegex(), "*")
                    .replace("[^\\x00-\\x7F]".toRegex(), "/")
            ).toString()
            val decimal = BigDecimal(result)
            result = decimal.setScale(8, BigDecimal.ROUND_HALF_UP).toPlainString()
            equalClicked = true
        } catch (e: Exception) {
            Toast.makeText(applicationContext, getString(R.string.calc_wrong_format), Toast.LENGTH_SHORT).show()
            return
        }
        if (result == "Infinity") {
            Toast.makeText(
                applicationContext,
                getString(R.string.calc_division_by_zero),
                Toast.LENGTH_SHORT
            ).show()
            textViewInputNumbers.text = input
        } else if (result.contains(".")) {
            result = result.replace("\\.?0*$".toRegex(), "")
            textViewInputNumbers.text = result
        }
    }

    private fun saveLastExpression(input: String) {
        val lastOfExpression = input[input.length - 1].toString() + ""
        if (input.length > 1) {
            if (lastOfExpression == ")") {
                lastExpression = ")"
                var numberOfCloseParenthesis = 1
                for (i in input.length - 2 downTo 0) {
                    if (numberOfCloseParenthesis > 0) {
                        val last = input[i].toString() + ""
                        if (last == ")") {
                            numberOfCloseParenthesis++
                        } else if (last == "(") {
                            numberOfCloseParenthesis--
                        }
                        lastExpression = last + lastExpression
                    } else if (defineLastCharacter(input[i].toString() + "") == IS_OPERAND) {
                        lastExpression = input[i].toString() + lastExpression
                        break
                    } else {
                        lastExpression = ""
                    }
                }
            } else if (defineLastCharacter(lastOfExpression + "") == IS_NUMBER) {
                lastExpression = lastOfExpression
                for (i in input.length - 2 downTo 0) {
                    val last = input[i].toString() + ""
                    if (defineLastCharacter(last) == IS_NUMBER || defineLastCharacter(last) == IS_DOT) {
                        lastExpression = last + lastExpression
                    } else if (defineLastCharacter(last) == IS_OPERAND) {
                        lastExpression = last + lastExpression
                        break
                    }
                    if (i == 0) {
                        lastExpression = ""
                    }
                }
            }
        }
    }

    private fun defineLastCharacter(lastCharacter: String): Int {
        try {
            lastCharacter.toInt()
            return IS_NUMBER
        } catch (e: NumberFormatException) {
        }
        if (lastCharacter == "+" || lastCharacter == "-" || lastCharacter == "x" || lastCharacter == "\u00F7" || lastCharacter == "%") return IS_OPERAND
        if (lastCharacter == "(") return IS_OPEN_PARENTHESIS
        if (lastCharacter == ")") return IS_CLOSE_PARENTHESIS
        return if (lastCharacter == ".") IS_DOT else -1
    }

    companion object {
        private const val EXCEPTION = -1
        private const val IS_NUMBER = 0
        private const val IS_OPERAND = 1
        private const val IS_OPEN_PARENTHESIS = 2
        private const val IS_CLOSE_PARENTHESIS = 3
        private const val IS_DOT = 4
    }
}
