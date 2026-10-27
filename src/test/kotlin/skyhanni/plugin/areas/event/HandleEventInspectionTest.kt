package skyhanni.plugin.areas.event

import skyhanni.plugin.SkyHanniStub
import skyhanni.plugin.SkyHanniTestCase

class HandleEventInspectionTest : SkyHanniTestCase() {

    private val seemsToHandle = "Function seems to handle"
    private val nameMatchesPrimary = "Function name matches @PrimaryFunction of"
    private val redundant = intentionText("event.problem.redundantHandleEvent")

    private fun addFooEvent() {
        myFixture.addFileToProject(
            "com/example/FooEvent.kt",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.SkyHanniEvent
            class FooEvent : SkyHanniEvent()
            """.trimIndent(),
        )
    }

    private fun addPrimaryEvent() {
        myFixture.addFileToProject(
            "com/example/FooPrimaryEvent.kt",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.SkyHanniEvent
            import at.hannibal2.skyhanni.skyhannimodule.PrimaryFunction
            @PrimaryFunction("onFooPrimary")
            class FooPrimaryEvent : SkyHanniEvent()
            """.trimIndent(),
        )
    }

    private fun messages(code: String): List<String> {
        addStubs(SkyHanniStub.EVENT, SkyHanniStub.MODULE)
        return inspect(HandleEventInspection::class.java, "Test.kt", code).mapNotNull { it.description }
    }

    fun testPublicFunctionWithEventParamWarns() {
        addFooEvent()
        val m = messages(
            """
            package com.example
            object MyModule {
                fun onFoo(event: FooEvent) {}
            }
            """,
        )
        assertTrue(m.any { it.startsWith(seemsToHandle) })
    }

    fun testPublicFunctionWithEventReceiverWarns() {
        addFooEvent()
        val m = messages(
            """
            package com.example
            object MyModule {
                fun FooEvent.handle() {}
            }
            """,
        )
        assertTrue(m.any { it.startsWith(seemsToHandle) })
    }

    fun testPrivateFunctionWithEventReceiverStillWarnsMissingAnnotation() {
        addFooEvent()
        val m = messages(
            """
            package com.example
            object MyModule {
                private fun FooEvent.handle() {}
            }
            """,
        )
        assertFalse(m.any { it.startsWith(seemsToHandle) })
    }

    fun testAnnotatedFunctionDoesNotWarn() {
        addFooEvent()
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
        assertFalse(m.any { it.startsWith(seemsToHandle) })
    }

    fun testOpenFunctionDoesNotWarn() {
        addFooEvent()
        val m = messages(
            """
            package com.example
            object MyModule {
                open fun onFoo(event: FooEvent) {}
            }
            """,
        )
        assertFalse(m.any { it.startsWith(seemsToHandle) })
    }

    fun testPrimaryFunctionNameWithoutAnnotationWarns() {
        addPrimaryEvent()
        val m = messages(
            """
            package com.example
            object MyModule {
                fun onFooPrimary() {}
            }
            """,
        )
        assertTrue(m.any { it.startsWith(nameMatchesPrimary) })
    }

    fun testPrimaryFunctionNameWithAnnotationDoesNotWarn() {
        addPrimaryEvent()
        val m = messages(
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            object MyModule {
                @HandleEvent
                private fun onFooPrimary() {}
            }
            """,
        )
        assertFalse(m.any { it.startsWith(nameMatchesPrimary) })
    }

    fun testHandleEventOnNonEventFunctionWarns() {
        val m = messages(
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            object MyModule {
                @HandleEvent
                private fun doSomething(x: String) {}
            }
            """,
        )
        assertTrue(redundant in m)
    }

    fun testHandleEventOnRealEventFunctionDoesNotWarnRedundant() {
        addFooEvent()
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
        assertFalse(redundant in m)
    }
}
