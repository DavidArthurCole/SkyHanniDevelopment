package skyhanni.plugin.areas.misc

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import skyhanni.plugin.SkyHanniBundle
import skyhanni.plugin.api.SkyHanniIds
import skyhanni.plugin.api.constantText
import skyhanni.plugin.api.isCallTo
import skyhanni.plugin.api.isConstructorOf
import skyhanni.plugin.api.parameterNameOf
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class Regex101LineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element.node.elementType != KtTokens.IDENTIFIER) return null
        val call = element.parent?.parent as? KtCallExpression ?: return null
        if (call.calleeExpression != element.parent) return null
        val property = call.getParentOfType<KtProperty>(strict = true) ?: return null
        val regexArg = resolveRegexArg(call) ?: return null
        val info = RegexInfo.from(regexArg, property.docComment) ?: return null

        val url = buildUrl(info.regex.replace("\"", "\\\""), info.examples)
        val tooltip = if (info.examples.isEmpty()) {
            SkyHanniBundle.message("regex101.tooltip")
        } else {
            SkyHanniBundle.message("regex101.tooltip.withTests", info.examples.size)
        }

        return LineMarkerInfo(
            element,
            element.textRange,
            AllIcons.General.Web,
            { tooltip },
            { _, _ -> BrowserUtil.browse(url) },
            GutterIconRenderer.Alignment.LEFT,
            { tooltip },
        )
    }

    private fun resolveRegexArg(call: KtCallExpression): KtValueArgument? = analyze(call) {
        when {
            isCallTo(call, SkyHanniIds.REPO_PATTERN_PATTERN) -> call.valueArguments.firstOrNull { argument ->
                val expression = argument.getArgumentExpression() ?: return@firstOrNull false
                parameterNameOf(call, expression)?.asString() == "fallback"
            }
            isConstructorOf(call, SkyHanniIds.KOTLIN_REGEX) -> call.valueArguments.firstOrNull()
            else -> null
        }
    }

    private fun buildUrl(regex: String, examples: List<String>): String = "https://regex101.com/?" +
        "regex=${encode(regex)}" +
        "&testString=${encode(examples.joinToString("\n"))}" +
        "&flavor=java"

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
}

internal class RegexInfo private constructor(val regex: String, val examples: List<String>) {
    companion object {
        fun from(regexArg: KtValueArgument, comment: KDoc?): RegexInfo? {
            val template = regexArg.getArgumentExpression() as? KtStringTemplateExpression ?: return null
            val regex = template.constantText() ?: return null
            return RegexInfo(regex, examples(comment))
        }

        private fun examples(comment: KDoc?): List<String> = buildList {
            val lines = commentLines(comment) ?: return@buildList
            lines.filter { it.startsWith(REGEX_TEST_PREFIX) || it.startsWith(REGEX_FAIL_PREFIX) }
                .map { it.substring(REGEX_TEST_PREFIX.length) }
                .let(::addAll)
            lines.filter { it.startsWith(WRAPPED_REGEX_TEST_PREFIX) }
                .mapNotNull { line ->
                    val matcher = WRAPPED_REGEX_TEST_PATTERN.matcher(line)
                    if (matcher.matches()) matcher.group("test") else null
                }
                .let(::addAll)
        }

        private fun commentLines(comment: KDoc?): List<String>? = comment?.text
            ?.replace("/*", "")
            ?.replace("*/", "")
            ?.lines()
            ?.map { it.trim().trimStart('*').trim() }
    }
}
