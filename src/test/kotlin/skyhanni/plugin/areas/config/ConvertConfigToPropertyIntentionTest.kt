package skyhanni.plugin.areas.config

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty

/** Tests for [ConvertConfigToPropertyIntention.isApplicableTo]. */
class ConvertConfigToPropertyIntentionTest : BasePlatformTestCase() {

    private val intention = ConvertConfigToPropertyIntention()

    fun testApplicableToVarWithAnnotationTypeAndInitializer() {
        assertTrue(intention.isApplicableTo(property("var count: Int = 0")))
    }

    fun testNotApplicableToVal() {
        assertFalse(intention.isApplicableTo(property("val count: Int = 0")))
    }

    fun testNotApplicableWithoutAnnotation() {
        assertFalse(intention.isApplicableTo(property("var count: Int = 0", annotated = false)))
    }

    /** A property with an inferred type has no explicit [org.jetbrains.kotlin.psi.KtTypeReference]. */
    fun testNotApplicableWithInferredType() {
        assertFalse(intention.isApplicableTo(property("var count = 0")))
    }

    fun testNotApplicableWithoutInitializer() {
        assertFalse(intention.isApplicableTo(property("var count: Int")))
    }

    fun testNotApplicableInAbstractClass() {
        assertFalse(intention.isApplicableTo(property("var count: Int = 0", abstractClass = true)))
    }

    fun testNotApplicableWithUnrelatedAnnotation() {
        assertFalse(intention.isApplicableTo(property("var count: Int = 0", annotationName = "OtherOption")))
    }

    /**
     * Builds a [KtProperty] inside a synthetic config class. [annotated] controls whether
     * the property carries an annotation, [annotationName] overrides which annotation is used,
     * and [abstractClass] wraps it in an abstract class.
     */
    private fun property(
        declaration: String,
        annotated: Boolean = true,
        annotationName: String = CONFIG_OPTION_ANNOTATION,
        abstractClass: Boolean = false,
    ): KtProperty {
        val classModifier = if (abstractClass) "abstract " else ""
        val annotation = if (annotated) "@$annotationName\n                " else ""
        val file = myFixture.configureByText(
            "Config.kt",
            """
            annotation class $annotationName
            ${classModifier}class MyConfig {
                $annotation$declaration
            }
            """.trimIndent()
        ) as KtFile

        return (file.declarations.first { it.name == "MyConfig" } as KtClass)
            .declarations.filterIsInstance<KtProperty>()
            .first()
    }
}
