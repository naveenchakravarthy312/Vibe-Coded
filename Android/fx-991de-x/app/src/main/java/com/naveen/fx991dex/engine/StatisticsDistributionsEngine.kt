package com.naveen.fx991dex.engine

import kotlin.math.*

/**
 * Mode 6: Statistik (1-Variable & 2-Variable Regressions)
 */
enum class StatType(val germanLabel: String, val isTwoVar: Boolean) {
    ONE_VAR("1: 1-Variable", false),
    LINEAR("2: y = a + bx", true),
    QUADRATIC("3: y = a + bx + cx²", true),
    LOGARITHMIC("4: y = a + b·ln(x)", true),
    EXPONENTIAL("5: y = a·e^(bx)", true)
}

data class StatRow(val x: Double, val y: Double = 0.0, val freq: Double = 1.0)

data class OneVarStats(
    val n: Double,
    val mean: Double,
    val sumX: Double,
    val sumX2: Double,
    val popVariance: Double,
    val popStdDev: Double,
    val sampleVariance: Double,
    val sampleStdDev: Double,
    val minX: Double,
    val q1: Double,
    val median: Double,
    val q3: Double,
    val maxX: Double
)

data class RegressionResult(
    val type: StatType,
    val a: Double,
    val b: Double,
    val c: Double? = null,
    val r: Double? = null,
    val r2: Double? = null
)

object StatisticsEngine {

    fun computeOneVar(rows: List<StatRow>): OneVarStats? {
        val valid = rows.filter { it.freq > 0.0 }
        if (valid.isEmpty()) return null
        val n = valid.sumOf { it.freq }
        if (n <= 0.0) return null
        val sumX = valid.sumOf { it.x * it.freq }
        val sumX2 = valid.sumOf { it.x * it.x * it.freq }
        val mean = sumX / n
        val popVar = max(0.0, (sumX2 / n) - mean * mean)
        val sampleVar = if (n > 1.0) max(0.0, (sumX2 - n * mean * mean) / (n - 1.0)) else 0.0

        // Expand sorted values (capped for safety if large frequencies)
        val expanded = mutableListOf<Double>()
        for (row in valid) {
            val count = row.freq.roundToInt().coerceIn(1, 500)
            repeat(count) { expanded += row.x }
        }
        expanded.sort()
        val median = percentile(expanded, 0.5)
        val lowerHalf = expanded.subList(0, expanded.size / 2)
        val upperHalf = expanded.subList((expanded.size + 1) / 2, expanded.size)
        val q1 = if (lowerHalf.isNotEmpty()) percentile(lowerHalf, 0.5) else expanded.first()
        val q3 = if (upperHalf.isNotEmpty()) percentile(upperHalf, 0.5) else expanded.last()

        return OneVarStats(
            n = n,
            mean = mean,
            sumX = sumX,
            sumX2 = sumX2,
            popVariance = popVar,
            popStdDev = sqrt(popVar),
            sampleVariance = sampleVar,
            sampleStdDev = sqrt(sampleVar),
            minX = expanded.first(),
            q1 = q1,
            median = median,
            q3 = q3,
            maxX = expanded.last()
        )
    }

    private fun percentile(sorted: List<Double>, p: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else 0.5 * (sorted[mid - 1] + sorted[mid])
    }

    fun computeRegression(rows: List<StatRow>, type: StatType): RegressionResult? {
        if (rows.size < 2) return null
        return when (type) {
            StatType.ONE_VAR -> null
            StatType.LINEAR -> linearFit(rows.map { it.x to it.y }, type)
            StatType.LOGARITHMIC -> {
                if (rows.any { it.x <= 0.0 }) return null
                linearFit(rows.map { ln(it.x) to it.y }, type)
            }
            StatType.EXPONENTIAL -> {
                if (rows.any { it.y <= 0.0 }) return null
                val lin = linearFit(rows.map { it.x to ln(it.y) }, type) ?: return null
                lin.copy(a = exp(lin.a))
            }
            StatType.QUADRATIC -> quadraticFit(rows)
        }
    }

    private fun linearFit(points: List<Pair<Double, Double>>, type: StatType): RegressionResult? {
        val n = points.size.toDouble()
        val sx = points.sumOf { it.first }
        val sy = points.sumOf { it.second }
        val sxx = points.sumOf { it.first * it.first }
        val syy = points.sumOf { it.second * it.second }
        val sxy = points.sumOf { it.first * it.second }
        val den = n * sxx - sx * sx
        if (abs(den) < 1e-14) return null
        val b = (n * sxy - sx * sy) / den
        val a = (sy - b * sx) / n
        val rDen = sqrt(max(0.0, den * (n * syy - sy * sy)))
        val r = if (rDen > 1e-14) (n * sxy - sx * sy) / rDen else 0.0
        return RegressionResult(type = type, a = a, b = b, r = r, r2 = r * r)
    }

    private fun quadraticFit(rows: List<StatRow>): RegressionResult? {
        if (rows.size < 3) return null
        val n = rows.size.toDouble()
        val sx = rows.sumOf { it.x }
        val sx2 = rows.sumOf { it.x.pow(2) }
        val sx3 = rows.sumOf { it.x.pow(3) }
        val sx4 = rows.sumOf { it.x.pow(4) }
        val sy = rows.sumOf { it.y }
        val sxy = rows.sumOf { it.x * it.y }
        val sx2y = rows.sumOf { it.x * it.x * it.y }

        val mat = arrayOf(
            doubleArrayOf(n, sx, sx2),
            doubleArrayOf(sx, sx2, sx3),
            doubleArrayOf(sx2, sx3, sx4)
        )
        val rhs = doubleArrayOf(sy, sxy, sx2y)
        val sol = EquationInequalityEngine.solveLinearSystem(mat, rhs)
        if (sol !is LinearSystemSolution.Unique) return null
        val (a, b, c) = sol.values
        val meanY = sy / n
        val ssTot = rows.sumOf { (it.y - meanY).pow(2) }
        val ssRes = rows.sumOf { (it.y - (a + b * it.x + c * it.x * it.x)).pow(2) }
        val r2 = if (ssTot > 1e-14) 1.0 - ssRes / ssTot else 1.0
        return RegressionResult(StatType.QUADRATIC, a = a, b = b, c = c, r2 = r2)
    }
}

/**
 * Mode 7: Verteilungsfunktionen (Normal PD/CD/Inverse, Binomial PD/CD, Poisson PD/CD).
 */
enum class DistributionType(val germanName: String) {
    NORMAL_PD("1: Normal-Dichte"),
    NORMAL_CD("2: Kumul. Normal-V"),
    INVERSE_NORMAL("3: Invers Normal"),
    BINOMIAL_PD("4: Binomial-Dichte"),
    BINOMIAL_CD("5: Kumul. Binomial-V"),
    POISSON_PD("6: Poisson-Dichte"),
    POISSON_CD("7: Kumul. Poisson-V")
}

object DistributionEngine {

    fun normalPd(x: Double, sigma: Double, mu: Double): Double {
        if (sigma <= 0.0) throw ArithmeticException("Math FEHLER")
        val z = (x - mu) / sigma
        return exp(-0.5 * z * z) / (sigma * sqrt(2.0 * PI))
    }

    fun normalCdfStandard(z: Double): Double = 0.5 * (1.0 + erf(z / sqrt(2.0)))

    fun normalCd(lower: Double, upper: Double, sigma: Double, mu: Double): Double {
        if (sigma <= 0.0) throw ArithmeticException("Math FEHLER")
        if (upper < lower) return 0.0
        return normalCdfStandard((upper - mu) / sigma) - normalCdfStandard((lower - mu) / sigma)
    }

    /**
     * Acklam's rational approximation for standard normal inverse CDF (accurate to ~1e-9).
     */
    fun inverseNormal(area: Double, sigma: Double, mu: Double): Double {
        if (area <= 0.0 || area >= 1.0 || sigma <= 0.0) throw ArithmeticException("Math FEHLER")
        val a = doubleArrayOf(
            -3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02,
            1.383577518672690e+02, -3.066479806614716e+01, 2.506628277459239e+00
        )
        val b = doubleArrayOf(
            -5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02,
            6.680131188771972e+01, -1.328068155288572e+01
        )
        val c = doubleArrayOf(
            -7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00,
            -2.549732539343734e+00, 4.374664141464968e+00, 2.938163982698783e+00
        )
        val d = doubleArrayOf(
            7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00,
            3.754408661907416e+00
        )
        val pLow = 0.02425
        val pHigh = 1.0 - pLow
        val z = when {
            area < pLow -> {
                val q = sqrt(-2.0 * ln(area))
                (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) /
                    ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1.0)
            }
            area <= pHigh -> {
                val q = area - 0.5
                val r = q * q
                (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q /
                    (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1.0)
            }
            else -> {
                val q = sqrt(-2.0 * ln(1.0 - area))
                -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) /
                    ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1.0)
            }
        }
        return mu + sigma * z
    }

    fun binomialPd(k: Int, n: Int, p: Double): Double {
        if (n < 0 || p < 0.0 || p > 1.0) throw ArithmeticException("Math FEHLER")
        if (k < 0 || k > n) return 0.0
        if (p == 0.0) return if (k == 0) 1.0 else 0.0
        if (p == 1.0) return if (k == n) 1.0 else 0.0
        val logComb = logGamma(n + 1.0) - logGamma(k + 1.0) - logGamma(n - k + 1.0)
        return exp(logComb + k * ln(p) + (n - k) * ln(1.0 - p))
    }

    fun binomialCd(k: Int, n: Int, p: Double): Double {
        if (n < 0 || p < 0.0 || p > 1.0) throw ArithmeticException("Math FEHLER")
        var sum = 0.0
        for (i in 0..min(k, n)) sum += binomialPd(i, n, p)
        return sum.coerceIn(0.0, 1.0)
    }

    fun poissonPd(k: Int, lambda: Double): Double {
        if (lambda <= 0.0 || k < 0) return if (lambda == 0.0 && k == 0) 1.0 else 0.0
        return exp(-lambda + k * ln(lambda) - logGamma(k + 1.0))
    }

    fun poissonCd(k: Int, lambda: Double): Double {
        if (lambda < 0.0) throw ArithmeticException("Math FEHLER")
        if (k < 0) return 0.0
        var sum = 0.0
        for (i in 0..k) sum += poissonPd(i, lambda)
        return sum.coerceIn(0.0, 1.0)
    }

    /**
     * Abramowitz & Stegun 7.1.26 error function approximation (max error < 1.5e-7).
     */
    private fun erf(x: Double): Double {
        val sign = if (x < 0) -1.0 else 1.0
        val ax = abs(x)
        val t = 1.0 / (1.0 + 0.3275911 * ax)
        val poly = (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t
        return sign * (1.0 - poly * exp(-ax * ax))
    }

    /**
     * Lanczos approximation for ln(Gamma(z)).
     */
    private fun logGamma(z: Double): Double {
        val p = doubleArrayOf(
            676.5203681218851, -1259.1392167224028, 771.32342877765313,
            -176.61502916214059, 12.507343278686905, -0.13857109526572012,
            9.9843695780195716e-6, 1.5056327351493116e-7
        )
        val g = 7.0
        val zz = z - 1.0
        var x = 0.99999999999980993
        for (i in p.indices) x += p[i] / (zz + i + 1.0)
        val t = zz + g + 0.5
        return 0.5 * ln(2.0 * PI) + (zz + 0.5) * ln(t) - t + ln(x)
    }
}

/**
 * Mode 8: Tabellenkalkulation (Mini Spreadsheet A1:E45 with =formulas, Summe, Mittelwert, Min, Max).
 */
object SpreadsheetEngine {
    val columns = listOf("A", "B", "C", "D", "E")
    const val MAX_ROWS = 45

    fun cellId(col: Int, row: Int): String = "${columns[col]}${row + 1}"

    fun evaluateAll(rawCells: Map<String, String>): Map<String, Result<Double>> {
        val cache = mutableMapOf<String, Result<Double>>()
        val visiting = mutableSetOf<String>()

        fun evalCell(id: String): Result<Double> {
            cache[id]?.let { return it }
            if (id in visiting) return Result.failure(IllegalArgumentException("Zyklus FEHLER"))
            val raw = rawCells[id]?.trim().orEmpty()
            if (raw.isEmpty()) return Result.success(0.0)
            visiting.add(id)
            val res = try {
                if (raw.startsWith("=")) {
                    Result.success(evalFormula(raw.substring(1).trim(), ::evalCell))
                } else {
                    val v = raw.replace(',', '.').toDoubleOrNull()
                        ?: throw IllegalArgumentException("Syntax FEHLER")
                    Result.success(v)
                }
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                visiting.remove(id)
            }
            cache[id] = res
            return res
        }

        for (key in rawCells.keys) evalCell(key)
        return cache
    }

    private fun evalFormula(formula: String, resolveCell: (String) -> Result<Double>): Double {
        // Expand range functions Summe(A1:A5), Mittelwert(A1:B3), Min(A1:A5), Max(A1:A5)
        var expr = formula.replace(',', '.')
        val fnRegex = Regex("""(Summe|Mittelwert|Sum|Mean|Min|Max)\(([A-E]\d+):([A-E]\d+)\)""", RegexOption.IGNORE_CASE)
        expr = fnRegex.replace(expr) { m ->
            val fnName = m.groupValues[1].uppercase()
            val vals = expandRange(m.groupValues[2].uppercase(), m.groupValues[3].uppercase(), resolveCell)
            val computed = when (fnName) {
                "SUMME", "SUM" -> vals.sum()
                "MITTELWERT", "MEAN" -> if (vals.isEmpty()) 0.0 else vals.average()
                "MIN" -> vals.minOrNull() ?: 0.0
                "MAX" -> vals.maxOrNull() ?: 0.0
                else -> 0.0
            }
            computed.toString()
        }
        // Replace individual cell references like A1, B12
        val cellRegex = Regex("""\b([A-E])([1-9]|[1-3]\d|4[0-5])\b""", RegexOption.IGNORE_CASE)
        expr = cellRegex.replace(expr) { m ->
            val id = m.value.uppercase()
            val v = resolveCell(id).getOrThrow()
            "($v)"
        }
        return evalSimpleArithmetic(expr)
    }

    private fun expandRange(start: String, end: String, resolveCell: (String) -> Result<Double>): List<Double> {
        val c1 = columns.indexOf(start.substring(0, 1))
        val r1 = start.substring(1).toInt()
        val c2 = columns.indexOf(end.substring(0, 1))
        val r2 = end.substring(1).toInt()
        val out = mutableListOf<Double>()
        for (c in min(c1, c2)..max(c1, c2)) {
            for (r in min(r1, r2)..max(r1, r2)) {
                out += resolveCell("${columns[c]}$r").getOrThrow()
            }
        }
        return out
    }

    private fun evalSimpleArithmetic(expr: String): Double {
        val s = expr.replace(" ", "").replace("×", "*").replace("÷", "/")
        return ArithParser(s).parse()
    }

    private class ArithParser(private val s: String) {
        private var i = 0

        fun parse(): Double {
            val res = parseAdd()
            if (i < s.length) throw IllegalArgumentException("Syntax FEHLER")
            return res
        }

        private fun parseAdd(): Double {
            var v = parseMul()
            while (i < s.length && (s[i] == '+' || s[i] == '-')) {
                val op = s[i++]
                val r = parseMul()
                v = if (op == '+') v + r else v - r
            }
            return v
        }

        private fun parseMul(): Double {
            var v = parsePow()
            while (i < s.length && (s[i] == '*' || s[i] == '/')) {
                val op = s[i++]
                val r = parsePow()
                if (op == '/' && r == 0.0) throw ArithmeticException("Division durch Null")
                v = if (op == '*') v * r else v / r
            }
            return v
        }

        private fun parsePow(): Double {
            var v = parseAtom()
            if (i < s.length && s[i] == '^') {
                i++
                v = v.pow(parsePow())
            }
            return v
        }

        private fun parseAtom(): Double {
            if (i < s.length && s[i] == '-') { i++; return -parseAtom() }
            if (i < s.length && s[i] == '+') { i++; return parseAtom() }
            if (i < s.length && s[i] == '(') {
                i++
                val v = parseAdd()
                if (i < s.length && s[i] == ')') i++
                return v
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == 'E' || s[i] == 'e')) {
                if ((s[i] == 'E' || s[i] == 'e') && i + 1 < s.length && (s[i + 1] == '+' || s[i + 1] == '-')) {
                    i += 2
                } else {
                    i++
                }
            }
            if (start == i) throw IllegalArgumentException("Syntax FEHLER")
            return s.substring(start, i).toDouble()
        }
    }
}
