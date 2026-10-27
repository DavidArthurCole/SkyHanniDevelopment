package skyhanni.plugin.areas.config

import skyhanni.plugin.SkyHanniStub
import skyhanni.plugin.SkyHanniTestCase

class ConvertConfigToPropertyIntentionTest : SkyHanniTestCase() {

    private val familyName = intentionText("config.intention.convertToProperty")

    private fun available(declaration: String, annotated: Boolean = true, abstractClass: Boolean = false): Boolean {
        addStubs(SkyHanniStub.MOULCONFIG)
        val annotation = if (annotated) "@ConfigOption(name = \"\", desc = \"\")\n    " else ""
        val modifier = if (abstractClass) "abstract " else ""
        myFixture.configureByText(
            "Config.kt",
            """
            import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
            ${modifier}class MyConfig {
                $annotation$declaration<caret>
            }
            """.trimIndent(),
        )
        return myFixture.availableIntentions.any { it.text == familyName }
    }

    fun testApplicableToVarWithAnnotationTypeAndInitializer() {
        assertTrue(available("var count: Int = 0"))
    }

    fun testNotApplicableToVal() {
        assertFalse(available("val count: Int = 0"))
    }

    fun testNotApplicableWithoutAnnotation() {
        assertFalse(available("var count: Int = 0", annotated = false))
    }

    fun testNotApplicableWithInferredType() {
        assertFalse(available("var count = 0"))
    }

    fun testNotApplicableWithoutInitializer() {
        assertFalse(available("var count: Int"))
    }

    fun testNotApplicableInAbstractClass() {
        assertFalse(available("var count: Int = 0", abstractClass = true))
    }
}
