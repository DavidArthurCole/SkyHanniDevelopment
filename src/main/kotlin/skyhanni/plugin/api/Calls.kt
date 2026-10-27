@file:OptIn(KaExperimentalApi::class)

package skyhanni.plugin.api

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

fun KaSession.resolvedFunctionCall(call: KtCallElement): KaFunctionCall<*>? = call.resolveCall()

fun KaSession.resolvedCallableId(call: KtCallElement): CallableId? = resolvedFunctionCall(call)?.symbol?.callableId

fun KaSession.isCallTo(call: KtCallElement, ids: Set<CallableId>): Boolean = resolvedCallableId(call) in ids

fun KaSession.isConstructorOf(call: KtCallElement, id: ClassId): Boolean {
    val constructor = resolvedFunctionCall(call)?.symbol as? KaConstructorSymbol ?: return false
    return constructor.containingClassId == id
}

fun KaSession.parameterNameOf(call: KtCallElement, argument: KtExpression): Name? {
    val mapping = resolvedFunctionCall(call)?.valueArgumentMapping ?: return null
    val target = KtPsiUtil.safeDeparenthesize(argument)
    return mapping.entries.firstOrNull { KtPsiUtil.safeDeparenthesize(it.key) == target }?.value?.name
}

fun KtExpression.enclosingCall(): KtCallExpression? {
    val argument = parent as? KtValueArgument ?: return null
    val list = argument.parent as? KtValueArgumentList ?: return null
    return list.parent as? KtCallExpression
}

fun KtCallExpression.qualifiedParent(): KtDotQualifiedExpression? = parent as? KtDotQualifiedExpression
