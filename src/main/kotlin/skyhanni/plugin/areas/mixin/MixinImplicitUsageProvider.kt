package skyhanni.plugin.areas.mixin

import com.intellij.codeInsight.daemon.ImplicitUsageProvider
import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiParameter
import skyhanni.plugin.api.SkyHanniIds

private const val UNUSED_TOOL_ID = "unused"

class MixinImplicitUsageProvider : ImplicitUsageProvider {

    override fun isImplicitUsage(element: PsiElement): Boolean = when (element) {
        is PsiClass -> element.hasAnnotation(SkyHanniIds.Mixins.MIXIN)
        is PsiMethod -> element.isMixinInjector() || element.isMixinShadow()
        is PsiField -> element.isMixinShadow()
        is PsiParameter -> (element.declarationScope as? PsiMethod)?.isMixinInjector() == true
        else -> false
    }

    override fun isImplicitRead(element: PsiElement): Boolean = false

    override fun isImplicitWrite(element: PsiElement): Boolean = element is PsiField && element.isMixinShadow()
}

class MixinInspectionSuppressor : InspectionSuppressor {

    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        if (toolId != UNUSED_TOOL_ID) return false
        val parameter = element as? PsiParameter ?: element.parent as? PsiParameter ?: return false
        return (parameter.declarationScope as? PsiMethod)?.isMixinInjector() == true
    }

    override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> =
        SuppressQuickFix.EMPTY_ARRAY
}

private fun PsiMember.isInMixinClass(): Boolean = containingClass?.hasAnnotation(SkyHanniIds.Mixins.MIXIN) == true

private fun PsiMethod.isMixinInjector(): Boolean = isInMixinClass() && SkyHanniIds.Mixins.INJECTORS.any { hasAnnotation(it) }

private fun PsiMember.isMixinShadow(): Boolean = isInMixinClass() && hasAnnotation(SkyHanniIds.Mixins.SHADOW)

private fun PsiModifierListOwner.hasAnnotation(fqn: String): Boolean = annotations.any { it.hasQualifiedName(fqn) }
