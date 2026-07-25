package com.codetrio.spatialflow.data.lyrics

enum class ProviderStatus {
    OPERATIONAL,
    DEGRADED,
    DOWN,
    UNKNOWN
}

data class PaxsenixProviderStat(
    val providerName: String,
    val status: ProviderStatus,
    val latencyMs: Long = 0L,
    val successRate: Float = 1.0f
)

data class PaxsenixStats(
    val isGlobalOperational: Boolean = true,
    val activeKeyPresent: Boolean = false,
    val totalRequests: Long = 0L,
    val successfulRequests: Long = 0L,
    val providerStats: Map<String, PaxsenixProviderStat> = emptyMap()
)
