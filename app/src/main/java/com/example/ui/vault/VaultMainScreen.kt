package com.example.ui.vault

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.VaultCategory
import com.example.data.model.VaultItem
import com.example.data.repository.CloudVaultClient
import com.example.data.repository.UserPreferencesRepository
import com.example.data.repository.VaultRepository
import com.example.ui.theme.AccentGold
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DangerRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TealSecondary
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultMainScreen(
    vaultRepo: VaultRepository,
    userPrefs: UserPreferencesRepository,
    cloudClient: CloudVaultClient,
    onLockVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val vaultItems by vaultRepo.items.collectAsState()
    val authToken by userPrefs.authTokenFlow.collectAsState(initial = null)
    val authUsername by userPrefs.authUsernameFlow.collectAsState(initial = null)
    val serverUrl by userPrefs.serverUrlFlow.collectAsState(initial = UserPreferencesRepository.DEFAULT_SERVER_URL)

    var selectedCategory by remember { mutableStateOf(VaultCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Multi-selection
    val selectedItemIds = remember { mutableStateListOf<String>() }
    val isSelectionMode = selectedItemIds.isNotEmpty()

    // Dialogs
    var itemToPreview by remember { mutableStateOf<VaultItem?>(null) }
    var itemToRename by remember { mutableStateOf<VaultItem?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showCloudSyncDialog by remember { mutableStateOf(false) }
    var isUploadingToCloud by remember { mutableStateOf(false) }
    var uploadStatusMessage by remember { mutableStateOf<String?>(null) }

    // SAF Import Launcher
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                var successCount = 0
                uris.forEach { uri ->
                    val result = vaultRepo.importFile(uri)
                    if (result.isSuccess) successCount++
                }
                Toast.makeText(context, "$successCount file berhasil diamankan ke brankas!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Export single file launcher
    var pendingExportItem by remember { mutableStateOf<VaultItem?>(null) }
    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val item = pendingExportItem
        if (uri != null && item != null) {
            scope.launch {
                val res = vaultRepo.exportFileToUri(item, uri)
                if (res.isSuccess) {
                    Toast.makeText(context, "File '${item.name}' berhasil dipulihkan ke perangkat!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Gagal memulihkan file: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
                pendingExportItem = null
            }
        }
    }

    // Filtered items
    val filteredItems = vaultItems.filter { item ->
        val matchesCategory = selectedCategory == VaultCategory.ALL || item.category == selectedCategory
        val matchesSearch = searchQuery.isEmpty() || item.name.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesSearch
    }

    val totalVaultSize = vaultItems.sumOf { it.fileSize }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
            bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    fun syncItemToCloud(item: VaultItem) {
        val token = authToken
        if (token.isNullOrEmpty()) {
            Toast.makeText(context, "Silakan login ke Akun Cloud terlebih dahulu di Pengaturan", Toast.LENGTH_LONG).show()
            showSettingsDialog = true
            return
        }

        scope.launch {
            isUploadingToCloud = true
            uploadStatusMessage = "Mengunggah '${item.name}' ke cloud..."
            try {
                val tempFile = vaultRepo.getDecryptedTempPreview(item)
                val uploadRes = cloudClient.uploadVaultItem(serverUrl, token, item, tempFile)
                tempFile.delete()

                if (uploadRes.isSuccess) {
                    val cloudId = uploadRes.getOrThrow()
                    vaultRepo.markCloudSynced(item.id, cloudId)
                    Toast.makeText(context, "Berhasil diunggah ke Cloud privat!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Upload gagal: ${uploadRes.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isUploadingToCloud = false
                uploadStatusMessage = null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Cari nama file...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (isSelectionMode) {
                        Text("${selectedItemIds.size} dipilih", fontWeight = FontWeight.Bold)
                    } else {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Brankas Pribadi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Badge(containerColor = CyanPrimary) {
                                    Text("ENCRYPTED", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(
                                text = "${vaultItems.size} file (${formatFileSize(totalVaultSize)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { selectedItemIds.clear() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Batal Pilih")
                        }
                    } else if (isSearchActive) {
                        IconButton(onClick = {
                            isSearchActive = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Tutup Pencarian")
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Hapus Terpilih", tint = DangerRed)
                        }
                    } else {
                        IconButton(onClick = { isSearchActive = !isSearchActive }) {
                            Icon(Icons.Default.Search, contentDescription = "Cari File")
                        }
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Pengaturan Vault")
                        }
                        IconButton(
                            onClick = onLockVault,
                            modifier = Modifier.testTag("lock_vault_button")
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Kunci Brankas", tint = DangerRed)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // Open Android storage document / photo picker
                    importFileLauncher.launch(arrayOf("*/*"))
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("import_file_fab")
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Impor File", fontWeight = FontWeight.Bold)
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Cloud Status Banner
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (authToken != null) Icons.Default.CloudDone else Icons.Default.Cloud,
                            contentDescription = null,
                            tint = if (authToken != null) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (authToken != null) "Cloud: $authUsername" else "Cloud: Belum Terhubung",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (authToken != null) "Backend PHP Non-SQL Aktif" else "Atur server di Pengaturan",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { showCloudSyncDialog = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cloud Sync", fontSize = 11.sp)
                    }
                }
            }

            if (isUploadingToCloud) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                uploadStatusMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            // Categories Scrollable Tabs
            ScrollableTabRow(
                selectedTabIndex = VaultCategory.values().indexOf(selectedCategory),
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                VaultCategory.values().forEach { cat ->
                    val count = if (cat == VaultCategory.ALL) vaultItems.size else vaultItems.count { it.category == cat }
                    Tab(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        text = {
                            Text(
                                text = "${cat.displayName} ($count)",
                                fontWeight = if (selectedCategory == cat) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // Empty State or Content
            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Archive,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Tidak ada file yang cocok dengan pencarian" else "Belum ada file di kategori ${selectedCategory.displayName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Gunakan tombol '+ Impor File' untuk mengamankan foto, video, ZIP, atau dokumen dari memori perangkat.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                // If Photos category is active, render a 3-column media grid
                if (selectedCategory == VaultCategory.PHOTOS) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredItems, key = { it.id }) { item ->
                            VaultPhotoGridItem(
                                item = item,
                                vaultRepo = vaultRepo,
                                isSelected = selectedItemIds.contains(item.id),
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) {
                                        if (selectedItemIds.contains(item.id)) selectedItemIds.remove(item.id)
                                        else selectedItemIds.add(item.id)
                                    } else {
                                        itemToPreview = item
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        selectedItemIds.add(item.id)
                                    }
                                }
                            )
                        }
                    }
                } else {
                    // List view for documents, videos, archives, and all files
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredItems, key = { it.id }) { item ->
                            VaultFileListCard(
                                item = item,
                                isSelected = selectedItemIds.contains(item.id),
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) {
                                        if (selectedItemIds.contains(item.id)) selectedItemIds.remove(item.id)
                                        else selectedItemIds.add(item.id)
                                    } else {
                                        itemToPreview = item
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        selectedItemIds.add(item.id)
                                    }
                                },
                                onExport = {
                                    pendingExportItem = item
                                    exportFileLauncher.launch(item.name)
                                },
                                onSyncCloud = { syncItemToCloud(item) },
                                onRename = { itemToRename = item },
                                onDelete = {
                                    scope.launch {
                                        vaultRepo.deleteItem(item)
                                        Toast.makeText(context, "File '${item.name}' dihapus dari brankas", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog: Delete confirmation
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Konfirmasi Penghapusan") },
            text = {
                Text("Apakah Anda yakin ingin menghapus ${selectedItemIds.size} file terpilih dari brankas privat? File yang dihapus dari brankas tidak dapat dipulihkan kecuali tersimpan di Cloud.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val itemsToDelete = vaultItems.filter { selectedItemIds.contains(it.id) }
                            itemsToDelete.forEach { vaultRepo.deleteItem(it) }
                            selectedItemIds.clear()
                            showDeleteConfirmDialog = false
                            Toast.makeText(context, "${itemsToDelete.size} file dihapus", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Hapus Permanen")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Dialog: Rename
    if (itemToRename != null) {
        val target = itemToRename!!
        var newNameInput by remember { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            title = { Text("Ubah Nama File") },
            text = {
                OutlinedTextField(
                    value = newNameInput,
                    onValueChange = { newNameInput = it },
                    label = { Text("Nama File") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newNameInput.isNotBlank()) {
                            scope.launch {
                                vaultRepo.renameItem(target, newNameInput.trim())
                                itemToRename = null
                            }
                        }
                    }
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { itemToRename = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // Dialog: File Preview
    if (itemToPreview != null) {
        VaultFileViewerDialog(
            item = itemToPreview!!,
            vaultRepo = vaultRepo,
            onDismiss = { itemToPreview = null },
            onExport = {
                pendingExportItem = itemToPreview
                exportFileLauncher.launch(itemToPreview!!.name)
            },
            onSyncCloud = { syncItemToCloud(itemToPreview!!) }
        )
    }

    // Dialog: Settings
    if (showSettingsDialog) {
        VaultSettingsDialog(
            userPrefs = userPrefs,
            cloudClient = cloudClient,
            onDismiss = { showSettingsDialog = false }
        )
    }

    // Dialog: Cloud Sync Explorer
    if (showCloudSyncDialog) {
        CloudSyncDialog(
            userPrefs = userPrefs,
            cloudClient = cloudClient,
            vaultRepo = vaultRepo,
            onDismiss = { showCloudSyncDialog = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultPhotoGridItem(
    item: VaultItem,
    vaultRepo: VaultRepository,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val tempFile = remember(item.id) {
        try {
            vaultRepo.getDecryptedTempPreview(item)
        } catch (e: Exception) {
            null
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        if (tempFile != null && tempFile.exists()) {
            AsyncImage(
                model = tempFile,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (item.isCloudSynced) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(SuccessGreen)
                    .padding(2.dp)
            ) {
                Icon(Icons.Default.CloudDone, contentDescription = "Tersinkron Cloud", tint = Color.Black, modifier = Modifier.size(12.dp))
            }
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Dipilih", tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultFileListCard(
    item: VaultItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onExport: () -> Unit,
    onSyncCloud: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
            bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    val iconVector = when (item.category) {
        VaultCategory.PHOTOS -> Icons.Default.Image
        VaultCategory.VIDEOS -> Icons.Default.Movie
        VaultCategory.DOCUMENTS -> Icons.Default.Description
        VaultCategory.ARCHIVES -> Icons.Default.Archive
        else -> Icons.Default.Description
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${formatFileSize(item.fileSize)} • ${dateFormat.format(Date(item.createdAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (item.isCloudSynced) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = "Cloud Synced",
                            tint = SuccessGreen,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            if (!isSelectionMode) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Pulihkan ke Perangkat") },
                            onClick = {
                                showMenu = false
                                onExport()
                            },
                            leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Unggah ke Cloud") },
                            onClick = {
                                showMenu = false
                                onSyncCloud()
                            },
                            leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Ubah Nama") },
                            onClick = {
                                showMenu = false
                                onRename()
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Hapus dari Brankas", color = DangerRed) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = DangerRed) }
                        )
                    }
                }
            }
        }
    }
}
