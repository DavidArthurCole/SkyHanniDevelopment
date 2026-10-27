package skyhanni.plugin.areas.config

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import skyhanni.plugin.api.SkyHanniIds
import skyhanni.plugin.api.enclosingCall
import skyhanni.plugin.api.isCallTo
import skyhanni.plugin.api.parameterNameOf
import skyhanni.plugin.api.qualifiedParent

fun KtStringTemplateExpression.asConfigFixPathArg(): KtCallExpression? {
    val call = enclosingCall() ?: return null
    if (call.qualifiedParent() == null) return null
    val isPathArg = analyze(call) {
        isCallTo(call, SkyHanniIds.CONFIG_FIX_PATH_FUNCTIONS) &&
            parameterNameOf(call, this@asConfigFixPathArg) in SkyHanniIds.CONFIG_FIX_PATH_PARAMS
    }
    return call.takeIf { isPathArg }
}
