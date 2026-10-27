package skyhanni.plugin.areas.misc

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import skyhanni.plugin.api.SkyHanniIds
import skyhanni.plugin.api.hasAnnotation

class SkyHanniInspectionSuppressor : InspectionSuppressor {

    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean =
        isUnusedSkyHanniSymbol(element, toolId) || isSpellOrRegex(element, toolId)

    private fun isUnusedSkyHanniSymbol(element: PsiElement, toolId: String): Boolean {
        if (toolId != "unused") return false
        val declaration = element as? KtDeclaration ?: element.parent as? KtDeclaration ?: return false
        return when (declaration) {
            is KtObjectDeclaration -> declaration.hasAnnotation(SkyHanniIds.SKYHANNI_MODULE)
            is KtNamedFunction -> declaration.hasAnnotation(SkyHanniIds.HANDLE_EVENT)
            else -> false
        }
    }

    private fun isSpellOrRegex(element: PsiElement, toolId: String): Boolean {
        if (toolId == "RegExpRedundantEscape") return element.isInsideRepoPatternRegex()
        if (!toolId.contains("Spell", ignoreCase = true) && !toolId.contains("Grazie", ignoreCase = true)) return false
        if (element.isInsideRegexTestComment()) return true
        val template = element.enclosingStringTemplate() ?: return false
        return template.isIdentifierArg() || template.containsMinecraftColorCode()
    }

    override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> =
        SuppressQuickFix.EMPTY_ARRAY
}

internal fun PsiElement.enclosingStringTemplate(): KtStringTemplateExpression? {
    var current: PsiElement? = this
    while (current != null) {
        if (current is KtStringTemplateExpression) return current
        current = current.parent
    }
    return null
}

internal fun PsiElement.isInsideRepoPatternRegex(): Boolean = enclosingStringTemplate()?.isRepoPatternRegexArg() == true

internal fun PsiElement.isInsideRegexTestComment(): Boolean {
    var current: PsiElement? = this
    while (current != null) {
        if (current is PsiComment) return current.text.contains(REGEX_TEST_MARKER)
        current = current.parent
    }
    return false
}
