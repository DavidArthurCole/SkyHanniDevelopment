package skyhanni.plugin.areas.misc

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import skyhanni.plugin.SkyHanniStub
import skyhanni.plugin.SkyHanniTestCase

class SkyHanniCallSitesTest : SkyHanniTestCase() {

    private fun templates(code: String): List<KtStringTemplateExpression> {
        addStubs(SkyHanniStub.REPO_PATTERN, SkyHanniStub.COMMANDS, SkyHanniStub.REGEX_UTILS)
        val file = myFixture.configureByText("test.kt", code.trimIndent()) as KtFile
        return PsiTreeUtil.collectElementsOfType(file, KtStringTemplateExpression::class.java).sortedBy { it.textOffset }
    }

    private fun template(code: String, content: String) = templates(code).first { it.text == "\"$content\"" }

    fun testCommandNameArg() {
        val str = template(
            """
            import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
            fun f(event: CommandRegistrationEvent) { event.registerBrigadier("cmd") {} }
            """,
            "cmd",
        )
        assertTrue(str.isCommandNameArg())
    }

    fun testCommandNameArgFalseForUnrelatedFunction() {
        val str = template(
            """
            fun registerBrigadier(name: String, block: () -> Unit) {}
            fun f() { registerBrigadier("cmd") {} }
            """,
            "cmd",
        )
        assertFalse(str.isCommandNameArg())
    }

    fun testCommandAliasArg() {
        val str = template(
            """
            import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
            fun f(event: CommandRegistrationEvent) {
                event.registerBrigadier("cmd") {
                    aliases = listOf("alias1")
                }
            }
            """,
            "alias1",
        )
        assertTrue(str.isCommandAliasArg())
    }

    fun testCommandAliasArgFalseOutsideRegisterLambda() {
        val str = template("""fun f() { val x = listOf("alias1") }""", "alias1")
        assertFalse(str.isCommandAliasArg())
    }

    fun testRepoPatternGroupKey() {
        val str = template(
            """
            import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
            val g = RepoPattern.group("module.key")
            """,
            "module.key",
        )
        assertTrue(str.isRepoPatternKeyArg())
    }

    fun testRepoPatternExclusiveGroupKey() {
        val str = template(
            """
            import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
            val g = RepoPattern.exclusiveGroup("module.key")
            """,
            "module.key",
        )
        assertTrue(str.isRepoPatternKeyArg())
    }

    fun testRepoPatternPatternKeyAndRegex() {
        val all = templates(
            """
            import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
            val p by RepoPattern.pattern("module.key", "some\\.regex")
            """,
        )
        assertTrue(all[0].isRepoPatternKeyArg())
        assertFalse(all[0].isRepoPatternRegexArg())
        assertFalse(all[1].isRepoPatternKeyArg())
        assertTrue(all[1].isRepoPatternRegexArg())
    }

    fun testGroupMemberPatternKey() {
        val str = template(
            """
            import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
            val group = RepoPattern.group("g")
            val p by group.pattern("inner.key", "r")
            """,
            "inner.key",
        )
        assertTrue(str.isRepoPatternKeyArg())
    }

    fun testUnrelatedGroupIsNotRepoPatternKey() {
        val str = template(
            """
            object Other { fun group(name: String) = name }
            val g = Other.group("module.key")
            """,
            "module.key",
        )
        assertFalse(str.isRepoPatternKeyArg())
    }

    fun testGroupOrNullNameArg() {
        val str = template(
            """
            import at.hannibal2.skyhanni.utils.RegexUtils.groupOrNull
            fun f(m: java.util.regex.Matcher) { m.groupOrNull("name") }
            """,
            "name",
        )
        assertTrue(str.isGroupNameArg())
    }

    fun testUnrelatedGroupNameArg() {
        val str = template(
            """
            object Other { fun getGroup(name: String) = name }
            val g = Other.getGroup("name")
            """,
            "name",
        )
        assertFalse(str.isGroupNameArg())
    }
}
