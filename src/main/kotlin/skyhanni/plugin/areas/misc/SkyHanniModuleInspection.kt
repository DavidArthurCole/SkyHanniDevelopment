package skyhanni.plugin.areas.misc

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.name.isSubpackageOf
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.classOrObjectVisitor
import skyhanni.plugin.SkyHanniBundle
import skyhanni.plugin.api.SkyHanniIds
import skyhanni.plugin.api.addAnnotation
import skyhanni.plugin.api.hasAnnotation
import skyhanni.plugin.api.inspections.SkyHanniInspection
import skyhanni.plugin.api.isCallTo

class SkyHanniModuleInspection : SkyHanniInspection<KtClassOrObject, SkyHanniModuleInspection.Context>() {

    sealed interface Context : Problem {
        object WrongTarget : Context {
            override val message = SkyHanniBundle.message("module.problem.onlyObjects")
            override val highlightType = ProblemHighlightType.GENERIC_ERROR
        }

        object Missing : Context {
            override val message = SkyHanniBundle.message("module.problem.missing")
            override val fixes: List<LocalQuickFix> = listOf(AddSkyHanniModuleFix())
        }
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> =
        classOrObjectVisitor { visitTargetElement(it, holder, isOnTheFly) }

    override fun isApplicableByPsi(element: KtClassOrObject): Boolean = element.name != null

    override fun getApplicableRanges(element: KtClassOrObject): List<TextRange> = nameIdentifierRange(element)

    override fun KaSession.prepareContext(element: KtClassOrObject): Context? {
        if (!isFromSkyHanni(element)) return null
        val annotated = hasAnnotation(element, SkyHanniIds.SKYHANNI_MODULE)
        if (element is KtClass) return Context.WrongTarget.takeIf { annotated }
        if (element !is KtObjectDeclaration || annotated) return null
        val body = element.body ?: return null
        val hasHandlers = body.functions.any { isEventHandler(it) }
        val hasRepoPatterns = body.properties.any { isRepoPattern(it) }
        return Context.Missing.takeIf { hasHandlers || hasRepoPatterns }
    }

    private fun isFromSkyHanni(element: KtClassOrObject): Boolean {
        val packageName = element.getClassId()?.packageFqName ?: return false
        return packageName.isSubpackageOf(SkyHanniIds.SKYHANNI_PACKAGE) || packageName == SkyHanniIds.SKYHANNI_PACKAGE
    }

    private fun KaSession.isEventHandler(function: KtNamedFunction): Boolean =
        hasAnnotation(function, SkyHanniIds.HANDLE_EVENT)

    private fun KaSession.isRepoPattern(property: KtProperty): Boolean {
        val type = property.symbol.returnType
        if (type.isSubtypeOf(SkyHanniIds.REPO_PATTERN) ||
            type.isSubtypeOf(SkyHanniIds.REPO_PATTERN_GROUP) ||
            type.isSubtypeOf(SkyHanniIds.REPO_PATTERN_LIST)
        ) {
            return true
        }
        if (!property.hasDelegate()) return false
        val delegate = property.delegateExpression
        val call = delegate as? KtCallExpression
            ?: (delegate as? KtDotQualifiedExpression)?.selectorExpression as? KtCallExpression
            ?: return false
        return isCallTo(call, SkyHanniIds.REPO_PATTERN_FACTORIES)
    }
}

private class AddSkyHanniModuleFix : KotlinModCommandQuickFix<KtClassOrObject>() {
    override fun getFamilyName() = SkyHanniBundle.message("module.fix.add")

    override fun applyFix(project: Project, element: KtClassOrObject, updater: ModPsiUpdater) {
        element.addAnnotation(SkyHanniIds.SKYHANNI_MODULE)
    }
}
