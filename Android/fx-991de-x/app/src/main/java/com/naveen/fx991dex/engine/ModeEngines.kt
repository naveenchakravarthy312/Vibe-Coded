package com.naveen.fx991dex.engine

import kotlin.math.*

/**
 * Mode 3: Basis-N (32-bit signed integer arithmetic & bitwise logic across DEC, HEX, BIN, OCT).
 */
enum class NumberBase(val radix: Int, val label: String) {
    DEC(10, "Dec"),
    HEX(16, "Hex"),
    BIN(2, "Bin"),
    OCT(8, "Oct")
}

object BaseNEngine {
    fun formatValue(value: Int, base: NumberBase): String {
        return when (base) {
            NumberBase.DEC -> value.toString()
            NumberBase.HEX -> Integer.toHexString(value).uppercase()
            NumberBase.BIN -> Integer.toBinaryString(value)
            NumberBase.OCT -> Integer.toOctalString(value)
        }
    }

    fun evaluate(expression: String, base: NumberBase): Result<Int> {
        val cleaned = expression.trim()
        if (cleaned.isEmpty()) return Result.success(0)
        return try {
            val tokens = tokenize(cleaned)
            val parser = Parser(tokens, base)
            val result = parser.parseOr()
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Syntax FEHLER"))
        }
    }

    private fun tokenize(input: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c.isWhitespace() -> i++
                c in listOf('+', '-', '×', '*', '÷', '/', '(', ')') -> {
                    out += c.toString(); i++
                }
                else -> {
                    val start = i
                    while (i < input.length && !input[i].isWhitespace() &&
                        input[i] !in listOf('+', '-', '×', '*', '÷', '/', '(', ')')
                    ) i++
                    out += input.substring(start, i)
                }
            }
        }
        return out
    }

    private class Parser(private val tokens: List<String>, private val base: NumberBase) {
        private var pos = 0
        private fun peek() = tokens.getOrNull(pos)
        private fun consume() = tokens[pos++]

        fun parseOr(): Int {
            var left = parseXor()
            while (peek()?.equals("OR", true) == true) {
                consume()
                left = left or parseXor()
            }
            return left
        }

        private fun parseXor(): Int {
            var left = parseAnd()
            while (true) {
                val t = peek()?.uppercase() ?: break
                when (t) {
                    "XOR" -> { consume(); left = left xor parseAnd() }
                    "XNOR" -> { consume(); left = (left xor parseAnd()).inv() }
                    else -> return left
                }
            }
            return left
        }

        private fun parseAnd(): Int {
            var left = parseAddSub()
            while (peek()?.equals("AND", true) == true) {
                consume()
                left = left and parseAddSub()
            }
            return left
        }

        private fun parseAddSub(): Int {
            var left = parseMulDiv()
            while (true) {
                when (peek()) {
                    "+" -> { consume(); left += parseMulDiv() }
                    "-" -> { consume(); left -= parseMulDiv() }
                    else -> return left
                }
            }
        }

        private fun parseMulDiv(): Int {
            var left = parseUnary()
            while (true) {
                when (peek()) {
                    "×", "*" -> { consume(); left *= parseUnary() }
                    "÷", "/" -> {
                        consume()
                        val r = parseUnary()
                        if (r == 0) throw ArithmeticException("Division durch Null")
                        left /= r
                    }
                    else -> return left
                }
            }
        }

        private fun parseUnary(): Int {
            return when (peek()?.uppercase()) {
                "NOT" -> { consume(); parseUnary().inv() }
                "NEG", "-" -> { consume(); -parseUnary() }
                "+" -> { consume(); parseUnary() }
                else -> parsePrimary()
            }
        }

        private fun parsePrimary(): Int {
            val t = consume()
            if (t == "(") {
                val v = parseOr()
                if (peek() == ")") consume()
                return v
            }
            return java.lang.Long.parseUnsignedLong(t.uppercase(), base.radix).toInt()
        }
    }
}

/**
 * Mode 4: Matrizen (up to 4x4 real matrices: +, -, *, Det, Trn, Inverse).
 */
object MatrixEngine {
    fun empty(rows: Int, cols: Int): Array<DoubleArray> =
        Array(rows) { DoubleArray(cols) }

    fun identity(n: Int): Array<DoubleArray> =
        Array(n) { r -> DoubleArray(n) { c -> if (r == c) 1.0 else 0.0 } }

    fun add(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
        if (a.size != b.size || a[0].size != b[0].size) throw IllegalArgumentException("Dimension FEHLER")
        return Array(a.size) { r -> DoubleArray(a[0].size) { c -> a[r][c] + b[r][c] } }
    }

    fun scale(a: Array<DoubleArray>, k: Double): Array<DoubleArray> =
        Array(a.size) { r -> DoubleArray(a[0].size) { c -> a[r][c] * k } }

    fun multiply(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
        val m = a.size; val n = a[0].size; val p = b[0].size
        if (n != b.size) throw IllegalArgumentException("Dimension FEHLER")
        return Array(m) { r ->
            DoubleArray(p) { c ->
                var s = 0.0
                for (k in 0 until n) s += a[r][k] * b[k][c]
                s
            }
        }
    }

    fun transpose(a: Array<DoubleArray>): Array<DoubleArray> {
        val rows = a.size; val cols = a[0].size
        return Array(cols) { r -> DoubleArray(rows) { c -> a[c][r] } }
    }

    fun determinant(a: Array<DoubleArray>): Double {
        val n = a.size
        if (n == 0 || a[0].size != n) throw IllegalArgumentException("Dimension FEHLER")
        val m = Array(n) { r -> a[r].copyOf() }
        var det = 1.0
        for (i in 0 until n) {
            var pivot = i
            for (r in i + 1 until n) {
                if (abs(m[r][i]) > abs(m[pivot][i])) pivot = r
            }
            if (abs(m[pivot][i]) < 1e-13) return 0.0
            if (pivot != i) {
                val tmp = m[i]; m[i] = m[pivot]; m[pivot] = tmp
                det = -det
            }
            det *= m[i][i]
            for (r in i + 1 until n) {
                val f = m[r][i] / m[i][i]
                for (c in i until n) m[r][c] -= f * m[i][c]
            }
        }
        return det
    }

    fun inverse(a: Array<DoubleArray>): Array<DoubleArray> {
        val n = a.size
        if (n == 0 || a[0].size != n) throw IllegalArgumentException("Dimension FEHLER")
        val aug = Array(n) { r ->
            DoubleArray(2 * n) { c ->
                if (c < n) a[r][c] else if (c - n == r) 1.0 else 0.0
            }
        }
        for (i in 0 until n) {
            var pivot = i
            for (r in i + 1 until n) {
                if (abs(aug[r][i]) > abs(aug[pivot][i])) pivot = r
            }
            if (abs(aug[pivot][i]) < 1e-12) throw ArithmeticException("Math FEHLER")
            val tmp = aug[i]; aug[i] = aug[pivot]; aug[pivot] = tmp
            val div = aug[i][i]
            for (c in 0 until 2 * n) aug[i][c] /= div
            for (r in 0 until n) {
                if (r != i) {
                    val f = aug[r][i]
                    for (c in 0 until 2 * n) aug[r][c] -= f * aug[i][c]
                }
            }
        }
        return Array(n) { r -> DoubleArray(n) { c -> aug[r][c + n] } }
    }
}

/**
 * Mode 5: Vektorrechnung (2D / 3D vectors).
 */
object VectorEngine {
    fun add(a: DoubleArray, b: DoubleArray): DoubleArray {
        if (a.size != b.size) throw IllegalArgumentException("Dimension FEHLER")
        return DoubleArray(a.size) { i -> a[i] + b[i] }
    }

    fun scale(a: DoubleArray, k: Double): DoubleArray = DoubleArray(a.size) { i -> a[i] * k }

    fun dotProduct(a: DoubleArray, b: DoubleArray): Double {
        if (a.size != b.size) throw IllegalArgumentException("Dimension FEHLER")
        var s = 0.0
        for (i in a.indices) s += a[i] * b[i]
        return s
    }

    fun crossProduct(a: DoubleArray, b: DoubleArray): DoubleArray {
        val ax = a.getOrElse(0) { 0.0 }; val ay = a.getOrElse(1) { 0.0 }; val az = a.getOrElse(2) { 0.0 }
        val bx = b.getOrElse(0) { 0.0 }; val by = b.getOrElse(1) { 0.0 }; val bz = b.getOrElse(2) { 0.0 }
        return doubleArrayOf(
            ay * bz - az * by,
            az * bx - ax * bz,
            ax * by - ay * bx
        )
    }

    fun magnitude(a: DoubleArray): Double = sqrt(a.sumOf { it * it })

    fun unitVector(a: DoubleArray): DoubleArray {
        val m = magnitude(a)
        if (m < 1e-14) throw ArithmeticException("Math FEHLER")
        return DoubleArray(a.size) { i -> a[i] / m }
    }

    fun angleRadians(a: DoubleArray, b: DoubleArray): Double {
        val ma = magnitude(a); val mb = magnitude(b)
        if (ma < 1e-14 || mb < 1e-14) throw ArithmeticException("Math FEHLER")
        val cosT = (dotProduct(a, b) / (ma * mb)).coerceIn(-1.0, 1.0)
        return acos(cosT)
    }
}

/**
 * Mode 10 (Gleichung/Funkt.) & Mode 11 (Ungleichungen) & Mode 13 (Verhältnisse)
 */
sealed class LinearSystemSolution {
    data class Unique(val values: List<Double>) : LinearSystemSolution()
    data object Infinite : LinearSystemSolution()
    data object None : LinearSystemSolution()
}

data class PolyRoot(val real: Double, val imag: Double) {
    val isReal: Boolean get() = abs(imag) < 1e-7
}

data class PolynomialSolution(
    val roots: List<PolyRoot>,
    val vertexX: Double? = null,
    val vertexY: Double? = null,
    val isMinimum: Boolean? = null
)

object EquationInequalityEngine {

    /**
     * Solves n×n linear system Ax = b (n = 2..4) with partial pivoting.
     */
    fun solveLinearSystem(matrix: Array<DoubleArray>, rhs: DoubleArray): LinearSystemSolution {
        val n = matrix.size
        val aug = Array(n) { r -> DoubleArray(n + 1) { c -> if (c < n) matrix[r][c] else rhs[r] } }
        var row = 0
        for (col in 0 until n) {
            var pivot = row
            for (r in row until n) {
                if (abs(aug[r][col]) > abs(aug[pivot][col])) pivot = r
            }
            if (abs(aug[pivot][col]) < 1e-11) continue
            val tmp = aug[row]; aug[row] = aug[pivot]; aug[pivot] = tmp
            val div = aug[row][col]
            for (c in col..n) aug[row][c] /= div
            for (r in 0 until n) {
                if (r != row) {
                    val f = aug[r][col]
                    for (c in col..n) aug[r][c] -= f * aug[row][c]
                }
            }
            row++
        }
        // Check inconsistency
        for (r in row until n) {
            val allZero = (0 until n).all { abs(aug[r][it]) < 1e-9 }
            if (allZero && abs(aug[r][n]) > 1e-9) return LinearSystemSolution.None
        }
        if (row < n) return LinearSystemSolution.Infinite
        return LinearSystemSolution.Unique((0 until n).map { aug[it][n] })
    }

    /**
     * Solves polynomial a_n x^n + ... + a_0 = 0 (degree 2 to 4).
     * coeffs ordered highest degree first: [a, b, c] for ax² + bx + c = 0.
     */
    fun solvePolynomial(coeffs: DoubleArray): PolynomialSolution {
        // Strip leading zeros
        var start = 0
        while (start < coeffs.lastIndex && abs(coeffs[start]) < 1e-14) start++
        val c = coeffs.copyOfRange(start, coeffs.size)
        val deg = c.lastIndex
        if (deg <= 0) return PolynomialSolution(emptyList())
        if (deg == 1) {
            return PolynomialSolution(listOf(PolyRoot(-c[1] / c[0], 0.0)))
        }
        if (deg == 2) {
            val a = c[0]; val b = c[1]; val d = c[2]
            val disc = b * b - 4.0 * a * d
            val vx = -b / (2.0 * a)
            val vy = a * vx * vx + b * vx + d
            val roots = if (disc >= -1e-12) {
                val sd = sqrt(max(0.0, disc))
                listOf(PolyRoot((-b + sd) / (2.0 * a), 0.0), PolyRoot((-b - sd) / (2.0 * a), 0.0))
            } else {
                val sd = sqrt(-disc) / (2.0 * a)
                listOf(PolyRoot(-b / (2.0 * a), abs(sd)), PolyRoot(-b / (2.0 * a), -abs(sd)))
            }
            return PolynomialSolution(roots, vx, vy, a > 0.0)
        }

        // Normalize monic polynomial for Durand-Kerner complex root finder (deg 3 & 4)
        val lead = c[0]
        val monic = DoubleArray(c.size) { c[it] / lead }
        val r = 1.0 + monic.drop(1).maxOf { abs(it) }
        val rootsRe = DoubleArray(deg) { k -> r * cos(2.0 * PI * k / deg + 0.19) }
        val rootsIm = DoubleArray(deg) { k -> r * sin(2.0 * PI * k / deg + 0.19) }

        repeat(400) {
            var maxDelta = 0.0
            for (i in 0 until deg) {
                // Evaluate P(z_i)
                var pRe = 1.0; var pIm = 0.0
                val zRe = rootsRe[i]; val zIm = rootsIm[i]
                for (k in 1..deg) {
                    val nr = pRe * zRe - pIm * zIm + monic[k]
                    val ni = pRe * zIm + pIm * zRe
                    pRe = nr; pIm = ni
                }
                // Product (z_i - z_j) for j != i
                var dRe = 1.0; var dIm = 0.0
                for (j in 0 until deg) {
                    if (j == i) continue
                    val dr = zRe - rootsRe[j]; val di = zIm - rootsIm[j]
                    val nr = dRe * dr - dIm * di
                    val ni = dRe * di + dIm * dr
                    dRe = nr; dIm = ni
                }
                val denom = dRe * dRe + dIm * dIm
                if (denom > 1e-30) {
                    val stepRe = (pRe * dRe + pIm * dIm) / denom
                    val stepIm = (pIm * dRe - pRe * dIm) / denom
                    rootsRe[i] -= stepRe
                    rootsIm[i] -= stepIm
                    maxDelta = max(maxDelta, hypot(stepRe, stepIm))
                }
            }
            if (maxDelta < 1e-13) return@repeat
        }

        val finalRoots = (0 until deg).map { i ->
            val im = if (abs(rootsIm[i]) < 1e-6) 0.0 else rootsIm[i]
            val re = if (abs(rootsRe[i]) < 1e-11) 0.0 else rootsRe[i]
            PolyRoot(re, im)
        }.sortedWith(compareByDescending<PolyRoot> { it.isReal }.thenByDescending { it.real })

        return PolynomialSolution(finalRoots)
    }

    enum class InequalityOp(val symbol: String) {
        GT("> 0"),
        LT("< 0"),
        GE("≥ 0"),
        LE("≤ 0")
    }

    /**
     * Mode 11: Solves polynomial inequality P(x) op 0 (degree 2..4) and returns German interval text.
     */
    fun solveInequality(coeffs: DoubleArray, op: InequalityOp): String {
        val evalP: (Double) -> Double = { x ->
            var acc = 0.0
            for (c in coeffs) acc = acc * x + c
            acc
        }
        val check: (Double) -> Boolean = { v ->
            when (op) {
                InequalityOp.GT -> v > 1e-9
                InequalityOp.LT -> v < -1e-9
                InequalityOp.GE -> v >= -1e-9
                InequalityOp.LE -> v <= 1e-9
            }
        }
        val inclusive = op == InequalityOp.GE || op == InequalityOp.LE
        val sol = solvePolynomial(coeffs)
        val realRoots = sol.roots.filter { it.isReal }.map { it.real }.sorted()
            .fold(mutableListOf<Double>()) { acc, r ->
                if (acc.isEmpty() || abs(acc.last() - r) > 1e-5) acc.add(r)
                acc
            }

        if (realRoots.isEmpty()) {
            return if (check(evalP(0.0))) "Alle reellen Zahlen" else "Keine Lösung"
        }

        val intervals = mutableListOf<Pair<Double?, Double?>>()
        val isolatedPoints = mutableListOf<Double>()

        // Test (-inf, r0)
        if (check(evalP(realRoots.first() - 1.0))) {
            intervals += null to realRoots.first()
        }
        // Test between consecutive roots
        for (i in 0 until realRoots.lastIndex) {
            val mid = 0.5 * (realRoots[i] + realRoots[i + 1])
            if (check(evalP(mid))) {
                intervals += realRoots[i] to realRoots[i + 1]
            }
        }
        // Test (rLast, +inf)
        if (check(evalP(realRoots.last() + 1.0))) {
            intervals += realRoots.last() to null
        }

        // Merge adjacent intervals if inclusive
        val merged = mutableListOf<Pair<Double?, Double?>>()
        for (iv in intervals) {
            if (inclusive && merged.isNotEmpty() && merged.last().second != null && iv.first != null &&
                abs(merged.last().second!! - iv.first!!) < 1e-5
            ) {
                val prev = merged.removeAt(merged.lastIndex)
                merged += prev.first to iv.second
            } else {
                merged += iv
            }
        }

        if (inclusive) {
            for (r in realRoots) {
                val covered = merged.any { (lo, hi) ->
                    (lo == null || r >= lo - 1e-5) && (hi == null || r <= hi + 1e-5)
                }
                if (!covered) isolatedPoints += r
            }
        }

        if (merged.size == 1 && merged[0].first == null && merged[0].second == null) {
            return "Alle reellen Zahlen"
        }
        if (merged.isEmpty() && isolatedPoints.isEmpty()) {
            return "Keine Lösung"
        }

        val fmt = { d: Double -> SymbolicFormatter.formatGermanNumber(d) }
        val sym = if (inclusive) "≤" else "<"
        val parts = mutableListOf<String>()
        for ((lo, hi) in merged) {
            val text = when {
                lo == null && hi != null -> "x $sym ${fmt(hi)}"
                lo != null && hi == null -> "${fmt(lo)} $sym x"
                lo != null && hi != null -> "${fmt(lo)} $sym x $sym ${fmt(hi)}"
                else -> "Alle reellen Zahlen"
            }
            parts += text
        }
        for (pt in isolatedPoints) parts += "x = ${fmt(pt)}"
        return parts.joinToString(",  ")
    }

    /**
     * Mode 13: Ratio solver a:b = X:d (solveLeft = true) or a:b = c:X (solveLeft = false).
     */
    fun solveRatio(a: Double, b: Double, known: Double, solveFirstRight: Boolean): SymbolicValue {
        return try {
            val ra = Rational.fromDecimalString(a.toString())
            val rb = Rational.fromDecimalString(b.toString())
            val rk = Rational.fromDecimalString(known.toString())
            if (ra != null && rb != null && rk != null) {
                val res = if (solveFirstRight) {
                    // a/b = X/d => X = a*d / b
                    (ra * rk) / rb
                } else {
                    // a/b = c/X => X = b*c / a
                    (rb * rk) / ra
                }
                SymbolicValue.ExactRational(res)
            } else {
                val res = if (solveFirstRight) {
                    if (b == 0.0) throw ArithmeticException()
                    a * known / b
                } else {
                    if (a == 0.0) throw ArithmeticException()
                    b * known / a
                }
                SymbolicValue.ofDouble(res)
            }
        } catch (_: Exception) {
            SymbolicValue.Error("Math FEHLER")
        }
    }
}
