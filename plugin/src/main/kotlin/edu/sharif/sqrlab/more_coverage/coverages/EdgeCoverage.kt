package edu.sharif.sqrlab.more_coverage.coverages

import com.jetbrains.python.psi.impl.PyFunctionImpl
import edu.sharif.sqrlab.more_coverage.AbstractCoverageTestGeneratorAction
import edu.sharif.sqrlab.more_coverage.builders.ControlFlowGraphBuilder
import edu.sharif.sqrlab.more_coverage.models.CFGNode
import edu.sharif.sqrlab.more_coverage.models.TestCase

/**
 * EdgeCoverage generates test cases that cover all edges
 * in the control flow graph (CFG) of a Python function.
 * Back edges (loops) are considered only once per path.
 */
class EdgeCoverage : AbstractCoverageTestGeneratorAction() {

    override val name: String = "EdgeCoverage"

    /**
     * Generates a minimal set of test cases covering all edges
     * in the function's CFG. Handles loops by counting back edges once.
     */
    override fun generate(function: PyFunctionImpl): Iterable<TestCase> {
        val testCases = mutableListOf<TestCase>()

        // Step 0: Build the CFG
        val cfg = ControlFlowGraphBuilder.build(function)

        // Step 1: Collect all edges
        val allEdges = cfg.edges.map { it.from to it.to }.toMutableSet()

        // Step 2: BFS to collect all paths from sources to sinks
        val sources = cfg.nodes.filter { node -> cfg.edges.none { it.to == node } }
        val allPaths = mutableListOf<List<CFGNode>>()
        val queue = ArrayDeque<List<CFGNode>>()
        sources.forEach { queue.add(listOf(it)) }

        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            val lastNode = path.last()

            val visitedEdgesInPath = path.zip(path.drop(1)).toSet()

            val successors = cfg.edges
                .filter { it.from == lastNode }
                .map { it.to to it.from }
                .filter { (succ, from) -> Pair(from, succ) !in visitedEdgesInPath }

            if (successors.isEmpty()) {
                allPaths.add(path) // path ends
            } else {
                for ((succ, _) in successors) {
                    queue.add(path + succ)
                }
            }
        }

        // Step 3: Greedy selection of minimal paths covering all edges
        val edgesToCover = allEdges.toMutableSet()
        val selectedPaths = mutableListOf<List<CFGNode>>()

        while (edgesToCover.isNotEmpty()) {
            val bestPath = allPaths.maxByOrNull { path ->
                path.zip(path.drop(1)).count { it in edgesToCover }
            } ?: break

            selectedPaths.add(bestPath)
            edgesToCover.removeAll(bestPath.zip(bestPath.drop(1)))
            allPaths.remove(bestPath)
        }

        // Step 4: Generate TestCase objects for selected paths
        for ((index, path) in selectedPaths.withIndex()) {
            val edgeList = path.zip(path.drop(1))
            val coveredLines = path.flatMap { it.lineNumbers }.toSet()

            val pathDesc = path.joinToString(" -> ") {
                "${it.id}:${it.label.replace("\n", " ")} [lines: ${it.lineNumbers.joinToString(", ")}]"
            }

            val comment = """
                # Path $index: $pathDesc
                # Expected edges: ${edgeList.joinToString(", ") { "${it.first.id}->${it.second.id}" }}
                # Expected lines: ${coveredLines.joinToString(", ")}
            """.trimIndent().prependIndent("    ")

            val body = """
                pass  # TODO: Call your function here, e.g., ${function.name}(args)
            """.trimIndent()

            testCases.add(
                TestCase(
                    name = "${function.name ?: "func"}_edge_$index",
                    description = comment,
                    expectedLines = coveredLines,
                    body = body
                )
            )
        }

        return testCases
    }
}
