package skyhanni.plugin.areas.config

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.propertyVisitor
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import skyhanni.plugin.SkyHanniBundle
import skyhanni.plugin.api.inspections.SkyHanniInspection
import skyhanni.plugin.api.isAbstract

class CopyConfigPathInspection : SkyHanniInspection<KtProperty, CopyConfigPathInspection.Context>() {

    object Context : Problem {
        override val message = SkyHanniBundle.message("config.problem.copyPath")
        override val highlightType = ProblemHighlightType.INFORMATION
        override val fixes: List<LocalQuickFix> = listOf(CopyConfigPathFix())
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> =
        propertyVisitor { visitTargetElement(it, holder, isOnTheFly) }

    override fun isApplicableByPsi(element: KtProperty): Boolean =
        element.containingClassOrObject?.isAbstract() == false

    override fun getApplicableRanges(element: KtProperty): List<TextRange> = nameIdentifierRange(element)

    override fun KaSession.prepareContext(element: KtProperty): Context? = Context.takeIf { element.isConfigAnnotated() }
}

private class CopyConfigPathFix : ModCommandQuickFix() {
    override fun getFamilyName() = SkyHanniBundle.message("config.fix.copyPath")

    override fun perform(project: Project, descriptor: ProblemDescriptor): ModCommand {
        val property = PsiTreeUtil.getParentOfType(descriptor.psiElement, KtProperty::class.java) ?: return ModCommand.nop()
        val path = computeConfigPath(property) ?: return ModCommand.nop()
        return ModCommand.copyToClipboard(path).andThen(ModCommand.info(SkyHanniBundle.message("config.notification.copied", path)))
    }
}
