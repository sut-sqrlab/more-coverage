package edu.sharif.sqrlab.more_coverage.models

import com.intellij.psi.PsiElement

/**
 * Represents a single node in the Control Flow Graph (CFG).
 *
 * @property id Unique identifier for the node (e.g., "n1", "stmt_3").
 * @property label Human-readable label (usually the statement text).
 * @property element Optional reference to the PSI element in the source code.
 */
data class CFGNode(
    val id: String,
    val label: String,
    val element: PsiElement? = null
)

/**
 * Represents a directed edge between two nodes in the CFG.
 *
 * @property from Source node of the edge.
 * @property to Target node of the edge.
 */
data class CFGEdge(val from: CFGNode, val to: CFGNode)

/**
 * Represents a Control Flow Graph (CFG) for a Python function.
 *
 * This graph consists of:
 * - Nodes corresponding to statements or expressions in the function.
 * - Edges representing possible control flow transitions between nodes.
 *
 * The graph can be used to implement various test coverage criteria such as
 * Node Coverage, Edge Coverage, Prime Path Coverage, etc.
 */
class ControlFlowGraph {

    // All nodes (statements or control elements) in the CFG
    val nodes = mutableListOf<CFGNode>()

    // All directed edges (control transitions) in the CFG
    val edges = mutableSetOf<CFGEdge>()

    /**
     * Adds a node to the CFG.
     */
    fun addNode(node: CFGNode) {
        nodes.add(node)
    }

    /**
     * Adds a directed edge between two nodes in the CFG.
     */
    fun addEdge(from: CFGNode, to: CFGNode) {
        edges.add(CFGEdge(from, to))
    }

    /**
     * Returns a list of all edfes.
     */
    fun ControlFlowGraph.getEdges(): Set<CFGEdge> = edges

    /**
     * Returns a list of nodes that are directly reachable from the given node.
     */
    fun getSuccessors(node: CFGNode): List<CFGNode> =
        edges.filter { it.from == node }.map { it.to }

    /**
     * Returns a list of nodes that lead directly into the given node.
     */
    fun getPredecessors(node: CFGNode): List<CFGNode> =
        edges.filter { it.to == node }.map { it.from }
}
