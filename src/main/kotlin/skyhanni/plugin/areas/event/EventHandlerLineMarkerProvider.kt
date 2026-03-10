package skyhanni.plugin.areas.event

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Replaces def_fun_jumper.kt entirely.
 *
 * The LivePlugin used two mechanisms:
 *  1. An inspection that highlighted matching function names with a quick fix
 *  2. A Ctrl+Shift+Click EditorMouseListener for navigation
 *
 * A LineMarkerProvider is the correct IntelliJ idiom for this — it adds a
 * gutter icon that is clickable and shows a tooltip, without needing any
 * special key combo or an inspection highlight the user has to dismiss.
 *
 * Registered in plugin.xml under:
 *   <codeInsight.lineMarkerProvider language="kotlin"
 *       implementationClass="...EventHandlerLineMarkerProvider"/>
 */
class EventHandlerLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(
        elements: List<PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>
    ) {
        val project = elements.firstOrNull()?.project ?: return
        val facade = JavaPsiFacade.getInstance(project)
        val scope = GlobalSearchScope.allScope(project)
        val primaryNameMap by lazy { buildPrimaryNameMap(project) }

        for (element in elements) {
            val function = element.parent as? KtNamedFunction ?: continue
            if (element != function.nameIdentifier) continue

            val eventClass = resolveEventClass(function, facade, scope, primaryNameMap) ?: continue

            NavigationGutterIconBuilder
                .create(AllIcons.Gutter.ImplementedMethod)
                .setTargets(eventClass)
                .setTooltipText("Handles event: ${eventClass.name}")
                .setAlignment(GutterIconRenderer.Alignment.LEFT)
                .createLineMarkerInfo(element)
                .let(result::add)
        }
    }

    /**
     * Resolves the event class for a function using three strategies in order:
     *
     * 1. @HandleEvent(eventType = SomeEvent::class) — explicit class reference in annotation
     * 2. @HandleEvent fun onX(event: SomeEvent) — single SkyHanniEvent parameter
     * 3. @PrimaryFunction("onX") on SomeEvent — function name matches primary name map
     */
    private fun resolveEventClass(
        function: KtNamedFunction,
        facade: JavaPsiFacade,
        scope: GlobalSearchScope,
        primaryNameMap: Map<String, String>
    ): PsiClass? {
        val hasHandleEvent = function.annotationEntries
            .any { it.shortName?.asString() == HANDLE_EVENT_ANNOTATION }

        // Strategy 1: explicit eventType = SomeEvent::class in @HandleEvent
        if (hasHandleEvent) {
            val annotation = function.annotationEntries
                .find { it.shortName?.asString() == HANDLE_EVENT_ANNOTATION }

            val eventTypeArgument = annotation?.valueArguments?.firstOrNull { arg ->
                val name = arg.getArgumentName()?.asName?.asString()
                name == "eventType" || name == "eventTypes" || name == null
            }

            val classLiteral = eventTypeArgument?.getArgumentExpression() as? KtClassLiteralExpression
            val explicitClass = classLiteral?.receiverExpression?.text?.substringAfterLast('.')
            if (explicitClass != null) {
                val candidates = PsiShortNamesCache.getInstance(function.project)
                    .getClassesByName(explicitClass, scope)
                candidates.firstOrNull {
                    InheritanceUtil.isInheritor(it, SKYHANNI_EVENT_FQN)
                }?.let { return it }
            }

            // Strategy 2: single event parameter
            if (function.valueParameters.size == 1) {
                val typeRef = function.valueParameters.first().typeReference ?: return null
                val rawType = typeRef.text
                    .substringBefore('<')
                    .substringBefore('?')
                    .substringAfterLast('.')
                val candidates = PsiShortNamesCache.getInstance(function.project)
                    .getClassesByName(rawType, scope)
                return candidates.firstOrNull {
                    InheritanceUtil.isInheritor(it, SKYHANNI_EVENT_FQN)
                }
            }
        }

        // Strategy 3: @PrimaryFunction name match
        return primaryNameMap[function.name.orEmpty()]?.let { primaryClassName ->
            facade.findClass(primaryClassName, scope)
        }
    }
}
