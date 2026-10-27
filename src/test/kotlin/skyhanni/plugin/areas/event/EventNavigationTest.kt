package skyhanni.plugin.areas.event

import com.intellij.psi.PsiPolyVariantReference
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import skyhanni.plugin.SkyHanniStub
import skyhanni.plugin.SkyHanniTestCase

class EventNavigationTest : SkyHanniTestCase() {

    private fun fooEvent() {
        myFixture.addFileToProject(
            "com/example/FooEvent.kt",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.SkyHanniEvent
            import at.hannibal2.skyhanni.skyhannimodule.PrimaryFunction
            @PrimaryFunction("onFoo")
            class FooEvent : SkyHanniEvent()
            """.trimIndent(),
        )
    }

    private fun gutterTooltips(): List<String> = myFixture.findAllGutters().mapNotNull { it.tooltipText }

    fun testHandlerGutterPointsAtEvent() {
        addStubs(SkyHanniStub.EVENT, SkyHanniStub.MODULE)
        fooEvent()
        myFixture.configureByText(
            "MyModule.kt",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            object MyModule {
                @HandleEvent
                private fun onFoo(event: FooEvent) {}
            }
            """.trimIndent(),
        )
        assertTrue(intentionText("event.lineMarker.handles", "FooEvent") in gutterTooltips())
    }

    fun testUnannotatedFunctionHasNoHandlerGutter() {
        addStubs(SkyHanniStub.EVENT, SkyHanniStub.MODULE)
        fooEvent()
        myFixture.configureByText(
            "MyModule.kt",
            """
            package com.example
            object MyModule {
                fun onFoo() {}
            }
            """.trimIndent(),
        )
        assertFalse(intentionText("event.lineMarker.handles", "FooEvent") in gutterTooltips())
    }

    fun testEventGutterListsHandlers() {
        addStubs(SkyHanniStub.EVENT, SkyHanniStub.MODULE)
        myFixture.addFileToProject(
            "com/example/MyModule.kt",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            object MyModule {
                @HandleEvent
                private fun onFoo(event: FooEvent) {}
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "FooEvent.kt",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.SkyHanniEvent
            import at.hannibal2.skyhanni.skyhannimodule.PrimaryFunction
            @PrimaryFunction("onFoo")
            class FooEvent : SkyHanniEvent()
            """.trimIndent(),
        )
        assertTrue(intentionText("event.lineMarker.handledBy", 1) in gutterTooltips())
    }

    fun testPrimaryFunctionReferenceResolvesOnlyAnnotatedHandlers() {
        addStubs(SkyHanniStub.EVENT, SkyHanniStub.MODULE)
        myFixture.addFileToProject(
            "com/example/MyModule.kt",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            object MyModule {
                @HandleEvent
                private fun onFoo() {}
            }
            object Other {
                fun onFoo() {}
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "FooEvent.kt",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.SkyHanniEvent
            import at.hannibal2.skyhanni.skyhannimodule.PrimaryFunction
            @PrimaryFunction("on<caret>Foo")
            class FooEvent : SkyHanniEvent()
            """.trimIndent(),
        )
        val reference = myFixture.getReferenceAtCaretPositionWithAssertion() as PsiPolyVariantReference
        val targets = reference.multiResolve(false).mapNotNull { it.element as? KtNamedFunction }
        assertEquals(listOf("MyModule"), targets.map { it.containingClassOrObject?.name })
    }

    fun testCompletionSuggestsPrimaryFunctionName() {
        addStubs(SkyHanniStub.EVENT, SkyHanniStub.MODULE)
        fooEvent()
        myFixture.configureByText(
            "MyModule.kt",
            """
            package com.example
            object MyModule {
                fun onF<caret>
            }
            """.trimIndent(),
        )
        val offered = myFixture.completeBasic()?.map { it.lookupString }
        if (offered != null) {
            assertTrue(offered.toString(), "onFoo" in offered)
        } else {
            assertTrue(myFixture.editor.document.text.contains("fun onFoo(event: FooEvent)"))
        }
    }
}
