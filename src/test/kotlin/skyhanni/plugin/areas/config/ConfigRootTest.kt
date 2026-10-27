package skyhanni.plugin.areas.config

import org.junit.Assert.assertEquals
import org.junit.Test
import skyhanni.plugin.api.SkyHanniIds

class ConfigRootTest {

    @Test
    fun regularPathReturnsBaseConfigClassUnchanged() {
        val list = mutableListOf("inventory", "items")
        assertEquals(SkyHanniIds.SKYHANNI_CONFIG, rootClassId(list))
        assertEquals(listOf("inventory", "items"), list)
    }

    @Test
    fun profilePrefixReturnsProfileStorageAndRemovesPrefix() {
        val list = mutableListOf("#profile", "foo")
        assertEquals(SkyHanniIds.PROFILE_STORAGE, rootClassId(list))
        assertEquals(listOf("foo"), list)
    }

    @Test
    fun playerPrefixReturnsPlayerStorageAndRemovesPrefix() {
        val list = mutableListOf("#player", "bar")
        assertEquals(SkyHanniIds.PLAYER_STORAGE, rootClassId(list))
        assertEquals(listOf("bar"), list)
    }

    @Test
    fun prefixWithMultipleSegmentsRemovesOnlyFirstElement() {
        val list = mutableListOf("#profile", "a", "b", "c")
        assertEquals(SkyHanniIds.PROFILE_STORAGE, rootClassId(list))
        assertEquals(listOf("a", "b", "c"), list)
    }
}
