package com.example

import com.example.data.model.VlsmSubnetInput
import com.example.data.repository.SubnetCalculatorEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubnetCalculatorEngineTest {

    @Test
    fun testStandardSubnetCalculation() {
        val res = SubnetCalculatorEngine.calculate("192.168.1.50", 24)
        assertEquals("192.168.1.0", res.networkAddress)
        assertEquals("192.168.1.255", res.broadcastAddress)
        assertEquals("192.168.1.1", res.firstUsableIp)
        assertEquals("192.168.1.254", res.lastUsableIp)
        assertEquals(256L, res.totalHosts)
        assertEquals(254L, res.usableHosts)
        assertEquals("255.255.255.0", res.subnetMask)
        assertEquals("0.0.0.255", res.wildcardMask)
        assertTrue(res.ipClass.startsWith("Kelas C"))
        assertTrue(res.ipType.startsWith("Private"))
    }

    @Test
    fun testRfc3021Slash31PointToPoint() {
        val res = SubnetCalculatorEngine.calculate("10.0.0.0", 31)
        assertEquals(2L, res.usableHosts)
        assertEquals("10.0.0.0", res.firstUsableIp)
        assertEquals("10.0.0.1", res.lastUsableIp)
    }

    @Test
    fun testSlash32HostRoute() {
        val res = SubnetCalculatorEngine.calculate("8.8.8.8", 32)
        assertEquals(1L, res.usableHosts)
        assertEquals("8.8.8.8", res.firstUsableIp)
        assertEquals("8.8.8.8", res.lastUsableIp)
        assertTrue(res.ipType.startsWith("Public"))
    }

    @Test
    fun testVlsmPlanner() {
        val reqs = listOf(
            VlsmSubnetInput("1", "Subnet A", 60),
            VlsmSubnetInput("2", "Subnet B", 30),
            VlsmSubnetInput("3", "Subnet C", 14),
            VlsmSubnetInput("4", "Subnet D", 2)
        )
        val plan = SubnetCalculatorEngine.calculateVlsm("192.168.1.0", 24, reqs)
        assertEquals("192.168.1.0", plan.baseNetwork)
        assertFalse(plan.isOverAllocated)
        assertEquals(4, plan.subnets.size)
        // Subnet A needs 60 hosts -> /26 (62 usable)
        assertEquals(26, plan.subnets[0].prefix)
        assertEquals("192.168.1.0", plan.subnets[0].networkAddress)
        // Subnet B needs 30 hosts -> /27 (30 usable)
        assertEquals(27, plan.subnets[1].prefix)
        assertEquals("192.168.1.64", plan.subnets[1].networkAddress)
    }

    @Test
    fun testBinaryConversion() {
        assertEquals("11000000", SubnetCalculatorEngine.convertDecimalToBinary("192"))
        assertEquals("192", SubnetCalculatorEngine.convertBinaryToDecimal("11000000"))
    }
}
