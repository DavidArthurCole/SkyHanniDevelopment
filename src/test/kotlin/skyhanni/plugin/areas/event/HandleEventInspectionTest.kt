package skyhanni.plugin.areas.event

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for [HandleEventInspection].
 *
 * Covers the three diagnostic messages the inspection can emit and the
 * visibility-inference fix for override functions whose visibility is
 * inherited from an internal abstract declaration.
 */
class HandleEventInspectionTest : BasePlatformTestCase() {

    private val MISSING_ANNOTATION = "Event handler function should be annotated with @HandleEvent"
    private val MUST_BE_PUBLIC = "Function must be public to be annotated with @HandleEvent"
    private val SHOULD_NOT_BE_ANNOTATED = "Function should not be annotated with @HandleEvent if it does not take a SkyHanniEvent"
    private val INSPECTION_MESSAGES = setOf(MISSING_ANNOTATION, MUST_BE_PUBLIC, SHOULD_NOT_BE_ANNOTATED)

    private fun addEventBase() {
        myFixture.addFileToProject(
            "at/hannibal2/skyhanni/api/event/SkyHanniEvent.kt",
            """
            package at.hannibal2.skyhanni.api.event
            import kotlin.reflect.KClass
            open class SkyHanniEvent
            annotation class HandleEvent(val eventType: KClass<*> = SkyHanniEvent::class)
            annotation class PrimaryFunction(val value: String)
            """.trimIndent()
        )
    }

    private fun addFooEvent() {
        myFixture.addFileToProject(
            "com/example/FooEvent.kt",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.SkyHanniEvent
            class FooEvent : SkyHanniEvent()
            """.trimIndent()
        )
    }

    /** Runs the inspection on [code] and returns only the messages this inspection can emit. */
    private fun inspect(code: String): List<String> {
        myFixture.enableInspections(HandleEventInspection::class.java)
        myFixture.configureByText("Test.kt", code.trimIndent())
        return myFixture.doHighlighting()
            .mapNotNull { it.description }
            .filter { it in INSPECTION_MESSAGES }
    }

    // ---- Override visibility fix ----

    fun testOverrideWithEventReceiverAndNoExplicitVisibilityDoesNotWarn() {
        // Core regression: override inherits `internal` from the abstract declaration.
        // PSI sees no explicit modifier and reports isPublic=true, but the function is
        // not truly public. The inspection must not flag it.
        addEventBase()
        addFooEvent()
        val warnings = inspect("""
            package com.example
            abstract class AbstractTracker<T> {
                internal abstract fun T.handle(): Boolean
            }
            object ConcreteTracker : AbstractTracker<FooEvent>() {
                override fun FooEvent.handle(): Boolean = true
            }
        """)
        assertFalse(MISSING_ANNOTATION in warnings)
    }

    fun testOverrideWithEventParamAndNoExplicitVisibilityDoesNotWarn() {
        addEventBase()
        addFooEvent()
        val warnings = inspect("""
            package com.example
            abstract class AbstractModule {
                internal abstract fun handle(event: FooEvent)
            }
            object ConcreteModule : AbstractModule() {
                override fun handle(event: FooEvent) {}
            }
        """)
        assertFalse(MISSING_ANNOTATION in warnings)
    }

    fun testExplicitPublicOverrideWithEventReceiverWarns() {
        // An override that explicitly declares `public` is truly public and should be flagged.
        addEventBase()
        addFooEvent()
        val warnings = inspect("""
            package com.example
            abstract class AbstractTracker<T> {
                abstract fun T.handle(): Boolean
            }
            object ConcreteTracker : AbstractTracker<FooEvent>() {
                public override fun FooEvent.handle(): Boolean = true
            }
        """)
        assertTrue(MISSING_ANNOTATION in warnings)
    }

    fun testExplicitPublicOverrideWithHandleEventDoesNotWarn() {
        addEventBase()
        addFooEvent()
        val warnings = inspect("""
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            abstract class AbstractTracker<T> {
                abstract fun T.handle(): Boolean
            }
            object ConcreteTracker : AbstractTracker<FooEvent>() {
                @HandleEvent
                public override fun FooEvent.handle(): Boolean = true
            }
        """)
        assertFalse(MISSING_ANNOTATION in warnings)
    }

    // ---- Standard non-override cases ----

    fun testPublicFunctionWithEventParamWarns() {
        addEventBase()
        addFooEvent()
        val warnings = inspect("""
            package com.example
            object MyModule {
                fun onFoo(event: FooEvent) {}
            }
        """)
        assertTrue(MISSING_ANNOTATION in warnings)
    }

    fun testPublicFunctionWithEventReceiverWarns() {
        addEventBase()
        addFooEvent()
        val warnings = inspect("""
            package com.example
            object MyModule {
                fun FooEvent.handle() {}
            }
        """)
        assertTrue(MISSING_ANNOTATION in warnings)
    }

    fun testAnnotatedFunctionDoesNotWarn() {
        addEventBase()
        addFooEvent()
        val warnings = inspect("""
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            object MyModule {
                @HandleEvent
                fun onFoo(event: FooEvent) {}
            }
        """)
        assertFalse(MISSING_ANNOTATION in warnings)
    }

    // ---- Visibility modifiers that suppress the warning ----

    fun testInternalFunctionWithEventParamDoesNotWarn() {
        addEventBase()
        addFooEvent()
        val warnings = inspect("""
            package com.example
            object MyModule {
                internal fun onFoo(event: FooEvent) {}
            }
        """)
        assertFalse(MISSING_ANNOTATION in warnings)
    }

    fun testPrivateFunctionWithEventParamDoesNotWarn() {
        addEventBase()
        addFooEvent()
        val warnings = inspect("""
            package com.example
            object MyModule {
                private fun onFoo(event: FooEvent) {}
            }
        """)
        assertFalse(MISSING_ANNOTATION in warnings)
    }

    fun testOpenFunctionWithEventParamDoesNotWarn() {
        addEventBase()
        addFooEvent()
        val warnings = inspect("""
            package com.example
            object MyModule {
                open fun onFoo(event: FooEvent) {}
            }
        """)
        assertFalse(MISSING_ANNOTATION in warnings)
    }

    // ---- @HandleEvent on non-public function ----

    fun testHandleEventWithExplicitEventTypeOnInternalFunctionWarns() {
        addEventBase()
        addFooEvent()
        val warnings = inspect("""
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            object MyModule {
                @HandleEvent(eventType = FooEvent::class)
                internal fun doSomething() {}
            }
        """)
        assertTrue(MUST_BE_PUBLIC in warnings)
    }

    // ---- @HandleEvent on function that takes no event ----

    fun testHandleEventOnNonEventFunctionWarns() {
        addEventBase()
        val warnings = inspect("""
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            object MyModule {
                @HandleEvent
                fun doSomething(x: String) {}
            }
        """)
        assertTrue(SHOULD_NOT_BE_ANNOTATED in warnings)
    }
}
