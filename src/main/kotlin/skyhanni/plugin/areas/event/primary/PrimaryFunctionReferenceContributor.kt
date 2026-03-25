package skyhanni.plugin.areas.event.primary

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.ResolveResult
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import skyhanni.plugin.areas.event.PRIMARY_FUNCTION_ANNOTATION

/**
 * Makes the string literal inside @PrimaryFunction("onTick") a multi-target reference.
 * Ctrl+clicking it opens a popup listing all handler functions with that name in objects.
 */
// TODO Add unit tests
class PrimaryFunctionReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(KtStringTemplateExpression::class.java),
            PrimaryFunctionReferenceProvider(),
        )
    }
}

private class PrimaryFunctionReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val str = element as? KtStringTemplateExpression ?: return PsiReference.EMPTY_ARRAY
        val annotation = PsiTreeUtil.getParentOfType(str, KtAnnotationEntry::class.java)
            ?: return PsiReference.EMPTY_ARRAY
        if (annotation.shortName?.asString() != PRIMARY_FUNCTION_ANNOTATION) return PsiReference.EMPTY_ARRAY
        if (str.textLength < 2) return PsiReference.EMPTY_ARRAY
        return arrayOf(PrimaryFunctionReference(str))
    }
}

private class PrimaryFunctionReference(
    element: KtStringTemplateExpression,
) : PsiPolyVariantReferenceBase<KtStringTemplateExpression>(
    element,
    TextRange(1, element.textLength - 1),
) {
    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val name = element.entries
            .filterIsInstance<KtLiteralStringTemplateEntry>()
            .joinToString("") { it.text }
            .takeIf { it.isNotEmpty() }
            ?: return ResolveResult.EMPTY_ARRAY

        val project = element.project
        val scope = GlobalSearchScope.projectScope(project)
        return PsiShortNamesCache.getInstance(project)
            .getMethodsByName(name, scope)
            .mapNotNull { method ->
                val fn = method.navigationElement as? KtNamedFunction ?: return@mapNotNull null
                if (PsiTreeUtil.getParentOfType(fn, KtObjectDeclaration::class.java) == null) return@mapNotNull null
                PsiElementResolveResult(fn)
            }
            .toTypedArray()
    }

    // Suppress rename refactoring - this is a descriptive name, not a symbol reference
    override fun handleElementRename(newElementName: String) = element
}
