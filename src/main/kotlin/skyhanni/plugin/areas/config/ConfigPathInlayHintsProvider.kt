package skyhanni.plugin.areas.config

import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.NoSettings
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.codeInsight.hints.presentation.BasePresentation
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.lang.documentation.ide.impl.DocumentationManager
import com.intellij.lang.documentation.psi.psiDocumentationTargets
import com.intellij.model.Pointer
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.impl.DocumentationRequest
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import java.awt.Color
import java.awt.Cursor
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

private val HINT_COLOR = JBColor(Gray._130, Gray._150)
private val HINT_LAST_COLOR = JBColor(Gray._100, Gray._120)
private val HINT_HOVER_COLOR = JBColor(Color(88, 157, 246), Color(88, 157, 246))

/**
 * Renders a boxed, clickable config path hint for every non-abstract @ConfigOption, @ConfigLink,
 * or @Category property, and beside every config class declaration.
 *
 * - Each dot-separated segment is a clickable link navigating to its definition.
 * - The last segment (the property itself) is non-clickable and slightly darker.
 * - Hovering a link segment for 400 ms opens the standard documentation popup via [DocumentationManager].
 */
@Suppress("UnstableApiUsage")
class ConfigPathInlayHintsProvider : InlayHintsProvider<NoSettings> {

    override val key = SettingsKey<NoSettings>("skyhanni.config.path")
    override val name = "Config path"
    override val previewText = null

    override fun createSettings() = NoSettings()

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable =
        object : ImmediateConfigurable {
            override fun createComponent(listener: ChangeListener): JComponent = JPanel()
        }

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: NoSettings,
        sink: InlayHintsSink,
    ): InlayHintsCollector = object : FactoryInlayHintsCollector(editor) {
        override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
            when (element) {
                is KtProperty -> collectProperty(element, editor, sink)
                is KtClass, is KtObjectDeclaration -> collectClass(element, editor, sink)
            }
            return true
        }

        private fun collectProperty(property: KtProperty, editor: Editor, sink: InlayHintsSink) {
            if (!property.isConfigAnnotated()) return
            val containingClass = PsiTreeUtil.getParentOfType(property, KtClassOrObject::class.java) ?: return
            if (containingClass.isAbstract()) return
            val segments = computeConfigPathSegments(property) ?: return
            val presentation = buildPresentation(segments, editor, lastIsLink = false)
            val line = editor.document.getLineNumber(property.valOrVarKeyword.textRange.startOffset)
            sink.addInlineElement(editor.document.getLineEndOffset(line), true, presentation, false)
        }

        private fun collectClass(klass: KtClassOrObject, editor: Editor, sink: InlayHintsSink) {
            val segments = computeClassConfigPathSegments(klass) ?: return
            val presentation = buildPresentation(segments, editor, lastIsLink = true)
            val nameOffset = klass.nameIdentifier?.textRange?.startOffset ?: klass.textRange.startOffset
            val line = editor.document.getLineNumber(nameOffset)
            sink.addInlineElement(editor.document.getLineEndOffset(line), true, presentation, false)
        }

        private fun buildPresentation(
            segments: List<ConfigPathSegment>,
            editor: Editor,
            lastIsLink: Boolean,
        ): InlayPresentation {
            val dot = SegmentPresentation(".", null, editor, HINT_COLOR)
            val parts = segments.flatMapIndexed { i, segment ->
                val isLast = i == segments.lastIndex
                val navigable = if (!isLast || lastIsLink) segment.target as? NavigatablePsiElement else null
                val color = if (isLast && !lastIsLink) HINT_LAST_COLOR else HINT_COLOR
                val part = SegmentPresentation(segment.name, navigable, editor, color)
                if (!isLast) listOf(part, dot) else listOf(part)
            }
            // Gap provides left padding between the code text and the hint box
            val gap = SegmentPresentation(" ", null, editor, HINT_COLOR)
            val fontMetrics = editor.component.getFontMetrics(editor.colorsScheme.getFont(EditorFontType.PLAIN))
            return factory.inset(
                // seq() has no List overload
                @Suppress("SpreadOperator")
                factory.seq(gap, factory.roundWithBackground(factory.seq(*parts.toTypedArray()))),
                top = -((editor.lineHeight - fontMetrics.height) / 2)
            )
        }
    }
}

@Suppress("UnstableApiUsage")
// V2 declarative API does not support per-segment colors or hover documentation popups.
// V1 is intentionally used here; V2 is used in EventHandlerInlayHintsProvider where
// those features are not needed.
private class SegmentPresentation(
    private val label: String,
    private val target: NavigatablePsiElement?,
    private val editor: Editor,
    private val baseColor: Color,
) : BasePresentation() {

    private var hovered = false
    private val font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
    private val fontMetrics by lazy { editor.component.getFontMetrics(font) }
    private var docTimer: Timer? = null
    private var pendingDoc: java.util.concurrent.Future<*>? = null
    private var lastScreenPoint: Point? = null

    override val width get() = fontMetrics.stringWidth(label)
    override val height: Int get() = fontMetrics.height

    override fun paint(g: Graphics2D, attributes: TextAttributes) {
        g.font = font
        g.color = if (hovered && target != null) HINT_HOVER_COLOR else baseColor
        g.drawString(label, 0, fontMetrics.ascent)
    }

    override fun mouseClicked(event: MouseEvent, translated: Point) {
        if (SwingUtilities.isLeftMouseButton(event)) target?.navigate(true)
    }

    override fun mouseMoved(event: MouseEvent, translated: Point) {
        if (target == null || hovered) return
        lastScreenPoint = event.locationOnScreen
        hovered = true
        fireContentChanged(Rectangle(width, height))
        editor.contentComponent.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        docTimer = Timer(200) { showDocPopup() }.also {
            it.isRepeats = false
            it.start()
        }
    }

    override fun mouseExited() {
        docTimer?.stop()
        docTimer = null
        pendingDoc?.cancel(true)
        pendingDoc = null
        lastScreenPoint = null
        if (!hovered) return
        hovered = false
        fireContentChanged(Rectangle(width, height))
        editor.contentComponent.cursor = Cursor.getDefaultCursor()
        dismissDocPopup()
    }

    private fun docPopup(): JBPopup? {
        val project = editor.project ?: return null
        val manager = DocumentationManager.getInstance(project)
        return manager::class.java.declaredMethods
            .firstOrNull { it.name == "getPopup" && it.parameterCount == 0 }
            ?.also { it.isAccessible = true }
            ?.invoke(manager) as? JBPopup
    }

    private fun dismissDocPopup() = docPopup()?.cancel()

    private fun showDocPopup() {
        val project = editor.project ?: return
        val screenPoint = lastScreenPoint ?: return
        pendingDoc = ReadAction.nonBlocking<DocumentationRequest?> {
            if (!hovered) return@nonBlocking null
            val target = target ?: return@nonBlocking null
            val docTarget = psiDocumentationTargets(target, null).firstOrNull() ?: return@nonBlocking null

            @Suppress("UNCHECKED_CAST")
            val pointer = docTarget::class.java.getMethod("createPointer").invoke(docTarget)
                as Pointer<out DocumentationTarget>
            val presentation = docTarget::class.java.getMethod("computePresentation").invoke(docTarget)
                as TargetPresentation

            DocumentationRequest(pointer, presentation)
        }.finishOnUiThread(ModalityState.defaultModalityState()) { request ->
            pendingDoc = null
            if (request == null || !hovered) return@finishOnUiThread
            dismissDocPopup()
            val manager = DocumentationManager.getInstance(project)
            val componentLocation = editor.contentComponent.locationOnScreen
            val popupPoint = Point(
                screenPoint.x - componentLocation.x,
                screenPoint.y - componentLocation.y + editor.lineHeight,
            )
            try {
                val inlineCtxClass = Class.forName("com.intellij.lang.documentation.ide.impl.InlinePopupContext")
                val inlineCtx = inlineCtxClass.declaredConstructors.first()
                    .also { it.isAccessible = true }
                    .newInstance(project, editor, popupPoint)
                val showDocumentation = manager::class.java.declaredMethods
                    .first { it.name == "showDocumentation" && it.parameterTypes.firstOrNull() == List::class.java }
                    .also { it.isAccessible = true }
                @Suppress("SpreadOperator")
                showDocumentation.invoke(
                    manager,
                    listOf(request),
                    inlineCtx,
                    *arrayOfNulls(showDocumentation.parameterCount - 2)
                )
            } catch (e: java.lang.reflect.InvocationTargetException) {
                throw e.cause ?: e
            }
        }.submit(AppExecutorUtil.getAppExecutorService())
    }

    override fun toString() = label
}
