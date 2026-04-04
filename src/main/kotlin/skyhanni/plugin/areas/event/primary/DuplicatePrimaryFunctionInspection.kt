package skyhanni.plugin.areas.event.primary

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtVisitorVoid
import skyhanni.plugin.areas.event.PRIMARY_FUNCTION_ANNOTATION
import skyhanni.plugin.areas.event.SKYHANNI_EVENT_FQN

/**
 * Warns when two or more SkyHanniEvent subclasses declare the same @PrimaryFunction name.
 * Duplicate names break handler dispatch since the name-to-event mapping is 1:1.
 *
 * Suppressable via @Suppress("DuplicatePrimaryFunction").
 */
class DuplicatePrimaryFunctionInspection : AbstractKotlinInspection() {

    override fun getDisplayName() = "Multiple events use the same @PrimaryFunction name"
    override fun getShortName() = "DuplicatePrimaryFunction"
    override fun getGroupDisplayName() = "SkyHanni"
    override fun isEnabledByDefault() = true

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor = object : KtVisitorVoid() {
        override fun visitClass(klass: KtClass) {
            if (klass.hasModifier(KtTokens.ABSTRACT_KEYWORD)) return
            val annotation = klass.annotationEntries
                .firstOrNull { it.shortName?.asString() == PRIMARY_FUNCTION_ANNOTATION } ?: return
            val name = annotation.valueArguments.firstOrNull()
                ?.getArgumentExpression()?.text?.removeSurrounding("\"")
                ?.takeIf { it.isNotBlank() } ?: return

            val fqName = klass.fqName?.asString() ?: return
            val project = klass.project
            val scope = GlobalSearchScope.allScope(project)
            val psiClass = JavaPsiFacade.getInstance(project).findClass(fqName, scope) ?: return
            if (!InheritanceUtil.isInheritor(psiClass, SKYHANNI_EVENT_FQN)) return

            val duplicates = findAllWithPrimaryName(name, project)
                .filter { it != fqName }
                .takeIf { it.isNotEmpty() } ?: return
            val duplicateString = duplicates.joinToString(", ") { it.substringAfterLast('.') }

            holder.registerProblem(
                annotation.valueArguments.firstOrNull()?.getArgumentExpression() ?: return,
                "@PrimaryFunction(\"$name\") is also used by: $duplicateString",
                ProblemHighlightType.WARNING,
            )
        }
    }

    private fun findAllWithPrimaryName(targetName: String, project: Project): List<String> {
        val facade = JavaPsiFacade.getInstance(project)
        val skyHanniEventClass = facade.findClass(SKYHANNI_EVENT_FQN, GlobalSearchScope.allScope(project))
            ?: return emptyList()

        return ClassInheritorsSearch
            .search(skyHanniEventClass, GlobalSearchScope.allScope(project), true, true, false)
            .mapNotNull { psiInheritor ->
                val ktClass = psiInheritor.navigationElement as? KtClassOrObject ?: return@mapNotNull null
                if (ktClass.hasModifier(KtTokens.ABSTRACT_KEYWORD)) return@mapNotNull null
                val primaryName = ktClass.annotationEntries
                    .firstOrNull { it.shortName?.asString() == PRIMARY_FUNCTION_ANNOTATION }
                    ?.valueArguments?.firstOrNull()
                    ?.getArgumentExpression()?.text?.removeSurrounding("\"")
                    ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                if (primaryName != targetName) return@mapNotNull null
                ktClass.fqName?.asString()
            }.distinct()
    }
}
