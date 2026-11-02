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

    suspend fun register(serverUrl: String, username: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = JSONObject().apply {
                put("username", username)
                put("password", password)
            }
            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("$serverUrl?action=register")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val respStr = response.body?.string() ?: ""
                val json = JSONObject(respStr)
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
            val jsonBody = JSONObject().apply {
                put("username", username)
                put("password", password)
            }
            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("$serverUrl?action=login")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val respStr = response.body?.string() ?: ""
                val json = JSONObject(respStr)
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
                    Result.failure(Exception(json.optString("error", "Login gagal ($respStr)")))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchQuota(serverUrl: String, token: String): Result<CloudQuotaInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$serverUrl?action=quota")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val respStr = response.body?.string() ?: ""
                val json = JSONObject(respStr)
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
            val request = Request.Builder()
                .url("$serverUrl?action=list_files")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val respStr = response.body?.string() ?: ""
                val json = JSONObject(respStr)
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
            val fileBody = decryptedFile.asRequestBody(item.mimeType.toMediaTypeOrNull())
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("name", item.name)
                .addFormDataPart("mime_type", item.mimeType)
                .addFormDataPart("file", item.name, fileBody)
                .build()

            val request = Request.Builder()
                .url("$serverUrl?action=upload")
                .header("Authorization", "Bearer $token")
                .post(multipart)
                .build()

            client.newCall(request).execute().use { response ->
                val respStr = response.body?.string() ?: ""
                val json = JSONObject(respStr)
                if (response.isSuccessful && json.optBoolean("success", false)) {
                    val cloudFileId = json.getString("file_id")
                    Result.success(cloudFileId)
                } else {
                    Result.failure(Exception(json.optString("error", "Upload gagal ($respStr)")))
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
            val request = Request.Builder()
                .url("$serverUrl?action=download&id=$cloudFileId")
                .header("Authorization", "Bearer $token")
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
            val request = Request.Builder()
                .url("$serverUrl?action=delete&id=$cloudFileId")
                .header("Authorization", "Bearer $token")
                .post("{}".toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            client.newCall(request).execute().use { response ->
                val respStr = response.body?.string() ?: ""
                val json = JSONObject(respStr)
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
