package skyhanni.plugin.areas.config

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.name.isSubpackageOf
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtProperty
import skyhanni.plugin.api.SkyHanniIds
import skyhanni.plugin.api.isAbstract

data class ContainingPropertyResult(val name: String, val parentClass: KtClassOrObject, val property: KtProperty)

data class ConfigPathSegment(val name: String, val target: PsiElement?)

fun KtProperty.isConfigAnnotated(): Boolean = analyze(this) {
    symbol.annotations.classIds.any { it in SkyHanniIds.CONFIG_ANNOTATIONS }
}

private fun KtClassOrObject.isConfigClass(): Boolean =
    getClassId()?.packageFqName?.isSubpackageOf(SkyHanniIds.CONFIG_PACKAGE) == true

private fun KtClassOrObject.isRootConfigClass(): Boolean = getClassId() in SkyHanniIds.ROOT_CONFIG_CLASSES

fun computeConfigPathSegments(property: KtProperty): List<ConfigPathSegment>? {
    val project = property.project
    val segments = mutableListOf(ConfigPathSegment(property.name ?: return null, property.navigationElement))

    var currentClass = PsiTreeUtil.getParentOfType(property, KtClassOrObject::class.java)
    if (currentClass?.isAbstract() == true) return null

    while (currentClass != null) {
        if (currentClass.isRootConfigClass()) break
        val result = findContainingProperty(currentClass, project) ?: break
        segments.add(ConfigPathSegment(result.name, result.property.navigationElement))
        currentClass = result.parentClass
    }

    segments.reverse()
    return segments.takeIf { it.isNotEmpty() }
}

fun computeClassConfigPathSegments(klass: KtClassOrObject): List<ConfigPathSegment>? {
    if (klass.isAbstract()) return null
    if (klass is KtClass && klass.isEnum() || klass is KtEnumEntry) return null
    if (!klass.isConfigClass() || klass.isRootConfigClass()) return null
    val result = findContainingProperty(klass, klass.project) ?: return null
    return computeConfigPathSegments(result.property)
}

fun computeConfigPath(property: KtProperty): String? =
    computeConfigPathSegments(property)?.joinToString(".") { it.name }

fun findContainingProperty(klass: KtClassOrObject, project: Project): ContainingPropertyResult? {
    val scope = GlobalSearchScope.projectScope(project)
    fun toResult(reference: PsiReference): ContainingPropertyResult? {
        val property = PsiTreeUtil.getParentOfType(reference.element, KtProperty::class.java) ?: return null
        val parentClass = PsiTreeUtil.getParentOfType(property, KtClassOrObject::class.java) ?: return null
        val propName = property.name ?: return null
        if (!parentClass.isConfigClass() || parentClass == klass || property.parent !is KtClassBody) return null
        return ContainingPropertyResult(propName, parentClass, property)
    }
    return ReferencesSearch.search(klass, scope).findAll().firstNotNullOfOrNull(::toResult)
}
