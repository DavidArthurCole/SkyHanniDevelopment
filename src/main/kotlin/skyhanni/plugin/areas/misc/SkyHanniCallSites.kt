package skyhanni.plugin.areas.misc

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import skyhanni.plugin.api.SkyHanniIds
import skyhanni.plugin.api.enclosingCall
import skyhanni.plugin.api.isCallTo
import skyhanni.plugin.api.parameterNameOf
import skyhanni.plugin.api.resolvedCallableId

internal fun KtStringTemplateExpression.isIdentifierArg(): Boolean =
    isRepoPatternKeyArg() || isCommandNameArg() || isCommandAliasArg() || isGroupNameArg()

internal fun KtStringTemplateExpression.isRepoPatternKeyArg(): Boolean {
    val call = enclosingCall() ?: return false
    return analyze(call) {
        isCallTo(call, SkyHanniIds.REPO_PATTERN_FACTORIES) &&
            parameterNameOf(call, this@isRepoPatternKeyArg)?.asString() in setOf("key", "prefix", "subgroup")
    }
}

internal fun KtStringTemplateExpression.isRepoPatternRegexArg(): Boolean {
    val call = enclosingCall() ?: return false
    return analyze(call) {
        isCallTo(call, SkyHanniIds.REPO_PATTERN_PATTERN + SkyHanniIds.REPO_PATTERN_LIST_FN) &&
            parameterNameOf(call, this@isRepoPatternRegexArg)?.asString() in setOf("fallback", "fallbacks")
    }
}

internal fun KtStringTemplateExpression.isCommandNameArg(): Boolean {
    val call = enclosingCall() ?: return false
    return analyze(call) {
        isCallTo(call, setOf(SkyHanniIds.REGISTER_BRIGADIER)) &&
            parameterNameOf(call, this@isCommandNameArg)?.asString() == "name"
    }
}

internal fun KtStringTemplateExpression.isCommandAliasArg(): Boolean {
    val binary = getStrictParentOfType<KtBinaryExpression>() ?: return false
    if (binary.operationToken != KtTokens.EQ) return false
    val left = binary.left as? KtNameReferenceExpression ?: return false
    val resolvesToAliases = analyze(left) {
        (left.mainReference.resolveToSymbol() as? org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol)
            ?.name?.asString() == "aliases"
    }
    if (!resolvesToAliases) return false
    val lambda = getStrictParentOfType<KtLambdaArgument>() ?: return false
    val outerCall = lambda.parent as? KtCallExpression ?: return false
    return analyze(outerCall) { isCallTo(outerCall, setOf(SkyHanniIds.REGISTER_BRIGADIER)) }
}

internal fun KtStringTemplateExpression.isGroupNameArg(): Boolean {
    val call = enclosingCall() ?: return false
    return analyze(call) {
        val id = resolvedCallableId(call) ?: return@analyze false
        id == SkyHanniIds.REGEX_UTILS_GROUP_OR_NULL || id == SkyHanniIds.MATCHER_GROUP
    }
}

internal fun KtStringTemplateExpression.containsMinecraftColorCode(): Boolean = text.contains('§')
