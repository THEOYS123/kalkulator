package com.example.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object FileCryptoManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "NetSubnetVaultMasterKey"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts input stream into target destination file using AES-256 GCM.
     * Writes 12-byte IV at the beginning of the file.
     */
    fun encryptStreamToFile(sourceInput: InputStream, destinationEncryptedFile: File) {
        val secretKey = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv

        FileOutputStream(destinationEncryptedFile).use { fos ->
            fos.write(iv) // Write IV prefix
            CipherOutputStream(fos, cipher).use { cos ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (sourceInput.read(buffer).also { bytesRead = it } != -1) {
                    cos.write(buffer, 0, bytesRead)
                }
                cos.flush()
            }
        }
    }

    /**
     * Decrypts encrypted file into target output stream.
     */
    fun decryptFileToStream(encryptedSourceFile: File, destinationOutput: OutputStream) {
        val secretKey = getOrCreateKey()
        FileInputStream(encryptedSourceFile).use { fis ->
            val iv = ByteArray(GCM_IV_LENGTH)
            val ivRead = fis.read(iv)
            if (ivRead != GCM_IV_LENGTH) {
                throw IllegalStateException("Format file terenkripsi tidak valid (IV korup)")
            }

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            CipherInputStream(fis, cipher).use { cis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (cis.read(buffer).also { bytesRead = it } != -1) {
                    destinationOutput.write(buffer, 0, bytesRead)
                }
                destinationOutput.flush()
            }
        }
    }

    /**
     * Decrypts into temporary unencrypted cache file for media previewing.
     */
    fun decryptToTempFile(context: Context, encryptedSourceFile: File, fileName: String): File {
        val tempDir = File(context.cacheDir, "vault_preview")
        if (!tempDir.exists()) tempDir.mkdirs()
        val tempFile = File(tempDir, "preview_${System.currentTimeMillis()}_$fileName")
        tempFile.deleteOnExit()
        FileOutputStream(tempFile).use { fos ->
            decryptFileToStream(encryptedSourceFile, fos)
        }
        return tempFile
    }

    fun calculateMd5(file: File): String {
        val digest = java.security.MessageDigest.getInstance("MD5")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
