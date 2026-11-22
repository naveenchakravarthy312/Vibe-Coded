package com.naveen.fx991dex.engine

import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.*
import kotlin.random.Random

data class EvaluationContext(
    val angleUnit: AngleUnit = AngleUnit.DEG,
    val complexMode: Boolean = false,
    val variables: Map<String, Double> = emptyMap(),
    val ans: SymbolicValue = SymbolicValue.ofLong(0),
    val preAns: SymbolicValue = SymbolicValue.ofLong(0),
    val matrices: Map<String, Array<DoubleArray>> = emptyMap(),
    val vectors: Map<String, DoubleArray> = emptyMap(),
    val onPolRecStore: ((x: Double, y: Double) -> Unit)? = null
)

/**
 * Pure Kotlin recursive-descent AST evaluator supporting exact symbolic arithmetic
 * (Rational, a√b radicals, k·π/m multiples), complex numbers, calculus (Gauss-Kronrod
 * quadrature, Richardson derivative), Σ/Π, SOLVE (Newton-Raphson), and Verify comparisons.
 */
object MathEvaluator {

    fun evaluate(sequence: MathSequence, ctx: EvaluationContext): SymbolicValue {
        if (sequence.nodes.isEmpty()) return SymbolicValue.Error("Syntax FEHLER")
        return try {
            // Check for top-level comparison operator (=, ≠, <, >, ≤, ≥) for Verify mode
            val cmpIdx = findTopLevelComparison(sequence.nodes)
            if (cmpIdx != null) {
                val op = (sequence.nodes[cmpIdx] as MathNode.TokenNode).text
                val lhsSeq = MathSequence("lhs", sequence.nodes.subList(0, cmpIdx))
                val rhsSeq = MathSequence("rhs", sequence.nodes.subList(cmpIdx + 1, sequence.nodes.size))
                val lhs = evalSequence(lhsSeq, ctx)
                val rhs = evalSequence(rhsSeq, ctx)
                if (lhs is SymbolicValue.Error) return lhs
                if (rhs is SymbolicValue.Error) return rhs
                val l = lhs.toDouble()
                val r = rhs.toDouble()
                val eps = 1e-10 * max(1.0, max(abs(l), abs(r)))
                val truth = when (op) {
                    "=" -> abs(l - r) <= eps
                    "≠" -> abs(l - r) > eps
                    "<" -> l < r - eps
                    ">" -> l > r + eps
                    "≤" -> l <= r + eps
                    "≥" -> l >= r - eps
                    else -> false
                }
                return SymbolicValue.BooleanResult(truth)
            }
            evalSequence(sequence, ctx)
        } catch (e: ArithmeticException) {
            SymbolicValue.Error("Math FEHLER")
        } catch (e: IllegalArgumentException) {
            SymbolicValue.Error(e.message ?: "Syntax FEHLER")
        } catch (_: StackOverflowError) {
            SymbolicValue.Error("Stapel FEHLER")
        } catch (_: Exception) {
            SymbolicValue.Error("Syntax FEHLER")
        }
    }

    /**
     * Newton-Raphson + secant equation solver for SHIFT + CALC (SOLVE).
     * Expects an expression containing `=` (e.g. X² - 2 = 0) or solves f(X) = 0 directly.
     */
    fun solveEquation(
        sequence: MathSequence,
        initialGuess: Double,
        ctx: EvaluationContext,
        targetVar: String = "X"
    ): Result<Pair<Double, Double>> {
        val cmpIdx = findTopLevelComparison(sequence.nodes)
        val f: (Double) -> Double = if (cmpIdx != null) {
            val lhs = MathSequence("slhs", sequence.nodes.subList(0, cmpIdx))
            val rhs = MathSequence("srhs", sequence.nodes.subList(cmpIdx + 1, sequence.nodes.size))
            val evalDiff: (Double) -> Double = { x ->
                val subCtx = ctx.copy(variables = ctx.variables + (targetVar to x))
                val lv = evalSequence(lhs, subCtx).toDouble()
                val rv = evalSequence(rhs, subCtx).toDouble()
                lv - rv
            }
            evalDiff
        } else {
            val evalSingle: (Double) -> Double = { x ->
                val subCtx = ctx.copy(variables = ctx.variables + (targetVar to x))
                evalSequence(sequence, subCtx).toDouble()
            }
            evalSingle
        }

        var x = if (initialGuess.isFinite()) initialGuess else 1.0
        for (iter in 0 until 120) {
            val fx = f(x)
            if (!fx.isFinite()) return Result.failure(IllegalArgumentException("Keine Lösung"))
            if (abs(fx) < 1e-12) {
                return Result.success(x to fx)
            }
            val h = max(1e-7, abs(x) * 1e-7)
            val dfx = (f(x + h) - f(x - h)) / (2.0 * h)
            if (!dfx.isFinite() || abs(dfx) < 1e-14) {
                // Perturb slightly to escape flat saddle point
                x += 0.137
                continue
            }
            val step = (fx / dfx).coerceIn(-1e6, 1e6)
            val nextX = x - step
            if (abs(nextX - x) < 1e-12) {
                val res = f(nextX)
                if (abs(res) < 1e-6) return Result.success(nextX to res)
            }
            x = nextX
        }
        val finalRes = f(x)
        return if (finalRes.isFinite() && abs(finalRes) < 1e-5) {
            Result.success(x to finalRes)
        } else {
            Result.failure(IllegalArgumentException("Keine Lösung"))
        }
    }

    private fun findTopLevelComparison(nodes: List<MathNode>): Int? {
        var depth = 0
        for (i in nodes.indices) {
            val n = nodes[i]
            if (n is MathNode.TokenNode) {
                if (n.text.endsWith("(")) depth++
                else if (n.text == ")") depth = (depth - 1).coerceAtLeast(0)
                else if (depth == 0 && n.kind == TokenKind.COMPARE) return i
            }
        }
        return null
    }

    private fun evalSequence(seq: MathSequence, ctx: EvaluationContext): SymbolicValue {
        val parser = SeqParser(seq.nodes, ctx)
        val result = parser.parseExpression()
        if (parser.hasMore()) {
            throw IllegalArgumentException("Syntax FEHLER")
        }
        return result
    }

    private class SeqParser(
        private val nodes: List<MathNode>,
        private val ctx: EvaluationContext
    ) {
        private var pos = 0

        fun hasMore(): Boolean = pos < nodes.size
        private fun peek(): MathNode? = nodes.getOrNull(pos)
        private fun consume(): MathNode = nodes[pos++]

        fun parseExpression(): SymbolicValue {
            var left = parseTerm()
            while (hasMore()) {
                val n = peek() as? MathNode.TokenNode ?: break
                when (n.text) {
                    "+" -> {
                        consume()
                        left = add(left, parseTerm())
                    }
                    "-", "−" -> {
                        consume()
                        left = subtract(left, parseTerm())
                    }
                    else -> break
                }
            }
            return left
        }

        private fun parseTerm(): SymbolicValue {
            var left = parsePolarOrCombinatorics()
            while (hasMore()) {
                val n = peek()
                if (n is MathNode.TokenNode) {
                    when (n.text) {
                        "×", "*" -> {
                            consume()
                            left = multiply(left, parsePolarOrCombinatorics())
                            continue
                        }
                        "÷", "/" -> {
                            consume()
                            left = divide(left, parsePolarOrCombinatorics())
                            continue
                        }
                        "•" -> {
                            consume()
                            left = dotProduct(left, parsePolarOrCombinatorics())
                            continue
                        }
                        "▸" -> {
                            // Unit conversion operator token
                            val convText = n.text
                            consume()
                            val conv = ScientificCatalog.conversions.firstOrNull { it.label == convText }
                            if (conv != null) left = SymbolicValue.ofDouble(conv.convert(left.toDouble()))
                            continue
                        }
                    }
                    // Check unit conversion label embedded as single token (e.g. "in ▸ cm")
                    val conv = ScientificCatalog.conversions.firstOrNull { it.label == n.text }
                    if (conv != null) {
                        consume()
                        left = SymbolicValue.ofDouble(conv.convert(left.toDouble()))
                        continue
                    }
                }
                // Implicit multiplication (e.g. 2π, 3√2, 2sin(30), 2(3+1), (2)(3), 3X, 2i)
                if (startsImplicitFactor(n)) {
                    left = multiply(left, parsePolarOrCombinatorics())
                } else {
                    break
                }
            }
            return left
        }

        private fun startsImplicitFactor(node: MathNode?): Boolean {
            if (node == null) return false
            return when (node) {
                is MathNode.RootNode,
                is MathNode.NthRootNode,
                is MathNode.LogBaseNode,
                is MathNode.IntegralNode,
                is MathNode.DerivativeNode,
                is MathNode.SumProdNode,
                is MathNode.AbsNode,
                is MathNode.FractionNode,
                is MathNode.MixedFractionNode -> true
                is MathNode.TokenNode -> when (node.kind) {
                    TokenKind.CONSTANT, TokenKind.VARIABLE, TokenKind.FUNCTION -> true
                    TokenKind.PAREN -> node.text == "("
                    else -> false
                }
                else -> false
            }
        }

        private fun parsePolarOrCombinatorics(): SymbolicValue {
            var left = parseUnary()
            while (hasMore()) {
                val n = peek() as? MathNode.TokenNode ?: break
                when (n.text) {
                    "∠" -> {
                        consume()
                        val angleVal = parseUnary()
                        val r = left.toDouble()
                        val rad = toRadians(angleVal.toDouble(), ctx.angleUnit)
                        left = SymbolicValue.ComplexVal(r * cos(rad), r * sin(rad))
                    }
                    "P" -> {
                        consume()
                        val r = parseUnary()
                        left = nPr(left, r)
                    }
                    "C" -> {
                        consume()
                        val r = parseUnary()
                        left = nCr(left, r)
                    }
                    "×10^" -> {
                        consume()
                        val exp = parseUnary()
                        left = multiply(left, power(SymbolicValue.ofLong(10), exp))
                    }
                    else -> break
                }
            }
            return left
        }

        private fun parseUnary(): SymbolicValue {
            val n = peek()
            if (n is MathNode.TokenNode && (n.text == "-" || n.text == "−" || n.text == "(-)")) {
                consume()
                return negate(parseUnary())
            }
            if (n is MathNode.TokenNode && n.text == "+") {
                consume()
                return parseUnary()
            }
            return parsePostfix()
        }

        private fun parsePostfix(): SymbolicValue {
            var value = parsePrimary()
            while (hasMore()) {
                val next = peek()
                when {
                    next is MathNode.PowerNode -> {
                        consume()
                        val exp = evalSequence(next.exponent, ctx)
                        value = power(value, exp)
                    }
                    next is MathNode.TokenNode && next.text == "!" -> {
                        consume()
                        value = factorial(value)
                    }
                    next is MathNode.TokenNode && next.text == "%" -> {
                        consume()
                        value = divide(value, SymbolicValue.ofLong(100))
                    }
                    next is MathNode.TokenNode && next.text == "°" -> {
                        value = parseDmsTail(value.toDouble())
                    }
                    else -> break
                }
            }
            return value
        }

        private fun parseDmsTail(degPart: Double): SymbolicValue {
            // Consume '°'
            consume()
            val sign = if (degPart < 0) -1.0 else 1.0
            var total = abs(degPart)
            // Optional minutes followed by ° or '
            if (hasMore() && isNumericStart(peek())) {
                val mins = parseNumberValue().toDouble()
                total += mins / 60.0
                val mTok = peek() as? MathNode.TokenNode
                if (mTok != null && (mTok.text == "°" || mTok.text == "'")) consume()
                // Optional seconds followed by ° or "
                if (hasMore() && isNumericStart(peek())) {
                    val secs = parseNumberValue().toDouble()
                    total += secs / 3600.0
                    val sTok = peek() as? MathNode.TokenNode
                    if (sTok != null && (sTok.text == "°" || sTok.text == "\"")) consume()
                }
            }
            return SymbolicValue.DmsValue(sign * total)
        }

        private fun isNumericStart(node: MathNode?): Boolean {
            return node is MathNode.TokenNode && (node.kind == TokenKind.DIGIT || node.kind == TokenKind.DECIMAL)
        }

        private fun parsePrimary(): SymbolicValue {
            val node = peek() ?: throw IllegalArgumentException("Syntax FEHLER")
            return when (node) {
                is MathNode.FractionNode -> {
                    consume()
                    val num = evalSequence(node.numerator, ctx)
                    val den = evalSequence(node.denominator, ctx)
                    divide(num, den)
                }
                is MathNode.MixedFractionNode -> {
                    consume()
                    val whole = evalSequence(node.whole, ctx)
                    val num = evalSequence(node.numerator, ctx)
                    val den = evalSequence(node.denominator, ctx)
                    val frac = divide(num, den)
                    if (whole.toDouble() < 0) subtract(whole, frac) else add(whole, frac)
                }
                is MathNode.RootNode -> {
                    consume()
                    val rad = evalSequence(node.radicand, ctx)
                    squareRoot(rad)
                }
                is MathNode.NthRootNode -> {
                    consume()
                    val idx = evalSequence(node.index, ctx)
                    val rad = evalSequence(node.radicand, ctx)
                    nthRoot(idx, rad)
                }
                is MathNode.LogBaseNode -> {
                    consume()
                    val base = evalSequence(node.base, ctx)
                    val arg = evalSequence(node.argument, ctx)
                    evalLogBase(base, arg)
                }
                is MathNode.AbsNode -> {
                    consume()
                    val inner = evalSequence(node.inner, ctx)
                    evalAbs(inner)
                }
                is MathNode.IntegralNode -> {
                    consume()
                    val a = evalSequence(node.lower, ctx).toDouble()
                    val b = evalSequence(node.upper, ctx).toDouble()
                    val res = gaussKronrodIntegrate(a, b) { x ->
                        evalSequence(node.body, ctx.copy(variables = ctx.variables + ("X" to x))).toDouble()
                    }
                    SymbolicValue.ofDouble(res)
                }
                is MathNode.DerivativeNode -> {
                    consume()
                    val x0 = evalSequence(node.atX, ctx).toDouble()
                    val res = numericalDerivative(x0) { x ->
                        evalSequence(node.body, ctx.copy(variables = ctx.variables + ("X" to x))).toDouble()
                    }
                    SymbolicValue.ofDouble(res)
                }
                is MathNode.SumProdNode -> {
                    consume()
                    val lo = evalSequence(node.lower, ctx).toDouble().roundToInt()
                    val hi = evalSequence(node.upper, ctx).toDouble().roundToInt()
                    if (hi < lo || hi - lo > 50_000) throw ArithmeticException("Math FEHLER")
                    var acc = if (node.isProduct) SymbolicValue.ofLong(1) else SymbolicValue.ofLong(0)
                    for (k in lo..hi) {
                        val term = evalSequence(node.body, ctx.copy(variables = ctx.variables + ("X" to k.toDouble())))
                        acc = if (node.isProduct) multiply(acc, term) else add(acc, term)
                    }
                    acc
                }
                is MathNode.TokenNode -> parseToken(node)
                else -> throw IllegalArgumentException("Syntax FEHLER")
            }
        }

        private fun parseNumberValue(): SymbolicValue {
            val sb = StringBuilder()
            var seenDecimal = false
            while (hasMore()) {
                val n = peek() as? MathNode.TokenNode ?: break
                if (n.kind == TokenKind.DIGIT) {
                    sb.append(n.text)
                    consume()
                } else if (n.kind == TokenKind.DECIMAL && !seenDecimal) {
                    seenDecimal = true
                    sb.append('.')
                    consume()
                } else {
                    break
                }
            }
            val raw = sb.toString()
            if (raw.isEmpty() || raw == ".") throw IllegalArgumentException("Syntax FEHLER")
            val rat = Rational.fromDecimalString(raw)
            return if (rat != null) SymbolicValue.ExactRational(rat) else SymbolicValue.Decimal(BigDecimal(raw))
        }

        private fun parseToken(node: MathNode.TokenNode): SymbolicValue {
            if (node.kind == TokenKind.DIGIT || node.kind == TokenKind.DECIMAL) {
                return parseNumberValue()
            }
            consume()
            return when {
                node.text == "(" -> {
                    val inner = parseExpression()
                    val close = peek() as? MathNode.TokenNode
                    if (close != null && close.text == ")") consume()
                    inner
                }
                node.text == "π" -> SymbolicValue.PiMultiple(Rational.ONE)
                node.text == "e" -> SymbolicValue.Decimal(BigDecimal.valueOf(E))
                node.text == "i" -> SymbolicValue.ComplexVal(0.0, 1.0)
                node.text == "Ans" -> ctx.ans
                node.text == "PreAns" -> ctx.preAns
                node.text == "Ran#" -> SymbolicValue.ofDouble(Random.nextDouble())
                node.text.startsWith("Mat") -> {
                    val mat = ctx.matrices[node.text] ?: throw IllegalArgumentException("Dimension FEHLER")
                    SymbolicValue.MatrixResult(mat)
                }
                node.text.startsWith("Vct") -> {
                    val vct = ctx.vectors[node.text] ?: throw IllegalArgumentException("Dimension FEHLER")
                    SymbolicValue.VectorResult(vct)
                }
                ScientificCatalog.constantMap.containsKey(node.text) -> {
                    SymbolicValue.Decimal(BigDecimal.valueOf(ScientificCatalog.constantMap.getValue(node.text)))
                }
                node.kind == TokenKind.VARIABLE -> {
                    val v = ctx.variables[node.text] ?: 0.0
                    SymbolicValue.ofDouble(v)
                }
                node.kind == TokenKind.FUNCTION -> parseFunctionCall(node.text)
                else -> throw IllegalArgumentException("Syntax FEHLER")
            }
        }

        private fun parseFunctionCall(fnToken: String): SymbolicValue {
            val name = fnToken.removeSuffix("(")
            val args = mutableListOf<SymbolicValue>()
            if (hasMore()) {
                val p = peek() as? MathNode.TokenNode
                if (p == null || p.text != ")") {
                    args.add(parseExpression())
                    while (hasMore()) {
                        val sep = peek() as? MathNode.TokenNode
                        if (sep != null && sep.text == ";") {
                            consume()
                            args.add(parseExpression())
                        } else break
                    }
                }
            }
            val close = peek() as? MathNode.TokenNode
            if (close != null && close.text == ")") consume()

            return evaluateFunction(name, args)
        }

        private fun evaluateFunction(name: String, args: List<SymbolicValue>): SymbolicValue {
            if (args.isEmpty()) throw IllegalArgumentException("Argument FEHLER")
            val a0 = args[0]
            return when (name) {
                "sin" -> evalTrig(a0, TrigFn.SIN)
                "cos" -> evalTrig(a0, TrigFn.COS)
                "tan" -> evalTrig(a0, TrigFn.TAN)
                "sin⁻¹" -> evalInverseTrig(a0, TrigFn.SIN)
                "cos⁻¹" -> evalInverseTrig(a0, TrigFn.COS)
                "tan⁻¹" -> evalInverseTrig(a0, TrigFn.TAN)
                "sinh" -> SymbolicValue.ofDouble(sinh(requireRealDouble(a0)))
                "cosh" -> SymbolicValue.ofDouble(cosh(requireRealDouble(a0)))
                "tanh" -> SymbolicValue.ofDouble(tanh(requireRealDouble(a0)))
                "ln" -> evalLn(a0)
                "log" -> evalLog10(a0)
                "Abs" -> evalAbs(a0)
                "Arg" -> {
                    val c = toComplex(a0)
                    val rad = atan2(c.imag, c.real)
                    SymbolicValue.ofDouble(fromRadians(rad, ctx.angleUnit))
                }
                "Conjg" -> {
                    val c = toComplex(a0)
                    SymbolicValue.ComplexVal(c.real, -c.imag)
                }
                "ReP" -> SymbolicValue.ofDouble(toComplex(a0).real)
                "ImP" -> SymbolicValue.ofDouble(toComplex(a0).imag)
                "Int" -> SymbolicValue.ofLong(requireRealDouble(a0).toLong())
                "Intg" -> SymbolicValue.ofLong(floor(requireRealDouble(a0)).toLong())
                "Rnd" -> {
                    val v = BigDecimal.valueOf(requireRealDouble(a0)).setScale(10, java.math.RoundingMode.HALF_UP).toDouble()
                    SymbolicValue.ofDouble(v)
                }
                "GCD", "ggT" -> {
                    if (args.size < 2) throw IllegalArgumentException("Argument FEHLER")
                    val x = BigInteger.valueOf(requireRealDouble(args[0]).roundToLong())
                    val y = BigInteger.valueOf(requireRealDouble(args[1]).roundToLong())
                    SymbolicValue.ExactRational(Rational.of(Rational.gcd(x, y), BigInteger.ONE))
                }
                "LCM", "kgV" -> {
                    if (args.size < 2) throw IllegalArgumentException("Argument FEHLER")
                    val x = BigInteger.valueOf(requireRealDouble(args[0]).roundToLong())
                    val y = BigInteger.valueOf(requireRealDouble(args[1]).roundToLong())
                    SymbolicValue.ExactRational(Rational.of(Rational.lcm(x, y), BigInteger.ONE))
                }
                "RanInt#" -> {
                    if (args.size < 2) throw IllegalArgumentException("Argument FEHLER")
                    val lo = requireRealDouble(args[0]).roundToInt()
                    val hi = requireRealDouble(args[1]).roundToInt()
                    if (hi < lo) throw IllegalArgumentException("Argument FEHLER")
                    SymbolicValue.ofLong(Random.nextInt(lo, hi + 1).toLong())
                }
                "Pol" -> {
                    if (args.size < 2) throw IllegalArgumentException("Argument FEHLER")
                    val x = args[0].toDouble()
                    val y = args[1].toDouble()
                    val r = hypot(x, y)
                    val theta = fromRadians(atan2(y, x), ctx.angleUnit)
                    ctx.onPolRecStore?.invoke(r, theta)
                    SymbolicValue.ComplexVal(r, theta)
                }
                "Rec" -> {
                    if (args.size < 2) throw IllegalArgumentException("Argument FEHLER")
                    val r = args[0].toDouble()
                    val theta = toRadians(args[1].toDouble(), ctx.angleUnit)
                    val x = r * cos(theta)
                    val y = r * sin(theta)
                    ctx.onPolRecStore?.invoke(x, y)
                    SymbolicValue.ComplexVal(x, y)
                }
                "Det" -> {
                    val mat = (a0 as? SymbolicValue.MatrixResult)?.data
                        ?: throw IllegalArgumentException("Dimension FEHLER")
                    SymbolicValue.ofDouble(MatrixEngine.determinant(mat))
                }
                "Trn" -> {
                    val mat = (a0 as? SymbolicValue.MatrixResult)?.data
                        ?: throw IllegalArgumentException("Dimension FEHLER")
                    SymbolicValue.MatrixResult(MatrixEngine.transpose(mat))
                }
                "Einheitsv" -> {
                    val v = (a0 as? SymbolicValue.VectorResult)?.components
                        ?: throw IllegalArgumentException("Dimension FEHLER")
                    SymbolicValue.VectorResult(VectorEngine.unitVector(v))
                }
                "Winkel" -> {
                    if (args.size < 2) throw IllegalArgumentException("Argument FEHLER")
                    val v1 = (args[0] as? SymbolicValue.VectorResult)?.components
                        ?: throw IllegalArgumentException("Dimension FEHLER")
                    val v2 = (args[1] as? SymbolicValue.VectorResult)?.components
                        ?: throw IllegalArgumentException("Dimension FEHLER")
                    val rad = VectorEngine.angleRadians(v1, v2)
                    SymbolicValue.ofDouble(fromRadians(rad, ctx.angleUnit))
                }
                else -> throw IllegalArgumentException("Syntax FEHLER")
            }
        }

        // --- Exact Symbolic Arithmetic Operations ---

        private fun requireRealDouble(v: SymbolicValue): Double {
            if (v is SymbolicValue.Error) throw ArithmeticException(v.message)
            if (v is SymbolicValue.ComplexVal) {
                if (abs(v.imag) > 1e-12) throw ArithmeticException("Math FEHLER")
                return v.real
            }
            val d = v.toDouble()
            if (!d.isFinite()) throw ArithmeticException("Math FEHLER")
            return d
        }

        private fun negate(v: SymbolicValue): SymbolicValue = when (v) {
            is SymbolicValue.Error -> v
            is SymbolicValue.ExactRational -> SymbolicValue.ExactRational(-v.value)
            is SymbolicValue.Radical -> SymbolicValue.Radical(-v.constant, -v.coeff, v.radicand)
            is SymbolicValue.PiMultiple -> SymbolicValue.PiMultiple(-v.coeff)
            is SymbolicValue.ComplexVal -> SymbolicValue.ComplexVal(-v.real, -v.imag)
            is SymbolicValue.DmsValue -> SymbolicValue.DmsValue(-v.degrees)
            is SymbolicValue.MatrixResult -> SymbolicValue.MatrixResult(MatrixEngine.scale(v.data, -1.0))
            is SymbolicValue.VectorResult -> SymbolicValue.VectorResult(VectorEngine.scale(v.components, -1.0))
            else -> SymbolicValue.ofDouble(-v.toDouble())
        }

        private fun add(a: SymbolicValue, b: SymbolicValue): SymbolicValue {
            if (a is SymbolicValue.Error) return a
            if (b is SymbolicValue.Error) return b
            if (a is SymbolicValue.MatrixResult && b is SymbolicValue.MatrixResult) {
                return SymbolicValue.MatrixResult(MatrixEngine.add(a.data, b.data))
            }
            if (a is SymbolicValue.VectorResult && b is SymbolicValue.VectorResult) {
                return SymbolicValue.VectorResult(VectorEngine.add(a.components, b.components))
            }
            if (a is SymbolicValue.ComplexVal || b is SymbolicValue.ComplexVal) {
                val ca = toComplex(a); val cb = toComplex(b)
                return SymbolicValue.ComplexVal(ca.real + cb.real, ca.imag + cb.imag)
            }
            return when {
                a is SymbolicValue.ExactRational && b is SymbolicValue.ExactRational ->
                    SymbolicValue.ExactRational(a.value + b.value)
                a is SymbolicValue.PiMultiple && b is SymbolicValue.PiMultiple ->
                    SymbolicValue.simplifyPi(a.coeff + b.coeff)
                a is SymbolicValue.ExactRational && b is SymbolicValue.Radical ->
                    SymbolicValue.simplifyRadical(a.value + b.constant, b.coeff, b.radicand)
                a is SymbolicValue.Radical && b is SymbolicValue.ExactRational ->
                    SymbolicValue.simplifyRadical(a.constant + b.value, a.coeff, a.radicand)
                a is SymbolicValue.Radical && b is SymbolicValue.Radical && a.radicand == b.radicand ->
                    SymbolicValue.simplifyRadical(a.constant + b.constant, a.coeff + b.coeff, a.radicand)
                else -> SymbolicValue.ofDouble(a.toDouble() + b.toDouble())
            }
        }

        private fun subtract(a: SymbolicValue, b: SymbolicValue): SymbolicValue = add(a, negate(b))

        private fun multiply(a: SymbolicValue, b: SymbolicValue): SymbolicValue {
            if (a is SymbolicValue.MatrixResult && b is SymbolicValue.MatrixResult) {
                return SymbolicValue.MatrixResult(MatrixEngine.multiply(a.data, b.data))
            }
            if (a is SymbolicValue.MatrixResult) {
                return SymbolicValue.MatrixResult(MatrixEngine.scale(a.data, b.toDouble()))
            }
            if (b is SymbolicValue.MatrixResult) {
                return SymbolicValue.MatrixResult(MatrixEngine.scale(b.data, a.toDouble()))
            }
            if (a is SymbolicValue.VectorResult && b is SymbolicValue.VectorResult) {
                return SymbolicValue.VectorResult(VectorEngine.crossProduct(a.components, b.components))
            }
            if (a is SymbolicValue.VectorResult) {
                return SymbolicValue.VectorResult(VectorEngine.scale(a.components, b.toDouble()))
            }
            if (b is SymbolicValue.VectorResult) {
                return SymbolicValue.VectorResult(VectorEngine.scale(b.components, a.toDouble()))
            }
            if (a is SymbolicValue.ComplexVal || b is SymbolicValue.ComplexVal) {
                val ca = toComplex(a); val cb = toComplex(b)
                return SymbolicValue.ComplexVal(
                    ca.real * cb.real - ca.imag * cb.imag,
                    ca.real * cb.imag + ca.imag * cb.real
                )
            }
            return when {
                a is SymbolicValue.ExactRational && b is SymbolicValue.ExactRational ->
                    SymbolicValue.ExactRational(a.value * b.value)
                a is SymbolicValue.ExactRational && b is SymbolicValue.PiMultiple ->
                    SymbolicValue.simplifyPi(a.value * b.coeff)
                a is SymbolicValue.PiMultiple && b is SymbolicValue.ExactRational ->
                    SymbolicValue.simplifyPi(a.coeff * b.value)
                a is SymbolicValue.ExactRational && b is SymbolicValue.Radical ->
                    SymbolicValue.simplifyRadical(a.value * b.constant, a.value * b.coeff, b.radicand)
                a is SymbolicValue.Radical && b is SymbolicValue.ExactRational ->
                    SymbolicValue.simplifyRadical(a.constant * b.value, a.coeff * b.value, a.radicand)
                a is SymbolicValue.Radical && b is SymbolicValue.Radical &&
                    a.constant.isZero && b.constant.isZero -> {
                    SymbolicValue.simplifyRadical(
                        Rational.ZERO,
                        a.coeff * b.coeff,
                        a.radicand * b.radicand
                    )
                }
                a is SymbolicValue.Radical && b is SymbolicValue.Radical && a.radicand == b.radicand -> {
                    // (p + q√r)(u + v√r) = (pu + qvr) + (pv + qu)√r
                    val newConst = a.constant * b.constant + a.coeff * b.coeff * Rational.of(a.radicand, BigInteger.ONE)
                    val newCoeff = a.constant * b.coeff + a.coeff * b.constant
                    SymbolicValue.simplifyRadical(newConst, newCoeff, a.radicand)
                }
                else -> SymbolicValue.ofDouble(a.toDouble() * b.toDouble())
            }
        }

        private fun dotProduct(a: SymbolicValue, b: SymbolicValue): SymbolicValue {
            if (a is SymbolicValue.VectorResult && b is SymbolicValue.VectorResult) {
                return SymbolicValue.ofDouble(VectorEngine.dotProduct(a.components, b.components))
            }
            return multiply(a, b)
        }

        private fun divide(a: SymbolicValue, b: SymbolicValue): SymbolicValue {
            if (b is SymbolicValue.ComplexVal || a is SymbolicValue.ComplexVal) {
                val ca = toComplex(a); val cb = toComplex(b)
                val denom = cb.real * cb.real + cb.imag * cb.imag
                if (denom == 0.0) throw ArithmeticException("Division durch Null")
                return SymbolicValue.ComplexVal(
                    (ca.real * cb.real + ca.imag * cb.imag) / denom,
                    (ca.imag * cb.real - ca.real * cb.imag) / denom
                )
            }
            if (abs(b.toDouble()) == 0.0) throw ArithmeticException("Division durch Null")
            return when {
                a is SymbolicValue.ExactRational && b is SymbolicValue.ExactRational ->
                    SymbolicValue.ExactRational(a.value / b.value)
                a is SymbolicValue.PiMultiple && b is SymbolicValue.ExactRational ->
                    SymbolicValue.simplifyPi(a.coeff / b.value)
                a is SymbolicValue.PiMultiple && b is SymbolicValue.PiMultiple ->
                    SymbolicValue.ExactRational(a.coeff / b.coeff)
                a is SymbolicValue.Radical && b is SymbolicValue.ExactRational ->
                    SymbolicValue.simplifyRadical(a.constant / b.value, a.coeff / b.value, a.radicand)
                // Rationalize denominator: a / (p + q√r) = a*(p - q√r) / (p² - q²r)
                a is SymbolicValue.ExactRational && b is SymbolicValue.Radical -> {
                    val norm = b.constant * b.constant - b.coeff * b.coeff * Rational.of(b.radicand, BigInteger.ONE)
                    if (norm.isZero) throw ArithmeticException("Division durch Null")
                    val conj = SymbolicValue.Radical(b.constant / norm, (-b.coeff) / norm, b.radicand)
                    multiply(a, conj)
                }
                a is SymbolicValue.Radical && b is SymbolicValue.Radical -> {
                    val norm = b.constant * b.constant - b.coeff * b.coeff * Rational.of(b.radicand, BigInteger.ONE)
                    if (norm.isZero) throw ArithmeticException("Division durch Null")
                    val conj = SymbolicValue.Radical(b.constant / norm, (-b.coeff) / norm, b.radicand)
                    multiply(a, conj)
                }
                else -> SymbolicValue.ofDouble(a.toDouble() / b.toDouble())
            }
        }

        private fun power(base: SymbolicValue, exp: SymbolicValue): SymbolicValue {
            if (base is SymbolicValue.MatrixResult) {
                val e = exp.toDouble().roundToInt()
                return when (e) {
                    -1 -> SymbolicValue.MatrixResult(MatrixEngine.inverse(base.data))
                    0 -> SymbolicValue.MatrixResult(MatrixEngine.identity(base.data.size))
                    in 1..8 -> {
                        var res = base.data
                        repeat(e - 1) { res = MatrixEngine.multiply(res, base.data) }
                        SymbolicValue.MatrixResult(res)
                    }
                    else -> throw ArithmeticException("Math FEHLER")
                }
            }
            if (base is SymbolicValue.ComplexVal || exp is SymbolicValue.ComplexVal) {
                val cb = toComplex(base); val ce = toComplex(exp)
                val r = hypot(cb.real, cb.imag)
                val theta = atan2(cb.imag, cb.real)
                if (r == 0.0) return SymbolicValue.ComplexVal(0.0, 0.0)
                val lnr = ln(r)
                val mag = kotlin.math.exp(ce.real * lnr - ce.imag * theta)
                val ang = ce.real * theta + ce.imag * lnr
                return SymbolicValue.ComplexVal(mag * cos(ang), mag * sin(ang))
            }
            if (exp is SymbolicValue.ExactRational && exp.value.isInteger) {
                val intExp = exp.value.num.toIntOrNull()
                if (intExp != null && abs(intExp) <= 20) {
                    if (base is SymbolicValue.ExactRational) {
                        return SymbolicValue.ExactRational(base.value.pow(intExp))
                    }
                    if (base is SymbolicValue.Radical && intExp >= 0) {
                        var acc: SymbolicValue = SymbolicValue.ExactRational(Rational.ONE)
                        repeat(intExp) { acc = multiply(acc, base) }
                        return acc
                    }
                    if (base is SymbolicValue.Radical && intExp == -1) {
                        return divide(SymbolicValue.ExactRational(Rational.ONE), base)
                    }
                }
            }
            if (exp is SymbolicValue.ExactRational && exp.value == Rational.HALF) {
                return squareRoot(base)
            }
            val bVal = base.toDouble()
            val eVal = exp.toDouble()
            if (bVal < 0.0 && abs(eVal - eVal.roundToInt()) > 1e-12) {
                if (ctx.complexMode) {
                    val r = abs(bVal)
                    val mag = r.pow(eVal)
                    val ang = PI * eVal
                    return SymbolicValue.ComplexVal(mag * cos(ang), mag * sin(ang))
                }
                throw ArithmeticException("Math FEHLER")
            }
            return SymbolicValue.ofDouble(bVal.pow(eVal))
        }

        private fun squareRoot(v: SymbolicValue): SymbolicValue {
            if (v is SymbolicValue.ComplexVal) {
                val r = hypot(v.real, v.imag)
                val theta = atan2(v.imag, v.real) / 2.0
                return SymbolicValue.ComplexVal(sqrt(r) * cos(theta), sqrt(r) * sin(theta))
            }
            if (v is SymbolicValue.ExactRational) {
                if (v.value.isNegative) {
                    if (ctx.complexMode) {
                        return SymbolicValue.ComplexVal(0.0, sqrt((-v.value).toDouble()))
                    }
                    throw ArithmeticException("Math FEHLER")
                }
                // sqrt(p/q) = sqrt(p*q) / q
                val pq = v.value.num * v.value.den
                return SymbolicValue.simplifyRadical(
                    Rational.ZERO,
                    Rational.of(BigInteger.ONE, v.value.den),
                    pq
                )
            }
            val d = v.toDouble()
            if (d < 0) {
                if (ctx.complexMode) return SymbolicValue.ComplexVal(0.0, sqrt(-d))
                throw ArithmeticException("Math FEHLER")
            }
            return SymbolicValue.ofDouble(sqrt(d))
        }

        private fun nthRoot(index: SymbolicValue, radicand: SymbolicValue): SymbolicValue {
            val n = index.toDouble()
            if (n == 0.0) throw ArithmeticException("Math FEHLER")
            if (abs(n - 2.0) < 1e-12) return squareRoot(radicand)
            val x = radicand.toDouble()
            if (x < 0.0) {
                val nInt = n.roundToInt()
                if (abs(n - nInt) < 1e-12 && nInt % 2 != 0) {
                    return SymbolicValue.ofDouble(-((-x).pow(1.0 / n)))
                }
                if (ctx.complexMode) {
                    val mag = (-x).pow(1.0 / n)
                    val ang = PI / n
                    return SymbolicValue.ComplexVal(mag * cos(ang), mag * sin(ang))
                }
                throw ArithmeticException("Math FEHLER")
            }
            return SymbolicValue.ofDouble(x.pow(1.0 / n))
        }

        private fun evalAbs(v: SymbolicValue): SymbolicValue = when (v) {
            is SymbolicValue.Error -> v
            is SymbolicValue.ExactRational -> SymbolicValue.ExactRational(v.value.abs())
            is SymbolicValue.PiMultiple -> SymbolicValue.PiMultiple(v.coeff.abs())
            is SymbolicValue.Radical -> if (v.constant.isZero) {
                SymbolicValue.Radical(Rational.ZERO, v.coeff.abs(), v.radicand)
            } else {
                SymbolicValue.ofDouble(abs(v.toDouble()))
            }
            is SymbolicValue.ComplexVal -> SymbolicValue.ofDouble(hypot(v.real, v.imag))
            is SymbolicValue.VectorResult -> SymbolicValue.ofDouble(VectorEngine.magnitude(v.components))
            else -> SymbolicValue.ofDouble(abs(v.toDouble()))
        }

        private fun evalLn(v: SymbolicValue): SymbolicValue {
            if (v is SymbolicValue.ComplexVal) {
                val r = hypot(v.real, v.imag)
                if (r == 0.0) throw ArithmeticException("Math FEHLER")
                return SymbolicValue.ComplexVal(ln(r), atan2(v.imag, v.real))
            }
            val d = requireRealDouble(v)
            if (d <= 0.0) throw ArithmeticException("Math FEHLER")
            return SymbolicValue.ofDouble(ln(d))
        }

        private fun evalLog10(v: SymbolicValue): SymbolicValue {
            val d = requireRealDouble(v)
            if (d <= 0.0) throw ArithmeticException("Math FEHLER")
            return SymbolicValue.ofDouble(log10(d))
        }

        private fun evalLogBase(base: SymbolicValue, arg: SymbolicValue): SymbolicValue {
            val b = requireRealDouble(base)
            val x = requireRealDouble(arg)
            if (b <= 0.0 || b == 1.0 || x <= 0.0) throw ArithmeticException("Math FEHLER")
            return SymbolicValue.ofDouble(ln(x) / ln(b))
        }

        private fun factorial(v: SymbolicValue): SymbolicValue {
            val d = requireRealDouble(v)
            if (d < 0.0 || d > 69.0 || abs(d - d.roundToInt()) > 1e-10) {
                throw ArithmeticException("Math FEHLER")
            }
            val n = d.roundToInt()
            var acc = BigInteger.ONE
            for (i in 2..n) acc = acc.multiply(BigInteger.valueOf(i.toLong()))
            return if (n <= 20) {
                SymbolicValue.ExactRational(Rational.of(acc, BigInteger.ONE))
            } else {
                SymbolicValue.Decimal(BigDecimal(acc))
            }
        }

        private fun nPr(nVal: SymbolicValue, rVal: SymbolicValue): SymbolicValue {
            val nD = requireRealDouble(nVal)
            val rD = requireRealDouble(rVal)
            if (abs(nD - nD.roundToInt()) > 1e-10 || abs(rD - rD.roundToInt()) > 1e-10) {
                throw ArithmeticException("Math FEHLER")
            }
            val n = nD.roundToInt()
            val r = rD.roundToInt()
            if (n < 0 || r < 0 || r > n || n > 69) throw ArithmeticException("Math FEHLER")
            var acc = BigInteger.ONE
            for (i in (n - r + 1)..n) acc = acc.multiply(BigInteger.valueOf(i.toLong()))
            return SymbolicValue.ExactRational(Rational.of(acc, BigInteger.ONE))
        }

        private fun nCr(nVal: SymbolicValue, rVal: SymbolicValue): SymbolicValue {
            val nD = requireRealDouble(nVal)
            val rD = requireRealDouble(rVal)
            if (abs(nD - nD.roundToInt()) > 1e-10 || abs(rD - rD.roundToInt()) > 1e-10) {
                throw ArithmeticException("Math FEHLER")
            }
            val n = nD.roundToInt()
            val r = rD.roundToInt()
            if (n < 0 || r < 0 || r > n || n > 69) throw ArithmeticException("Math FEHLER")
            val k = min(r, n - r)
            var acc = BigInteger.ONE
            for (i in 1..k) {
                acc = acc.multiply(BigInteger.valueOf((n - k + i).toLong()))
                    .divide(BigInteger.valueOf(i.toLong()))
            }
            return SymbolicValue.ExactRational(Rational.of(acc, BigInteger.ONE))
        }

        private enum class TrigFn { SIN, COS, TAN }

        private fun evalTrig(arg: SymbolicValue, fn: TrigFn): SymbolicValue {
            val argVal = requireRealDouble(arg)
            // Compute normalized angle in degrees to recognize exact special values
            val deg: Double = when (ctx.angleUnit) {
                AngleUnit.DEG -> argVal
                AngleUnit.RAD -> argVal * 180.0 / PI
                AngleUnit.GRAD -> argVal * 0.9
            }
            val exactDeg = deg.roundToInt()
            if (abs(deg - exactDeg) < 1e-9) {
                val mod360 = ((exactDeg % 360) + 360) % 360
                val exact = exactTrigForDegree(mod360, fn)
                if (exact != null) return exact
            }
            val rad = toRadians(argVal, ctx.angleUnit)
            return when (fn) {
                TrigFn.SIN -> SymbolicValue.ofDouble(sin(rad))
                TrigFn.COS -> SymbolicValue.ofDouble(cos(rad))
                TrigFn.TAN -> {
                    if (abs(cos(rad)) < 1e-12) throw ArithmeticException("Math FEHLER")
                    SymbolicValue.ofDouble(tan(rad))
                }
            }
        }

        private fun exactTrigForDegree(deg: Int, fn: TrigFn): SymbolicValue? {
            return when (fn) {
                TrigFn.SIN -> when (deg) {
                    0, 180 -> SymbolicValue.ExactRational(Rational.ZERO)
                    90 -> SymbolicValue.ExactRational(Rational.ONE)
                    270 -> SymbolicValue.ExactRational(Rational.MINUS_ONE)
                    30, 150 -> SymbolicValue.ExactRational(Rational.HALF)
                    210, 330 -> SymbolicValue.ExactRational(-Rational.HALF)
                    45, 135 -> SymbolicValue.Radical(Rational.ZERO, Rational.HALF, BigInteger.TWO)
                    225, 315 -> SymbolicValue.Radical(Rational.ZERO, -Rational.HALF, BigInteger.TWO)
                    60, 120 -> SymbolicValue.Radical(Rational.ZERO, Rational.HALF, BigInteger.valueOf(3))
                    240, 300 -> SymbolicValue.Radical(Rational.ZERO, -Rational.HALF, BigInteger.valueOf(3))
                    else -> null
                }
                TrigFn.COS -> exactTrigForDegree((deg + 90) % 360, TrigFn.SIN)
                TrigFn.TAN -> when (deg) {
                    90, 270 -> throw ArithmeticException("Math FEHLER")
                    0, 180 -> SymbolicValue.ExactRational(Rational.ZERO)
                    45, 225 -> SymbolicValue.ExactRational(Rational.ONE)
                    135, 315 -> SymbolicValue.ExactRational(Rational.MINUS_ONE)
                    30, 210 -> SymbolicValue.Radical(Rational.ZERO, Rational.of(1, 3), BigInteger.valueOf(3))
                    150, 330 -> SymbolicValue.Radical(Rational.ZERO, Rational.of(-1, 3), BigInteger.valueOf(3))
                    60, 240 -> SymbolicValue.Radical(Rational.ZERO, Rational.ONE, BigInteger.valueOf(3))
                    120, 300 -> SymbolicValue.Radical(Rational.ZERO, Rational.MINUS_ONE, BigInteger.valueOf(3))
                    else -> null
                }
            }
        }

        private fun evalInverseTrig(arg: SymbolicValue, fn: TrigFn): SymbolicValue {
            val d = requireRealDouble(arg)
            val rad = when (fn) {
                TrigFn.SIN -> {
                    if (d < -1.0 || d > 1.0) throw ArithmeticException("Math FEHLER")
                    asin(d)
                }
                TrigFn.COS -> {
                    if (d < -1.0 || d > 1.0) throw ArithmeticException("Math FEHLER")
                    acos(d)
                }
                TrigFn.TAN -> atan(d)
            }
            if (ctx.angleUnit == AngleUnit.RAD) {
                val piRatio = rad / PI
                val approx = Rational.approximate(piRatio, maxDen = 12L, tolerance = 1e-10)
                if (approx != null) return SymbolicValue.simplifyPi(approx)
            }
            return SymbolicValue.ofDouble(fromRadians(rad, ctx.angleUnit))
        }

        private fun toComplex(v: SymbolicValue): SymbolicValue.ComplexVal = when (v) {
            is SymbolicValue.ComplexVal -> v
            else -> SymbolicValue.ComplexVal(v.toDouble(), 0.0)
        }

        private fun toRadians(v: Double, unit: AngleUnit): Double = when (unit) {
            AngleUnit.DEG -> Math.toRadians(v)
            AngleUnit.RAD -> v
            AngleUnit.GRAD -> v * PI / 200.0
        }

        private fun fromRadians(rad: Double, unit: AngleUnit): Double = when (unit) {
            AngleUnit.DEG -> Math.toDegrees(rad)
            AngleUnit.RAD -> rad
            AngleUnit.GRAD -> rad * 200.0 / PI
        }

        private fun BigInteger.toIntOrNull(): Int? =
            if (this >= BigInteger.valueOf(Int.MIN_VALUE.toLong()) &&
                this <= BigInteger.valueOf(Int.MAX_VALUE.toLong())
            ) toInt() else null
    }

    // --- 15-Point Adaptive Gauss-Kronrod Quadrature for Definite Integrals ---
    private val gkNodes = doubleArrayOf(
        -0.9914553711208126, -0.9491079123427585, -0.8648644233597691,
        -0.7415311855993945, -0.5860872354676911, -0.4058451513773972,
        -0.2077849550078985, 0.0,
        0.2077849550078985, 0.4058451513773972, 0.5860872354676911,
        0.7415311855993945, 0.8648644233597691, 0.9491079123427585,
        0.9914553711208126
    )
    private val gkWeights = doubleArrayOf(
        0.02293532201052922, 0.06309209262997855, 0.10479001032225018,
        0.14065325971552592, 0.16900472663926790, 0.19035057806478541,
        0.20443294007529889, 0.20948214108472782,
        0.20443294007529889, 0.19035057806478541, 0.16900472663926790,
        0.14065325971552592, 0.10479001032225018, 0.06309209262997855,
        0.02293532201052922
    )
    private val gWeights = doubleArrayOf(
        0.1294849661688697, 0.27970539148927664, 0.3818300505051189,
        0.4179591836734694,
        0.3818300505051189, 0.27970539148927664, 0.1294849661688697
    )

    fun gaussKronrodIntegrate(a: Double, b: Double, f: (Double) -> Double): Double {
        if (!a.isFinite() || !b.isFinite()) throw ArithmeticException("Math FEHLER")
        if (a == b) return 0.0
        val sign = if (a > b) -1.0 else 1.0
        val lo = min(a, b)
        val hi = max(a, b)
        return sign * adaptiveGK(lo, hi, f, 1e-9, 10)
    }

    private fun adaptiveGK(a: Double, b: Double, f: (Double) -> Double, tol: Double, depth: Int): Double {
        val mid = 0.5 * (a + b)
        val half = 0.5 * (b - a)
        var kSum = 0.0
        var gSum = 0.0
        for (i in gkNodes.indices) {
            val fx = f(mid + half * gkNodes[i])
            if (!fx.isFinite()) throw ArithmeticException("Math FEHLER")
            kSum += gkWeights[i] * fx
            if (i % 2 == 1) {
                gSum += gWeights[i / 2] * fx
            }
        }
        val kRes = kSum * half
        val gRes = gSum * half
        val err = abs(kRes - gRes)
        return if (err <= tol * max(1.0, abs(kRes)) || depth <= 0) {
            kRes
        } else {
            adaptiveGK(a, mid, f, tol * 0.5, depth - 1) +
                adaptiveGK(mid, b, f, tol * 0.5, depth - 1)
        }
    }

    fun numericalDerivative(x0: Double, f: (Double) -> Double): Double {
        val h = max(1e-5, abs(x0) * 1e-5)
        val d1 = (f(x0 + h) - f(x0 - h)) / (2.0 * h)
        val h2 = h * 0.5
        val d2 = (f(x0 + h2) - f(x0 - h2)) / (2.0 * h2)
        // Richardson extrapolation O(h^4)
        val res = (4.0 * d2 - d1) / 3.0
        if (!res.isFinite()) throw ArithmeticException("Math FEHLER")
        return res
    }
}
