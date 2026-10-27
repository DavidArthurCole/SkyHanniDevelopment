package skyhanni.plugin.areas.config

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import skyhanni.plugin.api.evaluateString

class ConfigPathReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(KtStringTemplateExpression::class.java),
            ConfigPathReferenceProvider(),
        )
    }
}

private class ConfigPathReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val template = element as? KtStringTemplateExpression ?: return PsiReference.EMPTY_ARRAY
        val literal = template.evaluateString() ?: return PsiReference.EMPTY_ARRAY
        if (!literal.contains('.')) return PsiReference.EMPTY_ARRAY
        template.asConfigFixPathArg() ?: return PsiReference.EMPTY_ARRAY
        return arrayOf(ConfigPathReference(template))
    }
}

private class ConfigPathReference(
    element: KtStringTemplateExpression,
) : PsiReferenceBase<KtStringTemplateExpression>(element, ElementManipulators.getValueTextRange(element), true) {

    override fun resolve(): PsiElement? {
        val path = element.evaluateString() ?: return null
        return (resolveConfigPath(path, element.project) as? ConfigResolution.Resolved)?.target
    }

    override fun handleElementRename(newElementName: String): PsiElement = element
}
