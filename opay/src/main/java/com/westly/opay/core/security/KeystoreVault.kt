package com.westly.opay.core.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import com.westly.opay.backend.SecureKeys
import com.westly.opay.backend.SecureStore
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.ProviderException
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The two device tokens: one signs in with a fingerprint, one authorises a payment. Each has its own key. */
enum class VaultPurpose(val alias: String, val storeKey: String) {
    Login("opay_biometric_login_key", SecureKeys.DEVICE_TOKEN_LOGIN),
    Pay("opay_biometric_pay_key", SecureKeys.DEVICE_TOKEN_PAY)
}

enum class TokenStatus {
    /** No token is stored. */
    Absent,

    /** A token is stored and its key can still be unlocked by a fingerprint prompt. */
    Valid,

    /** A token was stored but a new fingerprint was enrolled (or the key is gone). The token has been deleted. */
    Invalidated
}

/**
 * Thrown when a token cannot be used. [invalidated] is true when the key was permanently invalidated by a new
 * fingerprint enrolment. The stored token is already deleted when this is thrown for an invalidated key.
 */
class TokenUnavailableException(val invalidated: Boolean) : Exception()

/** What the settings screens and the fingerprint login need from the vault. A fake of this is used in unit tests. */
interface TokenVault {
    fun status(purpose: VaultPurpose): TokenStatus

    /** Creates a fresh key for [purpose], removing any old token, and returns a cipher to give to the prompt. */
    fun newEncryptCipher(purpose: VaultPurpose): Cipher

    /** Encrypts [token] with the cipher the fingerprint prompt unlocked and stores the result. */
    fun encryptToken(purpose: VaultPurpose, token: String, cipherFromPrompt: Cipher)

    /** A cipher for the stored token, to give to the prompt. */
    fun newDecryptCipher(purpose: VaultPurpose): Cipher

    /** Decrypts the stored token with the cipher the fingerprint prompt unlocked. */
    fun decryptToken(purpose: VaultPurpose, cipherFromPrompt: Cipher): String

    /** Removes the stored token and its key. */
    fun delete(purpose: VaultPurpose)
}

/**
 * AES-256 GCM keys in the Android Keystore, one per purpose. Each key needs a fingerprint for every use and is
 * invalidated when a new fingerprint is enrolled. Only the encrypted token (IV and ciphertext) is kept, in
 * [SecureStore]. The token and the key never leave this class in readable form.
 */
class KeystoreVault(private val store: SecureStore) : TokenVault {

    override fun status(purpose: VaultPurpose): TokenStatus {
        if (store.getString(purpose.storeKey).isNullOrBlank()) return TokenStatus.Absent
        return try {
            newDecryptCipher(purpose)
            TokenStatus.Valid
        } catch (e: TokenUnavailableException) {
            delete(purpose)
            TokenStatus.Invalidated
        }
    }

    override fun newEncryptCipher(purpose: VaultPurpose): Cipher {
        delete(purpose)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, createKey(purpose))
            cipher
        } catch (e: KeyPermanentlyInvalidatedException) {
            delete(purpose)
            throw TokenUnavailableException(invalidated = true)
        } catch (e: GeneralSecurityException) {
            delete(purpose)
            throw TokenUnavailableException(invalidated = false)
        } catch (e: ProviderException) {
            delete(purpose)
            throw TokenUnavailableException(invalidated = false)
        }
    }

    override fun encryptToken(purpose: VaultPurpose, token: String, cipherFromPrompt: Cipher) {
        try {
            val encrypted = cipherFromPrompt.doFinal(token.toByteArray(Charsets.UTF_8))
            val iv = cipherFromPrompt.iv ?: throw TokenUnavailableException(invalidated = false)
            val encoder = Base64.getEncoder()
            store.putString(purpose.storeKey, encoder.encodeToString(iv) + SEPARATOR + encoder.encodeToString(encrypted))
        } catch (e: GeneralSecurityException) {
            delete(purpose)
            throw TokenUnavailableException(invalidated = false)
        }
    }

    override fun newDecryptCipher(purpose: VaultPurpose): Cipher {
        val parts = store.getString(purpose.storeKey)?.split(SEPARATOR)
        if (parts == null || parts.size != 2) {
            delete(purpose)
            throw TokenUnavailableException(invalidated = false)
        }
        val iv = try {
            Base64.getDecoder().decode(parts[0])
        } catch (e: IllegalArgumentException) {
            delete(purpose)
            throw TokenUnavailableException(invalidated = false)
        }
        val key = findKey(purpose)
        if (key == null) {
            delete(purpose)
            throw TokenUnavailableException(invalidated = true)
        }
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            cipher
        } catch (e: KeyPermanentlyInvalidatedException) {
            delete(purpose)
            throw TokenUnavailableException(invalidated = true)
        } catch (e: GeneralSecurityException) {
            throw TokenUnavailableException(invalidated = false)
        }
    }

    override fun decryptToken(purpose: VaultPurpose, cipherFromPrompt: Cipher): String {
        val encoded = store.getString(purpose.storeKey)?.split(SEPARATOR)?.getOrNull(1)
            ?: throw TokenUnavailableException(invalidated = false)
        return try {
            val plain = cipherFromPrompt.doFinal(Base64.getDecoder().decode(encoded))
            String(plain, Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            throw TokenUnavailableException(invalidated = false)
        } catch (e: IllegalArgumentException) {
            throw TokenUnavailableException(invalidated = false)
        }
    }

    override fun delete(purpose: VaultPurpose) {
        store.remove(purpose.storeKey)
        try {
            val keyStore = keyStore()
            if (keyStore.containsAlias(purpose.alias)) keyStore.deleteEntry(purpose.alias)
        } catch (e: GeneralSecurityException) {
            // The stored token is already removed, so an orphaned key can never be used.
        }
    }

    private fun createKey(purpose: VaultPurpose): SecretKey {
        val builder = KeyGenParameterSpec.Builder(
            purpose.alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        requireAuthenticationForEveryUse(builder)
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(builder.build())
        return generator.generateKey()
    }

    @Suppress("DEPRECATION")
    private fun requireAuthenticationForEveryUse(builder: KeyGenParameterSpec.Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
    }

    private fun findKey(purpose: VaultPurpose): SecretKey? = try {
        keyStore().getKey(purpose.alias, null) as? SecretKey
    } catch (e: GeneralSecurityException) {
        null
    }

    private fun keyStore(): KeyStore = try {
        KeyStore.getInstance(KEYSTORE).apply { load(null) }
    } catch (e: IOException) {
        throw KeyStoreException(e)
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val SEPARATOR = ":"
    }
}
