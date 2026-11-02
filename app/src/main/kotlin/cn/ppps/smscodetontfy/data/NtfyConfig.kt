package cn.ppps.smscodetontfy.data

/**
 * ntfy 转发配置（单配置）。
 */
data class NtfyConfig(
    val server: String = "",
    val topic: String = "",
    val token: String = "",
    /** 经 Android Keystore 加密后的 PKCS12 字节(Base64)，空串表示不启用 mTLS */
    val clientCert: String = "",
    val enabled: Boolean = true
)
