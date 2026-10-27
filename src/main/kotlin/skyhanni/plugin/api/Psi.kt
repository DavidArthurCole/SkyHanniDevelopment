package skyhanni.plugin.api

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

fun ClassId.findKtClass(
    project: Project,
    scope: GlobalSearchScope = GlobalSearchScope.allScope(project),
): KtClassOrObject? = KotlinFullClassNameIndex.Helper.get(asFqNameString(), project, scope).firstOrNull()

fun KtClassOrObject.isAbstract(): Boolean =
    hasModifier(KtTokens.ABSTRACT_KEYWORD) || hasModifier(KtTokens.SEALED_KEYWORD) || (this is KtClass && isInterface())

val KtDeclaration.containingClassOrObjectOrNull: KtClassOrObject?
    get() = containingClassOrObject

fun PsiElement.rangeIn(parent: PsiElement): TextRange = textRange.shiftLeft(parent.textRange.startOffset)
