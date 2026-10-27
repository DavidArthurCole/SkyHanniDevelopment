package skyhanni.plugin.areas.event.primary

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.namedFunctionVisitor
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import skyhanni.plugin.SkyHanniBundle
import skyhanni.plugin.api.SkyHanniIds
import skyhanni.plugin.api.annotation
import skyhanni.plugin.api.hasAnnotation
import skyhanni.plugin.api.inspections.SkyHanniInspection
import skyhanni.plugin.api.stringArgument
import skyhanni.plugin.areas.event.resolveEventClass

class MismatchedPrimaryFunctionInspection : SkyHanniInspection<KtNamedFunction, MismatchedPrimaryFunctionInspection.Context>() {

    class Context(actual: String, expected: String, eventName: String) : Problem {
        override val message = SkyHanniBundle.message("primary.problem.mismatched", actual, expected, eventName)
        override val highlightType = ProblemHighlightType.WARNING
        override val fixes: List<LocalQuickFix> = listOf(RenameToPrimaryFunctionFix(expected))
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> =
        namedFunctionVisitor { visitTargetElement(it, holder, isOnTheFly) }

    override fun isApplicableByPsi(element: KtNamedFunction): Boolean =
        element.containingClassOrObject is KtObjectDeclaration && element.name != null

    override fun getApplicableRanges(element: KtNamedFunction): List<TextRange> = nameIdentifierRange(element)

    override fun KaSession.prepareContext(element: KtNamedFunction): Context? {
        val name = element.name ?: return null
        if (!hasAnnotation(element, SkyHanniIds.HANDLE_EVENT)) return null
        val eventClass = resolveEventClass(element) ?: return null
        val expected = annotation(eventClass, SkyHanniIds.PRIMARY_FUNCTION)?.stringArgument() ?: return null
        if (name == expected) return null
        return Context(name, expected, eventClass.name ?: "event")
    }
}

private class RenameToPrimaryFunctionFix(private val newName: String) : KotlinModCommandQuickFix<KtNamedFunction>() {
    override fun getFamilyName() = SkyHanniBundle.message("primary.fix.rename", newName)

    override fun applyFix(project: Project, element: KtNamedFunction, updater: ModPsiUpdater) {
        element.setName(newName)
    }
}
