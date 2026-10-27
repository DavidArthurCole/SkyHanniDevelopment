package skyhanni.plugin.areas.event

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.namedFunctionVisitor
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import skyhanni.plugin.SkyHanniBundle
import skyhanni.plugin.api.SkyHanniIds
import skyhanni.plugin.api.hasAnnotation
import skyhanni.plugin.api.inspections.SkyHanniInspection

class HandleEventVisibilityInspection : SkyHanniInspection<KtNamedFunction, HandleEventVisibilityInspection.Context>() {

    object Context : Problem {
        override val message = SkyHanniBundle.message("event.problem.shouldBePrivate")
        override val highlightType = ProblemHighlightType.WEAK_WARNING
        override val fixes: List<LocalQuickFix> = listOf(MakePrivateFix())
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> =
        namedFunctionVisitor { visitTargetElement(it, holder, isOnTheFly) }

    override fun isApplicableByPsi(element: KtNamedFunction): Boolean =
        element.containingClassOrObject is KtObjectDeclaration &&
            !element.hasModifier(KtTokens.PRIVATE_KEYWORD) &&
            !element.hasModifier(KtTokens.OVERRIDE_KEYWORD)

    override fun getApplicableRanges(element: KtNamedFunction): List<TextRange> = nameIdentifierRange(element)

    override fun KaSession.prepareContext(element: KtNamedFunction): Context? {
        if (!hasAnnotation(element, SkyHanniIds.HANDLE_EVENT)) return null
        return Context.takeIf { element.symbol.visibility != KaSymbolVisibility.PRIVATE }
    }
}

private class MakePrivateFix : KotlinModCommandQuickFix<KtNamedFunction>() {
    override fun getFamilyName() = SkyHanniBundle.message("event.fix.makePrivate")

    override fun applyFix(project: Project, element: KtNamedFunction, updater: ModPsiUpdater) {
        element.addModifier(KtTokens.PRIVATE_KEYWORD)
    }
}
