package com.openclaw.agent.core.mijia

import android.util.Base64
import android.util.Log
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * Ports the RC4 signing/encryption logic from mijiaAPI/miutils.py.
 *
 * Request signing flow:
 * 1. Generate nonce (random 8 bytes + time/60000)
 * 2. Compute signedNonce = SHA256(decode(ssecurity) + decode(nonce))
 * 3. For each param: encrypt with RC4(signedNonce)
 * 4. Add SHA1 signature + ssecurity + nonce to params
 */
object MijiaRequestSigner {

    /** Generate a nonce: random 8 bytes + (currentTimeMillis/60000) */
    fun genNonce(): String {
        val randomBytes = Random.nextBytes(8)
        val timeValue = System.currentTimeMillis() / 60000L
        // Encode timeValue as minimal big-endian bytes
        val timeLong = timeValue
        val timeBytes = when {
            timeLong < 0x100L -> byteArrayOf(timeLong.toByte())
            timeLong < 0x10000L -> byteArrayOf((timeLong shr 8).toByte(), timeLong.toByte())
            timeLong < 0x1000000L -> byteArrayOf((timeLong shr 16).toByte(), (timeLong shr 8).toByte(), timeLong.toByte())
            else -> byteArrayOf((timeLong shr 24).toByte(), (timeLong shr 16).toByte(), (timeLong shr 8).toByte(), timeLong.toByte())
        }
        val combined = randomBytes + timeBytes
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /** signedNonce = base64(SHA256(decode(ssecurity) + decode(nonce))) */
    fun getSignedNonce(ssecurity: String, nonce: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(Base64.decode(ssecurity, Base64.NO_WRAP))
        digest.update(Base64.decode(nonce, Base64.NO_WRAP))
        return Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
    }

    /**
     * Generate SHA1 signature over method + uri + params + signedNonce.
     * Format: "METHOD&uri&k1=v1&k2=v2&signedNonce"
     */
    fun genEncSignature(uri: String, method: String, signedNonce: String, params: Map<String, String>): String {
        val parts = mutableListOf(method.uppercase(), uri)
        params.forEach { (k, v) -> parts.add("$k=$v") }
        parts.add(signedNonce)
        val signatureString = parts.joinToString("&")
        val digest = MessageDigest.getInstance("SHA-1")
        return Base64.encodeToString(digest.digest(signatureString.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    /**
     * Full param encryption pipeline:
     * 1. Compute rc4_hash__ signature
     * 2. RC4-encrypt every param value
     * 3. Compute final signature
     * 4. Add ssecurity + nonce
     */
    fun generateEncParams(
        uri: String,
        method: String,
        signedNonce: String,
        nonce: String,
        params: MutableMap<String, String>,
        ssecurity: String
    ): Map<String, String> {
        Log.d("MijiaSign", "generateEncParams: uri=$uri, nonce=${nonce.take(10)}, signedNonce=${signedNonce.take(10)}")
        Log.d("MijiaSign", "  params before: ${params.keys}")

        // Step 1: compute rc4_hash__
        params["rc4_hash__"] = genEncSignature(uri, method, signedNonce, params)
        Log.d("MijiaSign", "  rc4_hash__=${params["rc4_hash__"]}")

        // Step 2: RC4-encrypt every value (in-place like Python)
        for (key in params.keys.toList()) {
            params[key] = encryptRc4(signedNonce, params[key]!!)
        }
        Log.d("MijiaSign", "  encrypted data=${params["data"]?.take(30)}")

        // Step 3: final signature over encrypted params
        params["signature"] = genEncSignature(uri, method, signedNonce, params)
        params["ssecurity"] = ssecurity
        params["_nonce"] = nonce

        Log.d("MijiaSign", "  final signature=${params["signature"]}")
        return params
    }

    /** RC4-encrypt payload. Skip first 1024 bytes of keystream (standard RC4 skip). */
    fun encryptRc4(password: String, payload: String): String {
        val key = Base64.decode(password, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("ARCFOUR")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "ARCFOUR"))
        // Skip 1024 bytes of keystream (using update, not doFinal)
        cipher.update(ByteArray(1024))
        // Use update for the actual payload too (stream cipher, no finalization needed)
        val encrypted = cipher.update(payload.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    /** RC4-decrypt payload to raw bytes. */
    fun decryptRc4Bytes(password: String, payload: String): ByteArray {
        val key = Base64.decode(password, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("ARCFOUR")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "ARCFOUR"))
        cipher.update(ByteArray(1024))
        return cipher.update(Base64.decode(payload, Base64.NO_WRAP))
    }

    /** RC4-decrypt payload. Auto-detects GZIP (magic bytes 0x1f 0x8b) and decompresses if needed. */
    fun decryptRc4(password: String, payload: String): String {
        val key = Base64.decode(password, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("ARCFOUR")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "ARCFOUR"))
        // Skip 1024 bytes of keystream
        cipher.update(ByteArray(1024))
        val decrypted = cipher.update(Base64.decode(payload, Base64.NO_WRAP))
        // Check GZIP magic bytes (0x1f 0x8b) before attempting UTF-8 decode
        return if (decrypted.size >= 2 &&
            decrypted[0] == 0x1f.toByte() &&
            decrypted[1] == 0x8b.toByte()
        ) {
            GZIPInputStream(ByteArrayInputStream(decrypted)).bufferedReader(Charsets.UTF_8).readText()
        } else {
            decrypted.toString(Charsets.UTF_8)
        }
    }
}
