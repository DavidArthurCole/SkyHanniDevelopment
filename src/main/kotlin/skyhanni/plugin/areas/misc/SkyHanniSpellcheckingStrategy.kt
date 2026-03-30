@file:Suppress("TooManyFunctions")

package skyhanni.plugin.areas.misc

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.spellchecker.inspections.PlainTextSplitter
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy
import com.intellij.spellchecker.tokenizer.TokenConsumer
import com.intellij.spellchecker.tokenizer.Tokenizer
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

/**
 * Suppresses IntelliJ inspection warnings in SkyHanni-specific contexts:
 * - Spell check (Grazie): RepoPattern keys, command names and aliases (inside registerBrigadier /
 *   registerComplex lambdas), group/groupOrNull arguments, strings starting with "/" or containing
 *   SkyHanni command references ("/sh"), Brigadier literal/literalCallback sub-command names,
 *   SearchTag annotation arguments, ConfigFixEvent path arguments (move/transform/add/remove),
 *   getConstant arguments, strings containing Minecraft color codes, and REGEX-TEST: lines.
 * - RegExpRedundantEscape: regex strings passed to RepoPattern.pattern().
 */
class SkyHanniInspectionSuppressor : InspectionSuppressor {

    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        if (element.isInsideRegexTestComment()) return true
        if (toolId == "RegExpRedundantEscape") return element.isInsideRepoPatternRegex()
        if (!toolId.contains("Spell", ignoreCase = true) && !toolId.contains("Grazie", ignoreCase = true)) return false
        var current: PsiElement? = element
        while (current != null) {
            if (current is KtStringTemplateExpression) {
                return current.isIdentifierArg() || current.containsMinecraftColorCode()
            }
            current = current.parent
        }
        return false
    }

    override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> =
        emptyArray()
}

class SkyHanniSpellcheckingStrategy : SpellcheckingStrategy() {

    override fun getTokenizer(element: PsiElement): Tokenizer<out PsiElement> {
        if (element.isInsideRegexTestComment()) return EMPTY_TOKENIZER
        var current: PsiElement? = element
        while (current != null) {
            if (current is KtStringTemplateExpression) {
                if (current.isIdentifierArg()) return EMPTY_TOKENIZER
                if (current.containsMinecraftColorCode()) return ColorCodeStrippingTokenizer
                break
            }
            current = current.parent
        }
        return super.getTokenizer(element)
    }
}

internal fun KtStringTemplateExpression.isIdentifierArg(): Boolean = isRepoPatternKeyArg() ||
    isCommandNameArg() ||
    isCommandAliasArg() ||
    isGroupNameArg() ||
    isCommandString() ||
    isSearchTagArg() ||
    isConfigFixArg() ||
    isGetConstantArg() ||
    isBrigadierLiteralArg() ||
    containsSlashCommandRef()

internal fun KtStringTemplateExpression.resolveCallArg(): Triple<KtCallExpression, Int, String>? {
    val valueArg = parent as? KtValueArgument ?: return null
    val argList = valueArg.parent as? KtValueArgumentList ?: return null
    val call = argList.parent as? KtCallExpression ?: return null
    val calleeName = call.calleeExpression?.text ?: return null
    return Triple(call, argList.arguments.indexOf(valueArg), calleeName)
}

internal fun KtStringTemplateExpression.isCommandNameArg(): Boolean {
    val (call, index, calleeName) = resolveCallArg() ?: return false
    if (calleeName != "registerBrigadier" && calleeName != "registerComplex") return false
    if (index != 0) return false
    return call.parent is KtDotQualifiedExpression
}

internal fun KtStringTemplateExpression.isCommandAliasArg(): Boolean {
    val (call, _, calleeName) = resolveCallArg() ?: return false
    if (calleeName != "listOf") return false
    val binaryExpr = call.parent as? KtBinaryExpression ?: return false
    if (binaryExpr.operationToken != KtTokens.EQ || binaryExpr.left?.text != "aliases") return false
    var current: PsiElement? = binaryExpr
    while (current != null) {
        if (current is KtLambdaArgument) {
            val outerCall = current.parent as? KtCallExpression ?: return false
            val name = outerCall.calleeExpression?.text
            return name == "registerBrigadier" || name == "registerComplex"
        }
        current = current.parent
    }
    return false
}

internal fun KtStringTemplateExpression.isRepoPatternKeyArg(): Boolean {
    val (call, index, calleeName) = resolveCallArg() ?: return false
    if (index != 0) return false
    val dotExpr = call.parent as? KtDotQualifiedExpression ?: return false
    val receiverText = dotExpr.receiverExpression.text
    return when (calleeName) {
        "group", "exclusiveGroup", "list" -> receiverText == "RepoPattern"
        "pattern" -> call.valueArguments.size == 2
        else -> false
    }
}

internal fun KtStringTemplateExpression.isGroupNameArg(): Boolean {
    val (_, index, calleeName) = resolveCallArg() ?: return false
    if (index != 0) return false
    return calleeName == "group" || calleeName == "groupOrNull"
}

internal fun KtStringTemplateExpression.isCommandString(): Boolean = entries.firstOrNull()?.text?.startsWith("/") == true

internal fun KtStringTemplateExpression.isBrigadierLiteralArg(): Boolean {
    val (call, index, calleeName) = resolveCallArg() ?: return false
    if (index != 0) return false
    if (calleeName != "literal" && calleeName != "literalCallback") return false
    return call.parent !is KtDotQualifiedExpression
}

internal fun KtStringTemplateExpression.isSearchTagArg(): Boolean {
    val valueArg = parent as? KtValueArgument ?: return false
    val argList = valueArg.parent as? KtValueArgumentList ?: return false
    val annotation = argList.parent as? KtAnnotationEntry ?: return false
    return annotation.typeReference?.text == "SearchTag"
}

internal fun KtStringTemplateExpression.isConfigFixArg(): Boolean {
    val (call, _, calleeName) = resolveCallArg() ?: return false
    if (calleeName !in setOf("move", "transform", "add", "remove")) return false
    val dotExpr = call.parent as? KtDotQualifiedExpression ?: return false
    return dotExpr.receiverExpression.text == "event"
}

internal fun KtStringTemplateExpression.isGetConstantArg(): Boolean {
    val (call, _, calleeName) = resolveCallArg() ?: return false
    if (calleeName != "getConstant") return false
    val dotExpr = call.parent as? KtDotQualifiedExpression ?: return false
    return dotExpr.receiverExpression.text == "event"
}

internal fun KtStringTemplateExpression.isRepoPatternRegexArg(): Boolean {
    val (_, index, calleeName) = resolveCallArg() ?: return false
    if (calleeName != "pattern") return false
    return index == 1
}

internal fun PsiElement.isInsideRepoPatternRegex(): Boolean {
    var current: PsiElement? = this
    while (current != null) {
        if (current is KtStringTemplateExpression) return current.isRepoPatternRegexArg()
        current = current.parent
    }
    return false
}

internal fun KtStringTemplateExpression.containsMinecraftColorCode(): Boolean = text.contains('§')

internal fun KtStringTemplateExpression.containsSlashCommandRef(): Boolean =
    entries.any { it.text.contains("/sh") }

private val colorCodeRegex = Regex("§.")

private object ColorCodeStrippingTokenizer : Tokenizer<PsiElement>() {
    override fun tokenize(element: PsiElement, consumer: TokenConsumer) {
        val text = element.text
        var lastEnd = 0

        fun TextRange.consumeToken() {
            consumer.consumeToken(element, text, false, 0, this, PlainTextSplitter.getInstance())
        }

        for (match in colorCodeRegex.findAll(text)) {
            if (match.range.first > lastEnd) {
                TextRange(lastEnd, match.range.first).consumeToken()
            }
            lastEnd = match.range.last + 1
        }
        if (lastEnd < text.length) {
            TextRange(lastEnd, text.length).consumeToken()
        }
    }
}

internal fun PsiElement.isInsideRegexTestComment(): Boolean {
    var current: PsiElement? = this
    while (current != null) {
        if (current is KDoc || current is PsiComment) return current.text.contains("REGEX-TEST:")
        current = current.parent
    }
    return false
}
