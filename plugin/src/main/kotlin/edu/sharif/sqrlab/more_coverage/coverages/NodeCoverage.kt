package edu.sharif.sqrlab.more_coverage.coverages

import com.jetbrains.python.psi.impl.PyFunctionImpl
import edu.sharif.sqrlab.more_coverage.AbstractCoverageTestGeneratorAction
import edu.sharif.sqrlab.more_coverage.builders.ControlFlowGraphBuilder
import edu.sharif.sqrlab.more_coverage.models.CFGNode
import edu.sharif.sqrlab.more_coverage.models.TestCase

/**
 * NodeCoverage generates test cases that cover all nodes
 * in the CFG of a Python function.
 * Handles loops by including nodes reachable via back edges at least once.
 */
class NodeCoverage : AbstractCoverageTestGeneratorAction() {

    override val name: String = "NodeCoverage"

    override fun generate(function: PyFunctionImpl): Iterable<TestCase> {
        val testCases = mutableListOf<TestCase>()

        // Step 0: Build the CFG
        val cfg = ControlFlowGraphBuilder.build(function)

        // Step 1: Prepare debug strings
        val nodesStr = cfg.nodes.joinToString(", ") { "${it.id}:${it.label.replace("\n", " ")}" }
        val edgesStr = cfg.edges.joinToString(", ") { "${it.from.id}->${it.to.id}" }

        // Step 2: BFS to collect all paths from sources to sinks
        val sources = cfg.nodes.filter { node -> cfg.edges.none { it.to == node } }
        val allPaths = mutableListOf<List<CFGNode>>()
        val queue = ArrayDeque<List<CFGNode>>()
        sources.forEach { queue.add(listOf(it)) }

        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            val lastNode = path.last()
            val successors = cfg.edges.filter { it.from == lastNode }.map { it.to }

            if (successors.isEmpty()) {
                allPaths.add(path) // Sink reached
            } else {
                for (succ in successors) {
                    if (succ !in path || isLoopEdge(cfg, lastNode, succ)) {
                        // Allow back edge once
                        queue.add(path + succ)
                    }
                }
            }
        }

        // Step 3: Greedy selection of minimal paths covering all nodes
        val nodesToCover = cfg.nodes.toMutableSet()
        val selectedPaths = mutableListOf<List<CFGNode>>()

        while (nodesToCover.isNotEmpty()) {
            val bestPath = allPaths.maxByOrNull { path ->
                path.count { it in nodesToCover }
            } ?: break

            selectedPaths.add(bestPath)
            nodesToCover.removeAll(bestPath)
            allPaths.remove(bestPath)
        }

        // Step 4: Generate TestCase objects for selected paths
        for ((index, path) in selectedPaths.withIndex()) {
            val pathDesc = path.joinToString(" -> ") { "${it.id}:${it.label.replace("\n", " ")}" }
            val comment = "Nodes: $nodesStr\n# Edges: $edgesStr\n# Path $index: $pathDesc"
            testCases.add(TestCase("${function.name ?: "func"}_node_$index", comment))
        }

        return testCases
    }

    /**
     * Determines if an edge is a back edge forming a loop.
     * Used to allow traversing back edges once for node coverage.
     */
    private fun isLoopEdge(cfg: edu.sharif.sqrlab.more_coverage.models.ControlFlowGraph, from: CFGNode, to: CFGNode): Boolean {
        return cfg.edges.any { it.from == from && it.to == to && cfg.nodes.indexOf(to) <= cfg.nodes.indexOf(from) }
    }
}
