package skyhanni.plugin.areas.misc

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import skyhanni.plugin.SkyHanniStub
import skyhanni.plugin.SkyHanniTestCase

class Regex101LineMarkerProviderTest : SkyHanniTestCase() {

    private val provider = Regex101LineMarkerProvider()

    private fun firstCall(code: String): KtCallExpression {
        val file = myFixture.configureByText("test.kt", code.trimIndent()) as KtFile
        return PsiTreeUtil.collectElementsOfType(file, KtCallExpression::class.java).first()
    }

    private fun markerFor(code: String) = provider.getLineMarkerInfo(firstCall(code).calleeExpression!!.firstChild)

    private fun infoFor(code: String, argIndex: Int = 1): RegexInfo? {
        val file = myFixture.configureByText("test.kt", code.trimIndent()) as KtFile
        val property = file.declarations.filterIsInstance<KtProperty>().first()
        val call = PsiTreeUtil.collectElementsOfType(property, KtCallExpression::class.java).first()
        return RegexInfo.from(call.valueArguments[argIndex], property.docComment)
    }

    fun testMarkerForRepoPatternPattern() {
        addStubs(SkyHanniStub.REPO_PATTERN)
        assertNotNull(
            markerFor(
                """
                import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
                val x by RepoPattern.pattern("key", "regex")
                """,
            ),
        )
    }

    fun testMarkerForKotlinRegexConstructor() {
        assertNotNull(markerFor("""val x = Regex("abc")"""))
    }

    fun testNoMarkerForUnrelatedPatternFunction() {
        assertNull(
            markerFor(
                """
                fun pattern(key: String, fallback: String) = fallback
                val x = pattern("key", "regex")
                """,
            ),
        )
    }

    fun testNoMarkerForInterpolatedRegex() {
        assertNull(
            markerFor(
                """
                val r = "x"
                val x = Regex("${'$'}r")
                """,
            ),
        )
    }

    fun testRegexTextLiteral() {
        assertEquals("hello world", infoFor("""val x = listOf("key", "hello world")""")?.regex)
    }

    fun testRegexTextEmpty() {
        assertEquals("", infoFor("""val x = listOf("key", "")""")?.regex)
    }

    fun testRegexTextUnescapesEscapeSequence() {
        assertEquals("\\.\\d+", infoFor("""val x = listOf("key", "\\.\\d+")""")?.regex)
    }

    fun testRegexInfoNullForInterpolation() {
        assertNull(infoFor("""val x = listOf("key", "${'$'}{"a".trim()}")"""))
    }

    fun testExamplesEmptyWithoutComment() {
        assertTrue(infoFor("""val x = listOf("key", "regex")""")!!.examples.isEmpty())
    }

    fun testExamplesCombineAllPrefixTypes() {
        val info = infoFor(
            """
            /**
             * Some description.
             * REGEX-TEST: plain test
             * REGEX-FAIL: plain fail
             * WRAPPED-REGEX-TEST: "wrapped test"
             * WRAPPED-REGEX-TEST: no quotes here
             */
            val x = listOf("key", "regex")
            """,
        )
        assertEquals(listOf("plain test", "plain fail", "wrapped test"), info!!.examples)
    }
}
