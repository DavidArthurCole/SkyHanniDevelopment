package skyhanni.plugin.api.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemDescriptorBase
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import skyhanni.plugin.api.rangeIn

abstract class SkyHanniInspection<E : KtElement, C : SkyHanniInspection.Problem> : KotlinApplicableInspectionBase<E, C>() {

    interface Problem {
        val message: String
        val highlightType: ProblemHighlightType
            get() = ProblemHighlightType.GENERIC_ERROR_OR_WARNING
        val fixes: List<LocalQuickFix>
            get() = emptyList()
        val rangeInElement: TextRange?
            get() = null
    }

    final override fun InspectionManager.createProblemDescriptor(
        element: E,
        context: C,
        rangeInElement: TextRange?,
        onTheFly: Boolean,
    ): ProblemDescriptor = ProblemDescriptorBase(
        element,
        element,
        context.message,
        context.fixes.toTypedArray(),
        context.highlightType,
        false,
        context.rangeInElement ?: rangeInElement,
        onTheFly,
        onTheFly,
    )

    protected fun nameIdentifierRange(element: KtNamedDeclaration): List<TextRange> =
        listOf(element.nameIdentifier?.rangeIn(element) ?: TextRange(0, element.textLength))
}
