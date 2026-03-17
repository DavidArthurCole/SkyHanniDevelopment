package skyhanni.plugin.areas.event

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction

class EventHandlerLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(
        elements: List<PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>,
    ) {
        val project = elements.firstOrNull()?.project ?: return
        val facade = JavaPsiFacade.getInstance(project)
        val scope = GlobalSearchScope.allScope(project)

        for (element in elements) {
            val parent = element.parent
            val namedId = (parent as? KtNamedFunction)?.nameIdentifier
            val classId = (parent as? KtClass)?.nameIdentifier
            if (element != namedId && element != classId) continue
            buildMarker(element, parent, facade, scope, project)?.let(result::add)
        }
    }

    private fun buildMarker(
        element: PsiElement,
        parent: PsiElement,
        facade: JavaPsiFacade,
        scope: GlobalSearchScope,
        project: com.intellij.openapi.project.Project,
    ): LineMarkerInfo<*>? = when (parent) {
        is KtNamedFunction -> {
            val eventClass = resolveEventClass(parent, project) ?: return null
            NavigationGutterIconBuilder.create(AllIcons.Gutter.ImplementedMethod)
                .setTargets(eventClass)
                .setTooltipText("Handles event: ${eventClass.name}")
                .setAlignment(GutterIconRenderer.Alignment.LEFT)
                .createLineMarkerInfo(element)
        }

        is KtClass -> {
            if (parent.hasModifier(KtTokens.ABSTRACT_KEYWORD)) return null
            val fqName = parent.fqName?.asString() ?: return null
            val psiClass = facade.findClass(fqName, scope) ?: return null
            if (!InheritanceUtil.isInheritor(psiClass, SKYHANNI_EVENT_FQN)) return null
            val handlers = findHandlersForEvent(psiClass, project).takeIf { it.isNotEmpty() } ?: return null
            NavigationGutterIconBuilder.create(AllIcons.Gutter.OverridenMethod)
                .setTargets(handlers)
                .setTooltipText("Handled by ${handlers.size} handler(s)")
                .setPopupTitle("Event Handlers")
                .setAlignment(GutterIconRenderer.Alignment.LEFT)
                .createLineMarkerInfo(element)
        }

        else -> null
    }
}
