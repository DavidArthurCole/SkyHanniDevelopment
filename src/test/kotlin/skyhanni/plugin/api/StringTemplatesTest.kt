package skyhanni.plugin.api

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import skyhanni.plugin.SkyHanniTestCase

class StringTemplatesTest : SkyHanniTestCase() {

    fun testLiteralString() {
        assertEquals("hello world", templateFor("""val x = "hello world"""").evaluateString())
    }

    fun testEmptyLiteral() {
        assertEquals("", templateFor("""val x = """"").evaluateString())
    }

    fun testEscapeSequencePreserved() {
        assertEquals("\\.\\d+", templateFor("""val x = "\\.\\d+"""").constantText())
    }

    fun testBlockEntryReturnsNull() {
        assertNull(templateFor($$"""fun f() { val x = "${g()}" }""").evaluateString())
    }

    fun testSimpleNameResolvedFromLocalVal() {
        val code = $$"""
            fun foo() {
                val greeting = "hello"
                val composed = "$greeting world"
            }
        """
        assertEquals("hello world", localTemplate(code, "composed"))
    }

    fun testUnknownNameReturnsNull() {
        val code = $$"""
            fun foo() {
                val result = "$unknown"
            }
        """
        assertNull(localTemplate(code, "result"))
    }

    fun testVarIsNotResolved() {
        val code = $$"""
            fun foo() {
                var mutable = "no"
                val result = "$mutable"
            }
        """
        assertNull(localTemplate(code, "result"))
    }

    fun testTwoHopResolution() {
        val code = $$"""
            fun foo() {
                val a = "x"
                val b = "$a.y"
                val c = "$b.z"
            }
        """
        assertEquals("x.y.z", localTemplate(code, "c"))
    }

    private fun templateFor(code: String): KtStringTemplateExpression {
        val file = myFixture.configureByText("Test.kt", code.trimIndent()) as KtFile
        return PsiTreeUtil.collectElementsOfType(file, KtStringTemplateExpression::class.java).first()
    }

    private fun localTemplate(code: String, name: String): String? {
        val file = myFixture.configureByText("Test.kt", code.trimIndent()) as KtFile
        val statements = file.declarations.filterIsInstance<KtFunction>().first().bodyBlockExpression!!.statements
        val template = statements.filterIsInstance<KtProperty>().first { it.name == name }.initializer as KtStringTemplateExpression
        return template.evaluateString()
    }
}
