package com.example.test

import android.util.Base64
import android.util.Log
import java.security.KeyPairGenerator
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptionUtil {
    private const val TAG = "EncryptionUtil"

    // Hardcoded 256-bit AES key for demonstration
    private val hardcodedKeyBytes = byteArrayOf(
        0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
        0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10,
        0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18,
        0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x20
    )

    private fun getSecretKey() = SecretKeySpec(hardcodedKeyBytes, "AES")

    init {
        // Run RSA demo once on initialization
        demoRsaEncryption()
    }

    fun encryptMessage(plainText: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), IvParameterSpec(iv))
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = iv + encryptedBytes
            val base64Str = Base64.encodeToString(combined, Base64.NO_WRAP)
            Log.d(TAG, "ENCRYPTED DATA: $base64Str")
            base64Str
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed", e)
            plainText
        }
    }

    fun decryptMessage(encryptedText: String): String {
        return try {
            val combined = Base64.decode(encryptedText, Base64.NO_WRAP)
            if (combined.size < 16) return encryptedText // Fallback
            val iv = combined.sliceArray(0 until 16)
            val encryptedBytes = combined.sliceArray(16 until combined.size)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), IvParameterSpec(iv))
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            val plainText = String(decryptedBytes, Charsets.UTF_8)
            Log.d(TAG, "DECRYPTED DATA: $plainText")
            plainText
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed or not encrypted", e)
            encryptedText
        }
    }

    fun encryptFile(data: ByteArray): ByteArray {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), IvParameterSpec(iv))
            val encryptedBytes = cipher.doFinal(data)
            val combined = iv + encryptedBytes
            Log.d(TAG, "ENCRYPTED DATA: File encrypted to ${combined.size} bytes from ${data.size}")
            combined
        } catch (e: Exception) {
            Log.e(TAG, "File encryption failed", e)
            data
        }
    }

    fun decryptFile(data: ByteArray): ByteArray {
        return try {
            if (data.size < 16) return data
            val iv = data.sliceArray(0 until 16)
            val encryptedBytes = data.sliceArray(16 until data.size)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), IvParameterSpec(iv))
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            Log.d(TAG, "DECRYPTED DATA: File decrypted to ${decryptedBytes.size} bytes from ${data.size}")
            decryptedBytes
        } catch (e: Exception) {
            Log.e(TAG, "File decryption failed", e)
            data
        }
    }

    private fun demoRsaEncryption() {
        try {
            val keyGen = KeyPairGenerator.getInstance("RSA")
            keyGen.initialize(2048)
            val pair = keyGen.generateKeyPair()

            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, pair.public)
            val encryptedAesKey = cipher.doFinal(hardcodedKeyBytes)

            Log.d(TAG, "RSA DEMO: AES Key Encrypted with RSA. Size: ${encryptedAesKey.size} bytes")
            Log.d(TAG, "RSA DEMO: Encrypted AES Key (Base64): ${Base64.encodeToString(encryptedAesKey, Base64.NO_WRAP)}")
        } catch (e: Exception) {
            Log.e(TAG, "RSA demo failed", e)
        }
    }
}
