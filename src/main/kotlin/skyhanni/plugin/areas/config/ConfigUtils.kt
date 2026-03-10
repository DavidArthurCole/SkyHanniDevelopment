package skyhanni.plugin.areas.config

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSimpleNameStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import kotlin.collections.setOf

const val CONFIG_OPTION_ANNOTATION = "ConfigOption"

const val BASE_CONFIG_PKG = "at.hannibal2.skyhanni.config"
const val BASE_CONFIG_CLASS = "at.hannibal2.skyhanni.config.Features"
const val PROFILE_STORAGE_CLASS = "at.hannibal2.skyhanni.config.storage.ProfileSpecificStorage"
const val PLAYER_STORAGE_CLASS = "at.hannibal2.skyhanni.config.storage.PlayerSpecificStorage"

const val PROPERTY_FQN = "io.github.notenoughupdates.moulconfig.observer.Property"

const val NOTIFICATION_GROUP = "SkyHanni Plugin"

/** FQNs that are considered config roots — traversal stops when one is reached. */
val ROOT_CONFIG_FQNS = setOf(BASE_CONFIG_CLASS, PROFILE_STORAGE_CLASS, PLAYER_STORAGE_CLASS)

/**
 * The "modules.editor." prefix that appears in the raw path but should be
 * stripped from the user-visible path.
 */
const val STRIP_PREFIX = "modules.editor."

/**
 * Walks up the config class hierarchy via reverse reference search,
 * building a dotted path from the nearest root class down to [prop].
 *
 * Returns e.g. `"inventory.items.slot"` with [STRIP_PREFIX] removed,
 * or `null` if the property name is unavailable.
 */
fun computeConfigPath(prop: KtProperty): String? {
    val project = prop.project
    val segments = mutableListOf<String>()
    segments.add(prop.name ?: return null)

    var currentClass = PsiTreeUtil.getParentOfType(prop, KtClassOrObject::class.java)
    while (currentClass != null) {
        if (currentClass.fqName?.asString() in ROOT_CONFIG_FQNS) break
        val (propertyName, parentClass) = findContainingProperty(currentClass, project) ?: break
        segments.add(propertyName)
        currentClass = parentClass
    }

    segments.reverse()
    return segments.joinToString(".").removePrefix(STRIP_PREFIX).takeIf { it.isNotEmpty() }
}

/**
 * Given a config class, finds the property in another class whose type
 * references this class — i.e. walks one level up the config tree.
 *
 * Returns `Pair(propertyName, containingClass)`, or `null` if at the root.
 */
fun findContainingProperty(
    kClass: KtClassOrObject,
    project: Project,
): Pair<String, KtClassOrObject>? {
    val fqName = kClass.fqName?.asString() ?: return null
    val psiClass = JavaPsiFacade.getInstance(project)
        .findClass(fqName, GlobalSearchScope.allScope(project))
        ?: return null

    for (ref in ReferencesSearch.search(psiClass, GlobalSearchScope.projectScope(project)).findAll()) {
        // Must be a class member, not a local variable or function parameter
        PsiTreeUtil.getParentOfType(ref.element, KtProperty::class.java)?.let { prop ->
            PsiTreeUtil.getParentOfType(prop, KtClassOrObject::class.java)?.let { parentClass ->
                val parentFqName = parentClass.fqName?.asString() ?: return@let
                if (!parentFqName.startsWith(BASE_CONFIG_PKG)) return@let
                if (prop.parent !is org.jetbrains.kotlin.psi.KtClassBody) return@let
                return Pair(prop.name ?: return@let, parentClass)
            }
        }
    }
    return null
}

/**
 * Evaluates a KtStringTemplateExpression to a plain string by resolving
 * simple local-val substitutions. Returns null if any part can't be resolved.
 */
fun evaluateStringTemplate(element: KtStringTemplateExpression): String? {
    val sb = StringBuilder()
    for (entry in element.entries) {
        when (entry) {
            is KtLiteralStringTemplateEntry -> sb.append(entry.text)
            is KtSimpleNameStringTemplateEntry -> {
                val resolved = resolveLocalValText(
                    entry.expression as? KtNameReferenceExpression ?: return null
                ) ?: return null
                sb.append(resolved)
            }
            is KtBlockStringTemplateEntry -> return null
            else -> return null
        }
    }
    return sb.toString()
}

fun resolveLocalValText(ref: KtNameReferenceExpression): String? {
    var scope: PsiElement? = ref.parent
    while (scope != null) {
        if (scope is KtBlockExpression || scope is KtFunctionLiteral) {
            val match = scope.children.filterIsInstance<KtProperty>()
                .firstOrNull { it.name == ref.getReferencedName() && !it.isVar }
            if (match != null) {
                val init = match.initializer as? KtStringTemplateExpression ?: return null
                return evaluateStringTemplate(init)
            }
        }
        scope = scope.parent
    }
    return null
}

/**
 * Finds a property by name in [kClass] or any of its supertypes within the config package.
 * Returns Pair(property, isInherited) — isInherited=true means it lives in a supertype.
 */
fun findPropertyInHierarchy(kClass: KtClassOrObject, name: String, project: Project): Pair<KtProperty, Boolean>? {
    val direct = kClass.declarations.filterIsInstance<KtProperty>().firstOrNull { it.name == name }
    if (direct != null) return Pair(direct, false)

    val scope = GlobalSearchScope.projectScope(project)
    return kClass.superTypeListEntries.firstNotNullOfOrNull entries@{ superEntry ->
        val wholeRawType = superEntry.typeReference?.text ?: return@entries null
        val rawType = wholeRawType.substringBefore('<').substringBefore('?')
        val superPsi = PsiShortNamesCache.getInstance(project).getClassesByName(rawType, scope).firstOrNull {
            it.qualifiedName?.startsWith(BASE_CONFIG_PKG) == true
        } ?: return@entries null
        val superKt = superPsi.navigationElement as? KtClassOrObject ?: return@entries null
        val found = findPropertyInHierarchy(superKt, name, project) ?: return@entries null
        Pair(found.first, true)
    }
}

fun MutableList<String>.getRootClassName(): String = when (this.first()) {
    "#profile" -> PROFILE_STORAGE_CLASS.also { removeFirst() }
    "#player" -> PLAYER_STORAGE_CLASS.also { removeFirst() }
    else -> BASE_CONFIG_CLASS
}
