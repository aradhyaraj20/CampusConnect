package com.example.campusconnect.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password hashing and HMAC helpers.
 *
 * Passwords are stored as salted PBKDF2 hashes in the format:
 *
 *     pbkdf2$<iterations>$<saltHex>$<hashHex>
 *
 * A fresh random salt is generated per password, so two identical passwords
 * produce different stored values (defeats rainbow tables / precomputation).
 * PBKDF2-HMAC-SHA256 ("pbkdf2s256") is used wherever the platform provides it
 * (API 26+). On API 24-25 it falls back to PBKDF2-HMAC-SHA1 ("pbkdf2"); those
 * hashes still verify everywhere and are upgraded on the next login.
 *
 * Verification also accepts legacy 64-char hex SHA-256 hashes so accounts
 * created before this change keep working. The old plaintext-equality
 * fallback has been removed - it accepted an unhashed password as its own
 * "hash", which defeated the point of hashing entirely.
 */
object CryptoUtils {

    private const val SHA1_ALGORITHM = "PBKDF2WithHmacSHA1"
    private const val SHA256_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 150_000
    private const val ITERATIONS_SHA256 = 210_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
    private const val PREFIX = "pbkdf2"        // legacy: PBKDF2-HMAC-SHA1
    private const val PREFIX_256 = "pbkdf2s256"  // current: PBKDF2-HMAC-SHA256 (API 26+)

    private val secureRandom = SecureRandom()

    /**
     * HMAC secret for QR ticket signing. Sourced from BuildConfig (populated
     * from local.properties at build time, never committed) with a fallback
     * default so the app still builds without local configuration. Read via
     * reflection so this object stays usable in plain JVM unit tests, mirroring
     * how AIConfig resolves the Gemini key.
     *
     * Note: because the app has no backend, the same secret must exist in every
     * install so a ticket signed on a student's phone verifies on an organizer's
     * scanner. A shared on-device secret is therefore inherent to this design;
     * R8/obfuscation (enabled for release) and overriding the default via
     * local.properties per deployment are the practical mitigations.
     */
    private val hmacSecret: String by lazy {
        runCatching {
            val field = Class.forName("com.example.campusconnect.BuildConfig")
                .getField("TICKET_HMAC_SECRET")
            (field.get(null) as? String)?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: DEFAULT_HMAC_SECRET
    }

    private const val DEFAULT_HMAC_SECRET = "CampusConnect_Secure_HMAC_Secret_Key_2026"

    private val sha256Available: Boolean by lazy {
        runCatching { SecretKeyFactory.getInstance(SHA256_ALGORITHM) }.isSuccess
    }

    fun hashPassword(password: String): String {
        val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
        val useSha256 = sha256Available
        val prefix = if (useSha256) PREFIX_256 else PREFIX
        val iterations = if (useSha256) ITERATIONS_SHA256 else ITERATIONS
        val hash = pbkdf2(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS, useSha256)
        return listOf(prefix, iterations.toString(), salt.toHex(), hash.toHex()).joinToString("$")
    }

    fun verifyPassword(password: String, storedHash: String): Boolean {
        val parts = storedHash.split("$")
        if (parts.size == 4 && (parts[0] == PREFIX || parts[0] == PREFIX_256)) {
            val sha256 = parts[0] == PREFIX_256
            if (sha256 && !sha256Available) return false
            val iterations = parts[1].toIntOrNull() ?: return false
            val salt = parts[2].hexToBytes() ?: return false
            val expected = parts[3].hexToBytes() ?: return false
            val actual = pbkdf2(password.toCharArray(), salt, iterations, expected.size * 8, sha256)
            return MessageDigest.isEqual(expected, actual)
        }
        // Legacy accounts: 64-char hex SHA-256 (no salt). Still verified so old
        // data keeps working, but new passwords are always PBKDF2.
        if (storedHash.length == 64 && storedHash.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            return MessageDigest.isEqual(
                legacySha256(password).toByteArray(Charsets.UTF_8),
                storedHash.lowercase().toByteArray(Charsets.UTF_8)
            )
        }
        return false
    }

    /** True when the stored hash is weaker than what this device can produce; re-hash after a successful login. */
    fun needsRehash(storedHash: String): Boolean =
        if (sha256Available) !storedHash.startsWith("$PREFIX_256$") else !storedHash.startsWith("$PREFIX$")

    private fun pbkdf2(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        keyLengthBits: Int = KEY_LENGTH_BITS,
        sha256: Boolean = false
    ): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, keyLengthBits)
        val factory = SecretKeyFactory.getInstance(if (sha256) SHA256_ALGORITHM else SHA1_ALGORITHM)
        return factory.generateSecret(spec).encoded
    }

    private fun legacySha256(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(password.toByteArray(Charsets.UTF_8)).toHex()
    }

    fun generateHmacSignature(data: String): String {
        val secretKeySpec = SecretKeySpec(hmacSecret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(secretKeySpec)
        val hmacBytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return hmacBytes.toHex()
    }

    fun verifyHmacSignature(data: String, signature: String): Boolean {
        return MessageDigest.isEqual(
            generateHmacSignature(data).toByteArray(Charsets.UTF_8),
            signature.toByteArray(Charsets.UTF_8)
        )
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray? {
        if (length % 2 != 0) return null
        return runCatching {
            ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        }.getOrNull()
    }
}
