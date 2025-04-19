package edu.sharif.sqrlab.more_coverage.coverages

import com.jetbrains.python.psi.impl.PyFunctionImpl
import edu.sharif.sqrlab.more_coverage.AbstractCoverageTestGeneratorAction
import edu.sharif.sqrlab.more_coverage.models.TestCase

class SimpleStatementCoverage : AbstractCoverageTestGeneratorAction() {
    override val name: String = "Statement Coverage"

    override fun generate(function: PyFunctionImpl): Iterable<TestCase> {
        val tests = ArrayList<TestCase>()

        for ((i, stmt) in function.statementList.statements.withIndex()) {
            tests.add(TestCase("statement${i}", "Write a test case that hits: `${stmt.text}`"))
        }

        return tests
    }
}