package edu.sharif.sqrlab.more_coverage.coverages

import com.jetbrains.python.psi.impl.PyFunctionImpl
import edu.sharif.sqrlab.more_coverage.AbstractCoverageTestGeneratorAction
import edu.sharif.sqrlab.more_coverage.builders.ControlFlowGraphBuilder
import edu.sharif.sqrlab.more_coverage.models.TestCase

class NodeCoverage : AbstractCoverageTestGeneratorAction() {

    override val name: String = "NodeCoverage"

    override fun generate(function: PyFunctionImpl): Iterable<TestCase> {
        val cfg = ControlFlowGraphBuilder.build(function)
        val tests = mutableListOf<TestCase>()

        for (node in cfg.nodes.filter { it.label.isNotBlank() }) {
            tests.add(
                TestCase(
                    name = "${function.name ?: "func"}_covers_${node.id}",
                    description = "Test for node ${node.id}: ${node.label.take(30).replace("\n", " ")}"
                )
            )
        }

        return tests
    }
}