package skyhanni.plugin.areas.event

import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayActionData
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.PsiPointerInlayActionNavigationHandler
import com.intellij.codeInsight.hints.declarative.PsiPointerInlayActionPayload
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import org.jetbrains.kotlin.psi.KtNamedFunction
import skyhanni.plugin.api.SkyHanniIds
import skyhanni.plugin.api.hasAnnotation

class EventHandlerInlayHintsProvider : InlayHintsProvider {
    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector = object : SharedBypassCollector {
        override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
            val function = element as? KtNamedFunction ?: return
            if (function.valueParameters.isNotEmpty()) return
            val name = function.name ?: return
            val paramList = function.valueParameterList ?: return
            if (!function.hasAnnotation(SkyHanniIds.HANDLE_EVENT)) return
            val eventClass = PrimaryFunctionIndex.eventClass(function.project, name) ?: return
            val eventName = eventClass.name ?: return
            val pointer = SmartPointerManager.createPointer(eventClass)
            sink.addPresentation(
                position = InlineInlayPosition(paramList.textRange.startOffset + 1, relatedToPrevious = true),
                hintFormat = HintFormat.default,
            ) {
                text(
                    text = "$eventName::class",
                    actionData = InlayActionData(
                        payload = PsiPointerInlayActionPayload(pointer),
                        handlerId = PsiPointerInlayActionNavigationHandler.HANDLER_ID,
                    ),
                )
            }
        }
    }
}
