package skyhanni.plugin.api

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

fun KtFile.ensureImport(fqName: FqName) {
    if (importDirectives.any { it.importedFqName == fqName }) return
    addImport(fqName)
}

fun KtElement.shortenReferences(): PsiElement? = ShortenReferencesFacility.getInstance().shorten(this)
