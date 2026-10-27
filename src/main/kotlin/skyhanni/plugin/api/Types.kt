package skyhanni.plugin.api

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.name.isSubpackageOf
import org.jetbrains.kotlin.psi.KtClassOrObject

fun KaSession.isSkyHanniEvent(type: KaType): Boolean =
    type.isSubtypeOf(SkyHanniIds.SKYHANNI_EVENT) && !type.isClassType(SkyHanniIds.SKYHANNI_EVENT)

fun KaSession.classPsi(type: KaType): KtClassOrObject? = type.expandedSymbol?.psi as? KtClassOrObject

fun KaSession.isSkyHanniEventClass(klass: KtClassOrObject): Boolean {
    val symbol = klass.symbol as? KaClassSymbol ?: return false
    return isSkyHanniEvent(symbol.defaultType)
}

fun KaSession.isConfigClass(type: KaType): Boolean {
    val classId = type.expandedSymbol?.classId ?: return false
    return classId.packageFqName.isSubpackageOf(SkyHanniIds.CONFIG_PACKAGE)
}

fun KaSession.isMapType(type: KaType): Boolean = type.isSubtypeOf(StandardClassIds.Map)
