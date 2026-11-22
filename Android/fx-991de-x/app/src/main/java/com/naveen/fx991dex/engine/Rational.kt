package com.naveen.fx991dex.engine

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

/**
 * Exact arbitrary-precision rational number p/q backed by BigInteger.
 * Always kept in lowest terms with strictly positive denominator.
 */
data class Rational private constructor(
    val num: BigInteger,
    val den: BigInteger
) : Comparable<Rational> {

    val isZero: Boolean get() = num == BigInteger.ZERO
    val isOne: Boolean get() = num == BigInteger.ONE && den == BigInteger.ONE
    val isNegative: Boolean get() = num < BigInteger.ZERO
    val isInteger: Boolean get() = den == BigInteger.ONE

    operator fun plus(other: Rational): Rational =
        of(num * other.den + other.num * den, den * other.den)

    operator fun minus(other: Rational): Rational =
        of(num * other.den - other.num * den, den * other.den)

    operator fun times(other: Rational): Rational =
        of(num * other.num, den * other.den)

    operator fun div(other: Rational): Rational {
        if (other.isZero) throw ArithmeticException("Division durch Null")
        return of(num * other.den, den * other.num)
    }

    operator fun unaryMinus(): Rational = Rational(num.negate(), den)

    fun abs(): Rational = if (isNegative) -this else this

    fun reciprocal(): Rational {
        if (isZero) throw ArithmeticException("Division durch Null")
        return of(den, num)
    }

    fun pow(exponent: Int): Rational {
        return when {
            exponent == 0 -> ONE
            exponent > 0 -> Rational(num.pow(exponent), den.pow(exponent))
            else -> {
                if (isZero) throw ArithmeticException("Division durch Null")
                val pos = -exponent
                of(den.pow(pos), num.pow(pos))
            }
        }
    }

    fun toBigDecimal(mc: MathContext = MATH_CONTEXT): BigDecimal {
        return BigDecimal(num).divide(BigDecimal(den), mc)
    }

    fun toDouble(): Double = toBigDecimal(MathContext.DECIMAL128).toDouble()

    /**
     * Extracts mixed number parts: (sign, whole, remainderNum, den).
     */
    fun toMixedParts(): MixedParts {
        val negative = isNegative
        val absNum = num.abs()
        val whole = absNum.divide(den)
        val rem = absNum.remainder(den)
        return MixedParts(negative, whole, rem, den)
    }

    override fun compareTo(other: Rational): Int {
        return (num * other.den).compareTo(other.num * den)
    }

    override fun toString(): String = if (isInteger) num.toString() else "$num/$den"

    data class MixedParts(
        val negative: Boolean,
        val whole: BigInteger,
        val remainder: BigInteger,
        val denominator: BigInteger
    )

    companion object {
        val MATH_CONTEXT = MathContext(34, RoundingMode.HALF_UP)
        val ZERO = Rational(BigInteger.ZERO, BigInteger.ONE)
        val ONE = Rational(BigInteger.ONE, BigInteger.ONE)
        val MINUS_ONE = Rational(BigInteger.ONE.negate(), BigInteger.ONE)
        val TWO = Rational(BigInteger.TWO, BigInteger.ONE)
        val HALF = Rational(BigInteger.ONE, BigInteger.TWO)

        fun of(num: Long, den: Long = 1L): Rational =
            of(BigInteger.valueOf(num), BigInteger.valueOf(den))

        fun of(num: Int, den: Int = 1): Rational =
            of(BigInteger.valueOf(num.toLong()), BigInteger.valueOf(den.toLong()))

        fun of(num: BigInteger, den: BigInteger = BigInteger.ONE): Rational {
            if (den == BigInteger.ZERO) throw ArithmeticException("Division durch Null")
            if (num == BigInteger.ZERO) return ZERO
            val sign = if (den < BigInteger.ZERO) -1 else 1
            val n = if (sign < 0) num.negate() else num
            val d = if (sign < 0) den.negate() else den
            val g = n.abs().gcd(d)
            return Rational(n / g, d / g)
        }

        fun fromDecimalString(raw: String): Rational? {
            val cleaned = raw.trim().replace(',', '.')
            if (cleaned.isEmpty()) return null
            return try {
                val bd = BigDecimal(cleaned)
                fromBigDecimal(bd)
            } catch (_: NumberFormatException) {
                null
            }
        }

        fun fromBigDecimal(bd: BigDecimal): Rational {
            val stripped = bd.stripTrailingZeros()
            val scale = stripped.scale()
            return if (scale <= 0) {
                of(stripped.toBigIntegerExact(), BigInteger.ONE)
            } else {
                val numerator = stripped.unscaledValue()
                val denominator = BigInteger.TEN.pow(scale)
                of(numerator, denominator)
            }
        }

        /**
         * Best rational approximation via continued fractions (used for S<=>D & clean display).
         */
        fun approximate(value: Double, maxDen: Long = 10_000L, tolerance: Double = 1e-10): Rational? {
            if (!value.isFinite()) return null
            val sign = if (value < 0) -1 else 1
            val x = kotlin.math.abs(value)
            var h1 = 1L
            var h2 = 0L
            var k1 = 0L
            var k2 = 1L
            var b = x
            repeat(24) {
                val a = kotlin.math.floor(b).toLong()
                val h = a * h1 + h2
                val k = a * k1 + k2
                if (k > maxDen || h > 1_000_000_000L) return null
                if (kotlin.math.abs(x - h.toDouble() / k.toDouble()) <= tolerance * kotlin.math.max(1.0, x)) {
                    return of(sign * h, k)
                }
                h2 = h1; h1 = h
                k2 = k1; k1 = k
                val frac = b - a
                if (frac < 1e-12) return of(sign * h1, k1)
                b = 1.0 / frac
            }
            return null
        }

        fun gcd(a: BigInteger, b: BigInteger): BigInteger = a.abs().gcd(b.abs())

        fun lcm(a: BigInteger, b: BigInteger): BigInteger {
            if (a == BigInteger.ZERO || b == BigInteger.ZERO) return BigInteger.ZERO
            return a.abs().multiply(b.abs()).divide(gcd(a, b))
        }

        /**
         * Extracts the largest square factor: n = out^2 * inside.
         */
        fun simplifySquareRoot(n: BigInteger): Pair<BigInteger, BigInteger> {
            if (n <= BigInteger.ZERO) return BigInteger.ZERO to BigInteger.ZERO
            var remaining = n
            var outside = BigInteger.ONE
            var d = BigInteger.TWO
            while (d * d <= remaining && d <= BigInteger.valueOf(100_000L)) {
                var count = 0
                while (remaining.mod(d) == BigInteger.ZERO) {
                    remaining = remaining.divide(d)
                    count++
                }
                if (count >= 2) {
                    outside = outside.multiply(d.pow(count / 2))
                }
                if (count % 2 == 1) {
                    remaining = remaining.multiply(d)
                    break
                }
                d = if (d == BigInteger.TWO) BigInteger.valueOf(3) else d.add(BigInteger.TWO)
            }
            // Re-run clean factorization for exact extraction
            return exactSquareFactor(n)
        }

        private fun exactSquareFactor(n: BigInteger): Pair<BigInteger, BigInteger> {
            var rem = n
            var outside = BigInteger.ONE
            var inside = BigInteger.ONE

            // Check factor 2
            var c2 = 0
            while (rem.mod(BigInteger.TWO) == BigInteger.ZERO) {
                rem = rem.divide(BigInteger.TWO)
                c2++
            }
            outside = outside.multiply(BigInteger.TWO.pow(c2 / 2))
            if (c2 % 2 == 1) inside = inside.multiply(BigInteger.TWO)

            var factor = 3L
            val limit = 100_000L
            while (factor <= limit) {
                val bf = BigInteger.valueOf(factor)
                if (bf * bf > rem) break
                if (rem.mod(bf) == BigInteger.ZERO) {
                    var cnt = 0
                    while (rem.mod(bf) == BigInteger.ZERO) {
                        rem = rem.divide(bf)
                        cnt++
                    }
                    outside = outside.multiply(bf.pow(cnt / 2))
                    if (cnt % 2 == 1) inside = inside.multiply(bf)
                }
                factor += 2L
            }
            // Check if leftover rem is a perfect square
            if (rem > BigInteger.ONE) {
                val root = rem.sqrt()
                if (root * root == rem) {
                    outside = outside.multiply(root)
                } else {
                    inside = inside.multiply(rem)
                }
            }
            return outside to inside
        }

        /**
         * Prime factorization for FACT key (SHIFT + ° ' ").
         */
        fun primeFactorize(n: BigInteger): List<Pair<BigInteger, Int>> {
            val absN = n.abs()
            if (absN <= BigInteger.ONE) return listOf(absN to 1)
            val result = mutableListOf<Pair<BigInteger, Int>>()
            var rem = absN

            var c2 = 0
            while (rem.mod(BigInteger.TWO) == BigInteger.ZERO) {
                rem = rem.divide(BigInteger.TWO)
                c2++
            }
            if (c2 > 0) result += BigInteger.TWO to c2

            var f = 3L
            val maxTrial = 1_000_000L
            while (f <= maxTrial) {
                val bf = BigInteger.valueOf(f)
                if (bf * bf > rem) break
                if (rem.mod(bf) == BigInteger.ZERO) {
                    var cnt = 0
                    while (rem.mod(bf) == BigInteger.ZERO) {
                        rem = rem.divide(bf)
                        cnt++
                    }
                    result += bf to cnt
                }
                f += 2L
            }
            if (rem > BigInteger.ONE) {
                result += rem to 1
            }
            return result
        }
    }
}
