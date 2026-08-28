package skyhanni.plugin.areas.event.primary

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVisitorVoid
import skyhanni.plugin.areas.event.PRIMARY_FUNCTION_ANNOTATION
import skyhanni.plugin.areas.event.PRIMARY_FUNCTION_FQN
import skyhanni.plugin.areas.event.SKYHANNI_EVENT_FQN
import skyhanni.plugin.areas.event.buildPrimaryNameMap

/**
 * Warns on concrete (non-abstract) SkyHanniEvent subclasses that have no @PrimaryFunction annotation.
 * These events can only be handled via explicit eventType = ... in @HandleEvent, which is verbose.
 * Adding @PrimaryFunction enables parameterless handlers and cleaner code.
 *
 * Where a candidate name can be derived and is not already taken, a quick fix is offered.
 *
 * Suppressable via @Suppress("MissingPrimaryFunction").
 */
class MissingPrimaryFunctionInspection : AbstractKotlinInspection() {

    override fun getDisplayName() = "Event should have @PrimaryFunction or be abstract"
    override fun getShortName() = "MissingPrimaryFunction"
    override fun getGroupDisplayName() = "SkyHanni"
    override fun isEnabledByDefault() = true

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor = object : KtVisitorVoid() {
        override fun visitClass(klass: KtClass) {
            if (klass.hasModifier(KtTokens.ABSTRACT_KEYWORD) || klass.hasModifier(KtTokens.SEALED_KEYWORD)) return
            if (klass.isInterface()) return
            checkClassOrObject(klass, holder, isOnTheFly)
        }

        override fun visitObjectDeclaration(declaration: KtObjectDeclaration) {
            checkClassOrObject(declaration, holder, isOnTheFly)
        }
    }

    private fun checkClassOrObject(classOrObject: KtClassOrObject, holder: ProblemsHolder, isOnTheFly: Boolean) {
        if (classOrObject.annotationEntries.any { it.shortName?.asString() == PRIMARY_FUNCTION_ANNOTATION }) return

        val fqName = classOrObject.fqName?.asString() ?: return
        val project = classOrObject.project
        val psiClass = JavaPsiFacade.getInstance(project)
            .findClass(fqName, GlobalSearchScope.allScope(project)) ?: return
        if (!InheritanceUtil.isInheritor(psiClass, SKYHANNI_EVENT_FQN)) return

        val className = classOrObject.name ?: return
        val stripped = if (className.endsWith("Event")) className.dropLast(5) else className
        val candidate = "on$stripped"

        val fix = if (candidate !in buildPrimaryNameMap(project).keys) AddPrimaryFunctionFix(candidate) else null

        holder.registerProblem(
            holder.manager.createProblemDescriptor(
                classOrObject.nameIdentifier ?: return,
                "Event should either have a @PrimaryFunction, or be abstract",
                fix,
                ProblemHighlightType.WEAK_WARNING,
                isOnTheFly,
            )
        )
    }

    private class AddPrimaryFunctionFix(private val name: String) : LocalQuickFix {
        override fun getName() = "Add @PrimaryFunction(\"$name\")"
        override fun getFamilyName() = "Add @PrimaryFunction"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val classOrObject = descriptor.psiElement.parent as? KtClassOrObject ?: return
            val factory = KtPsiFactory(project)
            val annotation = factory.createAnnotationEntry("@${PRIMARY_FUNCTION_ANNOTATION}(\"$name\")")
            classOrObject.addAnnotationEntry(annotation)
            val file = classOrObject.containingFile as? KtFile ?: return
            addImportIfMissing(file, factory, PRIMARY_FUNCTION_FQN)
        }

        private fun addImportIfMissing(file: KtFile, factory: KtPsiFactory, fqName: String) {
            if (file.importDirectives.any { it.importedFqName?.asString() == fqName }) return
            val importDirective = factory.createFile("import $fqName\n").importDirectives.firstOrNull() ?: return
            (file.importList ?: file).add(importDirective)
        }
    }
}
