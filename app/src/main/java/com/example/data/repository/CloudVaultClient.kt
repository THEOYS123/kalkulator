package com.example.data.repository

import com.example.data.crypto.FileCryptoManager
import com.example.data.model.CloudFileItem
import com.example.data.model.CloudQuotaInfo
import com.example.data.model.VaultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class CloudVaultClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        return if (!trimmed.endsWith(".php") && !trimmed.contains("api.php")) {
            "${trimmed.trimEnd('/')}/api.php"
        } else {
            trimmed
        }
    }

    private fun parseJsonResponse(respStr: String, httpCode: Int): JSONObject {
        val trimmed = respStr.trim()
        if (trimmed.startsWith("<") || trimmed.contains("<html", ignoreCase = true)) {
            when {
                trimmed.contains("aes.js") || trimmed.contains("requires Javascript", ignoreCase = true) || trimmed.contains("InfinityFree", ignoreCase = true) -> {
                    throw IllegalStateException("Hosting InfinityFree Free Plan memblokir API Android dengan proteksi bot browser (TestCookie/aes.js). Solusi: gunakan hosting API yang mendukung aplikasi mobile seperti Render.com, 000webhost, cPanel, atau XAMPP lokal.")
                }
                httpCode == 404 -> {
                    throw IllegalStateException("File api.php tidak ditemukan di server (HTTP 404). Pastikan file api.php sudah diunggah ke folder htdocs.")
                }
                httpCode in 500..599 -> {
                    throw IllegalStateException("Terjadi kesalahan internal server (HTTP $httpCode). Periksa error log di hosting.")
                }
                else -> {
                    throw IllegalStateException("Server mengembalikan halaman web HTML (bukan JSON API). Periksa kembali URL endpoint di Pengaturan.")
                }
            }
        }
        return try {
            JSONObject(trimmed)
        } catch (e: Exception) {
            throw IllegalStateException("Format respon server tidak valid: ${trimmed.take(120)}")
        }
    }

    suspend fun register(serverUrl: String, username: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val endpoint = normalizeUrl(serverUrl)
            val jsonBody = JSONObject().apply {
                put("username", username)
                put("password", password)
            }
            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("$endpoint?action=register")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; NetSubnet App)")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val respStr = response.body?.string() ?: ""
                val json = parseJsonResponse(respStr, response.code)
                if (response.isSuccessful && json.optBoolean("success", false)) {
                    Result.success(json.optString("message", "Pendaftaran berhasil"))
                } else {
                    Result.failure(Exception(json.optString("error", "Pendaftaran gagal ($respStr)")))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(serverUrl: String, username: String, password: String): Result<Pair<String, CloudQuotaInfo>> = withContext(Dispatchers.IO) {
        try {
            val endpoint = normalizeUrl(serverUrl)
            val jsonBody = JSONObject().apply {
                put("username", username)
                put("password", password)
            }
            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("$endpoint?action=login")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; NetSubnet App)")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val respStr = response.body?.string() ?: ""
                val json = parseJsonResponse(respStr, response.code)
                if (response.isSuccessful && json.optBoolean("success", false)) {
                    val token = json.getString("token")
                    val quotaObj = json.optJSONObject("quota")
                    val quotaInfo = CloudQuotaInfo(
                        usedBytes = quotaObj?.optLong("used_bytes") ?: 0L,
                        totalBytes = quotaObj?.optLong("total_bytes") ?: 1073741824L,
                        fileCount = quotaObj?.optInt("file_count") ?: 0,
                        username = username
                    )
                    Result.success(Pair(token, quotaInfo))
                } else {
                    Result.failure(Exception(json.optString("error", "Login gagal: ${json.optString("error")}")))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchQuota(serverUrl: String, token: String): Result<CloudQuotaInfo> = withContext(Dispatchers.IO) {
        try {
            val endpoint = normalizeUrl(serverUrl)
            val request = Request.Builder()
                .url("$endpoint?action=quota")
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; NetSubnet App)")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val respStr = response.body?.string() ?: ""
                val json = parseJsonResponse(respStr, response.code)
                if (response.isSuccessful && json.optBoolean("success", false)) {
                    val quotaObj = json.getJSONObject("quota")
                    Result.success(
                        CloudQuotaInfo(
                            usedBytes = quotaObj.optLong("used_bytes"),
                            totalBytes = quotaObj.optLong("total_bytes"),
                            fileCount = quotaObj.optInt("file_count"),
                            username = json.optString("username", "")
                        )
                    )
                } else {
                    Result.failure(Exception(json.optString("error", "Gagal mengambil data kuota")))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listCloudFiles(serverUrl: String, token: String): Result<List<CloudFileItem>> = withContext(Dispatchers.IO) {
        try {
            val endpoint = normalizeUrl(serverUrl)
            val request = Request.Builder()
                .url("$endpoint?action=list_files")
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; NetSubnet App)")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val respStr = response.body?.string() ?: ""
                val json = parseJsonResponse(respStr, response.code)
                if (response.isSuccessful && json.optBoolean("success", false)) {
                    val filesArray = json.optJSONArray("files") ?: JSONArray()
                    val list = mutableListOf<CloudFileItem>()
                    for (i in 0 until filesArray.length()) {
                        val f = filesArray.getJSONObject(i)
                        list.add(
                            CloudFileItem(
                                id = f.getString("id"),
                                name = f.getString("name"),
                                originalName = f.getString("original_name"),
                                mimeType = f.optString("mime_type", "application/octet-stream"),
                                size = f.optLong("size", 0L),
                                uploadedAt = f.optLong("uploaded_at", 0L),
                                checksum = f.optString("checksum", "")
                            )
                        )
                    }
                    Result.success(list)
                } else {
                    Result.failure(Exception(json.optString("error", "Gagal mengambil daftar file cloud")))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadVaultItem(
        serverUrl: String,
        token: String,
        item: VaultItem,
        decryptedFile: File,
        onProgress: ((Float) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val endpoint = normalizeUrl(serverUrl)
            val fileBody = decryptedFile.asRequestBody(item.mimeType.toMediaTypeOrNull())
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("name", item.name)
                .addFormDataPart("mime_type", item.mimeType)
                .addFormDataPart("file", item.name, fileBody)
                .build()

            val request = Request.Builder()
                .url("$endpoint?action=upload")
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; NetSubnet App)")
                .post(multipart)
                .build()

            client.newCall(request).execute().use { response ->
                val respStr = response.body?.string() ?: ""
                val json = parseJsonResponse(respStr, response.code)
                if (response.isSuccessful && json.optBoolean("success", false)) {
                    val cloudFileId = json.getString("file_id")
                    Result.success(cloudFileId)
                } else {
                    Result.failure(Exception(json.optString("error", "Upload gagal: ${json.optString("error")}")))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadCloudFile(
        serverUrl: String,
        token: String,
        cloudFileId: String,
        targetFile: File
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val endpoint = normalizeUrl(serverUrl)
            val request = Request.Builder()
                .url("$endpoint?action=download&id=$cloudFileId")
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; NetSubnet App)")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP Error ${response.code} saat mengunduh"))
                }
                val body = response.body ?: return@withContext Result.failure(Exception("Body kosong"))
                FileOutputStream(targetFile).use { fos ->
                    body.byteStream().use { input ->
                        input.copyTo(fos)
                    }
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCloudFile(
        serverUrl: String,
        token: String,
        cloudFileId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val endpoint = normalizeUrl(serverUrl)
            val request = Request.Builder()
                .url("$endpoint?action=delete&id=$cloudFileId")
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; NetSubnet App)")
                .post("{}".toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            client.newCall(request).execute().use { response ->
                val respStr = response.body?.string() ?: ""
                val json = parseJsonResponse(respStr, response.code)
                if (response.isSuccessful && json.optBoolean("success", false)) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(json.optString("error", "Gagal menghapus file dari cloud")))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
