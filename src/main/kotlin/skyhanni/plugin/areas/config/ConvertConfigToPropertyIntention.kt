package skyhanni.plugin.areas.config

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import skyhanni.plugin.SkyHanniBundle
import skyhanni.plugin.api.SkyHanniIds
import skyhanni.plugin.api.hasAnnotation
import skyhanni.plugin.api.isAbstract
import skyhanni.plugin.api.shortenReferences

class ConvertConfigToPropertyIntention : KotlinApplicableModCommandAction<KtProperty, Unit>(KtProperty::class) {

    override fun getFamilyName(): String = SkyHanniBundle.message("config.intention.convertToProperty")

    override fun isApplicableByPsi(element: KtProperty): Boolean =
        element.isVar &&
            element.typeReference != null &&
            element.initializer != null &&
            element.containingClassOrObject?.isAbstract() == false

    override fun KaSession.prepareContext(element: KtProperty): Unit? =
        Unit.takeIf { hasAnnotation(element, SkyHanniIds.CONFIG_OPTION) }

    override fun invoke(actionContext: ActionContext, element: KtProperty, elementContext: Unit, updater: ModPsiUpdater) {
        val factory = KtPsiFactory(element.project)
        val fqn = SkyHanniIds.MOULCONFIG_PROPERTY.asSingleFqName().asString()
        val oldType = element.typeReference ?: return
        val oldInit = element.initializer ?: return
        element.valOrVarKeyword.replace(factory.createValKeyword())
        oldType.replace(factory.createType("$fqn<${oldType.text}>"))
        element.initializer = factory.createExpression("$fqn.of(${oldInit.text})")
        element.shortenReferences()
    }
}
