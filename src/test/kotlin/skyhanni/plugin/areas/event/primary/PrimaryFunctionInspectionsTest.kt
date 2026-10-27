package skyhanni.plugin.areas.event.primary

import skyhanni.plugin.SkyHanniStub
import skyhanni.plugin.SkyHanniTestCase

class PrimaryFunctionInspectionsTest : SkyHanniTestCase() {

    private val missing = intentionText("primary.problem.missing")

    private fun event(name: String, primaryName: String?) {
        val annotation = primaryName?.let { "@PrimaryFunction(\"$it\")\n" }.orEmpty()
        myFixture.addFileToProject(
            "com/example/$name.kt",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.SkyHanniEvent
            import at.hannibal2.skyhanni.skyhannimodule.PrimaryFunction
            ${annotation}class $name : SkyHanniEvent()
            """.trimIndent(),
        )
    }

    private fun messages(tool: Class<out com.intellij.codeInspection.LocalInspectionTool>, code: String): List<String> =
        inspect(tool, "Test.kt", code).mapNotNull { it.description }

    fun testMissingPrimaryFunctionWarnsOnConcreteEvent() {
        addStubs(SkyHanniStub.EVENT, SkyHanniStub.MODULE)
        val m = messages(
            MissingPrimaryFunctionInspection::class.java,
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.SkyHanniEvent
            class BareEvent : SkyHanniEvent()
            """,
        )
        assertTrue(missing in m)
    }

    fun testMissingPrimaryFunctionSkipsAbstractEvent() {
        addStubs(SkyHanniStub.EVENT, SkyHanniStub.MODULE)
        val m = messages(
            MissingPrimaryFunctionInspection::class.java,
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.SkyHanniEvent
            abstract class BaseEvent : SkyHanniEvent()
            """,
        )
        assertFalse(missing in m)
    }

    fun testMissingPrimaryFunctionSkipsAnnotatedEvent() {
        addStubs(SkyHanniStub.EVENT, SkyHanniStub.MODULE)
        val m = messages(
            MissingPrimaryFunctionInspection::class.java,
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.SkyHanniEvent
            import at.hannibal2.skyhanni.skyhannimodule.PrimaryFunction
            @PrimaryFunction("onGood")
            class GoodEvent : SkyHanniEvent()
            """,
        )
        assertFalse(missing in m)
    }

    fun testMissingPrimaryFunctionSkipsNonEventClass() {
        addStubs(SkyHanniStub.EVENT, SkyHanniStub.MODULE)
        val m = messages(
            MissingPrimaryFunctionInspection::class.java,
            """
            package com.example
            class NotAnEvent
            """,
        )
        assertFalse(missing in m)
    }

    fun testMismatchedHandlerNameWarns() {
        addStubs(SkyHanniStub.EVENT, SkyHanniStub.MODULE)
        event("FooEvent", "onFoo")
        val m = messages(
            MismatchedPrimaryFunctionInspection::class.java,
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            object MyModule {
                @HandleEvent
                private fun handle(event: FooEvent) {}
            }
            """,
        )
        assertTrue(m.contains(intentionText("primary.problem.mismatched", "handle", "onFoo", "FooEvent")))
    }

    fun testMatchingHandlerNameDoesNotWarn() {
        addStubs(SkyHanniStub.EVENT, SkyHanniStub.MODULE)
        event("FooEvent", "onFoo")
        val m = messages(
            MismatchedPrimaryFunctionInspection::class.java,
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            object MyModule {
                @HandleEvent
                private fun onFoo(event: FooEvent) {}
            }
            """,
        )
        assertTrue(m.none { it.startsWith("Handler name") })
    }

    fun testDuplicatePrimaryFunctionWarns() {
        addStubs(SkyHanniStub.EVENT, SkyHanniStub.MODULE)
        event("FirstEvent", "onShared")
        val m = messages(
            DuplicatePrimaryFunctionInspection::class.java,
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.SkyHanniEvent
            import at.hannibal2.skyhanni.skyhannimodule.PrimaryFunction
            @PrimaryFunction("onShared")
            class SecondEvent : SkyHanniEvent()
            """,
        )
        assertTrue(m.contains(intentionText("primary.problem.duplicate", "onShared", "FirstEvent")))
    }

    fun testUniquePrimaryFunctionDoesNotWarn() {
        addStubs(SkyHanniStub.EVENT, SkyHanniStub.MODULE)
        event("FirstEvent", "onFirst")
        val m = messages(
            DuplicatePrimaryFunctionInspection::class.java,
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.SkyHanniEvent
            import at.hannibal2.skyhanni.skyhannimodule.PrimaryFunction
            @PrimaryFunction("onSecond")
            class SecondEvent : SkyHanniEvent()
            """,
        )
        assertTrue(m.none { it.startsWith("@PrimaryFunction") })
    }
}
