package com.example.data.model

enum class VaultCategory(val displayName: String) {
    ALL("Semua"),
    PHOTOS("Foto"),
    VIDEOS("Video"),
    DOCUMENTS("Dokumen"),
    ARCHIVES("Arsip ZIP"),
    OTHER("Lainnya")
}

data class VaultItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val originalFileName: String,
    val mimeType: String,
    val fileSize: Long,
    val category: VaultCategory,
    val localEncryptedPath: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isCloudSynced: Boolean = false,
    val cloudFileId: String? = null,
    val cloudUploadedAt: Long? = null,
    val checksum: String? = null
)

data class CloudFileItem(
    val id: String,
    val name: String,
    val originalName: String,
    val mimeType: String,
    val size: Long,
    val uploadedAt: Long,
    val checksum: String
)

data class CloudQuotaInfo(
    val usedBytes: Long,
    val totalBytes: Long,
    val fileCount: Int,
    val username: String
)
