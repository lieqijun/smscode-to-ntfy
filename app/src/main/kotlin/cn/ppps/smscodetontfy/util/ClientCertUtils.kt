package cn.ppps.smscodetontfy.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.KeyStore
import java.security.PrivateKey

/**
 * 客户端证书(mTLS)工具。使用原生 PKCS12（无需 BouncyCastle 依赖）。
 *
 * 导入：用用户输入的 PKCS#12 密码临时解密，取出私钥+证书链，
 * 以 [ClientCertCrypto.deriveBksPassword] 派生的密码重新封装成新的 PKCS12，
 * 再由 [ClientCertCrypto.encrypt] 加密存盘（密码不持久化）。
 *
 * 发送：解密存盘字节得到 PKCS12，用派生密码解锁，交给 HttpsURLConnection 做 mTLS。
 */
object ClientCertUtils {

    /**
     * 将内存中的 PKCS#12(.p12) 字节重新封装为使用 [newPassword] 的新 PKCS12 字节。
     */
    @Throws(Exception::class)
    fun p12BytesToPkcs12Bytes(
        p12Bytes: ByteArray,
        p12Password: String,
        newPassword: String
    ): ByteArray {
        val p12 = KeyStore.getInstance("PKCS12")
        p12.load(ByteArrayInputStream(p12Bytes), p12Password.toCharArray())

        val alias = p12.aliases().toList().first { p12.isKeyEntry(it) }
        val privateKey = p12.getKey(alias, p12Password.toCharArray()) as PrivateKey
        val chain = p12.getCertificateChain(alias) ?: arrayOf(p12.getCertificate(alias))

        val newKs = KeyStore.getInstance("PKCS12")
        newKs.load(null, null)
        newKs.setKeyEntry("client", privateKey, newPassword.toCharArray(), chain)

        val out = ByteArrayOutputStream()
        newKs.store(out, newPassword.toCharArray())
        return out.toByteArray()
    }

    /** 校验 PKCS#12 能否用给定密码打开（导入时调用，不保存密码）。 */
    @Throws(Exception::class)
    fun verifyP12(p12Bytes: ByteArray, p12Password: String) {
        KeyStore.getInstance("PKCS12").apply {
            load(ByteArrayInputStream(p12Bytes), p12Password.toCharArray())
        }
    }
}
