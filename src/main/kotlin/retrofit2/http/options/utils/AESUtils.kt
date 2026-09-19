package retrofit2.http.options.utils

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES Encryption utils.
 */
object AESUtils {

    const val MIN_KEY_SIZE = 16

    fun keyToBytes(key: String): ByteArray {
        val size = MIN_KEY_SIZE * (key.length / MIN_KEY_SIZE)
        val k = key.substring(0, size)
        return k.toByteArray()
    }

    @Throws(Exception::class)
    fun encrypt(key: String, cleartext: String): String {
        val keyValue = keyToBytes(key)
        val rawKey = getRawKey(keyValue)
        val result = encrypt(rawKey, cleartext.toByteArray())
        return toHex(result)
    }

    @Throws(Exception::class)
    fun decrypt(key: String, encrypted: String): String {
        val keyValue = keyToBytes(key)
        val enc = toByte(encrypted)
        val result = decrypt(keyValue, enc)
        return String(result)
    }

    @Throws(Exception::class)
    private fun getRawKey(keyValue: ByteArray): ByteArray {
        val key: SecretKey = SecretKeySpec(keyValue, "AES")
        return key.encoded
    }

    @Throws(Exception::class)
    private fun encrypt(raw: ByteArray, clear: ByteArray): ByteArray {
        val skeySpec: SecretKey = SecretKeySpec(raw, "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val iv = ByteArray(cipher.blockSize)
        val ivParams = IvParameterSpec(iv)
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec, ivParams)
        return cipher.doFinal(clear)
    }

    @Throws(Exception::class)
    private fun decrypt(keyValue: ByteArray, encrypted: ByteArray): ByteArray {
        val skeySpec: SecretKey = SecretKeySpec(keyValue, "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val iv = ByteArray(cipher.blockSize)
        val ivParams = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, skeySpec, ivParams)
        return cipher.doFinal(encrypted)
    }

    fun toByte(hexString: String): ByteArray {
        val len = hexString.length / 2
        val result = ByteArray(len)
        for (i in 0 until len)
            result[i] = hexString.substring(2 * i, 2 * i + 2).toInt(16).toByte()
        return result
    }

    fun toHex(buf: ByteArray?): String {
        if (buf == null) return ""
        val result = StringBuffer(2 * buf.size)
        for (b in buf) {
            appendHex(result, b)
        }
        return result.toString()
    }

    private const val HEX = "0123456789ABCDEF"

    private fun appendHex(sb: StringBuffer, b: Byte) {
        sb.append(HEX[(b.toInt() shr 4) and 0x0f]).append(HEX[b.toInt() and 0x0f])
    }
}
