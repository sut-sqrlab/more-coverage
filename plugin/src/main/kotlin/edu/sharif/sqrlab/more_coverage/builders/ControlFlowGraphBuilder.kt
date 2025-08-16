package edu.sharif.sqrlab.more_coverage.builders

import com.jetbrains.python.psi.*
import edu.sharif.sqrlab.more_coverage.models.CFGNode
import edu.sharif.sqrlab.more_coverage.models.ControlFlowGraph

/**
 * Builds a Control Flow Graph (CFG) from a Python function.
 *
 * Features:
 * 1. Consecutive linear statements are merged into a single CFG node.
 * 2. Handles control flow structures: if, while, for, return.
 * 3. Includes debug logging for nodes and edges.
 */
object ControlFlowGraphBuilder {

    /**
     * Entry point for building the CFG of a Python function.
     *
     * @param function PyFunction to build CFG for
     * @return ControlFlowGraph representing the function
     */
    fun build(function: PyFunction): ControlFlowGraph {
        val graph = ControlFlowGraph()
        println("=== Build CFG for function: ${function.name} ===")

        val statements = function.statementList.statements
        var prevNodes = emptyList<CFGNode>()

        // Connect all top-level statements
        prevNodes = connectBlock(graph, statements, prevNodes)

        println("=== Finished CFG for function: ${function.name} ===")
        println("Total nodes: ${graph.nodes.size}, Total edges: ${graph.edges.size}")
        return graph
    }

    /**
     * Connects a single statement into the CFG, dispatching based on type.
     *
     * @param graph CFG being built
     * @param stmt Statement to connect
     * @param prevNodes List of previous CFG nodes (predecessors)
     * @param exitNode Optional exit node for structured blocks
     * @return List of CFGNodes representing exits from this statement
     */
    private fun connectStatement(
        graph: ControlFlowGraph,
        stmt: PyStatement,
        prevNodes: List<CFGNode>,
        exitNode: CFGNode?
    ): List<CFGNode> = when (stmt) {
        is PyIfStatement -> handleIf(graph, stmt, prevNodes, exitNode)
        is PyWhileStatement -> handleWhile(graph, stmt, prevNodes, exitNode)
        is PyForStatement -> handleFor(graph, stmt, prevNodes, exitNode)
        is PyMatchStatement -> handleMatch(graph, stmt, prevNodes, exitNode)
        is PyReturnStatement -> handleReturn(graph, stmt, prevNodes)
        else -> handleLinear(graph, arrayOf(stmt), prevNodes) // simple statement
    }

    /**
     * Handles linear (non-control-flow) statements by merging them into a single CFG node.
     *
     * @param graph CFG being built
     * @param stmts Array of consecutive linear statements
     * @param prevNodes Predecessor nodes
     * @return List containing the created CFGNode
     */
    private fun handleLinear(
        graph: ControlFlowGraph,
        stmts: Array<out PyStatement>,
        prevNodes: List<CFGNode>
    ): List<CFGNode> {
        // Merge statements into one label separated by semicolon
        val label = stmts.joinToString(" ; ") { it.text }
        val node = CFGNode("n${graph.nodes.size}", label, stmts.first())
        graph.addNode(node)
        println("Added LINEAR node: ${node.label}")

        // Connect previous nodes to this new node
        prevNodes.forEach {
            graph.addEdge(it, node)
            println("Edge: ${it.label} -> ${node.label}")
        }

        return listOf(node)
    }

    /**
     * Handles a return statement, creating a CFG node and linking predecessors.
     *
     * @param graph CFG being built
     * @param stmt Return statement
     * @param prevNodes Predecessor nodes
     * @return empty list since return has no successors
     */
    private fun handleReturn(
        graph: ControlFlowGraph,
        stmt: PyReturnStatement,
        prevNodes: List<CFGNode>
    ): List<CFGNode> {
        val node = CFGNode("n${graph.nodes.size}", stmt.text, stmt)
        graph.addNode(node)
        println("Added RETURN node: ${node.label}")

        prevNodes.forEach {
            graph.addEdge(it, node)
            println("Edge: ${it.label} -> ${node.label}")
        }

        return emptyList() // no successors
    }

    /**
     * Handles an if statement, creating a condition node and connecting then/else blocks.
     *
     * @param graph CFG being built
     * @param stmt If statement
     * @param prevNodes Predecessor nodes
     * @param exitNode Optional exit node
     * @return List of exit nodes from the if-else
     */
    private fun handleIf(
        graph: ControlFlowGraph,
        stmt: PyIfStatement,
        prevNodes: List<CFGNode>,
        exitNode: CFGNode?
    ): List<CFGNode> {
        // Create CFG node for the if condition
        val conditionText = stmt.ifPart.condition?.text ?: "if"
        val ifNode = CFGNode("n${graph.nodes.size}", conditionText, stmt)
        graph.addNode(ifNode)
        println("Added IF node: ${ifNode.label}")

        // Connect predecessors to the if condition node
        prevNodes.forEach {
            graph.addEdge(it, ifNode)
            println("Edge: ${it.label} -> ${ifNode.label}")
        }

        // Connect the "then" block
        val thenNodes = connectBlock(graph, stmt.ifPart.statementList.statements, listOf(ifNode))
        // Connect the "else" block (or use condition node if none)
        val elseNodes = stmt.elsePart?.statementList?.statements?.let {
            connectBlock(graph, it, listOf(ifNode))
        } ?: listOf(ifNode)

        println("IF exits: then=${thenNodes.map { it.label }}, else=${elseNodes.map { it.label }}")
        return thenNodes + elseNodes
    }

    /**
     * Handles a while loop, creating a condition node and connecting the body with back edges.
     *
     * @param graph CFG being built
     * @param stmt While statement
     * @param prevNodes Predecessor nodes
     * @param exitNode Optional exit node
     * @return List of nodes representing the loop's exit (false path)
     */
    private fun handleWhile(
        graph: ControlFlowGraph,
        stmt: PyWhileStatement,
        prevNodes: List<CFGNode>,
        exitNode: CFGNode?
    ): List<CFGNode> {
        // Create node for the loop condition
        val conditionText = stmt.whilePart?.condition?.text ?: "while"
        val condNode = CFGNode("n${graph.nodes.size}", conditionText, stmt)
        graph.addNode(condNode)
        println("Added WHILE condition node: ${condNode.label}")

        // Connect predecessors to the condition
        prevNodes.forEach {
            graph.addEdge(it, condNode)
            println("Edge: ${it.label} -> ${condNode.label}")
        }

        // Connect loop body
        val bodyStatements = stmt.whilePart?.statementList?.statements ?: emptyArray()
        println("WHILE body statements count: ${bodyStatements.size}")
        val bodyExitNodes = connectBlock(graph, bodyStatements, listOf(condNode))

        // Back edges from body to condition
        bodyExitNodes.forEach {
            graph.addEdge(it, condNode)
            println("Back edge from body: ${it.label} -> while ${condNode.label}")
        }

        println("WHILE loop exits at: ${condNode.label}")
        return listOf(condNode) // false path exit
    }


    /**
     * Handles a match statement (Python 3.10+ structural pattern matching).
     *
     * @param graph CFG being built
     * @param stmt Match statement
     * @param prevNodes Predecessor nodes
     * @param exitNode Optional exit node
     * @return List of exit nodes from all case branches
     */
    private fun handleMatch(
        graph: ControlFlowGraph,
        stmt: PyMatchStatement,
        prevNodes: List<CFGNode>,
        exitNode: CFGNode?
    ): List<CFGNode> {
        // Create the main match node
        val matchLabel = "match ${stmt.subject?.text ?: ""}"
        val matchNode = CFGNode("n${graph.nodes.size}", matchLabel, stmt)
        graph.addNode(matchNode)
        println("Added MATCH node: ${matchNode.label}")

        // Connect predecessors to the match node
        prevNodes.forEach {
            graph.addEdge(it, matchNode)
            println("Edge: ${it.label} -> ${matchNode.label}")
        }

        val caseExits = mutableListOf<CFGNode>()

        // Process each case clause
        for (caseClause in stmt.caseClauses) {
            // Create a case node
            val caseLabel = "case ${caseClause.pattern?.text ?: "_"}"
            val caseNode = CFGNode("n${graph.nodes.size}", caseLabel, stmt)
            graph.addNode(caseNode)
            graph.addEdge(matchNode, caseNode)
            println("Added CASE node: ${caseNode.label}")
            println("Edge: ${matchNode.label} -> ${caseNode.label}")

            // Connect case body statements sequentially
            var lastNodes = listOf(caseNode)
            val statements = caseClause.statementList?.statements ?: emptyArray()
            for (stmtInCase in statements) {
                lastNodes = connectStatement(graph, stmtInCase, lastNodes, null)
            }

            // Collect exits of this case
            caseExits.addAll(lastNodes)
        }

        // Merge node after all cases
        val mergeNode = CFGNode("n${graph.nodes.size}", "match_end", stmt)
        graph.addNode(mergeNode)
        println("Added MERGE node: ${mergeNode.label}")

        // Connect all case exits to merge node
        caseExits.forEach {
            graph.addEdge(it, mergeNode)
            println("Edge: ${it.label} -> ${mergeNode.label}")
        }

        return listOf(mergeNode)
    }

    /**
     * Handles a for loop, creating a condition node and connecting the body with back edges.
     *
     * @param graph CFG being built
     * @param stmt For statement
     * @param prevNodes Predecessor nodes
     * @param exitNode Optional exit node
     * @return List of nodes representing the loop's exit (false path)
     */
    private fun handleFor(
        graph: ControlFlowGraph,
        stmt: PyForStatement,
        prevNodes: List<CFGNode>,
        exitNode: CFGNode?
    ): List<CFGNode> {
        val forPart = stmt.forPart
        val targetText = forPart?.target?.text ?: "target"
        val iterableText = forPart?.children
            ?.filterIsInstance<PyExpression>()
            ?.firstOrNull { it != forPart.target }?.text ?: "iterable"
        val conditionText = "for $targetText in $iterableText"
        val forNode = CFGNode("n${graph.nodes.size}", conditionText, stmt)
        graph.addNode(forNode)
        println("Added FOR node: ${forNode.label}")

        // Connect predecessors to the for loop
        prevNodes.forEach {
            graph.addEdge(it, forNode)
            println("Edge: ${it.label} -> ${forNode.label}")
        }

        // Connect loop body
        val suite: PyStatementList? = forPart?.statementList
            ?: (forPart?.children?.find { it is PyStatementList } as? PyStatementList)

        val bodyNodes = connectBlock(graph, suite?.statements ?: emptyArray(), listOf(forNode))

        // Back edges from body to loop condition
        bodyNodes.forEach {
            graph.addEdge(it, forNode)
            println("Back edge from body: ${it.label} -> for ${forNode.label}")
        }

        return listOf(forNode)
    }

    /**
     * Connects a sequence of statements (block) into the CFG.
     * Consecutive linear statements are buffered and merged into single nodes.
     *
     * @param graph CFG being built
     * @param stmts Statements in the block
     * @param entryPoints CFG nodes that are predecessors to this block
     * @return List of exit nodes from the block
     */
    private fun connectBlock(
        graph: ControlFlowGraph,
        stmts: Array<out PyStatement>,
        entryPoints: List<CFGNode>
    ): List<CFGNode> {
        var prev = entryPoints
        val linearBuffer = mutableListOf<PyStatement>()

        for (stmt in stmts) {
            when (stmt) {
                // Control-flow statements break the linear sequence
                is PyIfStatement, is PyWhileStatement, is PyForStatement, is PyMatchStatement, is PyReturnStatement -> {
                    // Flush any buffered linear statements as one node
                    if (linearBuffer.isNotEmpty()) {
                        prev = handleLinear(graph, linearBuffer.toTypedArray(), prev)
                        linearBuffer.clear()
                    }
                    prev = connectStatement(graph, stmt, prev, null)
                }
                else -> linearBuffer.add(stmt) // buffer linear statements
            }
        }

        // Flush remaining linear statements
        if (linearBuffer.isNotEmpty()) {
            prev = handleLinear(graph, linearBuffer.toTypedArray(), prev)
        }

        return prev
    }
}
