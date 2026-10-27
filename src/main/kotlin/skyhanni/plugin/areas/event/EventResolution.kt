package skyhanni.plugin.areas.event

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.stubindex.KotlinFunctionShortNameIndex
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import skyhanni.plugin.api.SkyHanniIds
import skyhanni.plugin.api.annotation
import skyhanni.plugin.api.classArguments
import skyhanni.plugin.api.classPsi
import skyhanni.plugin.api.findKtClass
import skyhanni.plugin.api.hasAnnotation
import skyhanni.plugin.api.isSkyHanniEvent
import skyhanni.plugin.api.stringArgument

fun resolveEventClass(function: KtNamedFunction): KtClassOrObject? = analyze(function) { resolveEventClass(function) }

fun KaSession.resolveEventClass(function: KtNamedFunction): KtClassOrObject? {
    val project = function.project
    val handleEvent = annotation(function, SkyHanniIds.HANDLE_EVENT)
    val explicit = handleEvent?.classArguments("eventType", "eventTypes")?.firstOrNull { it != SkyHanniIds.SKYHANNI_EVENT }
    val signatureType = listOfNotNull(
        function.valueParameters.singleOrNull()?.symbol?.returnType,
        function.symbol.receiverParameter?.returnType,
    ).firstOrNull { isSkyHanniEvent(it) }
    return explicit?.findKtClass(project)
        ?: signatureType?.let { classPsi(it) }
        ?: function.name?.takeIf { handleEvent != null }?.let { PrimaryFunctionIndex.eventClass(project, it) }
}

fun findHandlersForEvent(klass: KtClassOrObject): List<KtNamedFunction> {
    val project = klass.project
    val scope = GlobalSearchScope.projectScope(project)
    val fromReferences = ReferencesSearch.search(klass, scope).findAll().mapNotNull { reference ->
        val element = reference.element
        val function = element.getStrictParentOfType<KtNamedFunction>() ?: return@mapNotNull null
        val inSignature = element.getStrictParentOfType<KtParameter>()?.let { it.ownerFunction == function } == true ||
            element.getStrictParentOfType<KtTypeReference>() == function.receiverTypeReference ||
            element.getStrictParentOfType<KtAnnotationEntry>()?.let { it.parent?.parent == function } == true
        function.takeIf { inSignature && it.hasAnnotation(SkyHanniIds.HANDLE_EVENT) }
    }
    val primaryName = analyze(klass) { annotation(klass, SkyHanniIds.PRIMARY_FUNCTION)?.stringArgument() }
    val fromName = primaryName?.let { name ->
        KotlinFunctionShortNameIndex.Helper.get(name, project, scope).filter {
            it.containingClassOrObject is KtObjectDeclaration && it.hasAnnotation(SkyHanniIds.HANDLE_EVENT)
        }
    }.orEmpty()
    return (fromReferences + fromName).distinct()
}
