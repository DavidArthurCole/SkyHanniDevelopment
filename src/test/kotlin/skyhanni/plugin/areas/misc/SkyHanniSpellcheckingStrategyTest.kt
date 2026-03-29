package skyhanni.plugin.areas.misc

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Tests for the spellcheck-suppression helper functions:
 * [resolveCallArg], [isCommandNameArg], [isCommandAliasArg],
 * [isRepoPatternKeyArg], [isGroupNameArg], and [isRepoPatternRegexArg].
 */
class SkyHanniSpellcheckingStrategyTest : BasePlatformTestCase() {

    /** Parses [code] and returns the first string template whose literal text equals [content]. */
    private fun stringTemplate(code: String, content: String): KtStringTemplateExpression {
        val file = myFixture.configureByText("test.kt", code.trimIndent()) as KtFile
        return PsiTreeUtil.collectElementsOfType(file, KtStringTemplateExpression::class.java)
            .first { it.text == "\"$content\"" }
    }

    /** Parses [code] and returns all string templates ordered by offset. */
    private fun allStringTemplates(code: String): List<KtStringTemplateExpression> {
        val file = myFixture.configureByText("test.kt", code.trimIndent()) as KtFile
        return PsiTreeUtil.collectElementsOfType(file, KtStringTemplateExpression::class.java)
            .sortedBy { it.textOffset }
    }

    fun testResolveCallArgReturnsTripleForArgInCall() {
        val str = stringTemplate("""fun f() { foo("bar") }""", "bar")
        val (_, index, name) = str.resolveCallArg()!!
        assertEquals(0, index)
        assertEquals("foo", name)
    }

    fun testResolveCallArgReturnsCorrectIndexForSecondArg() {
        val str = stringTemplate("""fun f() { foo("a", "b") }""", "b")
        val (_, index, _) = str.resolveCallArg()!!
        assertEquals(1, index)
    }

    fun testResolveCallArgNullForTopLevelStringExpression() {
        val str = stringTemplate("""val x = "standalone" """, "standalone")
        assertNull(str.resolveCallArg())
    }

    fun testResolveCallArgNullForStringInLocalVal() {
        val str = stringTemplate("""fun f() { val x = "value" }""", "value")
        assertNull(str.resolveCallArg())
    }

    fun testIsCommandNameArgTrueForRegisterBrigadierFirstArg() {
        val str = stringTemplate(
            """fun f() { CommandUtil.registerBrigadier("cmd") {} }""",
            "cmd"
        )
        assertTrue(str.isCommandNameArg())
    }

    fun testIsCommandNameArgTrueForRegisterComplexFirstArg() {
        val str = stringTemplate(
            """fun f() { CommandUtil.registerComplex("cmd") {} }""",
            "cmd"
        )
        assertTrue(str.isCommandNameArg())
    }

    fun testIsCommandNameArgFalseWhenNoDotReceiver() {
        val str = stringTemplate(
            """fun f() { registerBrigadier("cmd") {} }""",
            "cmd"
        )
        assertFalse(str.isCommandNameArg())
    }

    fun testIsCommandNameArgFalseForWrongFunctionName() {
        val str = stringTemplate(
            """fun f() { CommandUtil.registerOther("cmd") {} }""",
            "cmd"
        )
        assertFalse(str.isCommandNameArg())
    }

    fun testIsCommandNameArgFalseForSecondArg() {
        val templates = allStringTemplates(
            """fun f() { CommandUtil.registerBrigadier("cmd", "extra") {} }"""
        )
        assertTrue(templates[0].isCommandNameArg())
        assertFalse(templates[1].isCommandNameArg())
    }

    fun testIsCommandAliasArgTrueInsideRegisterBrigadierLambda() {
        val str = stringTemplate(
            """
            fun f() {
                CommandUtil.registerBrigadier("cmd") {
                    aliases = listOf("alias1")
                }
            }
            """,
            "alias1"
        )
        assertTrue(str.isCommandAliasArg())
    }

    fun testIsCommandAliasArgTrueInsideRegisterComplexLambda() {
        val str = stringTemplate(
            """
            fun f() {
                CommandUtil.registerComplex("cmd") {
                    aliases = listOf("alias1")
                }
            }
            """,
            "alias1"
        )
        assertTrue(str.isCommandAliasArg())
    }

    fun testIsCommandAliasArgFalseWhenNotAssignedToAliases() {
        val str = stringTemplate(
            """
            fun f() {
                CommandUtil.registerBrigadier("cmd") {
                    other = listOf("alias1")
                }
            }
            """,
            "alias1"
        )
        assertFalse(str.isCommandAliasArg())
    }

    fun testIsCommandAliasArgFalseWhenOutsideRegisterLambda() {
        val str = stringTemplate(
            """fun f() { val x = listOf("alias1") }""",
            "alias1"
        )
        assertFalse(str.isCommandAliasArg())
    }

    fun testIsRepoPatternKeyArgTrueForRepoPatternGroup() {
        val str = stringTemplate(
            """val g = RepoPattern.group("module.key")""",
            "module.key"
        )
        assertTrue(str.isRepoPatternKeyArg())
    }

    fun testIsRepoPatternKeyArgTrueForRepoPatternExclusiveGroup() {
        val str = stringTemplate(
            """val g = RepoPattern.exclusiveGroup("module.key")""",
            "module.key"
        )
        assertTrue(str.isRepoPatternKeyArg())
    }

    fun testIsRepoPatternKeyArgTrueForRepoPatternList() {
        val str = stringTemplate(
            """val g = RepoPattern.list("module.key")""",
            "module.key"
        )
        assertTrue(str.isRepoPatternKeyArg())
    }

    fun testIsRepoPatternKeyArgTrueForPatternKeyAtIndex0() {
        val templates = allStringTemplates(
            """val p = RepoPattern.pattern("module.key", "regex")"""
        )
        assertTrue(templates[0].isRepoPatternKeyArg())
        assertFalse(templates[1].isRepoPatternKeyArg())
    }

    fun testIsRepoPatternKeyArgFalseForWrongReceiver() {
        val str = stringTemplate(
            """val g = someObj.group("module.key")""",
            "module.key"
        )
        assertFalse(str.isRepoPatternKeyArg())
    }

    fun testIsRepoPatternKeyArgFalseForPatternWithOneArg() {
        val str = stringTemplate(
            """val p = someObj.pattern("key")""",
            "key"
        )
        assertFalse(str.isRepoPatternKeyArg())
    }

    fun testIsGroupNameArgTrueForGroup() {
        val str = stringTemplate(
            """val g = something.group("name")""",
            "name"
        )
        assertTrue(str.isGroupNameArg())
    }

    fun testIsGroupNameArgTrueForGroupOrNull() {
        val str = stringTemplate(
            """val g = something.groupOrNull("name")""",
            "name"
        )
        assertTrue(str.isGroupNameArg())
    }

    fun testIsGroupNameArgFalseForOtherFunction() {
        val str = stringTemplate(
            """val g = something.getGroup("name")""",
            "name"
        )
        assertFalse(str.isGroupNameArg())
    }

    fun testIsGroupNameArgFalseForSecondArg() {
        val templates = allStringTemplates(
            """val g = group("name", "extra")"""
        )
        assertTrue(templates[0].isGroupNameArg())
        assertFalse(templates[1].isGroupNameArg())
    }

    fun testIsRepoPatternRegexArgTrueForSecondArgOfPattern() {
        val templates = allStringTemplates(
            """val p = someGroup.pattern("key", "some\\.regex")"""
        )
        assertFalse(templates[0].isRepoPatternRegexArg())
        assertTrue(templates[1].isRepoPatternRegexArg())
    }

    fun testIsRepoPatternRegexArgFalseForNonPatternCall() {
        val templates = allStringTemplates(
            """val p = other("key", "regex")"""
        )
        assertFalse(templates[0].isRepoPatternRegexArg())
        assertFalse(templates[1].isRepoPatternRegexArg())
    }
}
