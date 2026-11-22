package com.naveen.fx991dex.engine

import java.util.concurrent.atomic.AtomicLong

enum class TokenKind {
    DIGIT,
    DECIMAL,
    OPERATOR,
    FUNCTION,
    CONSTANT,
    VARIABLE,
    PAREN,
    SEPARATOR,
    SYMBOL,
    COMPARE
}

/**
 * Ordered list of AST nodes inside a single editable slot (e.g. root line, numerator, exponent).
 */
data class MathSequence(
    val id: String,
    val nodes: List<MathNode> = emptyList()
) {
    val isEmpty: Boolean get() = nodes.isEmpty()
}

/**
 * AST nodes for Natural Textbook Display (Natürliche Darstellung).
 */
sealed class MathNode {
    abstract val id: String

    data class TokenNode(
        override val id: String,
        val text: String,
        val kind: TokenKind = TokenKind.SYMBOL
    ) : MathNode()

    data class FractionNode(
        override val id: String,
        val numerator: MathSequence,
        val denominator: MathSequence
    ) : MathNode()

    data class MixedFractionNode(
        override val id: String,
        val whole: MathSequence,
        val numerator: MathSequence,
        val denominator: MathSequence
    ) : MathNode()

    data class RootNode(
        override val id: String,
        val radicand: MathSequence
    ) : MathNode()

    data class NthRootNode(
        override val id: String,
        val index: MathSequence,
        val radicand: MathSequence
    ) : MathNode()

    /**
     * Attached superscript exponent (placed right after the base expression).
     */
    data class PowerNode(
        override val id: String,
        val exponent: MathSequence
    ) : MathNode()

    data class SubscriptNode(
        override val id: String,
        val baseLabel: String,
        val subscript: MathSequence
    ) : MathNode()

    data class LogBaseNode(
        override val id: String,
        val base: MathSequence,
        val argument: MathSequence
    ) : MathNode()

    data class IntegralNode(
        override val id: String,
        val body: MathSequence,
        val lower: MathSequence,
        val upper: MathSequence
    ) : MathNode()

    data class DerivativeNode(
        override val id: String,
        val body: MathSequence,
        val atX: MathSequence
    ) : MathNode()

    data class SumProdNode(
        override val id: String,
        val isProduct: Boolean,
        val body: MathSequence,
        val lower: MathSequence,
        val upper: MathSequence
    ) : MathNode()

    data class AbsNode(
        override val id: String,
        val inner: MathSequence
    ) : MathNode()

    data class MatrixNode(
        override val id: String,
        val rows: Int,
        val cols: Int,
        val cells: List<List<MathSequence>>
    ) : MathNode()

    fun childSequences(): List<MathSequence> = when (this) {
        is TokenNode -> emptyList()
        is FractionNode -> listOf(numerator, denominator)
        is MixedFractionNode -> listOf(whole, numerator, denominator)
        is RootNode -> listOf(radicand)
        is NthRootNode -> listOf(index, radicand)
        is PowerNode -> listOf(exponent)
        is SubscriptNode -> listOf(subscript)
        is LogBaseNode -> listOf(base, argument)
        is IntegralNode -> listOf(body, lower, upper)
        is DerivativeNode -> listOf(body, atX)
        is SumProdNode -> listOf(body, lower, upper)
        is AbsNode -> listOf(inner)
        is MatrixNode -> cells.flatten()
    }
}

data class CursorLocation(
    val sequenceId: String,
    val index: Int
)

data class EditorDocument(
    val root: MathSequence,
    val cursor: CursorLocation
)

/**
 * Pure immutable AST manipulation & 4-way D-Pad cursor traversal engine.
 */
object AstEditor {
    private val idCounter = AtomicLong(1L)

    fun nextId(prefix: String = "n"): String = "${prefix}_${idCounter.getAndIncrement()}"

    fun emptyDocument(): EditorDocument {
        val rootId = nextId("root")
        return EditorDocument(
            root = MathSequence(rootId, emptyList()),
            cursor = CursorLocation(rootId, 0)
        )
    }

    fun token(text: String, kind: TokenKind = TokenKind.SYMBOL): MathNode.TokenNode =
        MathNode.TokenNode(nextId("t"), text, kind)

    fun seq(vararg nodes: MathNode): MathSequence =
        MathSequence(nextId("s"), nodes.toList())

    fun seqOfText(text: String, kind: TokenKind = TokenKind.SYMBOL): MathSequence =
        MathSequence(nextId("s"), text.map { token(it.toString(), kind) })

    fun insertToken(doc: EditorDocument, text: String, kind: TokenKind): EditorDocument {
        val node = token(text, kind)
        return insertNodes(doc, listOf(node))
    }

    fun insertNodes(doc: EditorDocument, newNodes: List<MathNode>): EditorDocument {
        val updated = updateSequence(doc.root, doc.cursor.sequenceId) { seq ->
            val idx = doc.cursor.index.coerceIn(0, seq.nodes.size)
            val list = seq.nodes.toMutableList()
            list.addAll(idx, newNodes)
            seq.copy(nodes = list)
        }
        return EditorDocument(
            root = updated,
            cursor = doc.cursor.copy(index = doc.cursor.index + newNodes.size)
        )
    }

    /**
     * Inserts a fraction template. If digits/tokens immediately precede the cursor,
     * lifts that contiguous operand into the numerator just like a real CASIO ClassWiz!
     */
    fun insertFraction(doc: EditorDocument): EditorDocument {
        val currentSeq = findSequence(doc.root, doc.cursor.sequenceId) ?: return doc
        val idx = doc.cursor.index.coerceIn(0, currentSeq.nodes.size)

        // Check if we can lift trailing operand (digits/decimal/variables/constants) into numerator
        var liftStart = idx
        while (liftStart > 0) {
            val prev = currentSeq.nodes[liftStart - 1]
            val canLift = when (prev) {
                is MathNode.TokenNode -> prev.kind in setOf(
                    TokenKind.DIGIT,
                    TokenKind.DECIMAL,
                    TokenKind.VARIABLE,
                    TokenKind.CONSTANT
                )
                is MathNode.RootNode, is MathNode.PowerNode, is MathNode.AbsNode -> true
                else -> false
            }
            if (canLift) liftStart-- else break
        }

        val liftedNodes = if (liftStart < idx) currentSeq.nodes.subList(liftStart, idx) else emptyList()
        val numSeq = MathSequence(nextId("num"), liftedNodes)
        val denSeq = MathSequence(nextId("den"), emptyList())
        val fracNode = MathNode.FractionNode(nextId("frac"), numSeq, denSeq)

        val updatedRoot = updateSequence(doc.root, doc.cursor.sequenceId) { s ->
            val list = s.nodes.toMutableList()
            if (liftStart < idx) {
                list.subList(liftStart, idx).clear()
            }
            list.add(liftStart, fracNode)
            s.copy(nodes = list)
        }

        val targetCursor = if (liftedNodes.isNotEmpty()) {
            CursorLocation(denSeq.id, 0)
        } else {
            CursorLocation(numSeq.id, 0)
        }
        return EditorDocument(updatedRoot, targetCursor)
    }

    fun insertMixedFraction(doc: EditorDocument): EditorDocument {
        val whole = MathSequence(nextId("whole"), emptyList())
        val num = MathSequence(nextId("num"), emptyList())
        val den = MathSequence(nextId("den"), emptyList())
        val node = MathNode.MixedFractionNode(nextId("mfrac"), whole, num, den)
        val updated = updateSequence(doc.root, doc.cursor.sequenceId) { s ->
            val idx = doc.cursor.index.coerceIn(0, s.nodes.size)
            val list = s.nodes.toMutableList()
            list.add(idx, node)
            s.copy(nodes = list)
        }
        return EditorDocument(updated, CursorLocation(whole.id, 0))
    }

    fun insertRoot(doc: EditorDocument): EditorDocument {
        val rad = MathSequence(nextId("rad"), emptyList())
        val node = MathNode.RootNode(nextId("sqrt"), rad)
        val updated = updateSequence(doc.root, doc.cursor.sequenceId) { s ->
            val idx = doc.cursor.index.coerceIn(0, s.nodes.size)
            val list = s.nodes.toMutableList()
            list.add(idx, node)
            s.copy(nodes = list)
        }
        return EditorDocument(updated, CursorLocation(rad.id, 0))
    }

    fun insertCubeRoot(doc: EditorDocument): EditorDocument {
        val idxSeq = MathSequence(nextId("ridx"), listOf(token("3", TokenKind.DIGIT)))
        val radSeq = MathSequence(nextId("rad"), emptyList())
        val node = MathNode.NthRootNode(nextId("nroot"), idxSeq, radSeq)
        val updated = updateSequence(doc.root, doc.cursor.sequenceId) { s ->
            val idx = doc.cursor.index.coerceIn(0, s.nodes.size)
            val list = s.nodes.toMutableList()
            list.add(idx, node)
            s.copy(nodes = list)
        }
        return EditorDocument(updated, CursorLocation(radSeq.id, 0))
    }

    fun insertNthRoot(doc: EditorDocument): EditorDocument {
        val idxSeq = MathSequence(nextId("ridx"), emptyList())
        val radSeq = MathSequence(nextId("rad"), emptyList())
        val node = MathNode.NthRootNode(nextId("nroot"), idxSeq, radSeq)
        val updated = updateSequence(doc.root, doc.cursor.sequenceId) { s ->
            val idx = doc.cursor.index.coerceIn(0, s.nodes.size)
            val list = s.nodes.toMutableList()
            list.add(idx, node)
            s.copy(nodes = list)
        }
        return EditorDocument(updated, CursorLocation(idxSeq.id, 0))
    }

    fun insertPower(doc: EditorDocument, prefilledExponent: String? = null): EditorDocument {
        val expNodes = prefilledExponent?.map { ch ->
            val kind = when {
                ch.isDigit() -> TokenKind.DIGIT
                ch == '-' || ch == '−' -> TokenKind.OPERATOR
                else -> TokenKind.SYMBOL
            }
            token(ch.toString(), kind)
        } ?: emptyList()
        val expSeq = MathSequence(nextId("exp"), expNodes)
        val node = MathNode.PowerNode(nextId("pow"), expSeq)
        val updated = updateSequence(doc.root, doc.cursor.sequenceId) { s ->
            val idx = doc.cursor.index.coerceIn(0, s.nodes.size)
            val list = s.nodes.toMutableList()
            list.add(idx, node)
            s.copy(nodes = list)
        }
        val nextCursor = if (prefilledExponent != null) {
            CursorLocation(doc.cursor.sequenceId, doc.cursor.index + 1)
        } else {
            CursorLocation(expSeq.id, 0)
        }
        return EditorDocument(updated, nextCursor)
    }

    fun insertBaseWithPower(doc: EditorDocument, baseText: String, baseKind: TokenKind): EditorDocument {
        val baseNode = token(baseText, baseKind)
        val expSeq = MathSequence(nextId("exp"), emptyList())
        val powNode = MathNode.PowerNode(nextId("pow"), expSeq)
        val updated = updateSequence(doc.root, doc.cursor.sequenceId) { s ->
            val idx = doc.cursor.index.coerceIn(0, s.nodes.size)
            val list = s.nodes.toMutableList()
            list.add(idx, baseNode)
            list.add(idx + 1, powNode)
            s.copy(nodes = list)
        }
        return EditorDocument(updated, CursorLocation(expSeq.id, 0))
    }

    fun insertLogBase(doc: EditorDocument): EditorDocument {
        val baseSeq = MathSequence(nextId("lbase"), emptyList())
        val argSeq = MathSequence(nextId("larg"), emptyList())
        val node = MathNode.LogBaseNode(nextId("logb"), baseSeq, argSeq)
        val updated = updateSequence(doc.root, doc.cursor.sequenceId) { s ->
            val idx = doc.cursor.index.coerceIn(0, s.nodes.size)
            val list = s.nodes.toMutableList()
            list.add(idx, node)
            s.copy(nodes = list)
        }
        return EditorDocument(updated, CursorLocation(baseSeq.id, 0))
    }

    fun insertIntegral(doc: EditorDocument): EditorDocument {
        val body = MathSequence(nextId("ibody"), emptyList())
        val lower = MathSequence(nextId("ilow"), emptyList())
        val upper = MathSequence(nextId("iup"), emptyList())
        val node = MathNode.IntegralNode(nextId("int"), body, lower, upper)
        val updated = updateSequence(doc.root, doc.cursor.sequenceId) { s ->
            val idx = doc.cursor.index.coerceIn(0, s.nodes.size)
            val list = s.nodes.toMutableList()
            list.add(idx, node)
            s.copy(nodes = list)
        }
        return EditorDocument(updated, CursorLocation(body.id, 0))
    }

    fun insertDerivative(doc: EditorDocument): EditorDocument {
        val body = MathSequence(nextId("dbody"), emptyList())
        val atX = MathSequence(nextId("dat"), emptyList())
        val node = MathNode.DerivativeNode(nextId("deriv"), body, atX)
        val updated = updateSequence(doc.root, doc.cursor.sequenceId) { s ->
            val idx = doc.cursor.index.coerceIn(0, s.nodes.size)
            val list = s.nodes.toMutableList()
            list.add(idx, node)
            s.copy(nodes = list)
        }
        return EditorDocument(updated, CursorLocation(body.id, 0))
    }

    fun insertSumProd(doc: EditorDocument, isProduct: Boolean): EditorDocument {
        val body = MathSequence(nextId("sbody"), emptyList())
        val lower = MathSequence(nextId("slow"), emptyList())
        val upper = MathSequence(nextId("sup"), emptyList())
        val node = MathNode.SumProdNode(nextId("sp"), isProduct, body, lower, upper)
        val updated = updateSequence(doc.root, doc.cursor.sequenceId) { s ->
            val idx = doc.cursor.index.coerceIn(0, s.nodes.size)
            val list = s.nodes.toMutableList()
            list.add(idx, node)
            s.copy(nodes = list)
        }
        return EditorDocument(updated, CursorLocation(body.id, 0))
    }

    fun insertAbs(doc: EditorDocument): EditorDocument {
        val inner = MathSequence(nextId("absi"), emptyList())
        val node = MathNode.AbsNode(nextId("abs"), inner)
        val updated = updateSequence(doc.root, doc.cursor.sequenceId) { s ->
            val idx = doc.cursor.index.coerceIn(0, s.nodes.size)
            val list = s.nodes.toMutableList()
            list.add(idx, node)
            s.copy(nodes = list)
        }
        return EditorDocument(updated, CursorLocation(inner.id, 0))
    }

    fun deleteBeforeCursor(doc: EditorDocument): EditorDocument {
        val seq = findSequence(doc.root, doc.cursor.sequenceId) ?: return doc
        if (doc.cursor.index > 0) {
            val targetIndex = doc.cursor.index - 1
            val prevNode = seq.nodes[targetIndex]
            // If previous node is a structural node with children and not empty, delete it cleanly
            val updated = updateSequence(doc.root, seq.id) { s ->
                val list = s.nodes.toMutableList()
                list.removeAt(targetIndex)
                s.copy(nodes = list)
            }
            return EditorDocument(updated, CursorLocation(seq.id, targetIndex))
        }
        // Cursor is at index 0 inside a child slot: delete parent structural node if empty or move out
        val parentInfo = findParentOfSequence(doc.root, seq.id) ?: return doc
        val (parentSeq, nodeIndex, parentNode) = parentInfo
        val allEmpty = parentNode.childSequences().all { it.isEmpty }
        val unwrapEmptyDenomFrac = parentNode is MathNode.FractionNode && parentNode.denominator.isEmpty
        return if (allEmpty || unwrapEmptyDenomFrac || parentNode is MathNode.PowerNode || parentNode is MathNode.RootNode || parentNode is MathNode.AbsNode) {
            val preserved = when (parentNode) {
                is MathNode.RootNode -> parentNode.radicand.nodes
                is MathNode.AbsNode -> parentNode.inner.nodes
                is MathNode.FractionNode -> if (parentNode.denominator.isEmpty) parentNode.numerator.nodes else emptyList()
                else -> emptyList()
            }
            val updated = updateSequence(doc.root, parentSeq.id) { s ->
                val list = s.nodes.toMutableList()
                list.removeAt(nodeIndex)
                list.addAll(nodeIndex, preserved)
                s.copy(nodes = list)
            }
            EditorDocument(updated, CursorLocation(parentSeq.id, nodeIndex + preserved.size))
        } else {
            // Move left out of the slot
            moveLeft(doc)
        }
    }

    fun moveLeft(doc: EditorDocument): EditorDocument {
        val seq = findSequence(doc.root, doc.cursor.sequenceId) ?: return doc
        if (doc.cursor.index > 0) {
            val prevNode = seq.nodes[doc.cursor.index - 1]
            val children = prevNode.childSequences()
            if (children.isNotEmpty()) {
                val lastChild = children.last()
                return doc.copy(cursor = CursorLocation(lastChild.id, lastChild.nodes.size))
            }
            return doc.copy(cursor = CursorLocation(seq.id, doc.cursor.index - 1))
        }
        // At index 0: jump to previous sibling slot in same parent node, or exit parent node to left
        val parentInfo = findParentOfSequence(doc.root, seq.id) ?: return doc
        val (parentSeq, nodeIndex, parentNode) = parentInfo
        val slots = parentNode.childSequences()
        val slotPos = slots.indexOfFirst { it.id == seq.id }
        return if (slotPos > 0) {
            val prevSlot = slots[slotPos - 1]
            doc.copy(cursor = CursorLocation(prevSlot.id, prevSlot.nodes.size))
        } else {
            doc.copy(cursor = CursorLocation(parentSeq.id, nodeIndex))
        }
    }

    fun moveRight(doc: EditorDocument): EditorDocument {
        val seq = findSequence(doc.root, doc.cursor.sequenceId) ?: return doc
        if (doc.cursor.index < seq.nodes.size) {
            val nextNode = seq.nodes[doc.cursor.index]
            val children = nextNode.childSequences()
            if (children.isNotEmpty()) {
                val firstChild = children.first()
                return doc.copy(cursor = CursorLocation(firstChild.id, 0))
            }
            return doc.copy(cursor = CursorLocation(seq.id, doc.cursor.index + 1))
        }
        // At end of slot: jump to next sibling slot in parent node, or exit parent node to right
        val parentInfo = findParentOfSequence(doc.root, seq.id) ?: return doc
        val (parentSeq, nodeIndex, parentNode) = parentInfo
        val slots = parentNode.childSequences()
        val slotPos = slots.indexOfFirst { it.id == seq.id }
        return if (slotPos in 0 until slots.lastIndex) {
            val nextSlot = slots[slotPos + 1]
            doc.copy(cursor = CursorLocation(nextSlot.id, 0))
        } else {
            doc.copy(cursor = CursorLocation(parentSeq.id, nodeIndex + 1))
        }
    }

    /**
     * Vertical cursor navigation inside 2D templates (fractions, integral bounds, sum/prod bounds).
     * Returns null if cursor is at top-level so caller can scroll calculation history instead.
     */
    fun moveUp(doc: EditorDocument): EditorDocument? {
        val parentInfo = findParentOfSequence(doc.root, doc.cursor.sequenceId) ?: return null
        val (_, _, parentNode) = parentInfo
        return when (parentNode) {
            is MathNode.FractionNode -> if (doc.cursor.sequenceId == parentNode.denominator.id) {
                doc.copy(cursor = CursorLocation(parentNode.numerator.id, doc.cursor.index.coerceAtMost(parentNode.numerator.nodes.size)))
            } else null
            is MathNode.MixedFractionNode -> if (doc.cursor.sequenceId == parentNode.denominator.id) {
                doc.copy(cursor = CursorLocation(parentNode.numerator.id, doc.cursor.index.coerceAtMost(parentNode.numerator.nodes.size)))
            } else null
            is MathNode.IntegralNode -> when (doc.cursor.sequenceId) {
                parentNode.lower.id -> doc.copy(cursor = CursorLocation(parentNode.upper.id, parentNode.upper.nodes.size))
                parentNode.body.id -> doc.copy(cursor = CursorLocation(parentNode.upper.id, parentNode.upper.nodes.size))
                else -> null
            }
            is MathNode.SumProdNode -> when (doc.cursor.sequenceId) {
                parentNode.lower.id -> doc.copy(cursor = CursorLocation(parentNode.upper.id, parentNode.upper.nodes.size))
                parentNode.body.id -> doc.copy(cursor = CursorLocation(parentNode.upper.id, parentNode.upper.nodes.size))
                else -> null
            }
            else -> null
        }
    }

    fun moveDown(doc: EditorDocument): EditorDocument? {
        val parentInfo = findParentOfSequence(doc.root, doc.cursor.sequenceId) ?: return null
        val (_, _, parentNode) = parentInfo
        return when (parentNode) {
            is MathNode.FractionNode -> if (doc.cursor.sequenceId == parentNode.numerator.id) {
                doc.copy(cursor = CursorLocation(parentNode.denominator.id, doc.cursor.index.coerceAtMost(parentNode.denominator.nodes.size)))
            } else null
            is MathNode.MixedFractionNode -> if (doc.cursor.sequenceId == parentNode.numerator.id) {
                doc.copy(cursor = CursorLocation(parentNode.denominator.id, doc.cursor.index.coerceAtMost(parentNode.denominator.nodes.size)))
            } else null
            is MathNode.IntegralNode -> when (doc.cursor.sequenceId) {
                parentNode.upper.id -> doc.copy(cursor = CursorLocation(parentNode.lower.id, parentNode.lower.nodes.size))
                parentNode.body.id -> doc.copy(cursor = CursorLocation(parentNode.lower.id, parentNode.lower.nodes.size))
                else -> null
            }
            is MathNode.SumProdNode -> when (doc.cursor.sequenceId) {
                parentNode.upper.id -> doc.copy(cursor = CursorLocation(parentNode.lower.id, parentNode.lower.nodes.size))
                parentNode.body.id -> doc.copy(cursor = CursorLocation(parentNode.lower.id, parentNode.lower.nodes.size))
                else -> null
            }
            else -> null
        }
    }

    fun extractVariables(seq: MathSequence): List<String> {
        val vars = linkedSetOf<String>()
        val allowed = setOf("A", "B", "C", "D", "E", "F", "X", "Y", "M")
        fun walk(s: MathSequence) {
            for (node in s.nodes) {
                if (node is MathNode.TokenNode && node.kind == TokenKind.VARIABLE && node.text in allowed) {
                    vars.add(node.text)
                }
                node.childSequences().forEach { walk(it) }
            }
        }
        walk(seq)
        return vars.toList()
    }

    fun containsEquationEquals(seq: MathSequence): Boolean {
        for (node in seq.nodes) {
            if (node is MathNode.TokenNode && node.kind == TokenKind.COMPARE && node.text == "=") {
                return true
            }
        }
        return false
    }

    fun findSequence(root: MathSequence, id: String): MathSequence? {
        if (root.id == id) return root
        for (node in root.nodes) {
            for (child in node.childSequences()) {
                val found = findSequence(child, id)
                if (found != null) return found
            }
        }
        return null
    }

    fun updateSequence(root: MathSequence, targetId: String, transform: (MathSequence) -> MathSequence): MathSequence {
        if (root.id == targetId) return transform(root)
        val mapped = root.nodes.map { node ->
            when (node) {
                is MathNode.TokenNode -> node
                is MathNode.FractionNode -> node.copy(
                    numerator = updateSequence(node.numerator, targetId, transform),
                    denominator = updateSequence(node.denominator, targetId, transform)
                )
                is MathNode.MixedFractionNode -> node.copy(
                    whole = updateSequence(node.whole, targetId, transform),
                    numerator = updateSequence(node.numerator, targetId, transform),
                    denominator = updateSequence(node.denominator, targetId, transform)
                )
                is MathNode.RootNode -> node.copy(
                    radicand = updateSequence(node.radicand, targetId, transform)
                )
                is MathNode.NthRootNode -> node.copy(
                    index = updateSequence(node.index, targetId, transform),
                    radicand = updateSequence(node.radicand, targetId, transform)
                )
                is MathNode.PowerNode -> node.copy(
                    exponent = updateSequence(node.exponent, targetId, transform)
                )
                is MathNode.SubscriptNode -> node.copy(
                    subscript = updateSequence(node.subscript, targetId, transform)
                )
                is MathNode.LogBaseNode -> node.copy(
                    base = updateSequence(node.base, targetId, transform),
                    argument = updateSequence(node.argument, targetId, transform)
                )
                is MathNode.IntegralNode -> node.copy(
                    body = updateSequence(node.body, targetId, transform),
                    lower = updateSequence(node.lower, targetId, transform),
                    upper = updateSequence(node.upper, targetId, transform)
                )
                is MathNode.DerivativeNode -> node.copy(
                    body = updateSequence(node.body, targetId, transform),
                    atX = updateSequence(node.atX, targetId, transform)
                )
                is MathNode.SumProdNode -> node.copy(
                    body = updateSequence(node.body, targetId, transform),
                    lower = updateSequence(node.lower, targetId, transform),
                    upper = updateSequence(node.upper, targetId, transform)
                )
                is MathNode.AbsNode -> node.copy(
                    inner = updateSequence(node.inner, targetId, transform)
                )
                is MathNode.MatrixNode -> node.copy(
                    cells = node.cells.map { row ->
                        row.map { cell -> updateSequence(cell, targetId, transform) }
                    }
                )
            }
        }
        return root.copy(nodes = mapped)
    }

    private fun findParentOfSequence(
        current: MathSequence,
        targetSeqId: String
    ): Triple<MathSequence, Int, MathNode>? {
        current.nodes.forEachIndexed { index, node ->
            val children = node.childSequences()
            if (children.any { it.id == targetSeqId }) {
                return Triple(current, index, node)
            }
            for (child in children) {
                val found = findParentOfSequence(child, targetSeqId)
                if (found != null) return found
            }
        }
        return null
    }
}
