package skyhanni.plugin.areas.event.primary

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.ResolveResult
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.stubindex.KotlinFunctionShortNameIndex
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import skyhanni.plugin.api.SkyHanniIds
import skyhanni.plugin.api.constantText
import skyhanni.plugin.api.hasAnnotation
import skyhanni.plugin.api.resolvesTo

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
        val template = element as? KtStringTemplateExpression ?: return PsiReference.EMPTY_ARRAY
        val entry = template.getStrictParentOfType<KtAnnotationEntry>() ?: return PsiReference.EMPTY_ARRAY
        if (template.constantText().isNullOrEmpty()) return PsiReference.EMPTY_ARRAY
        if (!analyze(entry) { resolvesTo(entry, SkyHanniIds.PRIMARY_FUNCTION) }) return PsiReference.EMPTY_ARRAY
        return arrayOf(PrimaryFunctionReference(template))
    }
}

private class PrimaryFunctionReference(
    element: KtStringTemplateExpression,
) : PsiPolyVariantReferenceBase<KtStringTemplateExpression>(element, ElementManipulators.getValueTextRange(element)) {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val name = element.constantText()?.takeIf { it.isNotEmpty() } ?: return ResolveResult.EMPTY_ARRAY
        val project = element.project
        return KotlinFunctionShortNameIndex.Helper.get(name, project, GlobalSearchScope.projectScope(project))
            .filter { it.containingClassOrObject is KtObjectDeclaration && it.hasAnnotation(SkyHanniIds.HANDLE_EVENT) }
            .map(::PsiElementResolveResult)
            .toTypedArray()
    }

    override fun handleElementRename(newElementName: String): PsiElement = element
}
