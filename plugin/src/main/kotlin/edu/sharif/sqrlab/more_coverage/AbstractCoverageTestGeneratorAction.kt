package edu.sharif.sqrlab.more_coverage

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.findOrCreateFile
import com.intellij.openapi.vfs.writeText
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.impl.PyFunctionImpl
import edu.sharif.sqrlab.more_coverage.models.TestCase

abstract class AbstractCoverageTestGeneratorAction : PsiElementBaseIntentionAction() {

    override fun getText(): String = "${this.familyName}: Generate ${this.name} Tests"

    override fun getFamilyName(): String = "More coverage"

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        return true
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val file = element.containingFile.virtualFile
        val function = PsiTreeUtil.getParentOfType(element, PyFunctionImpl::class.java, false) ?: return
        val testFile = file.parent.findOrCreateFile("test_${element.containingFile.name}")

        val sb = StringBuilder()

        // --- Imports ---
        sb.appendLine("import unittest")
        sb.appendLine("import coverage")
        sb.appendLine("import os")
        sb.appendLine("import functools")
        sb.appendLine()
        sb.appendLine()

        // --- Decorator definition ---
        sb.appendLine("def with_coverage(expected_lines, filename=\"${file.name}\"):")
        sb.appendLine("    def decorator(func):")
        sb.appendLine("        @functools.wraps(func)")
        sb.appendLine("        def wrapper(*args, **kwargs):")
        sb.appendLine("            cov = coverage.Coverage()")
        sb.appendLine("            cov.start()")
        sb.appendLine("            try:")
        sb.appendLine("                return func(*args, **kwargs)")
        sb.appendLine("            finally:")
        sb.appendLine("                cov.stop()")
        sb.appendLine("                cov_data = cov.get_data().lines(os.path.abspath(filename)) or set()")
        sb.appendLine("                missing = set(expected_lines) - set(cov_data)")
        sb.appendLine("                assert not missing, f\"Missing lines: {missing}\"")
        sb.appendLine("        return wrapper")
        sb.appendLine("    return decorator")
        sb.appendLine()
        sb.appendLine()

        // --- Test class declaration ---
        val className = "Test_" + (function.name?.replaceFirstChar { it.uppercase() } ?: "Unknown")
        sb.appendLine("class $className(unittest.TestCase):")
        sb.appendLine()

        // --- Generate tests ---
        for (test in this.generate(function)) {
            sb.append("").appendLine(test.description)

            val expectedLines = test.expectedLines.joinToString(", ")
            sb.appendLine("    @with_coverage(expected_lines=[$expectedLines])")

            sb.append("    def test_")
                .append(test.name)
                .appendLine("(self):")

            if (test.body.isBlank()) {
                sb.appendLine("        pass")
            } else {
                test.body.lines().forEach { line ->
                    sb.append("        ").appendLine(line)
                }
            }
            sb.appendLine()
        }

        // --- Main runner ---
        sb.appendLine()
        sb.appendLine("if __name__ == \"__main__\":")
        sb.appendLine("    unittest.main()")

        // --- Write out ---
        testFile.writeText(sb.toString())
    }

    abstract val name: String
    abstract fun generate(function: PyFunctionImpl): Iterable<TestCase>
}
