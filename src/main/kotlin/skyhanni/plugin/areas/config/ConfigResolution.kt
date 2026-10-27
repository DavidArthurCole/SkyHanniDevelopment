package skyhanni.plugin.areas.config

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClassOrObject
import skyhanni.plugin.SkyHanniBundle
import skyhanni.plugin.api.SkyHanniIds
import skyhanni.plugin.api.classPsi
import skyhanni.plugin.api.findKtClass
import skyhanni.plugin.api.isMapType

sealed interface ConfigResolution {
    data class Resolved(val target: PsiElement) : ConfigResolution
    data class Error(val message: String) : ConfigResolution
}

fun rootClassId(segments: MutableList<String>): ClassId = when (segments.firstOrNull()) {
    "#profile" -> SkyHanniIds.PROFILE_STORAGE.also { segments.removeAt(0) }
    "#player" -> SkyHanniIds.PLAYER_STORAGE.also { segments.removeAt(0) }
    else -> SkyHanniIds.SKYHANNI_CONFIG
}

fun resolveConfigPath(path: String, project: Project): ConfigResolution {
    val segments = path.split('.').toMutableList()
    val rootId = rootClassId(segments)
    if (segments.isEmpty()) return error("config.navigate.error.evaluate", path)
    var current = rootId.findKtClass(project)
        ?: return error("config.navigate.error.root", rootId.asFqNameString(), path)

    for ((index, name) in segments.withIndex()) {
        val step = analyze(current) { resolveStep(current, name, index == segments.lastIndex) }
            ?: return error("config.navigate.error.property", name, current.name.orEmpty(), path)
        when (step) {
            is Step.Terminal -> return ConfigResolution.Resolved(step.target)
            is Step.Descend -> current = step.nextClass
        }
    }
    return error("config.navigate.error.property", segments.last(), current.name.orEmpty(), path)
}

private fun error(@PropertyKey(resourceBundle = "messages.SkyHanniBundle") key: String, vararg params: Any) =
    ConfigResolution.Error(SkyHanniBundle.message(key, params = params))

private sealed interface Step {
    data class Terminal(val target: PsiElement) : Step
    data class Descend(val nextClass: KtClassOrObject) : Step
}

private fun KaSession.resolveStep(owner: KtClassOrObject, name: String, isLast: Boolean): Step? {
    val classSymbol = owner.symbol as? KaClassSymbol ?: return null
    val resolved = findPropertyInHierarchy(classSymbol, Name.identifier(name)) ?: return null
    val property = resolved.symbol.psi ?: return null
    val returnType = resolved.symbol.returnType
    return when {
        resolved.inherited -> Step.Terminal(owner.navigationElement)
        isLast || isMapType(returnType) -> Step.Terminal(property.navigationElement)
        else -> classPsi(returnType)?.let(Step::Descend)
    }
}

private data class ResolvedProperty(val symbol: KaPropertySymbol, val inherited: Boolean)

private fun KaSession.findPropertyInHierarchy(
    classSymbol: KaClassSymbol,
    name: Name,
    inherited: Boolean = false,
): ResolvedProperty? {
    classSymbol.memberScope.callables(name).filterIsInstance<KaPropertySymbol>().firstOrNull()?.let {
        val declaredHere = it.containingDeclaration == classSymbol
        return ResolvedProperty(it, inherited || !declaredHere)
    }
    return classSymbol.superTypes.firstNotNullOfOrNull { superType ->
        val superSymbol = superType.expandedSymbol ?: return@firstNotNullOfOrNull null
        findPropertyInHierarchy(superSymbol, name, inherited = true)
    }
}
