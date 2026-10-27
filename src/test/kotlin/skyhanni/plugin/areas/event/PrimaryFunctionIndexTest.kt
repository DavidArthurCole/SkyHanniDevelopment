package skyhanni.plugin.areas.event

import skyhanni.plugin.SkyHanniStub
import skyhanni.plugin.SkyHanniTestCase

class PrimaryFunctionIndexTest : SkyHanniTestCase() {

    private fun concreteEvent(name: String, primaryName: String) {
        myFixture.addFileToProject(
            "com/example/$name.kt",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.SkyHanniEvent
            import at.hannibal2.skyhanni.skyhannimodule.PrimaryFunction
            @PrimaryFunction("$primaryName")
            class $name : SkyHanniEvent()
            """.trimIndent(),
        )
    }

    fun testIncludesConcreteAnnotatedEvent() {
        addStubs(SkyHanniStub.EVENT, SkyHanniStub.MODULE)
        concreteEvent("FooEvent", "onFoo")
        assertEquals("com.example.FooEvent", PrimaryFunctionIndex.get(project)["onFoo"]?.asFqNameString())
    }

    fun testExcludesAbstractEvent() {
        addStubs(SkyHanniStub.EVENT, SkyHanniStub.MODULE)
        myFixture.addFileToProject(
            "com/example/AbstractFooEvent.kt",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.SkyHanniEvent
            import at.hannibal2.skyhanni.skyhannimodule.PrimaryFunction
            @PrimaryFunction("onAbstractFoo")
            abstract class AbstractFooEvent : SkyHanniEvent()
            """.trimIndent(),
        )
        assertNull(PrimaryFunctionIndex.get(project)["onAbstractFoo"])
    }

    fun testExcludesEventWithoutAnnotation() {
        addStubs(SkyHanniStub.EVENT, SkyHanniStub.MODULE)
        myFixture.addFileToProject(
            "com/example/BareEvent.kt",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.SkyHanniEvent
            class BareEvent : SkyHanniEvent()
            """.trimIndent(),
        )
        assertTrue(PrimaryFunctionIndex.get(project).values.none { it.asFqNameString() == "com.example.BareEvent" })
    }

    fun testIncludesObjectEvent() {
        addStubs(SkyHanniStub.EVENT, SkyHanniStub.MODULE)
        myFixture.addFileToProject(
            "com/example/DisconnectEvent.kt",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.SkyHanniEvent
            import at.hannibal2.skyhanni.skyhannimodule.PrimaryFunction
            @PrimaryFunction("onDisconnect")
            object DisconnectEvent : SkyHanniEvent()
            """.trimIndent(),
        )
        assertEquals("com.example.DisconnectEvent", PrimaryFunctionIndex.get(project)["onDisconnect"]?.asFqNameString())
    }

    fun testIncludesMultipleEvents() {
        addStubs(SkyHanniStub.EVENT, SkyHanniStub.MODULE)
        concreteEvent("FooEvent", "onFoo")
        concreteEvent("BarEvent", "onBar")
        val map = PrimaryFunctionIndex.get(project)
        assertEquals("com.example.FooEvent", map["onFoo"]?.asFqNameString())
        assertEquals("com.example.BarEvent", map["onBar"]?.asFqNameString())
    }

    fun testAllForReportsDuplicates() {
        addStubs(SkyHanniStub.EVENT, SkyHanniStub.MODULE)
        concreteEvent("FooEvent", "onShared")
        concreteEvent("BarEvent", "onShared")
        assertEquals(2, PrimaryFunctionIndex.allFor(project, "onShared").size)
    }
}
