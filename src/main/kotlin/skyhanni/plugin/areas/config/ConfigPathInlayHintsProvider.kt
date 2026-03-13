package skyhanni.plugin.areas.config

import com.intellij.codeInsight.hints.declarative.EndOfLinePosition
import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.refactoring.isAbstract
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtProperty

import com.intellij.codeInsight.hints.declarative.InlayHintsProvider as DeclarativeInlayHintsProvider

class ConfigPathInlayHintsProvider : DeclarativeInlayHintsProvider {

    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector =
        object : SharedBypassCollector {
            override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
                val property = element as? KtProperty ?: return
                if (property.annotationEntries.none { it.shortName?.asString() == CONFIG_OPTION_ANNOTATION }) return
                val containingClass = PsiTreeUtil.getParentOfType(property, KtClassOrObject::class.java) ?: return

                // Abstract classes cannot be actual data locations
                if (containingClass.isAbstract()) return

                val path = computeConfigPath(property) ?: return
                val varLine = editor.document.getLineNumber(
                    property.valOrVarKeyword.textRange.startOffset
                )

                sink.addPresentation(
                    position = EndOfLinePosition(varLine),
                    hintFormat = HintFormat.default,
                ) {
                    text(path)
                }
            }
        }
}
