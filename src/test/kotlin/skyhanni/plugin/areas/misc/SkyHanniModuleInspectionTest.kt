package skyhanni.plugin.areas.misc

import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import skyhanni.plugin.SkyHanniStub
import skyhanni.plugin.SkyHanniTestCase

class SkyHanniModuleInspectionTest : SkyHanniTestCase() {

    private val missing = intentionText("module.problem.missing")
    private val onlyObjects = intentionText("module.problem.onlyObjects")

    private fun messages(code: String): List<String> {
        addStubs(SkyHanniStub.EVENT, SkyHanniStub.MODULE, SkyHanniStub.REPO_PATTERN)
        return inspect(SkyHanniModuleInspection::class.java, "Test.kt", code).mapNotNull { it.description }
    }

    fun testObjectWithHandlerWarns() {
        val m = messages(
            """
            package at.hannibal2.skyhanni.features
            import at.hannibal2.skyhanni.api.event.HandleEvent
            object Feature {
                @HandleEvent
                private fun onTick() {}
            }
            """,
        )
        assertTrue(missing in m)
    }

    fun testObjectWithDelegatedPatternWarns() {
        val m = messages(
            """
            package at.hannibal2.skyhanni.features
            import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
            object Feature {
                private val fooPattern by RepoPattern.pattern("feature.foo", "foo")
            }
            """,
        )
        assertTrue(missing in m)
    }

    fun testObjectWithGroupPatternWarns() {
        val m = messages(
            """
            package at.hannibal2.skyhanni.features
            import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
            object Feature {
                private val group = RepoPattern.group("feature")
                private val fooPattern by group.pattern("foo", "foo")
            }
            """,
        )
        assertTrue(missing in m)
    }

    fun testAnnotatedObjectDoesNotWarn() {
        val m = messages(
            """
            package at.hannibal2.skyhanni.features
            import at.hannibal2.skyhanni.api.event.HandleEvent
            import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
            @SkyHanniModule
            object Feature {
                @HandleEvent
                private fun onTick() {}
            }
            """,
        )
        assertFalse(missing in m)
    }

    fun testObjectOutsideSkyHanniDoesNotWarn() {
        val m = messages(
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            object Feature {
                @HandleEvent
                private fun onTick() {}
            }
            """,
        )
        assertFalse(missing in m)
    }

    fun testPlainObjectDoesNotWarn() {
        val m = messages(
            """
            package at.hannibal2.skyhanni.features
            object Feature {
                fun helper() {}
            }
            """,
        )
        assertFalse(missing in m)
    }

    fun testAnnotatedClassIsAnError() {
        val m = messages(
            """
            package at.hannibal2.skyhanni.features
            import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
            @SkyHanniModule
            class Feature
            """,
        )
        assertTrue(onlyObjects in m)
    }

    fun testSuppressorHidesUnusedOnModuleAndHandler() {
        addStubs(SkyHanniStub.EVENT, SkyHanniStub.MODULE)
        val file = myFixture.configureByText(
            "Test.kt",
            """
            package at.hannibal2.skyhanni.features
            import at.hannibal2.skyhanni.api.event.HandleEvent
            import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
            @SkyHanniModule
            object Feature {
                @HandleEvent
                private fun onTick() {}
                private fun helper() {}
            }
            """.trimIndent(),
        ) as KtFile
        val module = file.declarations.filterIsInstance<KtObjectDeclaration>().single()
        val functions = module.declarations.filterIsInstance<KtNamedFunction>()
        val suppressor = SkyHanniInspectionSuppressor()
        assertTrue(suppressor.isSuppressedFor(module, "unused"))
        assertTrue(suppressor.isSuppressedFor(functions.first { it.name == "onTick" }, "unused"))
        assertFalse(suppressor.isSuppressedFor(functions.first { it.name == "helper" }, "unused"))
    }
}
