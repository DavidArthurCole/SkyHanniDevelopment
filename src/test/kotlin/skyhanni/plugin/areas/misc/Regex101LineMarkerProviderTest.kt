package skyhanni.plugin.areas.misc

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtValueArgument

/**
 * Tests for [resolveRegexArgIndex] and [RegexInfo].
 */
class Regex101LineMarkerProviderTest : BasePlatformTestCase() {

    /** Parses [code] and returns the first call expression whose callee text is [calleeName]. */
    private fun callNamed(code: String, calleeName: String): KtCallExpression {
        val file = myFixture.configureByText("test.kt", code.trimIndent()) as KtFile
        return PsiTreeUtil.collectElementsOfType(file, KtCallExpression::class.java)
            .first { it.calleeExpression?.text == calleeName }
    }

    /** Returns the [KtSimpleNameExpression] callee of [call]. */
    private fun KtCallExpression.callee() = calleeExpression as KtSimpleNameExpression

    /** Returns [RegexInfo] built from the value argument at [argIndex] in [call], with no KDoc. */
    private fun regexInfoAt(call: KtCallExpression, argIndex: Int) =
        RegexInfo(call.valueArguments[argIndex], null)

    /** Parses [code] and returns [RegexInfo] for the second argument of the first `pattern` call,
     *  with the KDoc of the enclosing property as the comment. */
    private fun regexInfoWithDoc(code: String): RegexInfo {
        val file = myFixture.configureByText("test.kt", code.trimIndent()) as KtFile
        val property = file.declarations.filterIsInstance<KtProperty>().first()
        val call = PsiTreeUtil.collectElementsOfType(property, KtCallExpression::class.java)
            .first { it.calleeExpression?.text == "pattern" }
        return RegexInfo(call.valueArguments[1], property.docComment)
    }

    fun testResolveRegexArgIndexForPatternWithTwoArgs() {
        val call = callNamed("""val x = pattern("key", "regex")""", "pattern")
        assertEquals(1, resolveRegexArgIndex(call.callee(), call))
    }

    fun testResolveRegexArgIndexForPatternWithOneArgIsNull() {
        val call = callNamed("""val x = pattern("key")""", "pattern")
        assertNull(resolveRegexArgIndex(call.callee(), call))
    }

    fun testResolveRegexArgIndexForRegexWithOneArg() {
        val call = callNamed("""val x = Regex("abc")""", "Regex")
        assertEquals(0, resolveRegexArgIndex(call.callee(), call))
    }

    fun testResolveRegexArgIndexForRegexWithTwoArgs() {
        val call = callNamed("""val x = Regex("abc", setOf())""", "Regex")
        assertEquals(0, resolveRegexArgIndex(call.callee(), call))
    }

    fun testResolveRegexArgIndexForRegexWithNoArgsIsNull() {
        val call = callNamed("""val x = Regex()""", "Regex")
        assertNull(resolveRegexArgIndex(call.callee(), call))
    }

    fun testResolveRegexArgIndexForUnknownCallIsNull() {
        val call = callNamed("""val x = compile("abc")""", "compile")
        assertNull(resolveRegexArgIndex(call.callee(), call))
    }

    fun testGetRegexTextReturnsLiteralString() {
        val call = callNamed("""val x = pattern("key", "hello world")""", "pattern")
        assertEquals("hello world", regexInfoAt(call, 1).getRegexText())
    }

    fun testGetRegexTextReturnsEmptyString() {
        val call = callNamed("""val x = pattern("key", "")""", "pattern")
        assertEquals("", regexInfoAt(call, 1).getRegexText())
    }

    fun testGetRegexTextUnescapesEscapeSequence() {
        // \\ in a Kotlin string literal is a KtEscapeStringTemplateEntry with unescapedValue "\"
        // so "\\.\\d+" in source → getRegexText() returns the string \.\d+
        val call = callNamed("""val x = pattern("key", "\\.\\d+")""", "pattern")
        assertEquals("\\.\\d+", regexInfoAt(call, 1).getRegexText())
    }

    fun testGetRegexTextNullForInterpolatedString() {
        val call = callNamed("""val r = "x"; val x = pattern("key", "${'$'}r")""", "pattern")
        assertNull(regexInfoAt(call, 1).getRegexText())
    }

    fun testGetRegexTextNullForBlockInterpolation() {
        val call = callNamed("""val r = "x"; val x = pattern("key", "${'$'}{r.trim()}")""", "pattern")
        assertNull(regexInfoAt(call, 1).getRegexText())
    }

    fun testGetExamplesEmptyWhenNoComment() {
        val call = callNamed("""val x = pattern("key", "regex")""", "pattern")
        assertTrue(regexInfoAt(call, 1).getExamples().isEmpty())
    }

    fun testGetExamplesExtractsRegexTestLines() {
        val examples = regexInfoWithDoc(
            """
            /**
             * REGEX-TEST: test input one
             * REGEX-TEST: test input two
             */
            val x = pattern("key", "regex")
            """
        ).getExamples()
        assertEquals(listOf("test input one", "test input two"), examples)
    }

    fun testGetExamplesExtractsRegexFailLines() {
        val examples = regexInfoWithDoc(
            """
            /**
             * REGEX-FAIL: bad input
             */
            val x = pattern("key", "regex")
            """
        ).getExamples()
        assertEquals(listOf("bad input"), examples)
    }

    fun testGetExamplesExtractsWrappedRegexTestLines() {
        val examples = regexInfoWithDoc(
            """
            /**
             * WRAPPED-REGEX-TEST: "wrapped example"
             */
            val x = pattern("key", "regex")
            """
        ).getExamples()
        assertEquals(listOf("wrapped example"), examples)
    }

    fun testGetExamplesIgnoresWrappedLineWithoutQuotes() {
        val examples = regexInfoWithDoc(
            """
            /**
             * WRAPPED-REGEX-TEST: no quotes here
             */
            val x = pattern("key", "regex")
            """
        ).getExamples()
        assertTrue(examples.isEmpty())
    }

    fun testGetExamplesCombinesAllPrefixTypes() {
        val examples = regexInfoWithDoc(
            """
            /**
             * REGEX-TEST: plain test
             * REGEX-FAIL: plain fail
             * WRAPPED-REGEX-TEST: "wrapped test"
             */
            val x = pattern("key", "regex")
            """
        ).getExamples()
        assertEquals(listOf("plain test", "plain fail", "wrapped test"), examples)
    }

    fun testGetExamplesIgnoresUnrelatedCommentLines() {
        val examples = regexInfoWithDoc(
            """
            /**
             * Some description.
             * REGEX-TEST: the only test
             * @param key the key
             */
            val x = pattern("key", "regex")
            """
        ).getExamples()
        assertEquals(listOf("the only test"), examples)
    }
}
