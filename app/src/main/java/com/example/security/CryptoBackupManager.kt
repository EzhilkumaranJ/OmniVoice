package com.example.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Military-grade AES-256-GCM authenticated encryption for private offline backup files.
 * Uses standard java.util.Base64 for reliable cross-platform Android/JVM execution.
 */
object CryptoBackupManager {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12

    fun deriveKey(passphrase: String): SecretKey {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(passphrase.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Encrypts plaintext JSON data with a passphrase.
     * Returns: Base64 string containing [IV (12 bytes) + Ciphertext + GCM Tag]
     */
    fun encrypt(plaintext: String, passphrase: String): String {
        val key = deriveKey(passphrase)
        val iv = ByteArray(IV_LENGTH_BYTE)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(ALGORITHM)
        val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)

        return Base64.getEncoder().encodeToString(combined)
    }

    /**
     * Decrypts Base64 encrypted payload with the passphrase.
     * Throws exception if passphrase is incorrect or data was tampered with.
     */
    fun decrypt(encryptedBase64: String, passphrase: String): String {
        val key = deriveKey(passphrase)
        val combined = Base64.getDecoder().decode(encryptedBase64.trim())

        if (combined.size < IV_LENGTH_BYTE) {
            throw IllegalArgumentException("Corrupted backup data")
        }

        val iv = ByteArray(IV_LENGTH_BYTE)
        val ciphertext = ByteArray(combined.size - IV_LENGTH_BYTE)

        System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTE)
        System.arraycopy(combined, IV_LENGTH_BYTE, ciphertext, 0, ciphertext.size)

        val cipher = Cipher.getInstance(ALGORITHM)
        val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val plaintextBytes = cipher.doFinal(ciphertext)
        return String(plaintextBytes, Charsets.UTF_8)
    }
}
