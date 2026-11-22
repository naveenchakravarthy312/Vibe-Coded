package com.naveen.fx991dex.engine

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.*

enum class AngleUnit(val badge: String, val germanName: String) {
    DEG("D", "Gradmaß (Deg)"),
    RAD("R", "Bogenmaß (Rad)"),
    GRAD("G", "Neugrad (Gra)")
}

enum class NumberFormatMode(val germanName: String) {
    NORM1("Norm 1"),
    NORM2("Norm 2"),
    FIX("Fix"),
    SCI("Sci")
}

enum class FractionFormat(val germanName: String) {
    IMPROPER("d/c"),
    MIXED("a b/c")
}

enum class ComplexFormat(val germanName: String) {
    RECTANGULAR("a+bi"),
    POLAR("r∠θ")
}

data class DisplayFormatConfig(
    val numberFormat: NumberFormatMode = NumberFormatMode.NORM1,
    val digits: Int = 4,
    val fractionFormat: FractionFormat = FractionFormat.IMPROPER,
    val complexFormat: ComplexFormat = ComplexFormat.RECTANGULAR,
    val engExponentOffset: Int? = null
)

/**
 * Exact symbolic + high-precision numerical value representation.
 */
sealed class SymbolicValue {

    abstract fun toDouble(): Double
    abstract fun isExactSymbolic(): Boolean

    data class ExactRational(val value: Rational) : SymbolicValue() {
        override fun toDouble(): Double = value.toDouble()
        override fun isExactSymbolic(): Boolean = !value.isInteger
    }

    /**
     * Exact radical form: constant + coeff * sqrt(radicand), where radicand > 1 is square-free.
     * Supports e.g. sqrt(2)/2, 2*sqrt(3), (1 + sqrt(5))/2.
     */
    data class Radical(
        val constant: Rational,
        val coeff: Rational,
        val radicand: BigInteger
    ) : SymbolicValue() {
        override fun toDouble(): Double =
            constant.toDouble() + coeff.toDouble() * sqrt(radicand.toDouble())
        override fun isExactSymbolic(): Boolean = true
    }

    /**
     * Exact pi multiple: coeff * pi (e.g. pi/3, -2*pi/3, pi).
     */
    data class PiMultiple(val coeff: Rational) : SymbolicValue() {
        override fun toDouble(): Double = coeff.toDouble() * PI
        override fun isExactSymbolic(): Boolean = true
    }

    data class Decimal(val value: BigDecimal) : SymbolicValue() {
        override fun toDouble(): Double = value.toDouble()
        override fun isExactSymbolic(): Boolean = false
    }

    data class ComplexVal(
        val real: Double,
        val imag: Double,
        val exactReal: SymbolicValue? = null,
        val exactImag: SymbolicValue? = null
    ) : SymbolicValue() {
        override fun toDouble(): Double = hypot(real, imag)
        override fun isExactSymbolic(): Boolean = exactReal != null || exactImag != null
    }

    data class PrimeFactorized(
        val negative: Boolean,
        val factors: List<Pair<BigInteger, Int>>,
        val original: BigInteger
    ) : SymbolicValue() {
        override fun toDouble(): Double = original.toDouble()
        override fun isExactSymbolic(): Boolean = true
    }

    data class DmsValue(
        val degrees: Double
    ) : SymbolicValue() {
        override fun toDouble(): Double = degrees
        override fun isExactSymbolic(): Boolean = false
    }

    data class BooleanResult(val truth: Boolean) : SymbolicValue() {
        override fun toDouble(): Double = if (truth) 1.0 else 0.0
        override fun isExactSymbolic(): Boolean = false
    }

    data class MatrixResult(val data: Array<DoubleArray>) : SymbolicValue() {
        override fun toDouble(): Double = 0.0
        override fun isExactSymbolic(): Boolean = true
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is MatrixResult) return false
            if (data.size != other.data.size) return false
            return data.indices.all { data[it].contentEquals(other.data[it]) }
        }
        override fun hashCode(): Int = data.contentDeepHashCode()
    }

    data class VectorResult(val components: DoubleArray) : SymbolicValue() {
        override fun toDouble(): Double = sqrt(components.sumOf { it * it })
        override fun isExactSymbolic(): Boolean = true
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is VectorResult) return false
            return components.contentEquals(other.components)
        }
        override fun hashCode(): Int = components.contentHashCode()
    }

    data class Error(val message: String) : SymbolicValue() {
        override fun toDouble(): Double = Double.NaN
        override fun isExactSymbolic(): Boolean = false
    }

    companion object {
        fun ofLong(v: Long): SymbolicValue = ExactRational(Rational.of(v))
        fun ofDouble(v: Double): SymbolicValue {
            if (v.isNaN() || v.isInfinite()) return Error("Math FEHLER")
            val cleaned = if (abs(v) < 1e-14) 0.0 else v
            val rounded = cleaned.roundToLong()
            if (abs(cleaned - rounded.toDouble()) < 1e-12 && abs(cleaned) < 1e12) {
                return ExactRational(Rational.of(rounded))
            }
            return Decimal(BigDecimal.valueOf(cleaned))
        }

        fun simplifyRadical(constant: Rational, coeff: Rational, radicand: BigInteger): SymbolicValue {
            if (coeff.isZero || radicand == BigInteger.ZERO) return ExactRational(constant)
            if (radicand < BigInteger.ZERO) return Error("Math FEHLER")
            val (out, inside) = Rational.simplifySquareRoot(radicand)
            val newCoeff = coeff * Rational.of(out, BigInteger.ONE)
            if (inside == BigInteger.ONE) {
                return ExactRational(constant + newCoeff)
            }
            return Radical(constant, newCoeff, inside)
        }

        fun simplifyPi(coeff: Rational): SymbolicValue {
            return if (coeff.isZero) ExactRational(Rational.ZERO) else PiMultiple(coeff)
        }
    }
}

/**
 * Formats SymbolicValue into either an exact Natural Textbook AST (MathSequence)
 * or a German-localized decimal string (comma as decimal separator).
 */
object SymbolicFormatter {

    fun formatGermanNumber(value: Double, config: DisplayFormatConfig = DisplayFormatConfig()): String {
        if (value.isNaN() || value.isInfinite()) return "Math FEHLER"
        val clean = if (abs(value) < 1e-14) 0.0 else value

        // Engineering notation override (ENG key)
        config.engExponentOffset?.let { targetExp ->
            val mantissa = clean / 10.0.pow(targetExp)
            val formattedMant = formatDecimalPlain(mantissa, 10)
            return "${formattedMant}×10^$targetExp"
        }

        return when (config.numberFormat) {
            NumberFormatMode.FIX -> {
                val bd = BigDecimal.valueOf(clean).setScale(config.digits.coerceIn(0, 9), RoundingMode.HALF_UP)
                bd.toPlainString().replace('.', ',')
            }
            NumberFormatMode.SCI -> {
                formatScientific(clean, config.digits.coerceIn(1, 10))
            }
            NumberFormatMode.NORM1 -> {
                if (clean != 0.0 && (abs(clean) < 1e-2 || abs(clean) >= 1e10)) {
                    formatScientific(clean, 10)
                } else {
                    formatDecimalPlain(clean, 10)
                }
            }
            NumberFormatMode.NORM2 -> {
                if (clean != 0.0 && (abs(clean) < 1e-9 || abs(clean) >= 1e10)) {
                    formatScientific(clean, 10)
                } else {
                    formatDecimalPlain(clean, 10)
                }
            }
        }
    }

    private fun formatDecimalPlain(value: Double, sigDigits: Int): String {
        if (value == 0.0) return "0"
        val bd = BigDecimal(value, MathContext(sigDigits, RoundingMode.HALF_UP)).stripTrailingZeros()
        val plain = bd.toPlainString()
        return plain.replace('.', ',')
    }

    private fun formatScientific(value: Double, sigDigits: Int): String {
        if (value == 0.0) return "0"
        val exp = floor(log10(abs(value))).toInt()
        val mant = value / 10.0.pow(exp)
        val mantStr = formatDecimalPlain(mant, sigDigits)
        return "${mantStr}×10^$exp"
    }

    fun formatDms(degrees: Double): String {
        if (!degrees.isFinite()) return "Math FEHLER"
        val neg = degrees < 0
        val totalCentiSeconds = BigDecimal.valueOf(abs(degrees) * 360000.0)
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
        val totalWholeSeconds = totalCentiSeconds / 100L
        val centiRem = (totalCentiSeconds % 100L).toInt()
        val d = totalWholeSeconds / 3600L
        val m = (totalWholeSeconds % 3600L) / 60L
        val sWhole = totalWholeSeconds % 60L
        val sRounded = BigDecimal(sWhole * 100L + centiRem, MathContext(10))
            .movePointLeft(2)
            .stripTrailingZeros()
            .toPlainString()
            .replace('.', ',')
        val prefix = if (neg && totalCentiSeconds > 0L) "-" else ""
        return "${prefix}${d}°${m}'${sRounded}\""
    }

    fun toDecimalText(
        value: SymbolicValue,
        config: DisplayFormatConfig = DisplayFormatConfig(),
        angleUnit: AngleUnit = AngleUnit.DEG
    ): String {
        return when (value) {
            is SymbolicValue.Error -> value.message
            is SymbolicValue.BooleanResult -> if (value.truth) "WAHR" else "FALSCH"
            is SymbolicValue.DmsValue -> formatDms(value.degrees)
            is SymbolicValue.ComplexVal -> formatComplex(value, config, angleUnit)
            is SymbolicValue.PrimeFactorized -> formatPrimeFactorizedString(value)
            is SymbolicValue.ExactRational -> formatGermanNumber(value.toDouble(), config)
            is SymbolicValue.Radical -> formatGermanNumber(value.toDouble(), config)
            is SymbolicValue.PiMultiple -> formatGermanNumber(value.toDouble(), config)
            is SymbolicValue.Decimal -> formatGermanNumber(value.toDouble(), config)
            is SymbolicValue.MatrixResult -> "Mat(${value.data.size}×${value.data.firstOrNull()?.size ?: 0})"
            is SymbolicValue.VectorResult -> "[ ${value.components.joinToString("  ") { formatGermanNumber(it, config) }} ]"
        }
    }

    private fun formatComplex(c: SymbolicValue.ComplexVal, config: DisplayFormatConfig, angleUnit: AngleUnit): String {
        val re = if (abs(c.real) < 1e-12) 0.0 else c.real
        val im = if (abs(c.imag) < 1e-12) 0.0 else c.imag
        if (config.complexFormat == ComplexFormat.POLAR) {
            val r = hypot(re, im)
            val rad = atan2(im, re)
            val angle = when (angleUnit) {
                AngleUnit.DEG -> Math.toDegrees(rad)
                AngleUnit.RAD -> rad
                AngleUnit.GRAD -> Math.toDegrees(rad) * 10.0 / 9.0
            }
            return "${formatGermanNumber(r, config)}∠${formatGermanNumber(angle, config)}"
        }
        if (im == 0.0) return formatGermanNumber(re, config)
        val imAbs = abs(im)
        val imStr = if (abs(imAbs - 1.0) < 1e-12) "i" else "${formatGermanNumber(imAbs, config)}i"
        if (re == 0.0) return if (im < 0) "-$imStr" else imStr
        val sign = if (im < 0) "-" else "+"
        return "${formatGermanNumber(re, config)}$sign$imStr"
    }

    private fun formatPrimeFactorizedString(pf: SymbolicValue.PrimeFactorized): String {
        val prefix = if (pf.negative) "-" else ""
        return prefix + pf.factors.joinToString("×") { (prime, exp) ->
            if (exp == 1) prime.toString() else "$prime^$exp"
        }
    }

    /**
     * Converts a SymbolicValue into a Natural Textbook Display AST (MathSequence)
     * for authentic exact output rendering (stacked fractions, √, π, exponents, matrices).
     */
    fun toDisplayAst(
        value: SymbolicValue,
        forceDecimal: Boolean,
        config: DisplayFormatConfig = DisplayFormatConfig(),
        angleUnit: AngleUnit = AngleUnit.DEG
    ): MathSequence {
        if (value is SymbolicValue.Error) {
            return AstEditor.seq(AstEditor.token(value.message, TokenKind.SYMBOL))
        }
        if (value is SymbolicValue.BooleanResult) {
            return AstEditor.seq(AstEditor.token(if (value.truth) "WAHR" else "FALSCH", TokenKind.SYMBOL))
        }
        if (value is SymbolicValue.DmsValue) {
            return AstEditor.seq(AstEditor.token(formatDms(value.degrees), TokenKind.DIGIT))
        }
        if (value is SymbolicValue.PrimeFactorized) {
            val nodes = mutableListOf<MathNode>()
            if (value.negative) nodes += AstEditor.token("-", TokenKind.OPERATOR)
            value.factors.forEachIndexed { idx, (prime, exp) ->
                if (idx > 0) nodes += AstEditor.token("×", TokenKind.OPERATOR)
                nodes += AstEditor.token(prime.toString(), TokenKind.DIGIT)
                if (exp > 1) {
                    nodes += MathNode.PowerNode(
                        AstEditor.nextId("pfexp"),
                        AstEditor.seq(AstEditor.token(exp.toString(), TokenKind.DIGIT))
                    )
                }
            }
            return MathSequence(AstEditor.nextId("out"), nodes)
        }
        if (value is SymbolicValue.MatrixResult) {
            val cellSeqs = value.data.map { row ->
                row.map { v ->
                    val approx = if (!forceDecimal) Rational.approximate(v, maxDen = 1000L) else null
                    if (approx != null && !approx.isInteger) {
                        rationalToAst(approx, FractionFormat.IMPROPER)
                    } else {
                        AstEditor.seq(AstEditor.token(formatGermanNumber(v, config), TokenKind.DIGIT))
                    }
                }
            }
            return AstEditor.seq(
                MathNode.MatrixNode(
                    AstEditor.nextId("matout"),
                    value.data.size,
                    value.data.firstOrNull()?.size ?: 0,
                    cellSeqs
                )
            )
        }
        if (value is SymbolicValue.VectorResult) {
            val cellSeqs = value.components.map { v ->
                listOf(AstEditor.seq(AstEditor.token(formatGermanNumber(v, config), TokenKind.DIGIT)))
            }
            return AstEditor.seq(
                MathNode.MatrixNode(
                    AstEditor.nextId("vctout"),
                    value.components.size,
                    1,
                    cellSeqs
                )
            )
        }

        if (forceDecimal || config.engExponentOffset != null) {
            return decimalStringToAst(toDecimalText(value, config, angleUnit))
        }

        return when (value) {
            is SymbolicValue.ExactRational -> rationalToAst(value.value, config.fractionFormat)
            is SymbolicValue.PiMultiple -> piMultipleToAst(value.coeff)
            is SymbolicValue.Radical -> radicalToAst(value)
            is SymbolicValue.ComplexVal -> decimalStringToAst(formatComplex(value, config, angleUnit))
            is SymbolicValue.Decimal -> decimalStringToAst(formatGermanNumber(value.toDouble(), config))
            else -> decimalStringToAst(toDecimalText(value, config, angleUnit))
        }
    }

    private fun decimalStringToAst(text: String): MathSequence {
        val sciIdx = text.indexOf("×10^")
        if (sciIdx != -1) {
            val mant = text.substring(0, sciIdx)
            val exp = text.substring(sciIdx + 4)
            return AstEditor.seq(
                AstEditor.token(mant, TokenKind.DIGIT),
                AstEditor.token("×10", TokenKind.OPERATOR),
                MathNode.PowerNode(
                    AstEditor.nextId("sciexp"),
                    AstEditor.seq(AstEditor.token(exp, TokenKind.DIGIT))
                )
            )
        }
        return AstEditor.seq(AstEditor.token(text, TokenKind.DIGIT))
    }

    private fun rationalToAst(r: Rational, fracFormat: FractionFormat): MathSequence {
        if (r.isInteger) {
            return AstEditor.seq(AstEditor.token(r.num.toString(), TokenKind.DIGIT))
        }
        if (fracFormat == FractionFormat.MIXED && r.num.abs() > r.den) {
            val parts = r.toMixedParts()
            val nodes = mutableListOf<MathNode>()
            if (parts.negative) nodes += AstEditor.token("-", TokenKind.OPERATOR)
            nodes += MathNode.MixedFractionNode(
                AstEditor.nextId("omf"),
                AstEditor.seq(AstEditor.token(parts.whole.toString(), TokenKind.DIGIT)),
                AstEditor.seq(AstEditor.token(parts.remainder.toString(), TokenKind.DIGIT)),
                AstEditor.seq(AstEditor.token(parts.denominator.toString(), TokenKind.DIGIT))
            )
            return MathSequence(AstEditor.nextId("out"), nodes)
        }
        val nodes = mutableListOf<MathNode>()
        if (r.isNegative) nodes += AstEditor.token("-", TokenKind.OPERATOR)
        nodes += MathNode.FractionNode(
            AstEditor.nextId("ofr"),
            AstEditor.seq(AstEditor.token(r.num.abs().toString(), TokenKind.DIGIT)),
            AstEditor.seq(AstEditor.token(r.den.toString(), TokenKind.DIGIT))
        )
        return MathSequence(AstEditor.nextId("out"), nodes)
    }

    private fun piMultipleToAst(c: Rational): MathSequence {
        val nodes = mutableListOf<MathNode>()
        if (c.isNegative) nodes += AstEditor.token("-", TokenKind.OPERATOR)
        val absNum = c.num.abs()
        val numNodes = mutableListOf<MathNode>()
        if (absNum != BigInteger.ONE) numNodes += AstEditor.token(absNum.toString(), TokenKind.DIGIT)
        numNodes += AstEditor.token("π", TokenKind.CONSTANT)

        if (c.den == BigInteger.ONE) {
            nodes.addAll(numNodes)
        } else {
            nodes += MathNode.FractionNode(
                AstEditor.nextId("pifr"),
                MathSequence(AstEditor.nextId("pinum"), numNodes),
                AstEditor.seq(AstEditor.token(c.den.toString(), TokenKind.DIGIT))
            )
        }
        return MathSequence(AstEditor.nextId("out"), nodes)
    }

    private fun radicalToAst(rad: SymbolicValue.Radical): MathSequence {
        // Combine constant + coeff * sqrt(r) over common denominator
        val commonDen = Rational.lcm(rad.constant.den, rad.coeff.den)
        val aNum = rad.constant.num * (commonDen / rad.constant.den)
        val bNum = rad.coeff.num * (commonDen / rad.coeff.den)

        val numNodes = mutableListOf<MathNode>()
        if (aNum != BigInteger.ZERO) {
            numNodes += AstEditor.token(aNum.toString(), TokenKind.DIGIT)
            if (bNum > BigInteger.ZERO) {
                numNodes += AstEditor.token("+", TokenKind.OPERATOR)
            } else {
                numNodes += AstEditor.token("-", TokenKind.OPERATOR)
            }
            val bAbs = bNum.abs()
            if (bAbs != BigInteger.ONE) {
                numNodes += AstEditor.token(bAbs.toString(), TokenKind.DIGIT)
            }
            numNodes += MathNode.RootNode(
                AstEditor.nextId("orad"),
                AstEditor.seq(AstEditor.token(rad.radicand.toString(), TokenKind.DIGIT))
            )
        } else {
            val isNeg = bNum < BigInteger.ZERO
            val bAbs = bNum.abs()
            if (commonDen == BigInteger.ONE) {
                if (isNeg) numNodes += AstEditor.token("-", TokenKind.OPERATOR)
                if (bAbs != BigInteger.ONE) numNodes += AstEditor.token(bAbs.toString(), TokenKind.DIGIT)
                numNodes += MathNode.RootNode(
                    AstEditor.nextId("orad"),
                    AstEditor.seq(AstEditor.token(rad.radicand.toString(), TokenKind.DIGIT))
                )
                return MathSequence(AstEditor.nextId("out"), numNodes)
            } else {
                val outerNodes = mutableListOf<MathNode>()
                if (isNeg) outerNodes += AstEditor.token("-", TokenKind.OPERATOR)
                val innerNum = mutableListOf<MathNode>()
                if (bAbs != BigInteger.ONE) innerNum += AstEditor.token(bAbs.toString(), TokenKind.DIGIT)
                innerNum += MathNode.RootNode(
                    AstEditor.nextId("orad"),
                    AstEditor.seq(AstEditor.token(rad.radicand.toString(), TokenKind.DIGIT))
                )
                outerNodes += MathNode.FractionNode(
                    AstEditor.nextId("radfr"),
                    MathSequence(AstEditor.nextId("radnum"), innerNum),
                    AstEditor.seq(AstEditor.token(commonDen.toString(), TokenKind.DIGIT))
                )
                return MathSequence(AstEditor.nextId("out"), outerNodes)
            }
        }

        if (commonDen == BigInteger.ONE) {
            return MathSequence(AstEditor.nextId("out"), numNodes)
        }
        return AstEditor.seq(
            MathNode.FractionNode(
                AstEditor.nextId("radfr"),
                MathSequence(AstEditor.nextId("radnum"), numNodes),
                AstEditor.seq(AstEditor.token(commonDen.toString(), TokenKind.DIGIT))
            )
        )
    }
}
