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
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Injects a grayed-out, clickable event class name inside the `()` of parameterless
 * functions whose names match a @PrimaryFunction annotation on a SkyHanniEvent subclass.
 *
 * Uses the declarative V2 inlay hints API for correct baseline vertical alignment.
 */
class EventHandlerInlayHintsProvider : InlayHintsProvider {

    override fun createCollector(
        file: PsiFile,
        editor: Editor
    ): InlayHintsCollector = object : SharedBypassCollector {
        override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
            val function = element as? KtNamedFunction ?: return
            if (function.valueParameters.isNotEmpty()) return

            val name = function.name ?: return
            val primaryNameMap = buildPrimaryNameMap(function.project)
            val eventFqn = primaryNameMap[name] ?: return
            val paramList = function.valueParameterList ?: return

            val eventClass = JavaPsiFacade.getInstance(function.project)
                .findClass(eventFqn, GlobalSearchScope.allScope(function.project))
                ?: return

            val eventName = eventClass.name ?: return
            val pointer = SmartPointerManager.createPointer(eventClass)

            // Insert just after the opening `(`
            val offset = paramList.textRange.startOffset + 1

            sink.addPresentation(
                position = InlineInlayPosition(offset, relatedToPrevious = true),
                hintFormat = HintFormat.default,
            ) {
                text(
                    text = "$eventName::class",
                    actionData = InlayActionData(
                        payload = PsiPointerInlayActionPayload(pointer),
                        handlerId = PsiPointerInlayActionNavigationHandler.HANDLER_ID
                    )
                )
            }
        }
    }
}

