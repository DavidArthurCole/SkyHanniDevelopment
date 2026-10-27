package skyhanni.plugin.areas.config

import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import skyhanni.plugin.SkyHanniStub
import skyhanni.plugin.SkyHanniTestCase

class ComputeConfigPathTest : SkyHanniTestCase() {

    fun testTwoLevelHierarchyBuildsCorrectPath() {
        addStubs(SkyHanniStub.MOULCONFIG)
        myFixture.addFileToProject(
            "at/hannibal2/skyhanni/config/Features.kt",
            """
            package at.hannibal2.skyhanni.config
            class Features {
                val inventory: InventoryConfig = InventoryConfig()
            }
            """.trimIndent(),
        )
        val child = myFixture.addFileToProject(
            "at/hannibal2/skyhanni/config/InventoryConfig.kt",
            """
            package at.hannibal2.skyhanni.config
            import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
            class InventoryConfig {
                @ConfigOption(name = "", desc = "")
                var slotCount: Int = 0
            }
            """.trimIndent(),
        ) as KtFile
        assertEquals("inventory.slotCount", computeConfigPath(property(child, "InventoryConfig", "slotCount")))
    }

    fun testAbstractClassReturnsNull() {
        addStubs(SkyHanniStub.MOULCONFIG)
        val file = myFixture.configureByText(
            "AbstractConfig.kt",
            """
            package at.hannibal2.skyhanni.config
            import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
            abstract class AbstractConfig {
                @ConfigOption(name = "", desc = "")
                var someField: Int = 0
            }
            """.trimIndent(),
        ) as KtFile
        assertNull(computeConfigPath(property(file, "AbstractConfig", "someField")))
    }

    fun testProfileStorageRootTerminatesWalk() {
        addStubs(SkyHanniStub.MOULCONFIG)
        myFixture.addFileToProject(
            "at/hannibal2/skyhanni/config/storage/ProfileSpecificStorage.kt",
            """
            package at.hannibal2.skyhanni.config.storage
            import at.hannibal2.skyhanni.config.ProfileConfig
            class ProfileSpecificStorage {
                val profile: ProfileConfig = ProfileConfig()
            }
            """.trimIndent(),
        )
        val child = myFixture.addFileToProject(
            "at/hannibal2/skyhanni/config/ProfileConfig.kt",
            """
            package at.hannibal2.skyhanni.config
            import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
            class ProfileConfig {
                @ConfigOption(name = "", desc = "")
                var xp: Int = 0
            }
            """.trimIndent(),
        ) as KtFile
        assertEquals("profile.xp", computeConfigPath(property(child, "ProfileConfig", "xp")))
    }

    private fun property(file: KtFile, className: String, name: String): KtProperty =
        (file.declarations.first { it.name == className } as KtClass)
            .declarations.filterIsInstance<KtProperty>().first { it.name == name }
}
