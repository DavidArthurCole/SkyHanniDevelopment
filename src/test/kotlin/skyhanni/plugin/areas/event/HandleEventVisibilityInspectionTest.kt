package skyhanni.plugin.areas.event

import skyhanni.plugin.SkyHanniStub
import skyhanni.plugin.SkyHanniTestCase

class HandleEventVisibilityInspectionTest : SkyHanniTestCase() {

    private val shouldBePrivate = intentionText("event.problem.shouldBePrivate")

    private fun messages(code: String): List<String> {
        addStubs(SkyHanniStub.EVENT, SkyHanniStub.MODULE)
        myFixture.addFileToProject(
            "com/example/FooEvent.kt",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.SkyHanniEvent
            class FooEvent : SkyHanniEvent()
            """.trimIndent(),
        )
        return inspect(HandleEventVisibilityInspection::class.java, "Test.kt", code).mapNotNull { it.description }
    }

    fun testPublicHandlerWarns() {
        val m = messages(
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            object MyModule {
                @HandleEvent
                fun onFoo(event: FooEvent) {}
            }
            """,
        )
        assertTrue(shouldBePrivate in m)
    }

    fun testInternalHandlerWarns() {
        val m = messages(
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            object MyModule {
                @HandleEvent(eventType = FooEvent::class)
                internal fun doSomething() {}
            }
            """,
        )
        assertTrue(shouldBePrivate in m)
    }

    fun testPrivateHandlerDoesNotWarn() {
        val m = messages(
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            object MyModule {
                @HandleEvent
                private fun onFoo(event: FooEvent) {}
            }
            """,
        )
        assertFalse(shouldBePrivate in m)
    }

    fun testOverrideDoesNotWarn() {
        val m = messages(
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            abstract class AbstractModule {
                open fun handle(event: FooEvent) {}
            }
            object ConcreteModule : AbstractModule() {
                @HandleEvent
                override fun handle(event: FooEvent) {}
            }
            """,
        )
        assertFalse(shouldBePrivate in m)
    }

    fun testPublicFunctionWithoutAnnotationDoesNotWarn() {
        val m = messages(
            """
            package com.example
            object MyModule {
                fun onFoo(event: FooEvent) {}
            }
            """,
        )
        assertFalse(shouldBePrivate in m)
    }
}
