package skyhanni.plugin.areas.event

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import skyhanni.plugin.SkyHanniBundle
import skyhanni.plugin.api.isAbstract
import skyhanni.plugin.api.isSkyHanniEventClass

class EventHandlerLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        for (element in elements) {
            val declaration = element.parent as? KtNamedDeclaration ?: continue
            if (declaration.nameIdentifier != element) continue
            val marker = when (declaration) {
                is KtNamedFunction -> handlerMarker(element, declaration)
                is KtClassOrObject -> eventMarker(element, declaration)
                else -> null
            }
            marker?.let(result::add)
        }
    }

    private fun handlerMarker(anchor: PsiElement, function: KtNamedFunction): LineMarkerInfo<*>? {
        val eventClass = resolveEventClass(function) ?: return null
        return NavigationGutterIconBuilder.create(AllIcons.Gutter.ImplementedMethod)
            .setTargets(eventClass)
            .setTooltipText(SkyHanniBundle.message("event.lineMarker.handles", eventClass.name.orEmpty()))
            .setAlignment(GutterIconRenderer.Alignment.LEFT)
            .createLineMarkerInfo(anchor)
    }

    private fun eventMarker(anchor: PsiElement, klass: KtClassOrObject): LineMarkerInfo<*>? {
        if (klass.isAbstract()) return null
        if (!analyze(klass) { isSkyHanniEventClass(klass) }) return null
        val handlers = findHandlersForEvent(klass).takeIf { it.isNotEmpty() } ?: return null
        return NavigationGutterIconBuilder.create(AllIcons.Gutter.OverridenMethod)
            .setTargets(handlers)
            .setTooltipText(SkyHanniBundle.message("event.lineMarker.handledBy", handlers.size))
            .setPopupTitle(SkyHanniBundle.message("event.lineMarker.popupTitle"))
            .setAlignment(GutterIconRenderer.Alignment.LEFT)
            .createLineMarkerInfo(anchor)
    }
}
