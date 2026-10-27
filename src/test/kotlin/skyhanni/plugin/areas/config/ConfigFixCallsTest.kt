package skyhanni.plugin.areas.config

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import skyhanni.plugin.SkyHanniStub
import skyhanni.plugin.SkyHanniTestCase

class ConfigFixCallsTest : SkyHanniTestCase() {

    private fun pathArg(body: String, content: String): KtStringTemplateExpression {
        addStubs(SkyHanniStub.CONFIG_MIGRATOR)
        val code = """
            import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
            fun f(event: ConfigUpdaterMigrator.ConfigFixEvent) { $body }
        """.trimIndent()
        val file = myFixture.configureByText("test.kt", code) as KtFile
        return PsiTreeUtil.collectElementsOfType(file, KtStringTemplateExpression::class.java)
            .first { it.text == "\"$content\"" }
    }

    fun testTransformPathArg() {
        assertNotNull(pathArg("""event.transform(1, "some.path")""", "some.path").asConfigFixPathArg())
    }

    fun testMoveFromPathArg() {
        assertNotNull(pathArg("""event.move(1, "from.path", "to.path")""", "from.path").asConfigFixPathArg())
    }

    fun testMoveToPathArg() {
        assertNotNull(pathArg("""event.move(1, "from.path", "to.path")""", "to.path").asConfigFixPathArg())
    }

    fun testAddPathArg() {
        assertNotNull(pathArg("""event.add(1, "new.path") { }""", "new.path").asConfigFixPathArg())
    }

    fun testRemovePathArg() {
        assertNotNull(pathArg("""event.remove(1, "old.path")""", "old.path").asConfigFixPathArg())
    }

    fun testStandaloneStringIsNotPathArg() {
        addStubs(SkyHanniStub.CONFIG_MIGRATOR)
        val file = myFixture.configureByText("test.kt", """val x = "standalone"""") as KtFile
        val str = PsiTreeUtil.collectElementsOfType(file, KtStringTemplateExpression::class.java).first()
        assertNull(str.asConfigFixPathArg())
    }

    fun testComputeClassConfigPathSegmentsNullForAbstractClass() {
        val file = myFixture.configureByText(
            "AbstractConfig.kt",
            """
            package at.hannibal2.skyhanni.config
            abstract class AbstractConfig
            """.trimIndent(),
        ) as KtFile
        assertNull(computeClassConfigPathSegments(ktClass(file, "AbstractConfig")))
    }

    fun testComputeClassConfigPathSegmentsNullForEnumClass() {
        val file = myFixture.configureByText(
            "MyEnum.kt",
            """
            package at.hannibal2.skyhanni.config
            enum class MyEnum { A, B }
            """.trimIndent(),
        ) as KtFile
        assertNull(computeClassConfigPathSegments(ktClass(file, "MyEnum")))
    }

    fun testComputeClassConfigPathSegmentsNullOutsideConfigPackage() {
        val file = myFixture.configureByText(
            "SomeClass.kt",
            """
            package com.example
            class SomeClass
            """.trimIndent(),
        ) as KtFile
        assertNull(computeClassConfigPathSegments(ktClass(file, "SomeClass")))
    }

    fun testComputeClassConfigPathSegmentsNullForOrphanConfigClass() {
        val file = myFixture.configureByText(
            "OrphanConfig.kt",
            """
            package at.hannibal2.skyhanni.config
            class OrphanConfig
            """.trimIndent(),
        ) as KtFile
        assertNull(computeClassConfigPathSegments(ktClass(file, "OrphanConfig")))
    }

    fun testComputeClassConfigPathSegmentsReturnsPathForNestedConfigClass() {
        myFixture.addFileToProject(
            "at/hannibal2/skyhanni/config/SkyHanniConfig.kt",
            """
            package at.hannibal2.skyhanni.config
            class SkyHanniConfig {
                val nested: NestedConfig = NestedConfig()
            }
            """.trimIndent(),
        )
        val child = myFixture.addFileToProject(
            "at/hannibal2/skyhanni/config/NestedConfig.kt",
            """
            package at.hannibal2.skyhanni.config
            class NestedConfig
            """.trimIndent(),
        ) as KtFile
        val segments = computeClassConfigPathSegments(ktClass(child, "NestedConfig"))
        assertNotNull(segments)
        assertEquals("nested", segments!!.joinToString(".") { it.name })
    }

    private fun ktClass(file: KtFile, name: String): KtClass =
        file.declarations.filterIsInstance<KtClass>().first { it.name == name }
}
