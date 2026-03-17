package skyhanni.plugin.areas.event.primary

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.refactoring.rename.RenameProcessor
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import skyhanni.plugin.areas.event.HANDLE_EVENT_ANNOTATION
import skyhanni.plugin.areas.event.PRIMARY_FUNCTION_ANNOTATION
import skyhanni.plugin.areas.event.resolveEventClass


class MismatchedPrimaryFunctionInspection : AbstractKotlinInspection() {

    override fun getDisplayName() = "Handler function name does not match @PrimaryFunction for the event"
    override fun getShortName() = "MismatchedPrimaryFunctionName"
    override fun getGroupDisplayName() = "SkyHanni"
    override fun isEnabledByDefault() = true

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor = object : KtVisitorVoid() {
        @Suppress("ReturnCount")
        override fun visitNamedFunction(function: KtNamedFunction) {
            if (function.containingClassOrObject !is KtObjectDeclaration) return
            val functionName = function.name ?: return

            val hasHandleEvent = function.annotationEntries
                .any { it.shortName?.asString() == HANDLE_EVENT_ANNOTATION }
            if (!hasHandleEvent) return

            val psiClass = resolveEventClass(function, function.project) ?: return
            val ktClass = psiClass.navigationElement as? KtClassOrObject ?: return

            val primaryFunctionAnnotation = ktClass.annotationEntries
                .firstOrNull { it.shortName?.asString() == PRIMARY_FUNCTION_ANNOTATION } ?: return
            val expectedName = primaryFunctionAnnotation.valueArguments.firstOrNull()
                ?.getArgumentExpression()?.text?.removeSurrounding("\"")
                ?.takeIf { it.isNotBlank() } ?: return

            if (functionName == expectedName) return

            val eventClassName = psiClass.name ?: "event"
            holder.registerProblem(
                function.nameIdentifier ?: return,
                "Handler name '$functionName' does not match @PrimaryFunction(\"$expectedName\") on $eventClassName",
                ProblemHighlightType.WARNING,
                RenameToPrimaryFunctionFix(expectedName),
            )
        }
    }
}

/**
 * Quick fix that delegates to IntelliJ's RenameProcessor, giving us full
 * refactor behavior. conflict detection, find-usages, preview, etc.
 */
private class RenameToPrimaryFunctionFix(private val newName: String) : LocalQuickFix {

    override fun getFamilyName() = "Rename to '$newName'"
    override fun getName() = familyName

    override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo =
        IntentionPreviewInfo.EMPTY

    // We need the full PSI element, so we cannot use the "safe" applyFix(Project, ProblemDescriptor)
    // that only receives a project, we use the descriptor overload instead.
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val function = descriptor.psiElement.parent as? KtNamedFunction ?: return
        ApplicationManager.getApplication().invokeLater {
            RenameProcessor(
                project,
                function,
                newName,
                /* isSearchInComments = */ false,
                /* isSearchTextOccurrences = */ false,
            ).run()
        }
    }
}
