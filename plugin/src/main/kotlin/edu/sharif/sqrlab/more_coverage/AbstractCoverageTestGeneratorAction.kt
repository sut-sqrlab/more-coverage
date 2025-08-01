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

        for (test in this.generate(function)) {
            sb.append("# ")
            sb.appendLine(test.description)
            sb.append("def test_")
                .append(function.name ?: "unknown")
                .append("__")
                .append(test.name)
                .appendLine("():")
                // TODO: check whether the editor is set to use tabs or spaces
                .appendLine("    pass")
                .appendLine()
        }

        testFile.writeText(sb.toString())
    }

    abstract val name: String
    abstract fun generate(function: PyFunctionImpl): Iterable<TestCase>
}
