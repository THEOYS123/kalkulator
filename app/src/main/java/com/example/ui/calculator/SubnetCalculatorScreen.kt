package com.example.ui.calculator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CalculationHistoryItem
import com.example.data.model.SubnetResult
import com.example.data.model.VlsmPlanResult
import com.example.data.model.VlsmSubnetInput
import com.example.data.repository.SubnetCalculatorEngine
import com.example.ui.theme.AccentGold
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DangerRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TealSecondary
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubnetCalculatorScreen(
    onOpenVaultRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Kalkulator Subnet", "Perencana VLSM", "Konverter Biner", "Riwayat")

    // Subnet State
    var ipInput by remember { mutableStateOf("192.168.1.100") }
    var prefixInput by remember { mutableIntStateOf(24) }
    var subnetResult by remember { mutableStateOf<SubnetResult?>(SubnetCalculatorEngine.calculate("192.168.1.100", 24)) }
    var ipError by remember { mutableStateOf<String?>(null) }

    // History State
    val historyList = remember { mutableStateListOf<CalculationHistoryItem>() }

    // VLSM State
    var vlsmBaseIp by remember { mutableStateOf("192.168.1.0") }
    var vlsmBasePrefix by remember { mutableIntStateOf(24) }
    val vlsmRequirements = remember {
        mutableStateListOf(
            VlsmSubnetInput(UUID.randomUUID().toString(), "Lab Komputer A", 60),
            VlsmSubnetInput(UUID.randomUUID().toString(), "Lab Komputer B", 30),
            VlsmSubnetInput(UUID.randomUUID().toString(), "Ruang Guru & Staff", 14),
            VlsmSubnetInput(UUID.randomUUID().toString(), "Link Router Antar Gedung", 2)
        )
    }
    var vlsmResult by remember {
        mutableStateOf<VlsmPlanResult?>(
            SubnetCalculatorEngine.calculateVlsm("192.168.1.0", 24, vlsmRequirements)
        )
    }

    // Binary Converter State
    var decInput by remember { mutableStateOf("192") }
    var binOutput by remember { mutableStateOf("11000000") }
    var binInput by remember { mutableStateOf("11000000") }
    var decOutput by remember { mutableStateOf("192") }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label disalin ke papan klip", Toast.LENGTH_SHORT).show()
    }

    fun performSubnetCalculation() {
        if (!SubnetCalculatorEngine.isValidIpv4(ipInput)) {
            ipError = "Format IPv4 tidak valid (contoh: 192.168.1.1)"
            return
        }
        ipError = null
        val res = SubnetCalculatorEngine.calculate(ipInput, prefixInput)
        subnetResult = res
        historyList.add(
            0,
            CalculationHistoryItem(
                ip = res.ipAddress,
                prefix = res.prefix,
                network = res.networkAddress,
                usableRange = res.usableHostRange,
                totalHosts = res.totalHosts
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lan,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "NetSubnet TKJ",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Kalkulator Jaringan & Subnetting",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // Secret Vault Trigger with shield lock icon
                    IconButton(
                        onClick = onOpenVaultRequested,
                        modifier = Modifier
                            .testTag("vault_secret_trigger_button")
                            .padding(end = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Ruang Pribadi Rahasia",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> SubnetCalculatorTab(
                    ipInput = ipInput,
                    onIpChange = {
                        ipInput = it
                        // Secret easter egg: entering specific passcode triggers vault
                        if (it == "1234" || it == "7777" || it == "vault") {
                            onOpenVaultRequested()
                        }
                    },
                    prefixInput = prefixInput,
                    onPrefixChange = { prefixInput = it },
                    ipError = ipError,
                    subnetResult = subnetResult,
                    onCalculate = { performSubnetCalculation() },
                    onCopy = { label, text -> copyToClipboard(label, text) },
                    onReset = {
                        ipInput = "192.168.1.1"
                        prefixInput = 24
                        performSubnetCalculation()
                    }
                )
                1 -> VlsmPlannerTab(
                    baseIp = vlsmBaseIp,
                    onBaseIpChange = { vlsmBaseIp = it },
                    basePrefix = vlsmBasePrefix,
                    onBasePrefixChange = { vlsmBasePrefix = it },
                    requirements = vlsmRequirements,
                    onAddSubnet = {
                        vlsmRequirements.add(
                            VlsmSubnetInput(
                                UUID.randomUUID().toString(),
                                "Subnet ${vlsmRequirements.size + 1}",
                                10
                            )
                        )
                    },
                    onRemoveSubnet = { id -> vlsmRequirements.removeAll { it.id == id } },
                    onUpdateSubnet = { id, name, hosts ->
                        val idx = vlsmRequirements.indexOfFirst { it.id == id }
                        if (idx != -1) {
                            vlsmRequirements[idx] = VlsmSubnetInput(id, name, hosts)
                        }
                    },
                    vlsmResult = vlsmResult,
                    onCalculate = {
                        if (SubnetCalculatorEngine.isValidIpv4(vlsmBaseIp)) {
                            vlsmResult = SubnetCalculatorEngine.calculateVlsm(
                                vlsmBaseIp,
                                vlsmBasePrefix,
                                vlsmRequirements
                            )
                        } else {
                            Toast.makeText(context, "IP Base VLSM tidak valid", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onCopy = { label, text -> copyToClipboard(label, text) }
                )
                2 -> BinaryConverterTab(
                    decInput = decInput,
                    onDecInputChange = {
                        decInput = it
                        binOutput = SubnetCalculatorEngine.convertDecimalToBinary(it)
                    },
                    binOutput = binOutput,
                    binInput = binInput,
                    onBinInputChange = {
                        binInput = it
                        decOutput = SubnetCalculatorEngine.convertBinaryToDecimal(it)
                    },
                    decOutput = decOutput,
                    onCopy = { label, text -> copyToClipboard(label, text) }
                )
                3 -> HistoryTab(
                    history = historyList,
                    onSelectHistory = { item ->
                        ipInput = item.ip
                        prefixInput = item.prefix
                        performSubnetCalculation()
                        selectedTab = 0
                    },
                    onClearHistory = { historyList.clear() }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubnetCalculatorTab(
    ipInput: String,
    onIpChange: (String) -> Unit,
    prefixInput: Int,
    onPrefixChange: (Int) -> Unit,
    ipError: String?,
    subnetResult: SubnetResult?,
    onCalculate: () -> Unit,
    onCopy: (String, String) -> Unit,
    onReset: () -> Unit
) {
    val quickPrefixes = listOf(8, 16, 24, 25, 26, 27, 28, 29, 30, 31, 32)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Konfigurasi IP & Subnet Mask",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = ipInput,
                        onValueChange = onIpChange,
                        label = { Text("Alamat IP IPv4") },
                        placeholder = { Text("Contoh: 192.168.1.100") },
                        singleLine = true,
                        isError = ipError != null,
                        supportingText = {
                            if (ipError != null) {
                                Text(ipError, color = MaterialTheme.colorScheme.error)
                            } else {
                                Text("Mendukung format IPv4 standar (0.0.0.0 s/d 255.255.255.255)")
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { onCalculate() }),
                        leadingIcon = {
                            Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingIcon = {
                            if (ipInput.isNotEmpty()) {
                                IconButton(onClick = { onIpChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Hapus")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ip_input_field")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Prefix CIDR: /$prefixInput",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = SubnetCalculatorEngine.longToIp(SubnetCalculatorEngine.prefixToMaskLong(prefixInput)),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = prefixInput.toFloat(),
                        onValueChange = { onPrefixChange(it.toInt()) },
                        valueRange = 0f..32f,
                        steps = 31,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Quick Prefix Chips
                    Text(
                        text = "Preset Cepat:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        quickPrefixes.forEach { pfx ->
                            FilterChip(
                                selected = prefixInput == pfx,
                                onClick = {
                                    onPrefixChange(pfx)
                                    onCalculate()
                                },
                                label = { Text("/$pfx", fontSize = 12.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onReset,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset")
                        }

                        Button(
                            onClick = onCalculate,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .weight(1.5f)
                                .testTag("calculate_subnet_button")
                        ) {
                            Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Hitung Subnet")
                        }
                    }
                }
            }
        }

        if (subnetResult != null) {
            item {
                SubnetResultSection(result = subnetResult, onCopy = onCopy)
            }
        }
    }
}

@Composable
fun SubnetResultSection(
    result: SubnetResult,
    onCopy: (String, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Hasil Perhitungan Jaringan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(
                onClick = {
                    val report = buildString {
                        appendLine("=== NETSUBNET REPORT ===")
                        appendLine("IP Address: ${result.ipAddress}/${result.prefix}")
                        appendLine("Network: ${result.networkAddress}")
                        appendLine("Subnet Mask: ${result.subnetMask}")
                        appendLine("Wildcard: ${result.wildcardMask}")
                        appendLine("Broadcast: ${result.broadcastAddress}")
                        appendLine("Usable Range: ${result.usableHostRange}")
                        appendLine("Usable Hosts: ${result.usableHosts} / ${result.totalHosts}")
                        appendLine("Class: ${result.ipClass}")
                        appendLine("Type: ${result.ipType}")
                        appendLine("Binary IP: ${result.ipBinary}")
                        appendLine("Binary Mask: ${result.maskBinary}")
                    }
                    onCopy("Laporan Lengkap", report)
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Salin Laporan", fontSize = 12.sp)
            }
        }

        if (result.note != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = result.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Summary Key Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                title = "Host Dapat Dipakai",
                value = String.format("%,d", result.usableHosts),
                subtitle = "dari ${String.format("%,d", result.totalHosts)} total",
                color = SuccessGreen,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Kelas & Tipe IP",
                value = result.ipClass.substringBefore(" ("),
                subtitle = result.ipType.substringBefore(" ("),
                color = CyanPrimary,
                modifier = Modifier.weight(1f)
            )
        }

        // Detailed Items Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ResultDetailRow("Alamat Jaringan (Network)", result.networkAddress, onCopy)
                ResultDetailRow("Alamat Broadcast", result.broadcastAddress, onCopy)
                ResultDetailRow("Rentang Host Usable", result.usableHostRange, onCopy)
                ResultDetailRow("IP Host Pertama", result.firstUsableIp, onCopy)
                ResultDetailRow("IP Host Terakhir", result.lastUsableIp, onCopy)
                ResultDetailRow("Subnet Mask Desimal", result.subnetMask, onCopy)
                ResultDetailRow("Wildcard Mask", result.wildcardMask, onCopy)
                ResultDetailRow("Format Heksadesimal", result.ipHex, onCopy)
                ResultDetailRow("DNS Reverse (in-addr.arpa)", result.inAddrArpa, onCopy)
            }
        }

        // Binary Representation Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Representasi Biner (32-Bit)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(text = "IP Address Biner:", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = result.ipBinary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(
                        onClick = { onCopy("Biner IP", result.ipBinary) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Salin", modifier = Modifier.size(14.dp))
                    }
                }

                Text(text = "Subnet Mask Biner:", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = result.maskBinary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = { onCopy("Biner Mask", result.maskBinary) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Salin", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ResultDetailRow(
    label: String,
    value: String,
    onCopy: (String, String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCopy(label, value) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = "Salin $label",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = color)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun VlsmPlannerTab(
    baseIp: String,
    onBaseIpChange: (String) -> Unit,
    basePrefix: Int,
    onBasePrefixChange: (Int) -> Unit,
    requirements: List<VlsmSubnetInput>,
    onAddSubnet: () -> Unit,
    onRemoveSubnet: (String) -> Unit,
    onUpdateSubnet: (String, String, Int) -> Unit,
    vlsmResult: VlsmPlanResult?,
    onCalculate: () -> Unit,
    onCopy: (String, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Jaringan Induk (Major Network)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = baseIp,
                            onValueChange = onBaseIpChange,
                            label = { Text("Base Network IP") },
                            singleLine = true,
                            modifier = Modifier.weight(2f)
                        )
                        OutlinedTextField(
                            value = basePrefix.toString(),
                            onValueChange = { onBasePrefixChange(it.toIntOrNull()?.coerceIn(8, 30) ?: 24) },
                            label = { Text("Prefix /") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Kebutuhan Subnet (${requirements.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = onAddSubnet,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tambah Subnet", fontSize = 12.sp)
                }
            }
        }

        items(requirements) { req ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = req.name,
                        onValueChange = { onUpdateSubnet(req.id, it, req.requiredHosts) },
                        label = { Text("Nama Bagian/Ruang", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1.8f)
                    )

                    OutlinedTextField(
                        value = req.requiredHosts.toString(),
                        onValueChange = { onUpdateSubnet(req.id, req.name, it.toIntOrNull() ?: 1) },
                        label = { Text("Host Butuh", fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1.2f)
                    )

                    IconButton(
                        onClick = { onRemoveSubnet(req.id) },
                        enabled = requirements.size > 1
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = DangerRed)
                    }
                }
            }
        }

        item {
            Button(
                onClick = onCalculate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Calculate, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Alokasikan Subnetting VLSM", fontWeight = FontWeight.Bold)
            }
        }

        if (vlsmResult != null) {
            item {
                if (vlsmResult.isOverAllocated) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DangerRed.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "PERINGATAN: Kebutuhan host melebihi kapasitas subnet induk /${vlsmResult.basePrefix}! Beberapa subnet melampaui rentang alamat.",
                            color = DangerRed,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            items(vlsmResult.subnets) { sub ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = sub.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                Text("/${sub.prefix}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }

                        Text(
                            text = "Kebutuhan: ${sub.requiredHosts} host | Alokasi: ${sub.allocatedHosts} host",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        ResultDetailRow("Network", sub.networkAddress, onCopy)
                        ResultDetailRow("Subnet Mask", sub.subnetMask, onCopy)
                        ResultDetailRow("Rentang Usable", sub.usableRange, onCopy)
                        ResultDetailRow("Broadcast", sub.broadcastAddress, onCopy)
                    }
                }
            }
        }
    }
}

@Composable
fun BinaryConverterTab(
    decInput: String,
    onDecInputChange: (String) -> Unit,
    binOutput: String,
    binInput: String,
    onBinInputChange: (String) -> Unit,
    decOutput: String,
    onCopy: (String, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Konverter Desimal ke Biner",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = decInput,
                    onValueChange = onDecInputChange,
                    label = { Text("Angka Desimal (contoh: 192)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Hasil Biner:", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = binOutput,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 16.sp
                        )
                    }
                    IconButton(onClick = { onCopy("Hasil Biner", binOutput) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Salin")
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Konverter Biner ke Desimal",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TealSecondary
                )

                OutlinedTextField(
                    value = binInput,
                    onValueChange = onBinInputChange,
                    label = { Text("Kode Biner (contoh: 11000000)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Hasil Desimal:", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = decOutput,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TealSecondary,
                            fontSize = 16.sp
                        )
                    }
                    IconButton(onClick = { onCopy("Hasil Desimal", decOutput) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Salin")
                    }
                }
            }
        }

        // Educational Table for TKJ Students
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Tabel Referensi Nilai Posisi Bit (1 Oktet / 8 Bit)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                val bitWeights = listOf(128, 64, 32, 16, 8, 4, 2, 1)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    bitWeights.forEachIndexed { idx, weight ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Bit ${7 - idx}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$weight", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryTab(
    history: List<CalculationHistoryItem>,
    onSelectHistory: (CalculationHistoryItem) -> Unit,
    onClearHistory: () -> Unit
) {
    if (history.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Belum ada riwayat perhitungan",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Riwayat Perhitungan (${history.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(
                        onClick = onClearHistory,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Hapus Semua", fontSize = 12.sp)
                    }
                }
            }

            items(history) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectHistory(item) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${item.ip} /${item.prefix}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Network: ${item.network}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Usable: ${item.usableRange}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Muat perhitungan ini",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
