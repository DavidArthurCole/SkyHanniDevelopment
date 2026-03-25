package skyhanni.plugin.areas.config

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests for [getRootClassName]. No IntelliJ Platform infrastructure is required. */
class ConfigUtilsPureTest {

    @Test
    fun regularPathReturnsBaseConfigClassUnchanged() {
        val list = mutableListOf("inventory", "items")
        assertEquals(BASE_CONFIG_CLASS, list.getRootClassName())
        assertEquals(listOf("inventory", "items"), list)
    }

    @Test
    fun profilePrefixReturnsProfileStorageClassAndRemovesPrefix() {
        val list = mutableListOf("#profile", "foo")
        assertEquals(PROFILE_STORAGE_CLASS, list.getRootClassName())
        assertEquals(listOf("foo"), list)
    }

    @Test
    fun playerPrefixReturnsPlayerStorageClassAndRemovesPrefix() {
        val list = mutableListOf("#player", "bar")
        assertEquals(PLAYER_STORAGE_CLASS, list.getRootClassName())
        assertEquals(listOf("bar"), list)
    }

    @Test
    fun singleElementListReturnsBaseConfigClassUnchanged() {
        val list = mutableListOf("solo")
        assertEquals(BASE_CONFIG_CLASS, list.getRootClassName())
        assertEquals(listOf("solo"), list)
    }

    @Test
    fun prefixWithMultipleSegmentsRemovesOnlyFirstElement() {
        val list = mutableListOf("#profile", "a", "b", "c")
        assertEquals(PROFILE_STORAGE_CLASS, list.getRootClassName())
        assertEquals(listOf("a", "b", "c"), list)
    }
}
