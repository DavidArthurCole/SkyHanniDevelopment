package skyhanni.plugin.areas.config

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.getOrCreateBody
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import skyhanni.plugin.SkyHanniBundle
import skyhanni.plugin.api.SkyHanniIds
import skyhanni.plugin.api.addAnnotation
import skyhanni.plugin.api.findKtClass
import skyhanni.plugin.api.hasAnnotation
import skyhanni.plugin.api.isAbstract
import skyhanni.plugin.api.parameterNameOf
import skyhanni.plugin.api.shortenReferences

private const val HANDLER_NAME = "onConfigFix"
private const val SINCE_PARAMETER = "since"

class CreateConfigMigrationIntention : KotlinApplicableModCommandAction<KtProperty, Unit>(KtProperty::class) {

    override fun getFamilyName(): String = SkyHanniBundle.message("config.intention.createMigration")

    override fun isApplicableByPsi(element: KtProperty): Boolean =
        element.containingClassOrObject?.isAbstract() == false

    override fun KaSession.prepareContext(element: KtProperty): Unit? =
        Unit.takeIf { hasAnnotation(element, SkyHanniIds.CONFIG_OPTION) }

    override fun generatePreview(context: ActionContext?, element: KtProperty): IntentionPreviewInfo =
        IntentionPreviewInfo.EMPTY

    override fun invoke(actionContext: ActionContext, element: KtProperty, elementContext: Unit, updater: ModPsiUpdater) {
        val original = PsiTreeUtil.getParentOfType(actionContext.file.findElementAt(element.textOffset), KtProperty::class.java, false)
        val oldPath = computeConfigPath(original ?: element) ?: return
        val project = element.project
        val factory = KtPsiFactory(project)
        val companion = when (val containingClass = element.containingClassOrObject) {
            is KtClass -> findOrCreateCompanion(containingClass, factory)
            is KtObjectDeclaration -> containingClass
            else -> return
        }
        val version = resolveMigrationVersion(project, factory, updater)
        val handler = findOrCreateHandler(companion, factory)
        val eventName = handler.valueParameters.firstOrNull()?.name ?: "event"
        val body = handler.bodyBlockExpression ?: return
        val rBrace = body.rBrace ?: return
        val call = factory.createExpression("""$eventName.move($version, "$oldPath", "$oldPath")""")
        body.addBefore(factory.createNewLine(), rBrace)
        val inserted = body.addBefore(call, rBrace)
        body.addBefore(factory.createNewLine(), rBrace)
        val moveCall = PsiTreeUtil.findChildOfType(inserted, KtCallExpression::class.java, false)
        val toArgument = moveCall?.valueArguments?.getOrNull(2) ?: return
        updater.moveCaretTo(toArgument.textRange.endOffset - 1)
    }

    private fun resolveMigrationVersion(project: Project, factory: KtPsiFactory, updater: ModPsiUpdater): Int {
        val migrator = SkyHanniIds.CONFIG_UPDATER_MIGRATOR.findKtClass(project) ?: return -1
        val versionName = SkyHanniIds.CONFIG_VERSION.callableName.asString()
        val versionProperty = migrator.declarations.filterIsInstance<KtProperty>().firstOrNull { it.name == versionName } ?: return -1
        val next = (versionProperty.initializer?.text?.toIntOrNull() ?: return -1) + 1
        if (migrationExistsForVersion(project, next)) return next
        updater.getWritable(versionProperty).initializer?.replace(factory.createExpression(next.toString()))
        return next
    }

    private fun migrationExistsForVersion(project: Project, version: Int): Boolean {
        val scope = GlobalSearchScope.projectScope(project)
        val pathFunctionNames = SkyHanniIds.CONFIG_FIX_PATH_FUNCTIONS.map { it.callableName.asString() }.toSet()
        val functions = SkyHanniIds.CONFIG_FIX_EVENT.findKtClass(project)?.declarations
            ?.filterIsInstance<KtFunction>()
            ?.filter { it.name in pathFunctionNames }
            .orEmpty()
        return functions.any { function ->
            ReferencesSearch.search(function, scope).findAll().any { reference ->
                val call = PsiTreeUtil.getParentOfType(reference.element, KtCallExpression::class.java)
                call != null && sinceArgumentValue(call) == version
            }
        }
    }

    private fun sinceArgumentValue(call: KtCallExpression): Int? {
        val named = analyze(call) {
            call.valueArguments.firstOrNull { argument ->
                val expression = argument.getArgumentExpression() ?: return@firstOrNull false
                parameterNameOf(call, expression)?.asString() == SINCE_PARAMETER
            }
        }
        val argument = named ?: call.valueArguments.firstOrNull()
        return argument?.getArgumentExpression()?.text?.toIntOrNull()
    }

    private fun findOrCreateCompanion(containingClass: KtClass, factory: KtPsiFactory): KtObjectDeclaration {
        val existing = containingClass.companionObjects.firstOrNull()
        val companion = existing ?: containingClass.getOrCreateBody().let { body ->
            body.addBefore(factory.createObject("companion object {}"), body.rBrace) as KtObjectDeclaration
        }
        if (!companion.hasAnnotation(SkyHanniIds.SKYHANNI_MODULE)) companion.addAnnotation(SkyHanniIds.SKYHANNI_MODULE)
        return companion
    }

    private fun findOrCreateHandler(companion: KtObjectDeclaration, factory: KtPsiFactory): KtFunction {
        companion.declarations.filterIsInstance<KtFunction>().firstOrNull { it.name == HANDLER_NAME }?.let { return it }
        val handleEvent = SkyHanniIds.HANDLE_EVENT.asSingleFqName().asString()
        val fixEvent = SkyHanniIds.CONFIG_FIX_EVENT.asSingleFqName().asString()
        val body = companion.getOrCreateBody()
        val function = body.addBefore(
            factory.createFunction("@$handleEvent\nprivate fun $HANDLER_NAME(event: $fixEvent) {}"),
            body.rBrace,
        ) as KtFunction
        return function.shortenReferences() as? KtFunction ?: function
    }
}
