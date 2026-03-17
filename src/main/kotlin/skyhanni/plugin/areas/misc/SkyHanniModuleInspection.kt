package skyhanni.plugin.areas.misc

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.util.AnnotationModificationHelper
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtVisitorVoid

private const val SUBSCRIBE_EVENT_ANNOTATION = "SubscribeEvent"
private const val HANDLE_EVENT_ANNOTATION = "HandleEvent"
private const val SKYHANNI_MODULE_ANNOTATION = "SkyHanniModule"
private const val SKYHANNI_MODULE_FQN = "at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule"
private const val SKYHANNI_PATH = "at.hannibal2.skyhanni"
private const val REPO_PATTERN_GROUP_FQN = "at.hannibal2.skyhanni.utils.repopatterns.RepoPatternGroup"
private const val PATTERN_FQN = "java.util.regex.Pattern"

class SkyHanniModuleInspection : AbstractKotlinInspection() {

    override fun getDisplayName() = "Module should have a @SkyHanniModule annotation"
    override fun getShortName() = "SkyHanniModuleInspection"
    override fun getGroupDisplayName() = "SkyHanni"
    override fun isEnabledByDefault() = true

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : KtVisitorVoid() {

            override fun visitClass(klass: KtClass) {
                if (!isFromSkyhanni(klass)) return
                if (klass.annotationEntries.any { it.shortName?.asString() == SKYHANNI_MODULE_ANNOTATION }) {
                    holder.registerProblem(
                        klass.nameIdentifier ?: return,
                        "@SkyHanniModule can only be applied to objects",
                        ProblemHighlightType.GENERIC_ERROR
                    )
                }
            }

            override fun visitObjectDeclaration(declaration: KtObjectDeclaration) {
                if (!isFromSkyhanni(declaration)) return
                if (declaration.annotationEntries.any { it.shortName?.asString() == SKYHANNI_MODULE_ANNOTATION }) return

                val body = declaration.body ?: return
                val hasSkyHanniEvents = body.functions.any { isEventHandler(it) }
                val hasRepoPatterns = body.properties.any { isRepoPattern(it) }
                if (!hasSkyHanniEvents && !hasRepoPatterns) return

                holder.registerProblem(
                    declaration,
                    "Module should have a @SkyHanniModule annotation",
                    AddSkyHanniModuleFix()
                )
            }
        }
}

private fun isEventHandler(function: KtNamedFunction): Boolean =
    function.annotationEntries.any {
        val name = it.shortName?.asString()
        name == HANDLE_EVENT_ANNOTATION || name == SUBSCRIBE_EVENT_ANNOTATION
    }

private fun isRepoPattern(property: KtProperty): Boolean {
    val typeRef = property.typeReference ?: return false
    val rawTypeName = typeRef.text
        .substringBefore('<')
        .substringBefore('?')

    val psiClass = PsiShortNamesCache.getInstance(property.project)
        .getClassesByName(rawTypeName, GlobalSearchScope.projectScope(property.project))
        .firstOrNull() ?: return false

    return when (psiClass.qualifiedName) {
        REPO_PATTERN_GROUP_FQN -> true
        PATTERN_FQN -> property.hasDelegate()
        else -> false
    }
}

private fun isFromSkyhanni(declaration: KtNamedDeclaration): Boolean =
    declaration.fqName?.asString()?.startsWith(SKYHANNI_PATH) == true

private class AddSkyHanniModuleFix : LocalQuickFix {
    override fun getName() = "Annotate with @SkyHanniModule"
    override fun getFamilyName() = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val obj = descriptor.psiElement as? KtObjectDeclaration ?: return
        AnnotationModificationHelper.addAnnotation(
            obj,
            FqName(SKYHANNI_MODULE_FQN),
            null,
            null,
            { null },
            " ",
            null
        )
    }
}
