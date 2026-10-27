package skyhanni.plugin.areas.config

import org.jetbrains.kotlin.psi.KtProperty
import skyhanni.plugin.SkyHanniStub
import skyhanni.plugin.SkyHanniTestCase

class ConfigIntentionsTest : SkyHanniTestCase() {

    private fun configTree() {
        addStubs(SkyHanniStub.MOULCONFIG, SkyHanniStub.CONFIG_MIGRATOR)
        myFixture.addFileToProject(
            "at/hannibal2/skyhanni/config/SkyHanniConfig.kt",
            """
            package at.hannibal2.skyhanni.config
            class SkyHanniConfig {
                val inventory: InventoryConfig = InventoryConfig()
            }
            """.trimIndent(),
        )
    }

    private fun inventoryConfig(caretOnProperty: Boolean = true) = myFixture.configureByText(
        "InventoryConfig.kt",
        """
        package at.hannibal2.skyhanni.config
        import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
        class InventoryConfig {
            @ConfigOption(name = "", desc = "")
            var ${if (caretOnProperty) "slot<caret>Count" else "slotCount"}: Int = 0
        }
        """.trimIndent(),
    )

    private fun intentionTexts() = myFixture.availableIntentions.map { it.text }

    fun testCreateMigrationIsAvailableOnConfigOption() {
        configTree()
        inventoryConfig()
        assertTrue(intentionText("config.intention.createMigration") in intentionTexts())
    }

    fun testCopyPathFixIsAvailableOnConfigOption() {
        configTree()
        myFixture.enableInspections(CopyConfigPathInspection::class.java)
        inventoryConfig()
        assertTrue(intentionText("config.fix.copyPath") in intentionTexts())
    }

    fun testPathStringResolvesToProperty() {
        configTree()
        myFixture.addFileToProject(
            "at/hannibal2/skyhanni/config/InventoryConfig.kt",
            """
            package at.hannibal2.skyhanni.config
            import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
            class InventoryConfig {
                @ConfigOption(name = "", desc = "")
                var slotCount: Int = 0
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Migration.kt",
            """
            import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
            fun fix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
                event.move(1, "inventory.slot<caret>Count", "inventory.other")
            }
            """.trimIndent(),
        )
        val target = myFixture.getReferenceAtCaretPositionWithAssertion().resolve()
        assertEquals("slotCount", (target as? KtProperty)?.name)
        assertTrue(intentionText("config.intention.navigate") in intentionTexts())
    }

    fun testUnrelatedStringHasNoConfigReference() {
        configTree()
        myFixture.configureByText(
            "Other.kt",
            """
            fun f() { println("inventory.slot<caret>Count") }
            """.trimIndent(),
        )
        assertFalse(myFixture.getReferenceAtCaretPosition()?.resolve() is KtProperty)
        assertFalse(intentionText("config.intention.navigate") in intentionTexts())
    }
}
