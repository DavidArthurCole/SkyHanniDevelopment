package skyhanni.plugin.areas.event

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration

/**
 * Tests for [buildPrimaryNameMap], [resolveEventClass], and [findHandlersForEvent].
 *
 * Each test builds a minimal SkyHanniEvent hierarchy in the PSI fixture so that the
 * index-backed searches used by these functions can find and walk the class graph.
 */
class EventUtilsTest : BasePlatformTestCase() {

    // -------------------------------------------------------------------------
    // Shared PSI scaffolding
    // -------------------------------------------------------------------------

    /** Adds the SkyHanniEvent base class and its companion annotations to the fixture. */
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

    /** Adds a concrete [SkyHanniEvent] subclass with [primaryName] as its @PrimaryFunction. */
    private fun addConcreteEvent(simpleName: String, primaryName: String): KtFile =
        myFixture.addFileToProject(
            "com/example/$simpleName.kt",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.SkyHanniEvent
            import at.hannibal2.skyhanni.api.event.PrimaryFunction
            @PrimaryFunction("$primaryName")
            class $simpleName : SkyHanniEvent()
            """.trimIndent()
        ) as KtFile

    /** Adds a concrete [SkyHanniEvent] subclass *without* a @PrimaryFunction annotation. */
    private fun addConcreteEventNoAnnotation(simpleName: String): KtFile =
        myFixture.addFileToProject(
            "com/example/$simpleName.kt",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.SkyHanniEvent
            class $simpleName : SkyHanniEvent()
            """.trimIndent()
        ) as KtFile

    /** Adds an *abstract* [SkyHanniEvent] subclass with a @PrimaryFunction (to confirm it's excluded). */
    private fun addAbstractEvent(simpleName: String, primaryName: String) {
        myFixture.addFileToProject(
            "com/example/$simpleName.kt",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.SkyHanniEvent
            import at.hannibal2.skyhanni.api.event.PrimaryFunction
            @PrimaryFunction("$primaryName")
            abstract class $simpleName : SkyHanniEvent()
            """.trimIndent()
        )
    }

    /** Adds an *object* [SkyHanniEvent] subclass with a @PrimaryFunction. */
    private fun addObjectEvent(simpleName: String, primaryName: String): KtFile =
        myFixture.addFileToProject(
            "com/example/$simpleName.kt",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.SkyHanniEvent
            import at.hannibal2.skyhanni.api.event.PrimaryFunction
            @PrimaryFunction("$primaryName")
            object $simpleName : SkyHanniEvent()
            """.trimIndent()
        ) as KtFile

    /** Extracts the named function from an object declaration in the given file. */
    private fun handlerFunction(file: KtFile, objectName: String, functionName: String): KtNamedFunction {
        val obj = file.declarations.filterIsInstance<KtObjectDeclaration>().first { it.name == objectName }
        return obj.declarations.filterIsInstance<KtNamedFunction>().first { it.name == functionName }
    }

    // -------------------------------------------------------------------------
    // buildPrimaryNameMap
    // -------------------------------------------------------------------------

    fun testBuildPrimaryNameMapIncludesConcreteAnnotatedEvent() {
        addEventBase()
        addConcreteEvent("FooEvent", "onFoo")

        val map = buildPrimaryNameMap(project)

        assertEquals("com.example.FooEvent", map["onFoo"])
    }

    fun testBuildPrimaryNameMapExcludesAbstractEvent() {
        addEventBase()
        addAbstractEvent("AbstractFooEvent", "onAbstractFoo")

        val map = buildPrimaryNameMap(project)

        assertNull(map["onAbstractFoo"])
    }

    fun testBuildPrimaryNameMapExcludesEventWithoutAnnotation() {
        addEventBase()
        addConcreteEventNoAnnotation("BareEvent")

        val map = buildPrimaryNameMap(project)

        assertFalse(map.values.contains("com.example.BareEvent"))
    }

    fun testBuildPrimaryNameMapIncludesObjectEvent() {
        addEventBase()
        addObjectEvent("DisconnectEvent", "onDisconnect")

        val map = buildPrimaryNameMap(project)

        assertEquals("com.example.DisconnectEvent", map["onDisconnect"])
    }

    fun testBuildPrimaryNameMapIncludesMultipleEvents() {
        addEventBase()
        addConcreteEvent("FooEvent", "onFoo")
        addConcreteEvent("BarEvent", "onBar")

        val map = buildPrimaryNameMap(project)

        assertEquals("com.example.FooEvent", map["onFoo"])
        assertEquals("com.example.BarEvent", map["onBar"])
    }

    // -------------------------------------------------------------------------
    // resolveEventClass
    // -------------------------------------------------------------------------

    fun testResolveEventClassViaParameterType() {
        addEventBase()
        addConcreteEvent("FooEvent", "onFoo")

        val handlerFile = myFixture.addFileToProject(
            "com/example/MyModule.kt",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            object MyModule {
                @HandleEvent
                fun onFoo(event: FooEvent) {}
            }
            """.trimIndent()
        ) as KtFile

        val fn = handlerFunction(handlerFile, "MyModule", "onFoo")
        val resolved = resolveEventClass(fn, project)

        assertEquals("FooEvent", resolved?.name)
    }

    fun testResolveEventClassViaExplicitEventType() {
        addEventBase()
        addConcreteEvent("FooEvent", "onFoo")

        val handlerFile = myFixture.addFileToProject(
            "com/example/MyModule.kt",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            object MyModule {
                @HandleEvent(eventType = FooEvent::class)
                fun handler() {}
            }
            """.trimIndent()
        ) as KtFile

        val fn = handlerFunction(handlerFile, "MyModule", "handler")
        val resolved = resolveEventClass(fn, project)

        assertEquals("FooEvent", resolved?.name)
    }

    fun testResolveEventClassViaReceiverType() {
        addEventBase()
        addConcreteEvent("FooEvent", "onFoo")

        val handlerFile = myFixture.addFileToProject(
            "com/example/MyModule.kt",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            object MyModule {
                @HandleEvent
                fun FooEvent.handler() {}
            }
            """.trimIndent()
        ) as KtFile

        val fn = handlerFunction(handlerFile, "MyModule", "handler")
        val resolved = resolveEventClass(fn, project)

        assertEquals("FooEvent", resolved?.name)
    }

    fun testResolveEventClassViaPrimaryFunctionName() {
        addEventBase()
        addConcreteEvent("FooEvent", "onFoo")

        val handlerFile = myFixture.addFileToProject(
            "com/example/MyModule.kt",
            """
            package com.example
            object MyModule {
                fun onFoo() {}
            }
            """.trimIndent()
        ) as KtFile

        val fn = handlerFunction(handlerFile, "MyModule", "onFoo")
        val resolved = resolveEventClass(fn, project)

        assertEquals("FooEvent", resolved?.name)
    }

    fun testResolveEventClassReturnsNullForUnrelatedFunction() {
        addEventBase()

        val file = myFixture.addFileToProject(
            "com/example/MyModule.kt",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            object MyModule {
                @HandleEvent
                fun doSomething() {}
            }
            """.trimIndent()
        ) as KtFile

        val fn = handlerFunction(file, "MyModule", "doSomething")
        val resolved = resolveEventClass(fn, project)

        assertNull(resolved)
    }

    // -------------------------------------------------------------------------
    // findHandlersForEvent
    // -------------------------------------------------------------------------

    private fun psiClassForEvent(eventSimpleName: String) =
        com.intellij.psi.JavaPsiFacade.getInstance(project)
            .findClass("com.example.$eventSimpleName", com.intellij.psi.search.GlobalSearchScope.allScope(project))!!

    fun testFindHandlersViaParameterType() {
        addEventBase()
        addConcreteEvent("FooEvent", "onFoo")
        myFixture.addFileToProject(
            "com/example/MyModule.kt",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            object MyModule {
                @HandleEvent
                fun onFoo(event: FooEvent) {}
            }
            """.trimIndent()
        )

        val handlers = findHandlersForEvent(psiClassForEvent("FooEvent"), project)

        assertEquals(1, handlers.size)
        assertEquals("onFoo", handlers.first().name)
    }

    fun testFindHandlersViaReceiverType() {
        addEventBase()
        addConcreteEvent("FooEvent", "onFoo")
        myFixture.addFileToProject(
            "com/example/MyModule.kt",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            object MyModule {
                @HandleEvent
                fun FooEvent.handler() {}
            }
            """.trimIndent()
        )

        val handlers = findHandlersForEvent(psiClassForEvent("FooEvent"), project)

        assertEquals(1, handlers.size)
        assertEquals("handler", handlers.first().name)
    }

    fun testFindHandlersViaExplicitEventType() {
        addEventBase()
        addConcreteEvent("FooEvent", "onFoo")
        myFixture.addFileToProject(
            "com/example/MyModule.kt",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            object MyModule {
                @HandleEvent(eventType = FooEvent::class)
                fun handler() {}
            }
            """.trimIndent()
        )

        val handlers = findHandlersForEvent(psiClassForEvent("FooEvent"), project)

        assertEquals(1, handlers.size)
        assertEquals("handler", handlers.first().name)
    }

    fun testFindHandlersViaPrimaryFunctionName() {
        addEventBase()
        addConcreteEvent("FooEvent", "onFoo")
        myFixture.addFileToProject(
            "com/example/MyModule.kt",
            """
            package com.example
            object MyModule {
                fun onFoo() {}
            }
            """.trimIndent()
        )

        val handlers = findHandlersForEvent(psiClassForEvent("FooEvent"), project)

        assertEquals(1, handlers.size)
        assertEquals("onFoo", handlers.first().name)
    }

    fun testFindHandlersReturnsEmptyWhenNoHandlers() {
        addEventBase()
        addConcreteEvent("FooEvent", "onFoo")

        val handlers = findHandlersForEvent(psiClassForEvent("FooEvent"), project)

        assertTrue(handlers.isEmpty())
    }

    fun testFindHandlersDeduplicatesMultipleMatchPaths() {
        // A function matching both parameter type AND primary function name should appear once.
        addEventBase()
        addConcreteEvent("FooEvent", "onFoo")
        myFixture.addFileToProject(
            "com/example/MyModule.kt",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            object MyModule {
                @HandleEvent
                fun onFoo(event: FooEvent) {}
            }
            """.trimIndent()
        )

        val handlers = findHandlersForEvent(psiClassForEvent("FooEvent"), project)

        assertEquals(1, handlers.size)
    }
}
