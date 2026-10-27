package skyhanni.plugin.areas.event.primary

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.classOrObjectVisitor
import skyhanni.plugin.SkyHanniBundle
import skyhanni.plugin.api.SkyHanniIds
import skyhanni.plugin.api.addAnnotation
import skyhanni.plugin.api.hasAnnotation
import skyhanni.plugin.api.inspections.SkyHanniInspection
import skyhanni.plugin.api.isAbstract
import skyhanni.plugin.api.isSkyHanniEventClass
import skyhanni.plugin.areas.event.PrimaryFunctionIndex

class MissingPrimaryFunctionInspection : SkyHanniInspection<KtClassOrObject, MissingPrimaryFunctionInspection.Context>() {

    class Context(candidate: String?) : Problem {
        override val message = SkyHanniBundle.message("primary.problem.missing")
        override val highlightType = ProblemHighlightType.WEAK_WARNING
        override val fixes: List<LocalQuickFix> = listOfNotNull(candidate?.let(::AddPrimaryFunctionFix))
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> =
        classOrObjectVisitor { visitTargetElement(it, holder, isOnTheFly) }

    override fun isApplicableByPsi(element: KtClassOrObject): Boolean = !element.isAbstract() && element.name != null

    override fun getApplicableRanges(element: KtClassOrObject): List<TextRange> = nameIdentifierRange(element)

    override fun KaSession.prepareContext(element: KtClassOrObject): Context? {
        if (hasAnnotation(element, SkyHanniIds.PRIMARY_FUNCTION) || !isSkyHanniEventClass(element)) return null
        val candidate = "on" + element.name.orEmpty().removeSuffix("Event")
        return Context(candidate.takeUnless { it in PrimaryFunctionIndex.all(element.project) })
    }
}

private class AddPrimaryFunctionFix(private val name: String) : KotlinModCommandQuickFix<KtClassOrObject>() {
    override fun getFamilyName() = SkyHanniBundle.message("primary.fix.add.family")

    override fun getName() = SkyHanniBundle.message("primary.fix.add", name)

    override fun applyFix(project: Project, element: KtClassOrObject, updater: ModPsiUpdater) {
        element.addAnnotation(SkyHanniIds.PRIMARY_FUNCTION, "\"$name\"")
    }
}
