package com.example.data.model

data class SubnetResult(
    val ipAddress: String,
    val prefix: Int,
    val subnetMask: String,
    val wildcardMask: String,
    val networkAddress: String,
    val broadcastAddress: String,
    val firstUsableIp: String,
    val lastUsableIp: String,
    val usableHostRange: String,
    val totalHosts: Long,
    val usableHosts: Long,
    val ipBinary: String,
    val maskBinary: String,
    val ipHex: String,
    val ipClass: String,
    val ipType: String, // Private (RFC 1918), Public, Loopback, Link-Local
    val integerId: Long,
    val inAddrArpa: String,
    val note: String? = null
)

data class VlsmSubnetInput(
    val id: String,
    val name: String,
    val requiredHosts: Int
)

data class VlsmSubnetResult(
    val name: String,
    val requiredHosts: Int,
    val allocatedHosts: Long,
    val prefix: Int,
    val subnetMask: String,
    val networkAddress: String,
    val usableRange: String,
    val broadcastAddress: String
)

data class VlsmPlanResult(
    val baseNetwork: String,
    val basePrefix: Int,
    val totalAvailableHosts: Long,
    val totalAllocatedHosts: Long,
    val isOverAllocated: Boolean,
    val subnets: List<VlsmSubnetResult>
)

data class CalculationHistoryItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val ip: String,
    val prefix: Int,
    val network: String,
    val usableRange: String,
    val totalHosts: Long,
    val timestamp: Long = System.currentTimeMillis()
)
