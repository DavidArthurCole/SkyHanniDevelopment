package skyhanni.plugin.api

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSimpleNameStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

fun KtStringTemplateExpression.constantText(): String? {
    val builder = StringBuilder()
    for (entry in entries) {
        when (entry) {
            is KtLiteralStringTemplateEntry -> builder.append(entry.text)
            is KtEscapeStringTemplateEntry -> builder.append(entry.unescapedValue)
            else -> return null
        }
    }
    return builder.toString()
}

fun KtStringTemplateExpression.evaluateString(): String? = analyze(this) { evaluateTemplate(this@evaluateString) }

private fun KaSession.evaluateTemplate(template: KtStringTemplateExpression): String? =
    ((template as KtExpression).evaluate() as? KaConstantValue.StringValue)?.value ?: foldEntries(template)

private fun KaSession.foldEntries(template: KtStringTemplateExpression): String? {
    val builder = StringBuilder()
    for (entry in template.entries) {
        when (entry) {
            is KtLiteralStringTemplateEntry -> builder.append(entry.text)
            is KtEscapeStringTemplateEntry -> builder.append(entry.unescapedValue)
            is KtSimpleNameStringTemplateEntry -> builder.append(resolveValInitializer(entry) ?: return null)
            else -> return null
        }
    }
    return builder.toString()
}

private fun KaSession.resolveValInitializer(entry: KtSimpleNameStringTemplateEntry): String? {
    val reference = entry.expression as? KtNameReferenceExpression ?: return null
    val symbol = reference.mainReference.resolveToSymbol() as? KaVariableSymbol ?: return null
    if (!symbol.isVal) return null
    val property = symbol.psi as? KtProperty ?: return null
    val initializer = property.initializer as? KtStringTemplateExpression ?: return null
    return evaluateTemplate(initializer)
}
