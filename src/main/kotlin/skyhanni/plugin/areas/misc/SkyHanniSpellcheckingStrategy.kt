package skyhanni.plugin.areas.misc

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.spellchecker.inspections.PlainTextSplitter
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy
import com.intellij.spellchecker.tokenizer.TokenConsumer
import com.intellij.spellchecker.tokenizer.Tokenizer

class SkyHanniSpellcheckingStrategy : SpellcheckingStrategy() {

    override fun getTokenizer(element: PsiElement): Tokenizer<out PsiElement> {
        if (element.isInsideRegexTestComment()) return EMPTY_TOKENIZER
        val template = element.enclosingStringTemplate() ?: return super.getTokenizer(element)
        if (template.isIdentifierArg()) return EMPTY_TOKENIZER
        if (template.containsMinecraftColorCode()) return ColorCodeStrippingTokenizer
        return super.getTokenizer(element)
    }
}

private val colorCodeRegex = Regex("§.")

private object ColorCodeStrippingTokenizer : Tokenizer<PsiElement>() {
    override fun tokenize(element: PsiElement, consumer: TokenConsumer) {
        val text = element.text
        var lastEnd = 0

        fun TextRange.consumeToken() {
            consumer.consumeToken(element, text, false, 0, this, PlainTextSplitter.getInstance())
        }

        for (match in colorCodeRegex.findAll(text)) {
            if (match.range.first > lastEnd) {
                TextRange(lastEnd, match.range.first).consumeToken()
            }
            lastEnd = match.range.last + 1
        }
        if (lastEnd < text.length) {
            TextRange(lastEnd, text.length).consumeToken()
        }
    }
}
