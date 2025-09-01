package edu.sharif.sqrlab.more_coverage.coverages

import com.jetbrains.python.psi.impl.PyFunctionImpl
import edu.sharif.sqrlab.more_coverage.AbstractCoverageTestGeneratorAction
import edu.sharif.sqrlab.more_coverage.builders.ControlFlowGraphBuilder
import edu.sharif.sqrlab.more_coverage.models.CFGNode
import edu.sharif.sqrlab.more_coverage.models.TestCase

/**
 * EdgePairCoverage generates test cases that cover all edge-pairs
 * (paths of length 2) in the CFG of a Python function.
 * Handles loops by including back edges once.
 */
class EdgePairCoverage : AbstractCoverageTestGeneratorAction() {

    override val name: String = "EdgePairCoverage"

    override fun generate(function: PyFunctionImpl): Iterable<TestCase> {
        val testCases = mutableListOf<TestCase>()

        // Step 0: Build the CFG
        val cfg = ControlFlowGraphBuilder.build(function)

        // Step 1: Collect all edge-pairs (n1 -> n2 -> n3)
        val allPairs = cfg.edges.flatMap { first ->
            cfg.edges.filter { it.from == first.to }.map { second ->
                Triple(first.from, first.to, second.to)
            }
        }.toMutableSet()

        // Step 2: BFS to generate paths (allowing back edges once)
        val sources = cfg.nodes.filter { node -> cfg.edges.none { it.to == node } }
        val allPaths = mutableListOf<List<CFGNode>>()
        val queue = ArrayDeque<List<CFGNode>>()
        sources.forEach { queue.add(listOf(it)) }

        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            val lastNode = path.last()
            val successors = cfg.edges.filter { it.from == lastNode }.map { it.to }

            if (successors.isEmpty()) {
                allPaths.add(path)
            } else {
                for (succ in successors) {
                    if (succ !in path || isLoopEdge(cfg, lastNode, succ)) {
                        queue.add(path + succ)
                    }
                }
            }
        }

        // Step 3: Greedy selection of minimal paths covering all edge-pairs
        val pairsToCover = allPairs.toMutableSet()
        val selectedPaths = mutableListOf<List<CFGNode>>()

        while (pairsToCover.isNotEmpty()) {
            val bestPath = allPaths.maxByOrNull { path ->
                (0 until path.size - 2).count { i ->
                    Triple(path[i], path[i + 1], path[i + 2]) in pairsToCover
                }
            } ?: break

            val coveredPairs = (0 until bestPath.size - 2).map { i ->
                Triple(bestPath[i], bestPath[i + 1], bestPath[i + 2])
            }.filter { it in pairsToCover }.toSet()

            pairsToCover.removeAll(coveredPairs)
            selectedPaths.add(bestPath)
            allPaths.remove(bestPath)
        }

        // Step 4: Generate TestCase objects for selected paths
        for ((index, path) in selectedPaths.withIndex()) {
            val coveredLines = path.flatMap { it.lineNumbers }.toSet()
            val pathDesc = path.joinToString(" -> ") {
                "${it.id}:${it.label.replace("\n", " ")} [lines: ${it.lineNumbers.joinToString(", ")}]"
            }
            val pairList = (0 until path.size - 2).map { i ->
                "${path[i].id}->${path[i + 1].id}->${path[i + 2].id}"
            }

            val comment = """
                # Edge-Pair Path $index: $pathDesc
                # Expected edge-pairs: ${pairList.joinToString(", ")}
                # Expected lines: ${coveredLines.joinToString(", ")}
            """.trimIndent().prependIndent("    ")

            val body = """
                pass  # TODO: Call your function here, e.g., ${function.name}(args)
            """.trimIndent()

            testCases.add(
                TestCase(
                    name = "${function.name ?: "func"}_edgepair_$index",
                    description = comment,
                    expectedLines = coveredLines,
                    body = body
                )
            )
        }

        return testCases
    }

    /**
     * Determines if an edge is a back edge forming a loop.
     * Used to allow traversing back edges once for edge-pair coverage.
     */
    private fun isLoopEdge(
        cfg: edu.sharif.sqrlab.more_coverage.models.ControlFlowGraph,
        from: CFGNode,
        to: CFGNode
    ): Boolean {
        return cfg.edges.any { it.from == from && it.to == to && cfg.nodes.indexOf(to) <= cfg.nodes.indexOf(from) }
    }
}
