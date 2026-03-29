package skyhanni.plugin.areas.config

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Tests for [asConfigEventPathArg] and [computeClassConfigPathSegments].
 */
class ConfigUtilsExtraTest : BasePlatformTestCase() {

    private fun stringTemplateIn(code: String, content: String): KtStringTemplateExpression {
        val file = myFixture.configureByText("test.kt", code.trimIndent()) as KtFile
        return PsiTreeUtil.collectElementsOfType(file, KtStringTemplateExpression::class.java)
            .first { it.text == "\"$content\"" }
    }

    fun testAsConfigEventPathArgReturnedForTransformAtIndex1() {
        val str = stringTemplateIn(
            """fun f(event: Any) { event.transform(1, "some.path") }""",
            "some.path"
        )
        val call = str.asConfigEventPathArg()
        assertNotNull(call)
        assertEquals("transform", call!!.calleeExpression?.text)
    }

    fun testAsConfigEventPathArgReturnedForMoveAtIndex1() {
        val str = stringTemplateIn(
            """fun f(event: Any) { event.move(1, "from.path", "to.path") }""",
            "from.path"
        )
        assertNotNull(str.asConfigEventPathArg())
    }

    fun testAsConfigEventPathArgReturnedForMoveAtIndex2() {
        val str = stringTemplateIn(
            """fun f(event: Any) { event.move(1, "from.path", "to.path") }""",
            "to.path"
        )
        assertNotNull(str.asConfigEventPathArg())
    }

    fun testAsConfigEventPathArgReturnedForAddAtIndex1() {
        val str = stringTemplateIn(
            """fun f(event: Any) { event.add(1, "new.path") }""",
            "new.path"
        )
        assertNotNull(str.asConfigEventPathArg())
    }

    fun testAsConfigEventPathArgReturnedForRemoveAtIndex1() {
        val str = stringTemplateIn(
            """fun f(event: Any) { event.remove(1, "old.path") }""",
            "old.path"
        )
        assertNotNull(str.asConfigEventPathArg())
    }

    fun testAsConfigEventPathArgNullForWrongReceiver() {
        val str = stringTemplateIn(
            """fun f(other: Any) { other.transform(1, "path") }""",
            "path"
        )
        assertNull(str.asConfigEventPathArg())
    }

    fun testAsConfigEventPathArgNullForUnknownFunction() {
        val str = stringTemplateIn(
            """fun f(event: Any) { event.unknown(1, "path") }""",
            "path"
        )
        assertNull(str.asConfigEventPathArg())
    }

    fun testAsConfigEventPathArgNullForArgAtIndex0() {
        // "path" is at index 0 (before the Int); argIndex < 1 → null
        val str = stringTemplateIn(
            """fun f(event: Any) { event.transform("path", 1) }""",
            "path"
        )
        assertNull(str.asConfigEventPathArg())
    }

    fun testAsConfigEventPathArgNullForNonMoveAtIndex2() {
        // "extra" is at index 2 in a non-move function → null
        val str = stringTemplateIn(
            """fun f(event: Any) { event.add(1, "path", "extra") }""",
            "extra"
        )
        assertNull(str.asConfigEventPathArg())
    }

    fun testAsConfigEventPathArgNullForStringNotInCall() {
        val str = stringTemplateIn(
            """val x = "standalone" """,
            "standalone"
        )
        assertNull(str.asConfigEventPathArg())
    }

    private fun addClass(fileName: String, code: String) {
        myFixture.addFileToProject(fileName, code.trimIndent())
    }

    private fun ktClass(file: KtFile, name: String): KtClass =
        file.declarations.filterIsInstance<KtClass>().first { it.name == name }

    fun testComputeClassConfigPathSegmentsNullForAbstractClass() {
        val file = myFixture.configureByText(
            "AbstractConfig.kt",
            """
            package at.hannibal2.skyhanni.config
            abstract class AbstractConfig
            """.trimIndent()
        ) as KtFile
        assertNull(computeClassConfigPathSegments(ktClass(file, "AbstractConfig")))
    }

    fun testComputeClassConfigPathSegmentsNullForEnumClass() {
        val file = myFixture.configureByText(
            "MyEnum.kt",
            """
            package at.hannibal2.skyhanni.config
            enum class MyEnum { A, B }
            """.trimIndent()
        ) as KtFile
        assertNull(computeClassConfigPathSegments(ktClass(file, "MyEnum")))
    }

    fun testComputeClassConfigPathSegmentsNullForClassOutsideConfigPackage() {
        val file = myFixture.configureByText(
            "SomeClass.kt",
            """
            package com.example
            class SomeClass
            """.trimIndent()
        ) as KtFile
        val klass = file.declarations.filterIsInstance<KtClass>().first { it.name == "SomeClass" }
        assertNull(computeClassConfigPathSegments(klass))
    }

    fun testComputeClassConfigPathSegmentsNullForRootConfigClass() {
        addClass(
            "at/hannibal2/skyhanni/config/SkyHanniConfig.kt",
            """
            package at.hannibal2.skyhanni.config
            class SkyHanniConfig
            """
        )
        val file = myFixture.addFileToProject(
            "at/hannibal2/skyhanni/config/SkyHanniConfig2.kt",
            """
            package at.hannibal2.skyhanni.config
            class SkyHanniConfig
            """.trimIndent()
        ) as KtFile
        // The FQN is in ROOT_CONFIG_FQNS, so should return null
        assertNull(computeClassConfigPathSegments(ktClass(file, "SkyHanniConfig")))
    }

    fun testComputeClassConfigPathSegmentsNullForClassWithNoConfigParent() {
        // A class inside the config package but not referenced by any property in a parent
        val file = myFixture.configureByText(
            "OrphanConfig.kt",
            """
            package at.hannibal2.skyhanni.config
            class OrphanConfig
            """.trimIndent()
        ) as KtFile
        assertNull(computeClassConfigPathSegments(ktClass(file, "OrphanConfig")))
    }

    fun testComputeClassConfigPathSegmentsReturnsPathForNestedConfigClass() {
        // SkyHanniConfig { val nested: NestedConfig }
        // computeClassConfigPathSegments(NestedConfig) → ["nested"]
        addClass(
            "at/hannibal2/skyhanni/config/SkyHanniConfig.kt",
            """
            package at.hannibal2.skyhanni.config
            class SkyHanniConfig {
                val nested: NestedConfig = NestedConfig()
            }
            """
        )
        val childFile = myFixture.addFileToProject(
            "at/hannibal2/skyhanni/config/NestedConfig.kt",
            """
            package at.hannibal2.skyhanni.config
            annotation class ConfigOption
            class NestedConfig {
                @ConfigOption
                var field: Int = 0
            }
            """.trimIndent()
        ) as KtFile

        val segments = computeClassConfigPathSegments(ktClass(childFile, "NestedConfig"))
        assertNotNull(segments)
        assertEquals("nested", segments!!.joinToString(".") { it.name })
    }
}
