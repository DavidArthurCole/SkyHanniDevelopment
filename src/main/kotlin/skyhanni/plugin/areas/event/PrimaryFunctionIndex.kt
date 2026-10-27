package skyhanni.plugin.areas.event

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import skyhanni.plugin.api.SkyHanniIds
import skyhanni.plugin.api.annotation
import skyhanni.plugin.api.findKtClass
import skyhanni.plugin.api.isAbstract
import skyhanni.plugin.api.isSkyHanniEventClass
import skyhanni.plugin.api.resolvesTo
import skyhanni.plugin.api.stringArgument

object PrimaryFunctionIndex {
    private val key = Key.create<CachedValue<Map<String, List<ClassId>>>>("skyhanni.primaryFunctionIndex")

    fun all(project: Project): Map<String, List<ClassId>> =
        CachedValuesManager.getManager(project).getCachedValue(project, key, {
            CachedValueProvider.Result.create(compute(project), PsiModificationTracker.MODIFICATION_COUNT)
        }, false)

    fun get(project: Project): Map<String, ClassId> = all(project).mapValues { it.value.first() }

    fun allFor(project: Project, name: String): List<ClassId> = all(project)[name].orEmpty()

    fun eventClass(project: Project, name: String): KtClassOrObject? = get(project)[name]?.findKtClass(project)

    private fun compute(project: Project): Map<String, List<ClassId>> {
        val scope = GlobalSearchScope.allScope(project)
        val shortName = SkyHanniIds.PRIMARY_FUNCTION.shortClassName.asString()
        return KotlinAnnotationsIndex.Helper.get(shortName, project, scope)
            .mapNotNull { entry ->
                val klass = entry.getStrictParentOfType<KtClassOrObject>() ?: return@mapNotNull null
                if (klass.isAbstract()) return@mapNotNull null
                val classId = klass.getClassId() ?: return@mapNotNull null
                analyze(klass) {
                    if (!resolvesTo(entry, SkyHanniIds.PRIMARY_FUNCTION) || !isSkyHanniEventClass(klass)) return@analyze null
                    annotation(klass, SkyHanniIds.PRIMARY_FUNCTION)?.stringArgument()?.let { it to classId }
                }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.distinct() }
    }
}
