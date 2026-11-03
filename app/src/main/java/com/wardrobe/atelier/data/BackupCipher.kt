package com.wardrobe.atelier.data

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupCipher {
    private val header = "YIXU01".toByteArray(Charsets.US_ASCII)

    private fun key(pass: String, salt: ByteArray) =
        SecretKeySpec(
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(PBEKeySpec(pass.toCharArray(), salt, 120000, 256))
                .encoded,
            "AES",
        )

    fun encrypt(data: ByteArray, pass: String): ByteArray {
        require(pass.length >= 8) { "备份口令至少 8 位" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(pass, salt), GCMParameterSpec(128, iv))
        c.updateAAD(header)
        return header + salt + iv + c.doFinal(data)
    }

    fun decrypt(data: ByteArray, pass: String): ByteArray {
        require(data.size >= 50 && data.copyOfRange(0, 6).contentEquals(header)) { "这不是衣序备份文件" }
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(
                Cipher.DECRYPT_MODE,
                key(pass, data.copyOfRange(6, 22)),
                GCMParameterSpec(128, data.copyOfRange(22, 34)),
            )
            c.updateAAD(header)
            c.doFinal(data.copyOfRange(34, data.size))
        } catch (e: Exception) {
            throw IllegalArgumentException("口令不正确，或备份文件已损坏")
        }
    }
}
