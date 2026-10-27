package skyhanni.plugin.areas.event

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import skyhanni.plugin.SkyHanniStub
import skyhanni.plugin.SkyHanniTestCase
import skyhanni.plugin.api.findKtClass

class EventResolutionTest : SkyHanniTestCase() {

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

    private fun handler(objectName: String, function: String, module: String): KtNamedFunction {
        val file = myFixture.addFileToProject("com/example/$objectName.kt", module.trimIndent()) as KtFile
        val obj = file.declarations.filterIsInstance<KtObjectDeclaration>().first { it.name == objectName }
        return obj.declarations.filterIsInstance<KtNamedFunction>().first { it.name == function }
    }

    fun testResolveViaParameterType() {
        addStubs(SkyHanniStub.EVENT, SkyHanniStub.MODULE)
        concreteEvent("FooEvent", "onFoo")
        val fn = handler(
            "MyModule", "onFoo",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            object MyModule {
                @HandleEvent
                private fun onFoo(event: FooEvent) {}
            }
            """,
        )
        assertEquals("FooEvent", resolveEventClass(fn)?.name)
    }

    fun testResolveViaExplicitEventType() {
        addStubs(SkyHanniStub.EVENT, SkyHanniStub.MODULE)
        concreteEvent("FooEvent", "onFoo")
        val fn = handler(
            "MyModule", "handler",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            object MyModule {
                @HandleEvent(eventType = FooEvent::class)
                private fun handler() {}
            }
            """,
        )
        assertEquals("FooEvent", resolveEventClass(fn)?.name)
    }

    fun testResolveViaReceiverType() {
        addStubs(SkyHanniStub.EVENT, SkyHanniStub.MODULE)
        concreteEvent("FooEvent", "onFoo")
        val fn = handler(
            "MyModule", "handler",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            object MyModule {
                @HandleEvent
                private fun FooEvent.handler() {}
            }
            """,
        )
        assertEquals("FooEvent", resolveEventClass(fn)?.name)
    }

    fun testResolveViaPrimaryFunctionNameOnlyWithHandleEvent() {
        addStubs(SkyHanniStub.EVENT, SkyHanniStub.MODULE)
        concreteEvent("FooEvent", "onFoo")
        val fn = handler(
            "MyModule", "onFoo",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            object MyModule {
                @HandleEvent
                private fun onFoo() {}
            }
            """,
        )
        assertEquals("FooEvent", resolveEventClass(fn)?.name)
    }

    fun testBareNameWithoutHandleEventDoesNotResolve() {
        addStubs(SkyHanniStub.EVENT, SkyHanniStub.MODULE)
        concreteEvent("FooEvent", "onFoo")
        val fn = handler(
            "MyModule", "onFoo",
            """
            package com.example
            object MyModule {
                fun onFoo() {}
            }
            """,
        )
        assertNull(resolveEventClass(fn))
    }

    fun testUnrelatedFunctionDoesNotResolve() {
        addStubs(SkyHanniStub.EVENT, SkyHanniStub.MODULE)
        val fn = handler(
            "MyModule", "doSomething",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            object MyModule {
                @HandleEvent
                private fun doSomething() {}
            }
            """,
        )
        assertNull(resolveEventClass(fn))
    }

    fun testFindHandlersViaParameterType() {
        addStubs(SkyHanniStub.EVENT, SkyHanniStub.MODULE)
        concreteEvent("FooEvent", "onFoo")
        handler(
            "MyModule", "onFoo",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            object MyModule {
                @HandleEvent
                private fun onFoo(event: FooEvent) {}
            }
            """,
        )
        val event = eventClass("FooEvent")
        val handlers = findHandlersForEvent(event)
        assertEquals(1, handlers.size)
        assertEquals("onFoo", handlers.first().name)
    }

    fun testFindHandlersEmptyWhenNone() {
        addStubs(SkyHanniStub.EVENT, SkyHanniStub.MODULE)
        concreteEvent("FooEvent", "onFoo")
        assertTrue(findHandlersForEvent(eventClass("FooEvent")).isEmpty())
    }

    fun testFindHandlersDeduplicates() {
        addStubs(SkyHanniStub.EVENT, SkyHanniStub.MODULE)
        concreteEvent("FooEvent", "onFoo")
        handler(
            "MyModule", "onFoo",
            """
            package com.example
            import at.hannibal2.skyhanni.api.event.HandleEvent
            object MyModule {
                @HandleEvent
                private fun onFoo(event: FooEvent) {}
            }
            """,
        )
        assertEquals(1, findHandlersForEvent(eventClass("FooEvent")).size)
    }

    private fun eventClass(name: String): KtClassOrObject =
        ClassId.topLevel(FqName("com.example.$name")).findKtClass(project)!!
}
