package skyhanni.plugin.areas.config

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModNavigate
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiBasedModCommandAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import skyhanni.plugin.SkyHanniBundle
import skyhanni.plugin.api.evaluateString

class NavigateToConfigIntention :
    PsiBasedModCommandAction<KtStringTemplateExpression>(KtStringTemplateExpression::class.java) {

    override fun getFamilyName(): String = SkyHanniBundle.message("config.intention.navigate")

    override fun getPresentation(context: ActionContext, element: KtStringTemplateExpression): Presentation? {
        val literal = element.evaluateString() ?: return null
        if (!literal.contains('.') || element.asConfigFixPathArg() == null) return null
        return Presentation.of(familyName)
    }

    override fun generatePreview(context: ActionContext, element: KtStringTemplateExpression): IntentionPreviewInfo =
        IntentionPreviewInfo.EMPTY

    override fun perform(context: ActionContext, element: KtStringTemplateExpression): ModCommand {
        val path = element.evaluateString()
            ?: return ModCommand.error(SkyHanniBundle.message("config.navigate.error.evaluate", element.text))
        return when (val resolution = resolveConfigPath(path, context.project)) {
            is ConfigResolution.Resolved -> navigateTo(resolution.target)
            is ConfigResolution.Error -> ModCommand.error(resolution.message)
        }
    }

    private fun navigateTo(target: PsiElement): ModCommand {
        val navigation = target.navigationElement ?: target
        val file = PsiUtilCore.getVirtualFile(navigation)
            ?: return ModCommand.error(SkyHanniBundle.message("config.navigate.error.evaluate", navigation.text))
        val offset = navigation.textRange.startOffset
        return ModNavigate(file, offset, offset, offset)
    }
}
