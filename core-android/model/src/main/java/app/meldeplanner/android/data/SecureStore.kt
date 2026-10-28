package app.meldeplanner.android.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small secret store for the WebUntis password or session cookies and the Microsoft
 * refresh token, the counterpart of meldo-ios/MeldePlanner/Data/Keychain.swift.
 *
 * Values are encrypted with an AES-256-GCM key that lives in the Android Keystore
 * (non-exportable, hardware-backed where the device has it) and stored as
 * ciphertext in a private SharedPreferences file. The key never leaves this
 * device, so a copy of the file on another device (backup, transfer) can't be read;
 * the file is also excluded from backups and device transfer (res/xml/securestore_backup_rules.xml).
 */
object SecureStore {
    private const val KEY_ALIAS = "app.meldeplanner.webuntis"
    private const val PREFS = "app.meldeplanner.securestore"
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12

    @Volatile private var appContext: Context? = null
    private val lock = Any()

    /** Called once by the application (or lazily by the first caller with a context). */
    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    private fun prefs(): SharedPreferences? = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(secret: String, account: String): Boolean = synchronized(lock) {
        val prefs = prefs() ?: return false
        runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            cipher.updateAAD(account.toByteArray(Charsets.UTF_8))
            val sealed = cipher.iv + cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
            prefs.edit().putString(account, Base64.encodeToString(sealed, Base64.NO_WRAP)).commit()
        }.getOrDefault(false)
    }

    fun read(account: String): String? = synchronized(lock) {
        val prefs = prefs() ?: return null
        val stored = prefs.getString(account, null) ?: return null
        val result = runCatching {
            val sealed = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, sealed, 0, IV_BYTES))
            cipher.updateAAD(account.toByteArray(Charsets.UTF_8))
            String(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES), Charsets.UTF_8)
        }.getOrNull()
        // A value that can't be decrypted (key gone, restored on another device) is useless.
        if (result == null) prefs.edit().remove(account).commit()
        result
    }

    fun delete(account: String): Boolean = synchronized(lock) {
        val prefs = prefs() ?: return false
        prefs.edit().remove(account).commit()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }
}
