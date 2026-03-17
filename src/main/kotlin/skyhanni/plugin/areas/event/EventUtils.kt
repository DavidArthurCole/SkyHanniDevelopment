package skyhanni.plugin.areas.event

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType

const val SKYHANNI_EVENT_FQN = "at.hannibal2.skyhanni.api.event.SkyHanniEvent"
const val HANDLE_EVENT_ANNOTATION = "HandleEvent"
const val HANDLE_EVENT_FQN = "at.hannibal2.skyhanni.api.event.HandleEvent"
const val PRIMARY_FUNCTION_ANNOTATION = "PrimaryFunction"

/**
 * Returns the simple referenced class name from this type reference,
 * stripping nullability and generic type arguments via PSI rather than text manipulation.
 *
 * e.g. `EntityNameTagRenderEvent<Player>` -> `"EntityNameTagRenderEvent"`
 *      `SomeEvent?` -> `"SomeEvent"`
 */
private fun KtTypeReference.referencedTypeName(): String? {
    val userType = (typeElement as? KtNullableType)?.innerType as? KtUserType
        ?: typeElement as? KtUserType
        ?: return null
    return userType.referencedName
}

/**
 * Searches all non-abstract classes inheriting from SkyHanniEvent and builds a map of
 * primary function name to fully-qualified event class name, sourced from
 * the @PrimaryFunction annotation on each event class.
 */
fun buildPrimaryNameMap(project: Project): Map<String, String> {
    val facade = JavaPsiFacade.getInstance(project)
    val skyHanniEventClass = facade.findClass(SKYHANNI_EVENT_FQN, GlobalSearchScope.allScope(project))
        ?: return emptyMap()

    return ClassInheritorsSearch
        .search(skyHanniEventClass, GlobalSearchScope.allScope(project), true, true, false)
        .mapNotNull { psiInheritor ->
            val ktClass = psiInheritor.navigationElement as? KtClassOrObject ?: return@mapNotNull null
            if (ktClass.hasModifier(KtTokens.ABSTRACT_KEYWORD)) return@mapNotNull null
            val annotation = ktClass.annotationEntries
                .firstOrNull { it.shortName?.asString() == PRIMARY_FUNCTION_ANNOTATION }
                ?: return@mapNotNull null
            val name = annotation.valueArguments.firstOrNull()
                ?.getArgumentExpression()?.text
                ?.removeSurrounding("\"")
                ?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val fqn = ktClass.fqName?.asString() ?: return@mapNotNull null
            name to fqn
        }
        .toMap()
}

/**
 * Resolves the event class handled by [function] using three strategies:
 * 1. Explicit eventType = SomeEvent::class in @HandleEvent
 * 2. Single SkyHanniEvent-typed parameter
 * 3. Function name matching a @PrimaryFunction-annotated event class
 */
fun resolveEventClass(function: KtNamedFunction, project: Project): PsiClass? {
    val scope = GlobalSearchScope.allScope(project)
    val hasHandleEvent = function.annotationEntries.any { it.shortName?.asString() == HANDLE_EVENT_ANNOTATION }

    if (hasHandleEvent) {
        val annotation = function.annotationEntries.find { it.shortName?.asString() == HANDLE_EVENT_ANNOTATION }

        val eventTypeArg = annotation?.valueArguments?.firstOrNull { arg ->
            val name = arg.getArgumentName()?.asName?.asString()
            name == "eventType" || name == "eventTypes" || name == null
        }
        val classLiteral = eventTypeArg?.getArgumentExpression() as? KtClassLiteralExpression
        val explicitClass = classLiteral?.receiverExpression?.text?.substringAfterLast('.')
        if (explicitClass != null) {
            PsiShortNamesCache.getInstance(project).getClassesByName(explicitClass, scope)
                .firstOrNull { InheritanceUtil.isInheritor(it, SKYHANNI_EVENT_FQN) }
                ?.let { return it }
        }

        if (function.valueParameters.size == 1) {
            val rawType = function.valueParameters.first().typeReference?.referencedTypeName() ?: return null
            return PsiShortNamesCache.getInstance(project).getClassesByName(rawType, scope)
                .firstOrNull { InheritanceUtil.isInheritor(it, SKYHANNI_EVENT_FQN) }
        }
    }

    return buildPrimaryNameMap(project)[function.name.orEmpty()]
        ?.let { JavaPsiFacade.getInstance(project).findClass(it, scope) }
}

/**
 * Finds all @HandleEvent functions in the project that handle [psiClass].
 *
 * Covers three cases:
 * - Parameter type reference: fun onX(event: SomeEvent)
 * - Annotation argument: @HandleEvent(eventType = SomeEvent::class)
 * - Primary function name match (parameterless handlers)
 */
fun findHandlersForEvent(psiClass: PsiClass, project: Project): List<KtNamedFunction> {
    val result = mutableListOf<KtNamedFunction>()
    val scope = GlobalSearchScope.projectScope(project)

    val primaryName = (psiClass.navigationElement as? KtClassOrObject)
        ?.annotationEntries
        ?.firstOrNull { it.shortName?.asString() == PRIMARY_FUNCTION_ANNOTATION }
        ?.valueArguments?.firstOrNull()
        ?.getArgumentExpression()?.text?.removeSurrounding("\"")

    for (ref in ReferencesSearch.search(psiClass, scope).findAll()) {
        val param = PsiTreeUtil.getParentOfType(ref.element, KtParameter::class.java)
        if (param != null) {
            val fn = PsiTreeUtil.getParentOfType(param, KtNamedFunction::class.java)
            if (fn != null && fn.annotationEntries.any { it.shortName?.asString() == HANDLE_EVENT_ANNOTATION }) {
                result.add(fn)
                continue
            }
        }
        val annotation = PsiTreeUtil.getParentOfType(ref.element, KtAnnotationEntry::class.java)
        if (annotation?.shortName?.asString() == HANDLE_EVENT_ANNOTATION) {
            val fn = PsiTreeUtil.getParentOfType(annotation, KtNamedFunction::class.java)
            if (fn != null) result.add(fn)
        }
    }

    if (primaryName != null) {
        PsiShortNamesCache.getInstance(project).getMethodsByName(primaryName, scope).forEach { method ->
            val fn = method.navigationElement as? KtNamedFunction ?: return@forEach
            if (PsiTreeUtil.getParentOfType(fn, KtObjectDeclaration::class.java) != null) result.add(fn)
        }
    }

    return result.distinct()
}
