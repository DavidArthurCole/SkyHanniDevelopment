package skyhanni.plugin.areas.event

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.util.AnnotationModificationHelper
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.isPublic

private const val EVENT_TYPE_PARAM = "eventType"
private const val EVENT_TYPES_PARAM = "eventTypes"
private val eventParams = setOf(EVENT_TYPE_PARAM, EVENT_TYPES_PARAM)

class HandleEventInspection : AbstractKotlinInspection() {

    override fun getDisplayName() = "Event handler function should be annotated with @HandleEvent"
    override fun getShortName() = "HandleEventInspection"
    override fun getGroupDisplayName() = "SkyHanni"
    override fun isEnabledByDefault() = true

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor = object : KtVisitorVoid() {
        override fun visitNamedFunction(function: KtNamedFunction) {
            if (function.containingClassOrObject !is KtObjectDeclaration) return
            val functionName = function.name ?: return
            val primaryNameMap = buildPrimaryNameMap(function.project)

            val hasHandleEventAnnotation = function.annotationEntries
                .any { it.shortName?.asString() == HANDLE_EVENT_ANNOTATION }

            val isPrimaryFunctionName = primaryNameMap.containsKey(functionName)

            // K2 compatibility guard - type resolution can throw in K2 mode
            val isEventParam = run {
                val typeRef = function.valueParameters.firstOrNull()?.typeReference ?: return@run false
                val rawType = typeRef.text
                    .substringBefore('<')
                    .substringBefore('?')
                    .substringAfterLast('.')
                val scope = GlobalSearchScope.allScope(function.project)
                PsiShortNamesCache.getInstance(function.project)
                    .getClassesByName(rawType, scope)
                    .any { InheritanceUtil.isInheritor(it, SKYHANNI_EVENT_FQN) }
            }

            val isEventReceiver = run {
                val typeRef = function.receiverTypeReference ?: return@run false
                val rawType = typeRef.text
                    .substringBefore('<')
                    .substringBefore('?')
                    .substringAfterLast('.')
                val scope = GlobalSearchScope.allScope(function.project)
                PsiShortNamesCache.getInstance(function.project)
                    .getClassesByName(rawType, scope)
                    .any { InheritanceUtil.isInheritor(it, SKYHANNI_EVENT_FQN) }
            }

            val annotationEntry = function.annotationEntries
                .find { it.shortName?.asString() == HANDLE_EVENT_ANNOTATION }

            val valueArguments = annotationEntry?.valueArguments.orEmpty()
            val hasExplicitEventType = valueArguments.any { arg ->
                val argName = arg.getArgumentName()?.asName?.asString()
                val position = valueArguments.indexOf(arg)
                val expressionText = arg.getArgumentExpression()?.text.orEmpty()
                argName in eventParams || position == 0 && expressionText.isNotEmpty()
            }

            // For override functions, visibility is inherited - PSI's isPublic returns true when there's no
            // explicit modifier, but the actual visibility may be internal or less. Require an explicit
            // public keyword for overrides to avoid false positives on internal abstract overrides.
            val isPublic = if (function.hasModifier(KtTokens.OVERRIDE_KEYWORD)) {
                function.hasModifier(KtTokens.PUBLIC_KEYWORD)
            } else {
                function.isPublic || function.hasModifier(KtTokens.PUBLIC_KEYWORD)
            }

            // @HandleEvent on non-public function.
            // For overrides without an explicit visibility modifier, we cannot determine inherited
            // visibility without type resolution. Only block @HandleEvent when the override carries
            // an explicit restricting modifier (private/internal/protected); otherwise the function
            // may legitimately inherit public visibility from its parent.
            val isExplicitlyNonPublic = function.hasModifier(KtTokens.PRIVATE_KEYWORD) ||
                function.hasModifier(KtTokens.INTERNAL_KEYWORD) ||
                function.hasModifier(KtTokens.PROTECTED_KEYWORD)
            val effectivelyNotPublic = if (function.hasModifier(KtTokens.OVERRIDE_KEYWORD)) isExplicitlyNonPublic else !isPublic
            val needsPublic = hasHandleEventAnnotation && (hasExplicitEventType || isPrimaryFunctionName)
            if (effectivelyNotPublic && needsPublic) return holder.registerProblem(
                function,
                "Function must be public to be annotated with @HandleEvent",
                ProblemHighlightType.GENERIC_ERROR
            )

            // Missing @HandleEvent on a clear event handler
            val isEventParamHandler = isEventParam && function.valueParameters.size == 1
            val isNoParamHandler = (isEventReceiver || isPrimaryFunctionName) && function.valueParameters.isEmpty()
            val isMissingAnnotation = (isEventParamHandler || isNoParamHandler) && !hasHandleEventAnnotation
            if (isMissingAnnotation && isPublic && !function.hasModifier(KtTokens.OPEN_KEYWORD)) {
                return holder.registerProblem(
                    function,
                    "Event handler function should be annotated with @HandleEvent",
                    AddHandleEventAnnotationFix()
                )
            }

            // @HandleEvent on a function that doesn't take a SkyHanniEvent
            if (!isEventParam && !isEventReceiver && !hasExplicitEventType && !isPrimaryFunctionName && hasHandleEventAnnotation) {
                holder.registerProblem(
                    function,
                    "Function should not be annotated with @HandleEvent if it does not take a SkyHanniEvent",
                    ProblemHighlightType.GENERIC_ERROR
                )
            }
        }
    }
}

private class AddHandleEventAnnotationFix : LocalQuickFix {
    override fun getName() = "Annotate with @HandleEvent"
    override fun getFamilyName() = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        AnnotationModificationHelper.addAnnotation(
            descriptor.psiElement as KtNamedFunction,
            FqName(HANDLE_EVENT_FQN),
            null,
            null,
            { null },
            " ",
            null
        )
    }
}
