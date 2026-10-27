package skyhanni.plugin.areas.event

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import skyhanni.plugin.api.SkyHanniIds
import skyhanni.plugin.api.ensureImport

class EventHandlerCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(KtTokens.IDENTIFIER).inside(KtNamedFunction::class.java),
            EventHandlerCompletionProvider(),
        )
    }
}

private class EventHandlerCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val position = parameters.position
        val function = position.getParentOfType<KtNamedFunction>(strict = true) ?: return
        if (!function.isNamePosition(position)) return
        val objectDecl = function.containingClassOrObject as? KtObjectDeclaration ?: return
        val primaryNames = PrimaryFunctionIndex.get(position.project)
        if (primaryNames.isEmpty()) return
        val existingNames = objectDecl.declarations.filterIsInstance<KtNamedFunction>().mapNotNullTo(mutableSetOf()) { it.name }
        for ((primaryName, classId) in primaryNames) {
            if (primaryName in existingNames) continue
            val eventName = classId.shortClassName.asString()
            result.addElement(
                LookupElementBuilder.create(primaryName)
                    .withTypeText(eventName)
                    .withIcon(AllIcons.Nodes.Method)
                    .bold()
                    .withInsertHandler(StubInsertHandler(primaryName, classId)),
            )
        }
    }
}

private fun KtNamedFunction.isNamePosition(position: PsiElement): Boolean {
    if (nameIdentifier == position) return true
    val incomplete = bodyExpression == null && valueParameters.isEmpty()
    return incomplete && receiverTypeReference?.textRange?.contains(position.textRange) == true
}

private class StubInsertHandler(private val primaryName: String, private val eventClassId: ClassId) : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        context.setAddCompletionChar(false)
        val project = context.project
        val document = context.document
        val documentManager = PsiDocumentManager.getInstance(project)
        documentManager.commitDocument(document)
        val file = documentManager.getPsiFile(document) as? KtFile ?: return
        val elementAtStart = file.findElementAt(context.startOffset) ?: return
        val function = elementAtStart.getParentOfType<KtNamedFunction>(strict = false) ?: return
        val eventName = eventClassId.shortClassName.asString()
        val handleEventName = SkyHanniIds.HANDLE_EVENT.shortClassName.asString()
        val stubText = "@$handleEventName\nfun $primaryName(event: $eventName) {\n\n}"
        val functionStart = function.textRange.startOffset
        val functionEnd = function.textRange.endOffset
        WriteCommandAction.runWriteCommandAction(project) {
            document.replaceString(functionStart, functionEnd, stubText)
            documentManager.commitDocument(document)
            val newFile = documentManager.getPsiFile(document) as? KtFile ?: return@runWriteCommandAction
            newFile.ensureImport(SkyHanniIds.HANDLE_EVENT.asSingleFqName())
            newFile.ensureImport(eventClassId.asSingleFqName())
            documentManager.doPostponedOperationsAndUnblockDocument(document)
            context.editor.caretModel.moveToOffset(functionStart + stubText.indexOfLast { it == '{' } + 2)
        }
    }
}
