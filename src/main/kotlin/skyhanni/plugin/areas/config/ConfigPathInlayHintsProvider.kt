package skyhanni.plugin.areas.config

import com.intellij.codeInsight.hints.declarative.EndOfLinePosition
import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayActionData
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.PresentationTreeBuilder
import com.intellij.codeInsight.hints.declarative.PsiPointerInlayActionNavigationHandler
import com.intellij.codeInsight.hints.declarative.PsiPointerInlayActionPayload
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtProperty
import skyhanni.plugin.api.isAbstract

class ConfigPathInlayHintsProvider : InlayHintsProvider {
    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector = object : SharedBypassCollector {
        override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
            when (element) {
                is KtProperty -> collectProperty(element, editor, sink)
                is KtClassOrObject -> collectClass(element, editor, sink)
            }
        }

        private fun collectProperty(property: KtProperty, editor: Editor, sink: InlayTreeSink) {
            if (!property.isConfigAnnotated()) return
            val containingClass = PsiTreeUtil.getParentOfType(property, KtClassOrObject::class.java) ?: return
            if (containingClass.isAbstract()) return
            val segments = computeConfigPathSegments(property) ?: return
            val offset = property.valOrVarKeyword.textRange.startOffset
            present(segments, lastIsLink = false, offset = offset, editor = editor, sink = sink)
        }

        private fun collectClass(klass: KtClassOrObject, editor: Editor, sink: InlayTreeSink) {
            val segments = computeClassConfigPathSegments(klass) ?: return
            val offset = klass.nameIdentifier?.textRange?.startOffset ?: klass.textRange.startOffset
            present(segments, lastIsLink = true, offset = offset, editor = editor, sink = sink)
        }

        private fun present(
            segments: List<ConfigPathSegment>,
            lastIsLink: Boolean,
            offset: Int,
            editor: Editor,
            sink: InlayTreeSink,
        ) {
            val line = editor.document.getLineNumber(offset)
            sink.addPresentation(EndOfLinePosition(line), emptyList(), null, HintFormat.default) {
                segments.forEachIndexed { index, segment ->
                    val isLast = index == segments.lastIndex
                    val navigable = if (!isLast || lastIsLink) segment.target as? NavigatablePsiElement else null
                    segmentText(segment.name, navigable)
                    if (!isLast) text(".")
                }
            }
        }

        private fun PresentationTreeBuilder.segmentText(label: String, target: NavigatablePsiElement?) {
            val action = target?.let {
                InlayActionData(
                    PsiPointerInlayActionPayload(SmartPointerManager.createPointer(it)),
                    PsiPointerInlayActionNavigationHandler.HANDLER_ID,
                )
            }
            text(label, action)
        }
    }
}
