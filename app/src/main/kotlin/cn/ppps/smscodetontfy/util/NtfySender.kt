package cn.ppps.smscodetontfy.util

import android.util.Base64
import android.util.Log
import cn.ppps.smscodetontfy.data.NtfyConfig
import java.io.ByteArrayInputStream
import java.net.URL
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * 通过 ntfy HTTP API 推送消息。
 * - 地址：POST {server}/{topic}
 * - 鉴权：Authorization: Bearer {token}（可选）
 * - mTLS：若配置了客户端证书，则在 TLS 握手时出示
 * - 服务端证书：宽松信任（兼容自签/内网域名，无需手动填 CA）
 */
object NtfySender {
    private const val TAG = "NtfySender"

    private val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    fun send(config: NtfyConfig, message: String, title: String = "smscode"): Boolean {
        return try {
            val base = config.server.trim().trimEnd('/')
            val url = URL("$base/${config.topic.trim()}")
            val conn = url.openConnection() as HttpsURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            conn.setRequestProperty("X-Title", title)
            if (config.token.isNotEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer ${config.token}")
            }

            val ctx = SSLContext.getInstance("TLS")
            val kmf = if (config.clientCert.isNotEmpty()) {
                val pkcs12 = ClientCertCrypto.decrypt(
                    Base64.decode(config.clientCert, Base64.DEFAULT)
                )
                val pwd = ClientCertCrypto.deriveBksPassword()
                val ks = KeyStore.getInstance("PKCS12")
                ks.load(ByteArrayInputStream(pkcs12), pwd.toCharArray())
                val k = KeyManagerFactory.getInstance("X509")
                k.init(ks, pwd.toCharArray())
                k
            } else null
            ctx.init(kmf?.keyManagers, trustAll, SecureRandom())
            conn.sslSocketFactory = ctx.socketFactory
            conn.hostnameVerifier = HostnameVerifier { _, _ -> true }

            conn.outputStream.use { it.write(message.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            conn.disconnect()
            Log.i(TAG, "send to ${config.server}/${config.topic} -> HTTP $code")
            code in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "send failed: ${e.message}")
            false
        }
    }
}
