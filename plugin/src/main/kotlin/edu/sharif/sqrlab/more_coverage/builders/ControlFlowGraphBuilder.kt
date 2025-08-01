package edu.sharif.sqrlab.more_coverage.builders

import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import edu.sharif.sqrlab.more_coverage.models.CFGNode
import edu.sharif.sqrlab.more_coverage.models.ControlFlowGraph

/**
 * Builds a Control Flow Graph (CFG) from a Python function.
 *
 * This builder traverses the statements of a PyFunction, creating nodes
 * representing statements or control structures and connecting them to
 * represent control flow paths.
 */
object ControlFlowGraphBuilder {

    /**
     * Constructs the CFG for the given Python function.
     *
     * @param function The PyFunction to analyze.
     * @return A ControlFlowGraph representing the function's control flow.
     */
    fun build(function: PyFunction): ControlFlowGraph {
        val graph = ControlFlowGraph()

        // Retrieve all top-level statements inside the function body
        val statements = function.statementList.statements

        // No artificial entry node — start with empty prevNodes list
        var prevNodes = emptyList<CFGNode>()

        // Process each statement in sequence, chaining control flow nodes
        for (stmt in statements) {
            // Connect current statement with previous nodes
            prevNodes = connectStatement(graph, stmt, prevNodes, null)
        }

        // No artificial exit node — do not connect leftover nodes

        return graph
    }

    /**
     * Dispatches statement handling based on the statement's type.
     *
     * @param graph The CFG under construction.
     * @param stmt The current PyStatement to process.
     * @param prevNodes Nodes from which control flows into this statement.
     * @param exitNode Nullable artificial exit node (no longer used).
     * @return List of nodes representing possible exit points after this statement.
     */
    private fun connectStatement(
        graph: ControlFlowGraph,
        stmt: PyStatement,
        prevNodes: List<CFGNode>,
        exitNode: CFGNode? // now nullable because we removed artificial exit node
    ): List<CFGNode> = when (stmt) {
        // Handle each control structure by delegating to specific handlers
        is PyIfStatement -> handleIf(graph, stmt, prevNodes, exitNode)
        is PyWhileStatement -> handleWhile(graph, stmt, prevNodes, exitNode)
        is PyForStatement -> handleFor(graph, stmt, prevNodes, exitNode)
        is PyReturnStatement -> handleReturn(graph, stmt, prevNodes)
        else -> {
            // Linear/simple statement (e.g., assignment, expression)
            val node = CFGNode("n${graph.nodes.size}", stmt.text, stmt)
            graph.addNode(node)
            // Connect all previous nodes to this node, if any
            if (prevNodes.isNotEmpty()) {
                prevNodes.forEach { graph.addEdge(it, node) }
            }
            // Return this node as the new previous node for subsequent statements
            listOf(node)
        }
    }

    /**
     * Handles return statements which terminate flow on this path.
     *
     * @return empty list, as no further statements follow return.
     */
    private fun handleReturn(
        graph: ControlFlowGraph,
        stmt: PyReturnStatement,
        prevNodes: List<CFGNode>
    ): List<CFGNode> {
        val node = CFGNode("n${graph.nodes.size}", stmt.text, stmt)
        graph.addNode(node)
        if (prevNodes.isNotEmpty()) {
            prevNodes.forEach { graph.addEdge(it, node) }
        }
        // Return statements terminate control flow, so no successors
        return emptyList()
    }

    /**
     * Handles if-else branching statements.
     *
     * Creates a condition node, then recursively connects
     * true and false branches.
     */
    private fun handleIf(
        graph: ControlFlowGraph,
        stmt: PyIfStatement,
        prevNodes: List<CFGNode>,
        exitNode: CFGNode?
    ): List<CFGNode> {
        val conditionText = stmt.ifPart.condition?.text ?: "if"
        val ifNode = CFGNode("n${graph.nodes.size}", conditionText, stmt)
        graph.addNode(ifNode)
        if (prevNodes.isNotEmpty()) {
            prevNodes.forEach { graph.addEdge(it, ifNode) }
        }

        // Connect true branch statements starting from ifNode
        val thenNodes = connectBlock(graph, stmt.ifPart.statementList.statements, listOf(ifNode), exitNode)

        // Connect else branch if exists; otherwise consider fallthrough
        val elseNodes = stmt.elsePart?.statementList?.statements?.let {
            connectBlock(graph, it, listOf(ifNode), exitNode)
        } ?: listOf(ifNode)

        // Combine possible exit points from both branches
        return thenNodes + elseNodes
    }

    /**
     * Handles while loops.
     *
     * Creates a condition node, connects the loop body,
     * and adds back edges for looping behavior.
     */
    private fun handleWhile(
        graph: ControlFlowGraph,
        stmt: PyWhileStatement,
        prevNodes: List<CFGNode>,
        exitNode: CFGNode?
    ): List<CFGNode> {
        val conditionText = stmt.whilePart?.condition?.text ?: "while"
        val condNode = CFGNode("n${graph.nodes.size}", conditionText, stmt)
        graph.addNode(condNode)
        if (prevNodes.isNotEmpty()) {
            prevNodes.forEach { graph.addEdge(it, condNode) }
        }

        // Connect body statements starting from condNode
        val bodyNodes = connectBlock(graph, stmt.whilePart?.statementList?.statements ?: emptyArray(), listOf(condNode), exitNode)

        // Add edges back from body nodes to condition to represent loop
        bodyNodes.forEach { graph.addEdge(it, condNode) }

        // The loop condition node itself is the exit point when loop terminates
        return listOf(condNode)
    }

    /**
     * Handles for loops.
     *
     * Extracts loop target and iterable, creates a condition node,
     * connects loop body, and loops back.
     */
    private fun handleFor(
        graph: ControlFlowGraph,
        stmt: PyForStatement,
        prevNodes: List<CFGNode>,
        exitNode: CFGNode?
    ): List<CFGNode> {
        val forPart = stmt.forPart

        // Extract the iterable expression (exclude target)
        val iterable = forPart?.children
            ?.filterIsInstance<PyExpression>()
            ?.firstOrNull { it != forPart.target }

        // Extract the body block of statements
        val suite = forPart?.children?.find { it is PyStatementList } as? PyStatementList

        val targetText = forPart?.target?.text ?: "target"
        val iterableText = iterable?.text ?: "iterable"

        val conditionText = "for $targetText in $iterableText"
        val forNode = CFGNode("n${graph.nodes.size}", conditionText, stmt)
        graph.addNode(forNode)

        if (prevNodes.isNotEmpty()) {
            prevNodes.forEach { graph.addEdge(it, forNode) }
        }

        // Connect loop body statements starting from forNode
        val bodyStatements = suite?.statements ?: emptyArray()
        val bodyNodes = connectBlock(graph, bodyStatements, listOf(forNode), exitNode)

        // Loop back edges from body nodes to forNode to represent iteration
        bodyNodes.forEach { graph.addEdge(it, forNode) }

        // Exit node for loop (after iteration finishes) is forNode itself
        return listOf(forNode)
    }

    /**
     * Connects a sequence of statements in a block,
     * chaining their control flow nodes linearly.
     *
     * @param graph The CFG being constructed.
     * @param stmts Array of statements to connect.
     * @param entryPoints Nodes where control flow enters this block.
     * @param exitNode Artificial exit node, nullable and unused here.
     * @return List of possible exit nodes after the block.
     */
    private fun connectBlock(
        graph: ControlFlowGraph,
        stmts: Array<out PyStatement>,
        entryPoints: List<CFGNode>,
        exitNode: CFGNode?
    ): List<CFGNode> {
        var prev = entryPoints
        // Sequentially connect each statement, threading previous nodes forward
        for (stmt in stmts) {
            prev = connectStatement(graph, stmt, prev, exitNode)
        }
        return prev
    }
}
