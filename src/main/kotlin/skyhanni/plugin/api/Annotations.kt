package skyhanni.plugin.api

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtPsiFactory

fun KaSession.hasAnnotation(declaration: KtDeclaration, id: ClassId): Boolean =
    declaration.symbol.annotations.contains(id)

fun KaSession.annotation(declaration: KtDeclaration, id: ClassId): KaAnnotation? =
    declaration.symbol.annotations[id].firstOrNull()

fun KtDeclaration.hasAnnotation(id: ClassId): Boolean = analyze(this) { hasAnnotation(this@hasAnnotation, id) }

fun KaAnnotation.stringArgument(name: String = "value"): String? {
    val argument = arguments.firstOrNull { it.name.asString() == name } ?: arguments.singleOrNull() ?: return null
    val constant = argument.expression as? KaAnnotationValue.ConstantValue ?: return null
    return (constant.value.value as? String)?.takeIf { it.isNotBlank() }
}

fun KaAnnotation.classArguments(vararg names: String): List<ClassId> = arguments
    .filter { it.name.asString() in names }
    .flatMap { it.expression.classIds() }

private fun KaAnnotationValue.classIds(): List<ClassId> = when (this) {
    is KaAnnotationValue.ClassLiteralValue -> listOfNotNull(classId)
    is KaAnnotationValue.ArrayValue -> values.flatMap { it.classIds() }
    else -> emptyList()
}

fun KaSession.resolvesTo(entry: KtAnnotationEntry, id: ClassId): Boolean =
    entry.typeReference?.type?.expandedSymbol?.classId == id

fun KtModifierListOwner.addAnnotation(id: ClassId, innerText: String? = null): KtAnnotationEntry {
    val inner = innerText?.let { "($it)" }.orEmpty()
    val entry = KtPsiFactory(project).createAnnotationEntry("@${id.asSingleFqName()}$inner")
    val added = addAnnotationEntry(entry)
    return (added.shortenReferences() as? KtAnnotationEntry) ?: added
}
