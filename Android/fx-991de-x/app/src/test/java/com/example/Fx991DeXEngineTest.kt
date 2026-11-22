package com.example

import com.naveen.fx991dex.engine.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import kotlin.math.PI
import kotlin.math.abs

class Fx991DeXEngineTest {

    @Test
    fun exactFractionAndRadicalSimplification() {
        // sin(45°) = √2 / 2
        val seq = AstEditor.seq(
            AstEditor.token("sin(", TokenKind.FUNCTION),
            AstEditor.token("4", TokenKind.DIGIT),
            AstEditor.token("5", TokenKind.DIGIT),
            AstEditor.token(")", TokenKind.PAREN)
        )
        val res = MathEvaluator.evaluate(seq, EvaluationContext(angleUnit = AngleUnit.DEG))
        assertTrue(res is SymbolicValue.Radical)
        val rad = res as SymbolicValue.Radical
        assertEquals(Rational.HALF, rad.coeff)
        assertEquals(BigInteger.TWO, rad.radicand)
        assertEquals("0,7071067812", SymbolicFormatter.toDecimalText(res))
    }

    @Test
    fun exactFractionAdditionAndMixedConversion() {
        val f1 = MathNode.FractionNode(
            "f1",
            AstEditor.seq(AstEditor.token("3", TokenKind.DIGIT)),
            AstEditor.seq(AstEditor.token("4", TokenKind.DIGIT))
        )
        val f2 = MathNode.FractionNode(
            "f2",
            AstEditor.seq(AstEditor.token("1", TokenKind.DIGIT)),
            AstEditor.seq(AstEditor.token("2", TokenKind.DIGIT))
        )
        val seq = AstEditor.seq(f1, AstEditor.token("+", TokenKind.OPERATOR), f2)
        val res = MathEvaluator.evaluate(seq, EvaluationContext()) as SymbolicValue.ExactRational
        assertEquals(Rational.of(5, 4), res.value)
        val mixed = res.value.toMixedParts()
        assertEquals(BigInteger.ONE, mixed.whole)
        assertEquals(BigInteger.ONE, mixed.remainder)
        assertEquals(BigInteger.valueOf(4), mixed.denominator)
    }

    @Test
    fun exactInverseTrigReturnsPiMultipleInRad() {
        // sin⁻¹(0,5) in RAD = π/6
        val seq = AstEditor.seq(
            AstEditor.token("sin⁻¹(", TokenKind.FUNCTION),
            AstEditor.token("0", TokenKind.DIGIT),
            AstEditor.token(",", TokenKind.DECIMAL),
            AstEditor.token("5", TokenKind.DIGIT),
            AstEditor.token(")", TokenKind.PAREN)
        )
        val res = MathEvaluator.evaluate(seq, EvaluationContext(angleUnit = AngleUnit.RAD))
        assertTrue(res is SymbolicValue.PiMultiple)
        assertEquals(Rational.of(1, 6), (res as SymbolicValue.PiMultiple).coeff)
    }

    @Test
    fun calculusDefiniteIntegralAndDerivative() {
        // ∫_0^1 x² dx = 1/3
        val integral = MathNode.IntegralNode(
            "i1",
            body = AstEditor.seq(
                AstEditor.token("X", TokenKind.VARIABLE),
                MathNode.PowerNode("p1", AstEditor.seq(AstEditor.token("2", TokenKind.DIGIT)))
            ),
            lower = AstEditor.seq(AstEditor.token("0", TokenKind.DIGIT)),
            upper = AstEditor.seq(AstEditor.token("1", TokenKind.DIGIT))
        )
        val intRes = MathEvaluator.evaluate(AstEditor.seq(integral), EvaluationContext())
        assertEquals(1.0 / 3.0, intRes.toDouble(), 1e-7)

        // d/dx(x^3)|_{x=2} = 12
        val deriv = MathNode.DerivativeNode(
            "d1",
            body = AstEditor.seq(
                AstEditor.token("X", TokenKind.VARIABLE),
                MathNode.PowerNode("p2", AstEditor.seq(AstEditor.token("3", TokenKind.DIGIT)))
            ),
            atX = AstEditor.seq(AstEditor.token("2", TokenKind.DIGIT))
        )
        val derivRes = MathEvaluator.evaluate(AstEditor.seq(deriv), EvaluationContext())
        assertEquals(12.0, derivRes.toDouble(), 1e-6)
    }

    @Test
    fun primeFactorizationAndGcdLcm() {
        val factors = Rational.primeFactorize(BigInteger.valueOf(360))
        assertEquals(
            listOf(BigInteger.TWO to 3, BigInteger.valueOf(3) to 2, BigInteger.valueOf(5) to 1),
            factors
        )
        assertEquals(BigInteger.valueOf(12), Rational.gcd(BigInteger.valueOf(36), BigInteger.valueOf(48)))
        assertEquals(BigInteger.valueOf(144), Rational.lcm(BigInteger.valueOf(36), BigInteger.valueOf(48)))
    }

    @Test
    fun newtonRaphsonSolve() {
        // X² - 2 = 0 -> √2
        val seq = AstEditor.seq(
            AstEditor.token("X", TokenKind.VARIABLE),
            MathNode.PowerNode("p", AstEditor.seq(AstEditor.token("2", TokenKind.DIGIT))),
            AstEditor.token("-", TokenKind.OPERATOR),
            AstEditor.token("2", TokenKind.DIGIT),
            AstEditor.token("=", TokenKind.COMPARE),
            AstEditor.token("0", TokenKind.DIGIT)
        )
        val solved = MathEvaluator.solveEquation(seq, 1.5, EvaluationContext()).getOrThrow()
        assertEquals(kotlin.math.sqrt(2.0), solved.first, 1e-9)
    }

    @Test
    fun specializedModesVerification() {
        // Mode 3: Base-N
        assertEquals(0xFF, BaseNEngine.evaluate("F0 OR 0F", NumberBase.HEX).getOrThrow())

        // Mode 4: Matrix Det & Inverse
        val m = arrayOf(doubleArrayOf(4.0, 7.0), doubleArrayOf(2.0, 6.0))
        assertEquals(10.0, MatrixEngine.determinant(m), 1e-9)
        val inv = MatrixEngine.inverse(m)
        val prod = MatrixEngine.multiply(m, inv)
        assertEquals(1.0, prod[0][0], 1e-9)
        assertEquals(0.0, prod[0][1], 1e-9)

        // Mode 5: Vector Cross & Angle
        val cross = VectorEngine.crossProduct(doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, 1.0, 0.0))
        assertArrayEquals(doubleArrayOf(0.0, 0.0, 1.0), cross, 1e-9)
        assertEquals(PI / 2.0, VectorEngine.angleRadians(doubleArrayOf(1.0, 0.0), doubleArrayOf(0.0, 1.0)), 1e-9)

        // Mode 6: Statistics
        val stats = StatisticsEngine.computeOneVar(listOf(StatRow(2.0), StatRow(4.0), StatRow(6.0)))!!
        assertEquals(4.0, stats.mean, 1e-9)

        // Mode 7: Distributions
        assertEquals(0.682689, DistributionEngine.normalCd(-1.0, 1.0, 1.0, 0.0), 1e-4)
        assertEquals(1.95996, DistributionEngine.inverseNormal(0.975, 1.0, 0.0), 1e-4)

        // Mode 8: Spreadsheet
        val sheet = SpreadsheetEngine.evaluateAll(mapOf("A1" to "10", "A2" to "20", "B1" to "=Summe(A1:A2)"))
        assertEquals(30.0, sheet["B1"]!!.getOrThrow(), 1e-9)

        // Mode 10 & 11: Polynomial & Inequality
        val poly = EquationInequalityEngine.solvePolynomial(doubleArrayOf(1.0, -3.0, 2.0))
        assertEquals(2.0, poly.roots[0].real, 1e-9)
        assertEquals(1.0, poly.roots[1].real, 1e-9)
        val ineq = EquationInequalityEngine.solveInequality(
            doubleArrayOf(1.0, 0.0, -4.0),
            EquationInequalityEngine.InequalityOp.LT
        )
        assertTrue(ineq.contains("-2 < x < 2"))

        // Mode 13: Ratio 3:4 = X:12 => X = 9
        val ratio = EquationInequalityEngine.solveRatio(3.0, 4.0, 12.0, true)
        assertTrue(abs(ratio.toDouble() - 9.0) < 1e-9)
    }

    @Test
    fun bugFixRegressions() {
        // 1. DMS rounding carry-over: 0.999999 deg -> 1°0'0" (not 0°59'60")
        assertEquals("1°0'0\"", SymbolicFormatter.formatDms(0.999999))
        assertEquals("-1°0'0\"", SymbolicFormatter.formatDms(-0.999999))

        // 2. Exact Abs(-2π) = 2π
        val absPi = MathNode.AbsNode(
            "a1",
            AstEditor.seq(AstEditor.token("-", TokenKind.OPERATOR), AstEditor.token("2", TokenKind.DIGIT), AstEditor.token("π", TokenKind.CONSTANT))
        )
        val absRes = MathEvaluator.evaluate(AstEditor.seq(absPi), EvaluationContext())
        assertTrue(absRes is SymbolicValue.PiMultiple)
        assertEquals(Rational.of(2), (absRes as SymbolicValue.PiMultiple).coeff)

        // 3. Complex argument in real-only factorial throws Math FEHLER
        val complexFact = AstEditor.seq(
            AstEditor.token("(", TokenKind.PAREN),
            AstEditor.token("1", TokenKind.DIGIT),
            AstEditor.token("+", TokenKind.OPERATOR),
            AstEditor.token("2", TokenKind.DIGIT),
            AstEditor.token("i", TokenKind.CONSTANT),
            AstEditor.token(")", TokenKind.PAREN),
            AstEditor.token("!", TokenKind.OPERATOR)
        )
        val factRes = MathEvaluator.evaluate(complexFact, EvaluationContext(complexMode = true))
        assertTrue(factRes is SymbolicValue.Error)

        // 4. Unwrapping FractionNode with non-empty numerator and empty denominator
        var doc = AstEditor.emptyDocument()
        doc = AstEditor.insertToken(doc, "5", TokenKind.DIGIT)
        doc = AstEditor.insertFraction(doc) // lifts 5 into numerator, cursor in empty denominator
        doc = AstEditor.deleteBeforeCursor(doc) // should unwrap back to 5
        assertEquals(1, doc.root.nodes.size)
        assertEquals("5", (doc.root.nodes[0] as MathNode.TokenNode).text)
    }
}
