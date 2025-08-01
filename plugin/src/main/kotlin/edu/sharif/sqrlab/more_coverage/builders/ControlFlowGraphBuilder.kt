package edu.sharif.sqrlab.more_coverage.builders

import com.jetbrains.python.psi.*
import edu.sharif.sqrlab.more_coverage.models.CFGNode
import edu.sharif.sqrlab.more_coverage.models.CFGEdge
import edu.sharif.sqrlab.more_coverage.models.ControlFlowGraph
import com.intellij.psi.util.PsiTreeUtil

/**
 * Builder responsible for constructing a Control Flow Graph (CFG)
 * from a given PyFunction in Python code.
 */
object ControlFlowGraphBuilder {

    /**
     * Builds the Control Flow Graph (CFG) for the given PyFunction.
     *
     * @param function The Python function to analyze.
     * @return The constructed CFG.
     */
    fun build(function: PyFunction): ControlFlowGraph {
        val graph = ControlFlowGraph()
        val body = function.statementList.statements

        var previousNode: CFGNode? = null

        for ((i, stmt) in body.withIndex()) {
            val node = CFGNode(
                id = "n$i",
                label = stmt.text,
                element = stmt
            )
            graph.addNode(node)

            // If there was a previous node, add an edge from it to the current node
            // This represents sequential flow between statements
            if (previousNode != null) {
                graph.addEdge(previousNode, node)
            }

            previousNode = node

            // TODO: Currently builds a linear control flow graph assuming sequential execution.
            //       Extend this method to properly handle control structures like:
            //         - if / else branches
            //         - loops (for, while)
            //         - early exits (return, break, continue)
            //       to build an accurate control flow graph representing all possible execution paths.
        }

        return graph
    }
}
