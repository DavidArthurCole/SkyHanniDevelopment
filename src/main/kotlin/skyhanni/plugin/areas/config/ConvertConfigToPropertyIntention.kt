package skyhanni.plugin.areas.config

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingOffsetIndependentIntention
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeReference

class ConvertConfigToPropertyIntention :
    SelfTargetingOffsetIndependentIntention<KtProperty>(
        KtProperty::class.java,
        { "Convert @ConfigOption to Property<T>" }
    ) {

    override fun isApplicableTo(element: KtProperty): Boolean =
        element.isVar &&
            element.annotationEntries.any { it.shortName?.asString() == CONFIG_OPTION_ANNOTATION } &&
            element.typeReference != null &&
            element.initializer != null

    override fun applyTo(element: KtProperty, editor: Editor?) {
        val factory = KtPsiFactory(element.project)

        // var → val
        element.valOrVarKeyword.replace(factory.createValKeyword())

        // T → Property<T>
        val oldType: KtTypeReference = element.typeReference ?: return
        oldType.replace(factory.createType("Property<${oldType.text}>"))

        // initializer → Property.of(initializer)
        val oldInit = element.initializer ?: return
        element.initializer = factory.createExpression("Property.of(${oldInit.text})")

        // Add import if not already present
        val file = element.containingKtFile
        if (file.importDirectives.none { it.importedFqName?.asString() == PROPERTY_FQN }) {
            val importDirective = factory.createFile("import $PROPERTY_FQN\n")
                .importDirectives
                .firstOrNull() ?: return
            (file.importList ?: file).add(importDirective)
        }
    }
}
