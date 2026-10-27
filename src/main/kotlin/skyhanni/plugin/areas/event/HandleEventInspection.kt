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
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.namedFunctionVisitor
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import skyhanni.plugin.SkyHanniBundle
import skyhanni.plugin.api.SkyHanniIds
import skyhanni.plugin.api.addAnnotation
import skyhanni.plugin.api.annotation
import skyhanni.plugin.api.classArguments
import skyhanni.plugin.api.classPsi
import skyhanni.plugin.api.inspections.SkyHanniInspection
import skyhanni.plugin.api.isSkyHanniEvent

class HandleEventInspection : SkyHanniInspection<KtNamedFunction, HandleEventInspection.Context>() {

    sealed interface Context : Problem {
        class MissingByType(eventName: String) : Context {
            override val message = SkyHanniBundle.message("event.problem.seemsToHandle", eventName)
            override val fixes: List<LocalQuickFix> = listOf(AddHandleEventFix())
        }

        class MissingByName(eventName: String) : Context {
            override val message = SkyHanniBundle.message("event.problem.nameMatchesPrimary", eventName)
            override val highlightType = ProblemHighlightType.WARNING
            override val fixes: List<LocalQuickFix> = listOf(AddHandleEventFix())
        }

        class Redundant(annotationIndex: Int) : Context {
            override val message = SkyHanniBundle.message("event.problem.redundantHandleEvent")
            override val highlightType = ProblemHighlightType.GENERIC_ERROR
            override val fixes: List<LocalQuickFix> = listOf(RemoveHandleEventFix(annotationIndex))
        }
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> =
        namedFunctionVisitor { visitTargetElement(it, holder, isOnTheFly) }

    override fun isApplicableByPsi(element: KtNamedFunction): Boolean =
        element.containingClassOrObject is KtObjectDeclaration && element.name != null

    override fun getApplicableRanges(element: KtNamedFunction): List<TextRange> = nameIdentifierRange(element)

    override fun KaSession.prepareContext(element: KtNamedFunction): Context? {
        val name = element.name ?: return null
        val handleEvent = annotation(element, SkyHanniIds.HANDLE_EVENT)
        val paramType = element.valueParameters.singleOrNull()?.symbol?.returnType?.takeIf { isSkyHanniEvent(it) }
        val receiverType = element.symbol.receiverParameter?.returnType?.takeIf { isSkyHanniEvent(it) }
        val explicitEventType = handleEvent?.classArguments("eventType", "eventTypes")
            ?.any { it != SkyHanniIds.SKYHANNI_EVENT } == true
        val primaryName = name in PrimaryFunctionIndex.all(element.project)
        val isPrivate = element.symbol.visibility == KaSymbolVisibility.PRIVATE
        val canBeHandler = !element.hasModifier(KtTokens.OPEN_KEYWORD) &&
            !(element.hasModifier(KtTokens.OVERRIDE_KEYWORD) && element.symbol.visibility != KaSymbolVisibility.PUBLIC)

        if (handleEvent != null) {
            val handlesEvent = paramType != null || receiverType != null || explicitEventType || primaryName
            val index = element.annotationEntries.indexOf(handleEvent.psi as? KtAnnotationEntry)
            return Context.Redundant(index).takeUnless { handlesEvent }
        }
        val eventType = paramType ?: receiverType?.takeIf { element.valueParameters.isEmpty() && !isPrivate }
        return when {
            !canBeHandler -> null
            eventType != null -> Context.MissingByType(classPsi(eventType)?.name ?: "event")
            primaryName -> Context.MissingByName(
                PrimaryFunctionIndex.get(element.project)[name]?.shortClassName?.asString() ?: name,
            )
            else -> null
        }
    }
}

private class AddHandleEventFix : KotlinModCommandQuickFix<KtNamedFunction>() {
    override fun getFamilyName() = SkyHanniBundle.message("event.fix.addHandleEvent")

    override fun applyFix(project: Project, element: KtNamedFunction, updater: ModPsiUpdater) {
        element.addAnnotation(SkyHanniIds.HANDLE_EVENT)
    }
}

private class RemoveHandleEventFix(private val annotationIndex: Int) : KotlinModCommandQuickFix<KtNamedFunction>() {
    override fun getFamilyName() = SkyHanniBundle.message("event.fix.removeHandleEvent")

    override fun applyFix(project: Project, element: KtNamedFunction, updater: ModPsiUpdater) {
        element.annotationEntries.getOrNull(annotationIndex)?.delete()
    }
}
