package com.example.ui.vault

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CloudQuotaInfo
import com.example.data.repository.CloudVaultClient
import com.example.data.repository.UserPreferencesRepository
import com.example.ui.theme.DangerRed
import com.example.ui.theme.SuccessGreen
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun VaultSettingsDialog(
    userPrefs: UserPreferencesRepository,
    cloudClient: CloudVaultClient,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val serverUrl by userPrefs.serverUrlFlow.collectAsState(initial = UserPreferencesRepository.DEFAULT_SERVER_URL)
    val secretIpCode by userPrefs.secretIpCodeFlow.collectAsState(initial = UserPreferencesRepository.DEFAULT_SECRET_IP)
    val authToken by userPrefs.authTokenFlow.collectAsState(initial = null)
    val authUsername by userPrefs.authUsernameFlow.collectAsState(initial = null)
    val isBiometricEnabled by userPrefs.biometricEnabledFlow.collectAsState(initial = false)

    var currentUrlInput by remember(serverUrl) { mutableStateOf(serverUrl) }
    var secretIpInput by remember(secretIpCode) { mutableStateOf(secretIpCode) }

    // PIN Change state
    var currentPinInput by remember { mutableStateOf("") }
    var newPinInput by remember { mutableStateOf("") }
    var pinMessage by remember { mutableStateOf<String?>(null) }

    // Auth state
    var authModeIsRegister by remember { mutableStateOf(false) }
    var usernameInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var isAuthLoading by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var quotaInfo by remember { mutableStateOf<CloudQuotaInfo?>(null) }

    LaunchedEffect(authToken) {
        if (!authToken.isNullOrEmpty()) {
            val res = cloudClient.fetchQuota(serverUrl, authToken!!)
            if (res.isSuccess) {
                quotaInfo = res.getOrNull()
            }
        }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
            bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Pengaturan Brankas & Cloud", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Security Section
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ubah PIN Akses", fontWeight = FontWeight.Bold)
                        }

                        OutlinedTextField(
                            value = currentPinInput,
                            onValueChange = { currentPinInput = it },
                            label = { Text("PIN Lama") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = newPinInput,
                            onValueChange = { newPinInput = it },
                            label = { Text("PIN Baru (4 Digit)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (pinMessage != null) {
                            Text(pinMessage!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    val validOld = userPrefs.verifyPin(currentPinInput)
                                    if (!validOld) {
                                        pinMessage = "PIN lama tidak sesuai!"
                                    } else if (newPinInput.length != 4) {
                                        pinMessage = "PIN baru harus 4 digit!"
                                    } else {
                                        userPrefs.setPin(newPinInput)
                                        pinMessage = "PIN berhasil diperbarui!"
                                        currentPinInput = ""
                                        newPinInput = ""
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Perbarui PIN")
                        }

                        // Biometrics toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Buka dengan Biometrik", style = MaterialTheme.typography.bodyMedium)
                            }
                            Switch(
                                checked = isBiometricEnabled,
                                onCheckedChange = {
                                    scope.launch { userPrefs.setBiometricEnabled(it) }
                                }
                            )
                        }
                    }
                }

                // Secret Trigger IP Setting Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Kode IP Pemicu Rahasia", fontWeight = FontWeight.Bold)
                        }

                        Text(
                            text = "Ketik alamat IP ini pada kolom input kalkulator utama, lalu tekan 'Hitung Subnet' untuk membuka brankas secara tersembunyi. Tidak ada tombol atau tanda rahasia yang terlihat pada antarmuka aplikasi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = secretIpInput,
                            onValueChange = { secretIpInput = it },
                            label = { Text("Alamat IP Pemicu") },
                            placeholder = { Text("Contoh: 192.168.99.99") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                if (secretIpInput.isNotBlank()) {
                                    scope.launch {
                                        userPrefs.setSecretIpCode(secretIpInput)
                                        Toast.makeText(context, "Kode IP rahasia berhasil disimpan: $secretIpInput", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Simpan Kode IP Pemicu")
                        }
                    }
                }

                // Cloud Backend Section (PHP Non-SQL)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Backend Server PHP (Non-SQL)", fontWeight = FontWeight.Bold)
                        }

                        OutlinedTextField(
                            value = currentUrlInput,
                            onValueChange = { currentUrlInput = it },
                            label = { Text("URL Endpoint API PHP") },
                            placeholder = { Text("http://domain-anda.com/api.php") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    userPrefs.setServerUrl(currentUrlInput)
                                    Toast.makeText(context, "URL Server Disimpan", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Simpan URL Server")
                        }

                        // Account status
                        if (authToken != null) {
                            Text(
                                text = "Akun Aktif: $authUsername",
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen
                            )

                            if (quotaInfo != null) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Kapasitas Cloud: ${formatFileSize(quotaInfo!!.usedBytes)} / ${formatFileSize(quotaInfo!!.totalBytes)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    val progress = if (quotaInfo!!.totalBytes > 0) quotaInfo!!.usedBytes.toFloat() / quotaInfo!!.totalBytes else 0f
                                    LinearProgressIndicator(
                                        progress = progress.coerceIn(0f, 1f),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        userPrefs.clearAuthSession()
                                        quotaInfo = null
                                        Toast.makeText(context, "Berhasil Keluar dari Akun Cloud", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Keluar (Logout)")
                            }
                        } else {
                            // Login / Register Form
                            Text(
                                text = if (authModeIsRegister) "Daftar Akun Cloud Baru" else "Masuk ke Akun Cloud",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            OutlinedTextField(
                                value = usernameInput,
                                onValueChange = { usernameInput = it },
                                label = { Text("Username") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                label = { Text("Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (authError != null) {
                                Text(authError!!, color = DangerRed, style = MaterialTheme.typography.bodySmall)
                            }

                            Button(
                                onClick = {
                                    if (usernameInput.isBlank() || passwordInput.isBlank()) {
                                        authError = "Username dan password tidak boleh kosong"
                                        return@Button
                                    }
                                    scope.launch {
                                        isAuthLoading = true
                                        authError = null
                                        if (authModeIsRegister) {
                                            val regRes = cloudClient.register(currentUrlInput, usernameInput, passwordInput)
                                            if (regRes.isSuccess) {
                                                Toast.makeText(context, "Pendaftaran berhasil! Silakan login.", Toast.LENGTH_SHORT).show()
                                                authModeIsRegister = false
                                            } else {
                                                authError = regRes.exceptionOrNull()?.message
                                            }
                                        } else {
                                            val logRes = cloudClient.login(currentUrlInput, usernameInput, passwordInput)
                                            if (logRes.isSuccess) {
                                                val (token, quota) = logRes.getOrThrow()
                                                userPrefs.saveAuthSession(token, usernameInput)
                                                quotaInfo = quota
                                                Toast.makeText(context, "Login Berhasil!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                authError = logRes.exceptionOrNull()?.message
                                            }
                                        }
                                        isAuthLoading = false
                                    }
                                },
                                enabled = !isAuthLoading,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isAuthLoading) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                                } else {
                                    Text(if (authModeIsRegister) "Daftar Akun" else "Masuk Akun")
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                OutlinedButton(onClick = {
                                    authModeIsRegister = !authModeIsRegister
                                    authError = null
                                }) {
                                    Text(if (authModeIsRegister) "Sudah punya akun? Masuk" else "Belum punya akun? Daftar")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}
