package skyhanni.plugin.areas.config

import com.intellij.openapi.editor.Editor
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingOffsetIndependentIntention
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeReference
import java.util.function.Supplier

class ConvertConfigToPropertyIntention :
    SelfTargetingOffsetIndependentIntention<KtProperty>(
        KtProperty::class.java,
        Supplier { "Convert @ConfigOption to Property<T>" }
    ) {

    override fun isApplicableTo(element: KtProperty): Boolean {
        if (!element.isVar) return false
        if (element.annotationEntries.none { it.shortName?.asString() == CONFIG_OPTION_ANNOTATION }) return false
        if (element.typeReference == null || element.initializer == null) return false
        val containingClass = PsiTreeUtil.getParentOfType(element, KtClassOrObject::class.java) ?: return false
        return !containingClass.isAbstract()
    }

    override fun applyTo(element: KtProperty, editor: Editor?) {
        val factory = KtPsiFactory(element.project)

        element.valOrVarKeyword.replace(factory.createValKeyword())

        val oldType: KtTypeReference = element.typeReference ?: return
        oldType.replace(factory.createType("Property<${oldType.text}>"))

        val oldInit = element.initializer ?: return
        element.initializer = factory.createExpression("Property.of(${oldInit.text})")

        val file = element.containingKtFile
        if (file.importDirectives.none { it.importedFqName?.asString() == PROPERTY_FQN }) {
            val importDirective = factory.createFile("import $PROPERTY_FQN\n").importDirectives.firstOrNull() ?: return
            val importList = file.importList ?: run {
                file.add(importDirective)
                return
            }
            val insertBefore = importList.imports.firstOrNull { existing ->
                (existing.importedFqName?.asString() ?: return@firstOrNull false) > PROPERTY_FQN
            }
            if (insertBefore != null) importList.addBefore(importDirective, insertBefore) else importList.add(importDirective)
        }
    }
}
