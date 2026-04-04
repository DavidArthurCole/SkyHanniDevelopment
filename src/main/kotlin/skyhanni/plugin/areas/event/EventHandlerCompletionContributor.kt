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
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

/**
 * Suggests primary function names (from @PrimaryFunction-annotated SkyHanniEvent subclasses)
 * when typing a function name inside a KtObjectDeclaration. Selecting a suggestion inserts
 * a complete @HandleEvent handler stub and adds the necessary imports.
 */
// TODO Add unit tests
class EventHandlerCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(KtTokens.IDENTIFIER)
                .withParent(KtNamedFunction::class.java)
                .inside(KtObjectDeclaration::class.java),
            EventHandlerCompletionProvider(),
        )
    }
}

private class EventHandlerCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        val position = parameters.position
        val function = position.parent as? KtNamedFunction ?: return
        if (function.nameIdentifier != position) return
        val objectDecl = function.containingClassOrObject as? KtObjectDeclaration ?: return

        val project = position.project
        val primaryNameMap = buildPrimaryNameMap(project)
        if (primaryNameMap.isEmpty()) return

        val existingNames = objectDecl.declarations
            .filterIsInstance<KtNamedFunction>()
            .mapNotNullTo(mutableSetOf()) { it.name }

        for ((primaryName, eventFqn) in primaryNameMap) {
            if (primaryName in existingNames) continue
            val eventSimpleName = eventFqn.substringAfterLast('.')
            result.addElement(
                LookupElementBuilder.create(primaryName)
                    .withTypeText(eventSimpleName)
                    .withIcon(AllIcons.Nodes.Method)
                    .bold()
                    .withInsertHandler(StubInsertHandler(primaryName, eventSimpleName, eventFqn))
            )
        }
    }
}

private class StubInsertHandler(
    private val primaryName: String,
    private val eventSimpleName: String,
    private val eventFqn: String,
) : InsertHandler<LookupElement> {

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        // Prevent IntelliJ from appending extra characters after the selected name
        context.setAddCompletionChar(false)

        val project = context.project
        val document = context.document
        PsiDocumentManager.getInstance(project).commitDocument(document)

        val file = PsiDocumentManager.getInstance(project).getPsiFile(document) as? KtFile ?: return
        val elementAtStart = file.findElementAt(context.startOffset) ?: return
        val function = PsiTreeUtil.getParentOfType(elementAtStart, KtNamedFunction::class.java, false) ?: return

        val stubText = "@$HANDLE_EVENT_ANNOTATION\nfun $primaryName(event: $eventSimpleName) {\n\n}"
        val functionStart = function.textRange.startOffset
        val functionEnd = function.textRange.endOffset

        WriteCommandAction.runWriteCommandAction(project) {
            document.replaceString(functionStart, functionEnd, stubText)
            PsiDocumentManager.getInstance(project).commitDocument(document)

            val newFile = PsiDocumentManager.getInstance(project).getPsiFile(document) as? KtFile
                ?: return@runWriteCommandAction
            val factory = KtPsiFactory(project)
            addImportIfMissing(newFile, factory, HANDLE_EVENT_FQN)
            addImportIfMissing(newFile, factory, eventFqn)
            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document)

            // Place caret on the blank line inside the body
            val bodyOffset = functionStart + stubText.indexOfLast { it == '{' } + 2
            context.editor.caretModel.moveToOffset(bodyOffset)
        }
    }

    private fun addImportIfMissing(file: KtFile, factory: KtPsiFactory, fqName: String) {
        if (file.importDirectives.any { it.importedFqName?.asString() == fqName }) return
        val importDirective = factory.createFile("import $fqName\n").importDirectives.firstOrNull() ?: return
        val importList = file.importList ?: run {
            file.add(importDirective)
            return
        }
        val insertBefore = importList.imports.firstOrNull { existing ->
            (existing.importedFqName?.asString() ?: return@firstOrNull false) > fqName
        }
        if (insertBefore != null) importList.addBefore(importDirective, insertBefore) else importList.add(importDirective)
    }
}
