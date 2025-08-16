package edu.sharif.sqrlab.more_coverage.coverages

import com.jetbrains.python.psi.impl.PyFunctionImpl
import edu.sharif.sqrlab.more_coverage.AbstractCoverageTestGeneratorAction
import edu.sharif.sqrlab.more_coverage.builders.ControlFlowGraphBuilder
import edu.sharif.sqrlab.more_coverage.models.CFGNode
import edu.sharif.sqrlab.more_coverage.models.TestCase

/**
 * PrimePathCoverage generates **maximal prime paths** in the CFG of a Python function.
 *
 * Algorithm:
 * 1. Enumerate paths starting from source nodes only.
 * 2. Extend paths iteratively:
 *    - Mark as finished if a sink is reached or a self-loop occurs.
 *    - Remove paths that cannot be extended.
 * 3. Greedy extension of finished paths:
 *    - Start from the longest finished path.
 *    - Extend left and right using other finished paths without duplicating subpaths.
 * 4. Generate test cases for all resulting maximal prime paths.
 *
 * Back edges are included **once** in a path to avoid infinite loops.
 */
class PrimePathCoverage : AbstractCoverageTestGeneratorAction() {

    override val name: String = "PrimePathCoverage"

    override fun generate(function: PyFunctionImpl): Iterable<TestCase> {
        val testCases = mutableListOf<TestCase>()

        // Step 0: Build CFG
        val cfg = ControlFlowGraphBuilder.build(function)
        val nodes = cfg.nodes
        val edges = cfg.edges.map { it.from to it.to }

        // Identify source and sink nodes
        val sources = nodes.filter { node -> edges.none { it.second == node } }
        val sinks = nodes.filter { node -> edges.none { it.first == node } }

        // Step 1: Prepare debug strings for test case descriptions
        val nodesStr = nodes.joinToString(", ") { "${it.id}:${it.label.replace("\n", " ")}" }
        val edgesStr = edges.joinToString(", ") { "${it.first.id}->${it.second.id}" }

        // Step 2: Initialize BFS queue with paths starting from source nodes
        val finishedPaths = mutableListOf<List<CFGNode>>() // Paths that reached sink or back edge
        val queue = ArrayDeque<List<CFGNode>>()
        sources.forEach { queue.add(listOf(it)) }

        // Step 3: Enumerate paths iteratively with back-edge-once logic
        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            val lastNode = path.last()

            // Get successors of the last node
            val successors = edges.filter { it.first == lastNode }.map { it.second }

            if (successors.isEmpty()) {
                // Sink reached -> finished path
                finishedPaths.add(path)
            } else {
                // Extend path for each successor
                for (succ in successors) {
                    // Count visits to allow back edge once
                    val timesVisited = path.count { it == succ }
                    if (timesVisited < 2) {
                        queue.add(path + succ)
                    }
                }
            }
        }

        // Step 4: Greedy extension of finished paths to form maximal prime paths
        val primePaths = mutableListOf<List<CFGNode>>()
        val remainingPaths = finishedPaths.toMutableList()

        while (remainingPaths.isNotEmpty()) {
            // Pick the longest remaining path
            val path = remainingPaths.maxByOrNull { it.size }!!
            remainingPaths.remove(path)
            var extendedPath = path.toMutableList()

            // Extend left using other finished paths
            for (candidate in remainingPaths.toList()) {
                if (candidate.last() == extendedPath.first() && !extendedPath.containsAll(candidate)) {
                    extendedPath = (candidate.dropLast(1) + extendedPath).toMutableList()
                    remainingPaths.remove(candidate)
                }
            }

            // Extend right using other finished paths
            for (candidate in remainingPaths.toList()) {
                if (candidate.first() == extendedPath.last() && !extendedPath.containsAll(candidate)) {
                    extendedPath = (extendedPath + candidate.drop(1)).toMutableList()
                    remainingPaths.remove(candidate)
                }
            }

            primePaths.add(extendedPath)
        }

        // Step 5: Generate TestCase objects for all prime paths
        for ((index, path) in primePaths.withIndex()) {
            val pathDesc = path.joinToString(" -> ") { "${it.id}:${it.label.replace("\n", " ")}" }
            val comment = "Nodes: $nodesStr\n# Edges: $edgesStr\n# Prime Path $index: $pathDesc"
            testCases.add(TestCase("${function.name ?: "func"}_prime_$index", comment))
        }

        return testCases
    }
}
