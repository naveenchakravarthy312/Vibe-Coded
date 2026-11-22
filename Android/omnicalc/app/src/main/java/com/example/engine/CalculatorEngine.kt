package com.example.engine

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.*

object CalculatorEngine {

    enum class AngleMode {
        DEG, RAD
    }

    sealed class EvalResult {
        data class Success(val value: Double, val formatted: String) : EvalResult()
        data class Error(val message: String) : EvalResult()
    }

    /**
     * Evaluates a mathematical expression string.
     */
    fun evaluate(expression: String, angleMode: AngleMode = AngleMode.DEG): EvalResult {
        val sanitized = sanitize(expression)
        if (sanitized.isBlank()) {
            return EvalResult.Success(0.0, "0")
        }

        return try {
            val parser = ExpressionParser(sanitized, angleMode)
            val result = parser.parse()
            if (result.isNaN()) {
                EvalResult.Error("Undefined result")
            } else if (result.isInfinite()) {
                EvalResult.Error("Cannot divide by zero")
            } else {
                EvalResult.Success(result, formatResult(result))
            }
        } catch (e: ArithmeticException) {
            EvalResult.Error(e.message ?: "Calculation error")
        } catch (e: Exception) {
            EvalResult.Error(e.message ?: "Invalid syntax")
        }
    }

    /**
     * Clean and normalize user-entered symbols.
     */
    private fun sanitize(raw: String): String {
        return raw
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace("–", "-")
            .replace("π", "PI")
            .replace("√", "sqrt")
            .replace("∛", "cbrt")
            .trim()
    }

    fun formatResult(value: Double): String {
        if (value.isNaN()) return "Undefined"
        if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"

        // Handle exact zero or very close to zero from trig rounding
        if (abs(value) < 1e-12) return "0"

        return try {
            val bd = BigDecimal(value.toString())
            // Check if scientific notation is needed for very large/small numbers
            if (abs(value) >= 1e11 || (abs(value) < 1e-5 && abs(value) > 0.0)) {
                val symbols = DecimalFormatSymbols(Locale.US)
                val sciFormat = DecimalFormat("0.######E0", symbols)
                sciFormat.format(value)
            } else {
                val rounded = bd.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros()
                rounded.toPlainString()
            }
        } catch (e: Exception) {
            val symbols = DecimalFormatSymbols(Locale.US)
            val df = DecimalFormat("#.########", symbols)
            df.format(value)
        }
    }

    private class ExpressionParser(
        private val str: String,
        private val angleMode: AngleMode
    ) {
        private var pos = -1
        private var ch = ' '

        private fun nextChar() {
            pos++
            ch = if (pos < str.length) str[pos] else '\u0000'
        }

        private fun eat(charToEat: Char): Boolean {
            while (ch == ' ') nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < str.length) {
                throw IllegalArgumentException("Unexpected: '$ch'")
            }
            return x
        }

        // Grammar:
        // expression = term | expression `+` term | expression `-` term
        // term = unary | term `*` unary | term `/` unary | term implicit-mul power
        // unary = `+` unary | `-` unary | power
        // power = postfix | postfix `^` unary
        // postfix = primary | postfix `!` | postfix `%`
        // primary = `(` expression `)` | number (with optional E[+-]digits) | constant | functionName primary
        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+') -> x += parseTerm()
                    eat('-') -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parseUnary()
            while (true) {
                while (ch == ' ') nextChar()
                when {
                    eat('*') -> x *= parseUnary()
                    eat('/') -> {
                        val divisor = parseUnary()
                        if (abs(divisor) < 1e-15) throw ArithmeticException("Cannot divide by zero")
                        x /= divisor
                    }
                    ch == '(' || ch in 'a'..'z' || ch in 'A'..'Z' -> {
                        // Implicit multiplication: e.g. 2(3), 5sin(30), 2π
                        x *= parsePower()
                    }
                    else -> return x
                }
            }
        }

        private fun parseUnary(): Double {
            if (eat('+')) return +parseUnary()
            if (eat('-')) return -parseUnary()
            return parsePower()
        }

        private fun parsePower(): Double {
            var x = parsePostfix()
            if (eat('^')) {
                val exp = parseUnary()
                x = x.pow(exp)
            }
            return x
        }

        private fun parsePostfix(): Double {
            var x = parsePrimary()
            while (true) {
                when {
                    eat('!') -> x = factorial(x)
                    eat('%') -> x /= 100.0
                    else -> break
                }
            }
            return x
        }

        private fun parsePrimary(): Double {
            while (ch == ' ') nextChar()
            val startPos = pos

            if (eat('(')) {
                val x = parseExpression()
                eat(')') // auto-close if omitted
                return x
            } else if ((ch in '0'..'9') || ch == '.') {
                while ((ch in '0'..'9') || ch == '.') nextChar()
                // Support scientific notation (e.g. 1E12, 1.5E-6)
                if (ch == 'E') {
                    val savedPos = pos
                    val savedCh = ch
                    nextChar()
                    if (ch == '+' || ch == '-') nextChar()
                    if (ch in '0'..'9') {
                        while (ch in '0'..'9') nextChar()
                    } else {
                        pos = savedPos
                        ch = savedCh
                    }
                }
                val numStr = str.substring(startPos, pos)
                return numStr.toDouble()
            } else if (ch in 'a'..'z' || ch in 'A'..'Z') {
                while (ch in 'a'..'z' || ch in 'A'..'Z') nextChar()
                val func = str.substring(startPos, pos).lowercase(Locale.US)
                return when (func) {
                    "pi" -> Math.PI
                    "e" -> Math.E
                    else -> {
                        val arg = if (ch == '(') parsePrimary() else parseUnary()
                        applyFunction(func, arg)
                    }
                }
            } else {
                throw IllegalArgumentException("Unexpected character: '$ch'")
            }
        }

        private fun applyFunction(name: String, arg: Double): Double {
            return when (name) {
                "sqrt" -> {
                    if (arg < 0) throw ArithmeticException("Negative square root")
                    sqrt(arg)
                }
                "cbrt" -> cbrt(arg)
                "sin" -> {
                    val rad = if (angleMode == AngleMode.DEG) Math.toRadians(arg) else arg
                    sin(rad)
                }
                "cos" -> {
                    val rad = if (angleMode == AngleMode.DEG) Math.toRadians(arg) else arg
                    cos(rad)
                }
                "tan" -> {
                    val rad = if (angleMode == AngleMode.DEG) Math.toRadians(arg) else arg
                    tan(rad)
                }
                "asin" -> {
                    if (arg < -1.0 || arg > 1.0) throw ArithmeticException("Domain error for asin")
                    val res = asin(arg)
                    if (angleMode == AngleMode.DEG) Math.toDegrees(res) else res
                }
                "acos" -> {
                    if (arg < -1.0 || arg > 1.0) throw ArithmeticException("Domain error for acos")
                    val res = acos(arg)
                    if (angleMode == AngleMode.DEG) Math.toDegrees(res) else res
                }
                "atan" -> {
                    val res = atan(arg)
                    if (angleMode == AngleMode.DEG) Math.toDegrees(res) else res
                }
                "ln" -> {
                    if (arg <= 0) throw ArithmeticException("Domain error for ln")
                    ln(arg)
                }
                "log" -> {
                    if (arg <= 0) throw ArithmeticException("Domain error for log")
                    log10(arg)
                }
                "abs" -> abs(arg)
                else -> throw IllegalArgumentException("Unknown function: $name")
            }
        }

        private fun factorial(n: Double): Double {
            if (n < 0 || n != floor(n)) throw ArithmeticException("Factorial requires non-negative integer")
            if (n > 170) return Double.POSITIVE_INFINITY
            var res = 1.0
            val limit = n.toInt()
            for (i in 2..limit) {
                res *= i
            }
            return res
        }
    }
}
