package skyhanni.plugin.areas.event.primary

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.classOrObjectVisitor
import skyhanni.plugin.SkyHanniBundle
import skyhanni.plugin.api.SkyHanniIds
import skyhanni.plugin.api.annotation
import skyhanni.plugin.api.inspections.SkyHanniInspection
import skyhanni.plugin.api.isAbstract
import skyhanni.plugin.api.isSkyHanniEventClass
import skyhanni.plugin.api.rangeIn
import skyhanni.plugin.api.stringArgument
import skyhanni.plugin.areas.event.PrimaryFunctionIndex

class DuplicatePrimaryFunctionInspection : SkyHanniInspection<KtClassOrObject, DuplicatePrimaryFunctionInspection.Context>() {

    class Context(name: String, duplicates: List<String>, override val rangeInElement: TextRange?) : Problem {
        override val message = SkyHanniBundle.message("primary.problem.duplicate", name, duplicates.joinToString(", "))
        override val highlightType = ProblemHighlightType.WARNING
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> =
        classOrObjectVisitor { visitTargetElement(it, holder, isOnTheFly) }

    override fun isApplicableByPsi(element: KtClassOrObject): Boolean = !element.isAbstract() && element.name != null

    override fun getApplicableRanges(element: KtClassOrObject): List<TextRange> = nameIdentifierRange(element)

    override fun KaSession.prepareContext(element: KtClassOrObject): Context? {
        val annotation = annotation(element, SkyHanniIds.PRIMARY_FUNCTION) ?: return null
        val name = annotation.stringArgument() ?: return null
        if (!isSkyHanniEventClass(element)) return null
        val classId = element.getClassId() ?: return null
        val duplicates = PrimaryFunctionIndex.allFor(element.project, name).filter { it != classId }
        if (duplicates.isEmpty()) return null
        val argument = (annotation.psi as? KtAnnotationEntry)?.valueArguments?.firstOrNull()?.getArgumentExpression()
        return Context(name, duplicates.map { it.shortClassName.asString() }, argument?.rangeIn(element))
    }
}
