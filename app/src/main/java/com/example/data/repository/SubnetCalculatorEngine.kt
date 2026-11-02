package com.example.data.repository

import com.example.data.model.SubnetResult
import com.example.data.model.VlsmPlanResult
import com.example.data.model.VlsmSubnetInput
import com.example.data.model.VlsmSubnetResult
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.pow

object SubnetCalculatorEngine {

    fun isValidIpv4(ip: String): Boolean {
        val parts = ip.trim().split(".")
        if (parts.size != 4) return false
        return parts.all {
            val num = it.toIntOrNull() ?: return false
            num in 0..255 && (it == "0" || !it.startsWith("0"))
        }
    }

    fun ipToLong(ip: String): Long {
        val parts = ip.trim().split(".").map { it.toLong() }
        return (parts[0] shl 24) or (parts[1] shl 16) or (parts[2] shl 8) or parts[3]
    }

    fun longToIp(value: Long): String {
        val octet1 = (value shr 24) and 0xFF
        val octet2 = (value shr 16) and 0xFF
        val octet3 = (value shr 8) and 0xFF
        val octet4 = value and 0xFF
        return "$octet1.$octet2.$octet3.$octet4"
    }

    fun prefixToMaskLong(prefix: Int): Long {
        if (prefix == 0) return 0L
        return (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
    }

    fun maskToPrefix(mask: String): Int? {
        if (!isValidIpv4(mask)) return null
        val maskLong = ipToLong(mask)
        var count = 0
        var foundZero = false
        for (i in 31 downTo 0) {
            val bit = (maskLong shr i) and 1L
            if (bit == 1L) {
                if (foundZero) return null // Non-contiguous mask is invalid
                count++
            } else {
                foundZero = true
            }
        }
        return count
    }

    fun toBinaryString(value: Long): String {
        val o1 = ((value shr 24) and 0xFF).toString(2).padStart(8, '0')
        val o2 = ((value shr 16) and 0xFF).toString(2).padStart(8, '0')
        val o3 = ((value shr 8) and 0xFF).toString(2).padStart(8, '0')
        val o4 = (value and 0xFF).toString(2).padStart(8, '0')
        return "$o1.$o2.$o3.$o4"
    }

    fun calculate(ipInput: String, prefixInput: Int): SubnetResult {
        val cleanIp = ipInput.trim()
        val prefix = prefixInput.coerceIn(0, 32)
        val ipLong = ipToLong(cleanIp)
        val maskLong = prefixToMaskLong(prefix)
        val wildcardLong = maskLong.inv() and 0xFFFFFFFFL

        val networkLong = ipLong and maskLong
        val broadcastLong = networkLong or wildcardLong

        val totalHosts = if (prefix == 0) 4294967296L else 2.0.pow(32 - prefix).toLong()

        val usableHosts: Long
        val firstUsable: String
        val lastUsable: String
        val note: String?

        when (prefix) {
            32 -> {
                usableHosts = 1
                firstUsable = cleanIp
                lastUsable = cleanIp
                note = "Host route (/32). Alamat tunggal tanpa broadcast atau network terpisah."
            }
            31 -> {
                // RFC 3021 Point-to-Point link
                usableHosts = 2
                firstUsable = longToIp(networkLong)
                lastUsable = longToIp(broadcastLong)
                note = "Point-to-point link (RFC 3021). Kedua alamat dapat digunakan untuk host."
            }
            else -> {
                usableHosts = (totalHosts - 2).coerceAtLeast(0)
                firstUsable = if (totalHosts > 2) longToIp(networkLong + 1) else "None"
                lastUsable = if (totalHosts > 2) longToIp(broadcastLong - 1) else "None"
                note = if (prefix == 0) "Default route (/0). Mencakup seluruh ruang alamat IPv4." else null
            }
        }

        val firstOctet = ((ipLong shr 24) and 0xFF).toInt()
        val ipClass = when {
            firstOctet in 1..126 -> "Kelas A (Default /8)"
            firstOctet == 127 -> "Kelas A (Loopback 127.0.0.0/8)"
            firstOctet in 128..191 -> "Kelas B (Default /16)"
            firstOctet in 192..223 -> "Kelas C (Default /24)"
            firstOctet in 224..239 -> "Kelas D (Multicast)"
            firstOctet in 240..255 -> "Kelas E (Eksperimental/Reserved)"
            else -> "Unknown (0.0.0.0/8)"
        }

        val ipType = when {
            firstOctet == 10 -> "Private (RFC 1918 10.0.0.0/8)"
            firstOctet == 172 && (((ipLong shr 16) and 0xFF).toInt() in 16..31) -> "Private (RFC 1918 172.16.0.0/12)"
            firstOctet == 192 && (((ipLong shr 16) and 0xFF).toInt() == 168) -> "Private (RFC 1918 192.168.0.0/16)"
            firstOctet == 127 -> "Loopback (127.0.0.1)"
            firstOctet == 169 && (((ipLong shr 16) and 0xFF).toInt() == 254) -> "Link-Local / APIPA (169.254.0.0/16)"
            firstOctet in 224..239 -> "Multicast"
            firstOctet >= 240 -> "Reserved"
            else -> "Public Internet IP"
        }

        val octets = cleanIp.split(".")
        val inAddrArpa = if (octets.size == 4) "${octets[3]}.${octets[2]}.${octets[1]}.${octets[0]}.in-addr.arpa" else ""

        val ipHex = "0x" + ipLong.toString(16).uppercase().padStart(8, '0')

        return SubnetResult(
            ipAddress = cleanIp,
            prefix = prefix,
            subnetMask = longToIp(maskLong),
            wildcardMask = longToIp(wildcardLong),
            networkAddress = longToIp(networkLong),
            broadcastAddress = if (prefix in 31..32) "N/A (${if (prefix == 31) "RFC 3021" else "Single Host"})" else longToIp(broadcastLong),
            firstUsableIp = firstUsable,
            lastUsableIp = lastUsable,
            usableHostRange = if (prefix == 32) cleanIp else if (prefix == 31) "$firstUsable - $lastUsable" else if (usableHosts > 0) "$firstUsable - $lastUsable" else "None",
            totalHosts = totalHosts,
            usableHosts = usableHosts,
            ipBinary = toBinaryString(ipLong),
            maskBinary = toBinaryString(maskLong),
            ipHex = ipHex,
            ipClass = ipClass,
            ipType = ipType,
            integerId = ipLong,
            inAddrArpa = inAddrArpa,
            note = note
        )
    }

    fun calculateVlsm(baseIp: String, basePrefix: Int, requirements: List<VlsmSubnetInput>): VlsmPlanResult {
        val totalAvailable = if (basePrefix == 0) 4294967296L else 2.0.pow(32 - basePrefix).toLong()
        val baseNetworkLong = ipToLong(baseIp) and prefixToMaskLong(basePrefix)

        // Sort subnets descending by required hosts
        val sortedReqs = requirements.sortedByDescending { it.requiredHosts }
        var currentIpLong = baseNetworkLong
        val results = mutableListOf<VlsmSubnetResult>()
        var totalAllocated = 0L
        var isOverAllocated = false

        for (req in sortedReqs) {
            val needed = req.requiredHosts
            // Calculate prefix: needed hosts + 2 (for network and broadcast, or handle /31 if 2 hosts)
            val hostBitsNeeded = if (needed <= 2) {
                // For 2 hosts, prefix can be /31 (RFC 3021) or /30 (traditional)
                2
            } else {
                ceil(log2((needed + 2).toDouble())).toInt().coerceAtLeast(2)
            }
            val subnetPrefix = (32 - hostBitsNeeded).coerceIn(basePrefix, 30)
            val blockSize = 2.0.pow(32 - subnetPrefix).toLong()

            // Align current IP to block boundary if needed
            val remainder = currentIpLong % blockSize
            if (remainder != 0L) {
                currentIpLong += (blockSize - remainder)
            }

            val maskLong = prefixToMaskLong(subnetPrefix)
            val network = currentIpLong
            val broadcast = network + blockSize - 1
            val first = network + 1
            val last = broadcast - 1

            if ((broadcast - baseNetworkLong + 1) > totalAvailable) {
                isOverAllocated = true
            }

            results.add(
                VlsmSubnetResult(
                    name = req.name,
                    requiredHosts = req.requiredHosts,
                    allocatedHosts = blockSize - 2,
                    prefix = subnetPrefix,
                    subnetMask = longToIp(maskLong),
                    networkAddress = longToIp(network),
                    usableRange = "${longToIp(first)} - ${longToIp(last)}",
                    broadcastAddress = longToIp(broadcast)
                )
            )

            currentIpLong += blockSize
            totalAllocated += blockSize
        }

        return VlsmPlanResult(
            baseNetwork = longToIp(baseNetworkLong),
            basePrefix = basePrefix,
            totalAvailableHosts = totalAvailable,
            totalAllocatedHosts = totalAllocated,
            isOverAllocated = isOverAllocated,
            subnets = results
        )
    }

    fun convertDecimalToBinary(decimalStr: String): String {
        val num = decimalStr.trim().toLongOrNull() ?: return "Input tidak valid"
        return num.toString(2)
    }

    fun convertBinaryToDecimal(binaryStr: String): String {
        val clean = binaryStr.trim().replace(".", "").replace(" ", "")
        return try {
            java.lang.Long.parseLong(clean, 2).toString()
        } catch (e: Exception) {
            "Biner tidak valid"
        }
    }
}
