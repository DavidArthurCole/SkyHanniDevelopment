package skyhanni.plugin.areas.mixin

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import skyhanni.plugin.SkyHanniStub
import skyhanni.plugin.SkyHanniTestCase

class MixinImplicitUsageProviderTest : SkyHanniTestCase() {

    private val provider = MixinImplicitUsageProvider()
    private val suppressor = MixinInspectionSuppressor()

    private fun javaClass(fqn: String): PsiClass =
        JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project))!!

    override fun setUp() {
        super.setUp()
        addStubs(SkyHanniStub.MIXIN)
        myFixture.addFileToProject(
            "com/example/mixins/MixinTarget.java",
            """
            package com.example.mixins;

            import org.spongepowered.asm.mixin.Mixin;
            import org.spongepowered.asm.mixin.Shadow;
            import org.spongepowered.asm.mixin.injection.Inject;
            import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

            @Mixin
            public class MixinTarget {
                @Shadow
                private int shadowed;

                @Inject(method = "tick")
                private void onTick(Object info) {}

                @WrapOperation(method = "tick")
                private void wrap(Object operation) {}

                private void helper(Object value) {}
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "com/example/Plain.java",
            """
            package com.example;

            import org.spongepowered.asm.mixin.injection.Inject;

            public class Plain {
                @Inject(method = "tick")
                private void onTick(Object info) {}
            }
            """.trimIndent(),
        )
    }

    fun testMixinClassIsImplicitlyUsed() {
        assertTrue(provider.isImplicitUsage(javaClass("com.example.mixins.MixinTarget")))
    }

    fun testInjectorsAndParametersAreImplicitlyUsed() {
        val mixin = javaClass("com.example.mixins.MixinTarget")
        for (name in listOf("onTick", "wrap")) {
            val method = mixin.findMethodsByName(name, false).single()
            assertTrue(provider.isImplicitUsage(method))
            assertTrue(provider.isImplicitUsage(method.parameterList.parameters.single()))
            assertTrue(suppressor.isSuppressedFor(method.parameterList.parameters.single(), "unused"))
        }
    }

    fun testShadowFieldIsUsedAndWritten() {
        val field = javaClass("com.example.mixins.MixinTarget").findFieldByName("shadowed", false)!!
        assertTrue(provider.isImplicitUsage(field))
        assertTrue(provider.isImplicitWrite(field))
    }

    fun testHelperIsNotImplicitlyUsed() {
        val helper = javaClass("com.example.mixins.MixinTarget").findMethodsByName("helper", false).single()
        assertFalse(provider.isImplicitUsage(helper))
        assertFalse(suppressor.isSuppressedFor(helper.parameterList.parameters.single(), "unused"))
    }

    fun testInjectOutsideMixinIsNotImplicitlyUsed() {
        val method = javaClass("com.example.Plain").findMethodsByName("onTick", false).single()
        assertFalse(provider.isImplicitUsage(method))
    }
}
