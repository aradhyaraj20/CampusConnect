package com.example.campusconnect.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoUtilsTest {

    @Test
    fun needsRehash_flagsLegacyHashesOnly() {
        assertFalse(CryptoUtils.needsRehash(CryptoUtils.hashPassword("password123")))
        assertTrue(CryptoUtils.needsRehash("a".repeat(64)))
    }

    @Test
    fun newHashes_useSha256AndOldSha1HashesStillVerify() {
        assertTrue(CryptoUtils.hashPassword("password123").startsWith("pbkdf2s256$"))

        // Hash in the format produced by the previous PBKDF2-HMAC-SHA1 implementation.
        val salt = ByteArray(16) { it.toByte() }
        val spec = javax.crypto.spec.PBEKeySpec("oldpassword".toCharArray(), salt, 1000, 256)
        val key = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
        val legacy = "pbkdf2$1000$${salt.hex()}$${key.hex()}"
        assertTrue(CryptoUtils.verifyPassword("oldpassword", legacy))
        assertFalse(CryptoUtils.verifyPassword("wrong", legacy))
        assertTrue(CryptoUtils.needsRehash(legacy))
    }

    @Test
    fun hashPassword_usesRandomSaltSoHashesDiffer() {
        val password = "password123"
        val hash1 = CryptoUtils.hashPassword(password)
        val hash2 = CryptoUtils.hashPassword(password)

        // Salted PBKDF2: same password must NOT produce the same stored value.
        assertNotEquals(hash1, hash2)
        assertNotEquals(password, hash1)
        // ...but both must still verify against the original password.
        assertTrue(CryptoUtils.verifyPassword(password, hash1))
        assertTrue(CryptoUtils.verifyPassword(password, hash2))
    }

    @Test
    fun verifyPassword_returnsTrueForValidPassword() {
        val password = "MySecurePassword"
        val hash = CryptoUtils.hashPassword(password)

        assertTrue(CryptoUtils.verifyPassword(password, hash))
    }

    @Test
    fun verifyPassword_returnsFalseForWrongPassword() {
        val hash = CryptoUtils.hashPassword("correct-horse")
        assertFalse(CryptoUtils.verifyPassword("battery-staple", hash))
    }

    @Test
    fun verifyPassword_rejectsPlaintextEquality() {
        // The old code accepted password == storedHash. Storing a plaintext
        // "hash" must no longer authenticate.
        assertFalse(CryptoUtils.verifyPassword("password", "password"))
    }

    @Test
    fun generateHmacSignature_producesValidSignature() {
        val data = "TICK_1_10_5:10:5:1700000000:1700600000"
        val sig1 = CryptoUtils.generateHmacSignature(data)
        val sig2 = CryptoUtils.generateHmacSignature(data)

        assertEquals(sig1, sig2)
        assertTrue(CryptoUtils.verifyHmacSignature(data, sig1))
    }
}
