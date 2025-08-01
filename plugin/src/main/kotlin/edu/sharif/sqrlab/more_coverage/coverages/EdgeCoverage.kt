package edu.sharif.sqrlab.more_coverage.coverages

import com.jetbrains.python.psi.impl.PyFunctionImpl
import edu.sharif.sqrlab.more_coverage.AbstractCoverageTestGeneratorAction
import edu.sharif.sqrlab.more_coverage.builders.ControlFlowGraphBuilder
import edu.sharif.sqrlab.more_coverage.models.CFGNode
import edu.sharif.sqrlab.more_coverage.models.TestCase

/**
 * EdgeCoverage generates test cases that cover all edges
 * in the control flow graph (CFG) of a Python function.
 * Uses BFS to explore paths and selects a minimal set of paths.
 */
class EdgeCoverage : AbstractCoverageTestGeneratorAction() {

    override val name: String = "EdgeCoverage"

    /**
     * Generates a minimal set of test cases covering all edges
     * in the function's CFG.
     *
     * @param function The PyFunctionImpl to analyze
     * @return Iterable<TestCase> covering all edges
     */
    override fun generate(function: PyFunctionImpl): Iterable<TestCase> {
        val testCases = mutableListOf<TestCase>()

        // Step 0: Build the CFG
        val cfg = ControlFlowGraphBuilder.build(function)

        // Step 1: Prepare debug strings
        val nodesStr = cfg.nodes.joinToString(", ") { "${it.id}:${it.label.replace("\n", " ")}" }
        val edgesStr = cfg.edges.joinToString(", ") { "${it.from.id}->${it.to.id}" }

        // Step 2: Collect all edges as explicit pairs
        val allEdges = cfg.edges.map { it.from to it.to }.toMutableSet()

        // Step 3: BFS to collect all paths from sources to sinks
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
                    queue.add(path + succ)
                }
            }
        }

        // Step 4: Greedy selection of minimal paths covering all edges
        val edgesToCover = allEdges.toMutableSet()
        val selectedPaths = mutableListOf<List<CFGNode>>()

        while (edgesToCover.isNotEmpty()) {
            // Pick path covering the most yet-uncovered edges
            val bestPath = allPaths.maxByOrNull { path ->
                path.zip(path.drop(1)).count { it in edgesToCover }
            } ?: break

            selectedPaths.add(bestPath)

            // Remove edges covered by this path
            edgesToCover.removeAll(bestPath.zip(bestPath.drop(1)))

            allPaths.remove(bestPath)
        }

        // Step 5: Generate TestCase objects for selected paths
        for ((index, path) in selectedPaths.withIndex()) {
            val pathDesc = path.joinToString(" -> ") { it.id }
            val comment = "Nodes: $nodesStr\n# Edges: $edgesStr\n# Path $index: $pathDesc"
            testCases.add(
                TestCase(
                    name = "${function.name ?: "func"}_edge_$index",
                    description = comment
                )
            )
        }

        return testCases
    }
}
