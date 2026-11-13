package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceOffsetsTest {

    @Test
    fun `offsetKey upper cases the address and keeps the prefix`() {
        val key = DeviceOffsets.offsetKey("00:11:22:aa:bb:cc")
        assertEquals("bt_offset_00:11:22:AA:BB:CC", key)
    }

    @Test
    fun `resolve returns 0 for a null address`() {
        val stored = mapOf("00:11:22:AA:BB:CC" to 150)
        val result = DeviceOffsets.resolve(null, stored)
        assertEquals(0, result)
    }

    @Test
    fun `resolve returns 0 for a blank address`() {
        val stored = mapOf("00:11:22:AA:BB:CC" to 150)
        val result = DeviceOffsets.resolve("   ", stored)
        assertEquals(0, result)
    }

    @Test
    fun `resolve returns 0 when the address is not in the stored map`() {
        val stored = mapOf("00:11:22:AA:BB:CC" to 150)
        val result = DeviceOffsets.resolve("11:22:33:44:55:66", stored)
        assertEquals(0, result)
    }

    @Test
    fun `resolve returns the stored value when the address matches`() {
        val stored = mapOf("00:11:22:AA:BB:CC" to 150)
        val result = DeviceOffsets.resolve("00:11:22:AA:BB:CC", stored)
        assertEquals(150, result)
    }

    @Test
    fun `resolve matches when the stored key and the active address differ in case`() {
        val stored = mapOf("00:11:22:AA:BB:CC" to 150)
        val result = DeviceOffsets.resolve("00:11:22:aa:bb:cc", stored)
        assertEquals(150, result)
    }

    @Test
    fun `resolve returns a stored negative value unchanged, since a negative offset is legal here`() {
        val stored = mapOf("00:11:22:AA:BB:CC" to -350)
        val result = DeviceOffsets.resolve("00:11:22:AA:BB:CC", stored)
        assertEquals(-350, result)
    }

    @Test
    fun `connectedFirst puts the active device on top, then the other connected ones, then the rest`() {
        val devices = listOf("AA", "BB", "cc", "DD", "EE")
        assertEquals(
            listOf("cc", "BB", "EE", "AA", "DD"),
            DeviceOffsets.connectedFirst(devices, "CC", setOf("CC", "BB", "EE")) { it }
        )
    }

    @Test
    fun `connectedFirst leaves the order alone when nothing is connected or the active one is not paired`() {
        val devices = listOf("AA", "BB")
        assertEquals(devices, DeviceOffsets.connectedFirst(devices, null, emptySet()) { it })
        assertEquals(devices, DeviceOffsets.connectedFirst(devices, "EE", setOf("EE")) { it })
    }

    @Test
    fun `isActive matches the playing device in any case and nothing when no device plays`() {
        assertTrue(DeviceOffsets.isActive("00:11:22:aa:bb:cc", "00:11:22:AA:BB:CC"))
        assertFalse(DeviceOffsets.isActive("00:11:22:AA:BB:CC", "00:11:22:AA:BB:CD"))
        assertFalse(DeviceOffsets.isActive("00:11:22:AA:BB:CC", null))
    }
}
