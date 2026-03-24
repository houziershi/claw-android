package com.openclaw.agent.core.mijia

/**
 * Holds all authentication data required for Xiaomi MIoT API calls.
 * Mirrors the fields in ~/.config/mijia-api/auth.json from the Python SDK.
 */
data class MijiaAuth(
    val userId: String,
    val cUserId: String,
    val ssecurity: String,       // Base64-encoded key for RC4 signing
    val serviceToken: String,    // Core auth cookie
    val passToken: String,
    val ua: String,              // Spoofed Android User-Agent
    val deviceId: String,        // Random 16-char device identifier
    val passO: String,           // Random 16-char hex
    val locale: String = "zh_CN",
    val expireTime: Long = 0L    // ms timestamp
)
