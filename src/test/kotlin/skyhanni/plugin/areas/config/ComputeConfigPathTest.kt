package skyhanni.plugin.areas.config

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty

/**
 * Tests for [computeConfigPath].
 *
 * Each test builds a minimal config class hierarchy that mirrors the real SkyHanni
 * package structure so that [computeConfigPath] can walk the reference chain through
 * the PSI index.
 */
class ComputeConfigPathTest : BasePlatformTestCase() {

    fun testTwoLevelHierarchyBuildsCorrectPath() {
        // Features.inventory -> InventoryConfig.slotCount => "inventory.slotCount"
        addFeaturesRoot("val inventory: InventoryConfig = InventoryConfig()")

        val childFile = myFixture.addFileToProject(
            "at/hannibal2/skyhanni/config/InventoryConfig.kt",
            """
            package at.hannibal2.skyhanni.config
            annotation class ConfigOption
            class InventoryConfig {
                @ConfigOption
                var slotCount: Int = 0
            }
            """.trimIndent()
        ) as KtFile

        assertEquals("inventory.slotCount", computeConfigPath(property(childFile, "InventoryConfig", "slotCount")))
    }

    fun testAbstractClassReturnsNull() {
        val ktFile = myFixture.configureByText(
            "AbstractConfig.kt",
            """
            package at.hannibal2.skyhanni.config
            annotation class ConfigOption
            abstract class AbstractConfig {
                @ConfigOption
                var someField: Int = 0
            }
            """.trimIndent()
        ) as KtFile

        assertNull(computeConfigPath(property(ktFile, "AbstractConfig", "someField")))
    }

    fun testProfileStorageRootTerminatesWalk() {
        // ProfileSpecificStorage is a recognized root FQN, so the walk stops there.
        myFixture.addFileToProject(
            "at/hannibal2/skyhanni/config/storage/ProfileSpecificStorage.kt",
            """
            package at.hannibal2.skyhanni.config.storage
            import at.hannibal2.skyhanni.config.ProfileConfig
            class ProfileSpecificStorage {
                val profile: ProfileConfig = ProfileConfig()
            }
            """.trimIndent()
        )

        val childFile = myFixture.addFileToProject(
            "at/hannibal2/skyhanni/config/ProfileConfig.kt",
            """
            package at.hannibal2.skyhanni.config
            annotation class ConfigOption
            class ProfileConfig {
                @ConfigOption
                var xp: Int = 0
            }
            """.trimIndent()
        ) as KtFile

        assertEquals("profile.xp", computeConfigPath(property(childFile, "ProfileConfig", "xp")))
    }

    private fun addFeaturesRoot(body: String) {
        myFixture.addFileToProject(
            "at/hannibal2/skyhanni/config/Features.kt",
            """
            package at.hannibal2.skyhanni.config
            class Features {
                $body
            }
            """.trimIndent()
        )
    }

    private fun property(file: KtFile, className: String, propName: String): KtProperty =
        (file.declarations.first { it.name == className } as KtClass)
            .declarations.filterIsInstance<KtProperty>()
            .first { it.name == propName }
}
