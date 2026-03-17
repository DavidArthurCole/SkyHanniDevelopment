package skyhanni.plugin.areas.config

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/** Tests for [evaluateStringTemplate] and [resolveLocalValText]. */
class EvaluateStringTemplateTest : BasePlatformTestCase() {

    fun testLiteralStringReturnsText() {
        val factory = KtPsiFactory(project)
        val expr = factory.createExpression("\"hello world\"") as KtStringTemplateExpression
        assertEquals("hello world", evaluateStringTemplate(expr))
    }

    fun testEmptyLiteralReturnsEmptyString() {
        val factory = KtPsiFactory(project)
        val expr = factory.createExpression("\"\"") as KtStringTemplateExpression
        assertEquals("", evaluateStringTemplate(expr))
    }

    fun testLiteralWithDotsReturnsConcatenatedText() {
        val factory = KtPsiFactory(project)
        val expr = factory.createExpression("\"foo.bar.baz\"") as KtStringTemplateExpression
        assertEquals("foo.bar.baz", evaluateStringTemplate(expr))
    }

    /** A block entry like [KtBlockStringTemplateEntry] (`${'$'}{expr()}`) cannot be statically resolved. */
    fun testBlockEntryReturnsNull() {
        val factory = KtPsiFactory(project)
        val expr = factory.createExpression("\"\${someExpr()}\"") as KtStringTemplateExpression
        assertNull(evaluateStringTemplate(expr))
    }

    fun testSimpleNameEntryResolvedFromLocalVal() {
        val ktFile = myFixture.configureByText(
            "Test.kt",
            """
            fun foo() {
                val greeting = "hello"
                val composed = "${'$'}greeting world"
            }
            """.trimIndent()
        ) as KtFile

        val template = functionBody(ktFile).filterIsInstance<KtProperty>()
            .first { it.name == "composed" }
            .initializer as KtStringTemplateExpression

        assertEquals("hello world", evaluateStringTemplate(template))
    }

    fun testSimpleNameEntryWithNoMatchingValReturnsNull() {
        val ktFile = myFixture.configureByText(
            "Test.kt",
            """
            fun foo() {
                val result = "${'$'}unknown"
            }
            """.trimIndent()
        ) as KtFile

        val template = functionBody(ktFile).filterIsInstance<KtProperty>()
            .first { it.name == "result" }
            .initializer as KtStringTemplateExpression

        assertNull(evaluateStringTemplate(template))
    }

    /** [resolveLocalValText] must not resolve `var` declarations, only `val`. */
    fun testVarDeclarationIsNotResolved() {
        val ktFile = myFixture.configureByText(
            "Test.kt",
            """
            fun foo() {
                var mutable = "should not resolve"
                val result = "${'$'}mutable"
            }
            """.trimIndent()
        ) as KtFile

        val template = functionBody(ktFile).filterIsInstance<KtProperty>()
            .first { it.name == "result" }
            .initializer as KtStringTemplateExpression

        assertNull(evaluateStringTemplate(template))
    }

    fun testOneHopLocalValResolution() {
        val ktFile = myFixture.configureByText(
            "Test.kt",
            """
            fun foo() {
                val a = "x"
                val b = "${'$'}a.y"
            }
            """.trimIndent()
        ) as KtFile

        val statements = functionBody(ktFile).filterIsInstance<KtProperty>()
        val bTemplate = statements.first { it.name == "b" }.initializer as KtStringTemplateExpression
        assertEquals("x.y", evaluateStringTemplate(bTemplate))
    }

    /**
     * Resolution is recursive: `b` refers to `a` (resolves to `"x.y"`), and `c` refers to
     * `b`, so `c` resolves transitively to `"x.y.z"`.
     */
    fun testTwoHopLocalValResolution() {
        val ktFile = myFixture.configureByText(
            "Test.kt",
            """
            fun foo() {
                val a = "x"
                val b = "${'$'}a.y"
                val c = "${'$'}b.z"
            }
            """.trimIndent()
        ) as KtFile

        val statements = functionBody(ktFile).filterIsInstance<KtProperty>()
        val cTemplate = statements.first { it.name == "c" }.initializer as KtStringTemplateExpression
        assertEquals("x.y.z", evaluateStringTemplate(cTemplate))
    }

    private fun functionBody(file: KtFile) =
        file.declarations.filterIsInstance<KtFunction>().first().bodyBlockExpression!!.statements
}
