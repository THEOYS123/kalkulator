<?php
/**
 * NetSubnet Web Admin Dashboard - Panel Kontrol Non-SQL
 * Manajemen Pengguna, Cloud Storage, Konfigurasi Telegram, dan Pengaturan Sistem.
 */

session_start();
require_once __DIR__ . '/config.php';

// Auth Guard
if (empty($_SESSION['admin_logged_in'])) {
    header('Location: login.php');
    exit;
}

$activeTab = $_GET['tab'] ?? 'dashboard';
$flashMessage = null;
$flashError = null;

if (!empty($_GET['notice']) && $_GET['notice'] === 'configure_telegram') {
    $flashMessage = "Selamat datang! Harap segera konfigurasikan Bot Token dan Chat ID Telegram Anda di tab 'Telegram' untuk mengaktifkan proteksi 2FA penuh.";
}

// Handler Operasi POST
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $action = $_POST['action'] ?? '';

    // 1. Simpan Pengaturan Aplikasi
    if ($action === 'save_app_settings') {
        $config['app_name'] = trim($_POST['app_name'] ?? $config['app_name']);
        $config['announcement'] = trim($_POST['announcement'] ?? '');
        $config['maintenance_mode'] = isset($_POST['maintenance_mode']);
        $config['max_upload_size_mb'] = max(1, (int)($_POST['max_upload_size_mb'] ?? 50));
        $config['default_user_quota_mb'] = max(10, (int)($_POST['default_user_quota_mb'] ?? 1024));

        if (saveSystemConfig($storage, $config)) {
            $flashMessage = "Pengaturan aplikasi berhasil disimpan!";
            $storage->logAudit('CONFIG_UPDATE', $_SESSION['admin_username'], ['module' => 'app_settings']);
        } else {
            $flashError = "Gagal menyimpan konfigurasi ke file storage.";
        }
    }

    // 2. Simpan Konfigurasi Telegram
    if ($action === 'save_telegram_config') {
        $config['telegram_bot_token'] = trim($_POST['telegram_bot_token'] ?? '');
        $config['telegram_admin_chat_id'] = trim($_POST['telegram_admin_chat_id'] ?? '');
        $config['otp_expiry_minutes'] = max(1, (int)($_POST['otp_expiry_minutes'] ?? 5));

        if (saveSystemConfig($storage, $config)) {
            $flashMessage = "Konfigurasi Telegram berhasil diperbarui!";
            $storage->logAudit('CONFIG_UPDATE', $_SESSION['admin_username'], ['module' => 'telegram']);
            // Refresh service
            $telegramService = new NetSubnet\Core\TelegramService($storage, $config);
        } else {
            $flashError = "Gagal menyimpan konfigurasi Telegram.";
        }
    }

    // 3. Tes Kirim Pesan Telegram
    if ($action === 'test_telegram') {
        $testMsg = "🚀 <b>TES KONEKSI TELEGRAM BOT BERHASIL!</b>\n\nPanel Admin NetSubnet terhubung dengan baik ke Telegram Bot API.\n🕒 " . date('Y-m-d H:i:s');
        $res = $telegramService->sendMessage($testMsg);
        if ($res['success']) {
            $flashMessage = "Pesan uji coba berhasil terkirim ke Telegram Anda!";
        } else {
            $flashError = "Gagal mengirim pesan Telegram: " . $res['error'];
        }
    }

    // 4. Ubah Kredensial Administrator
    if ($action === 'save_admin_security') {
        $newUsername = trim($_POST['admin_username'] ?? '');
        $newPassword = $_POST['admin_password'] ?? '';

        if (!empty($newUsername)) {
            $config['admin_username'] = $newUsername;
            $_SESSION['admin_username'] = $newUsername;
        }

        if (!empty($newPassword)) {
            if (strlen($newPassword) < 6) {
                $flashError = "Password admin baru minimal 6 karakter.";
            } else {
                $config['admin_password_hash'] = password_hash($newPassword, PASSWORD_DEFAULT);
                $flashMessage = "Kredensial administrator berhasil diperbarui!";
            }
        }

        if (!$flashError) {
            saveSystemConfig($storage, $config);
            $storage->logAudit('ADMIN_SECURITY_UPDATE', $_SESSION['admin_username']);
        }
    }

    // 5. Toggle Status Pengguna (Aktif / Nonaktif)
    if ($action === 'toggle_user_status') {
        $targetUserId = $_POST['user_id'] ?? '';
        $userDoc = $storage->readJson("users/{$targetUserId}.json");
        if ($userDoc) {
            $newStatus = ($userDoc['status'] === 'active') ? 'suspended' : 'active';
            $userDoc['status'] = $newStatus;
            $storage->writeJson("users/{$targetUserId}.json", $userDoc);
            $flashMessage = "Status akun '{$userDoc['username']}' diubah menjadi: $newStatus";
            $storage->logAudit('USER_STATUS_CHANGE', $_SESSION['admin_username'], ['target' => $userDoc['username'], 'status' => $newStatus]);
        }
    }

    // 6. Ubah Kuota Pengguna
    if ($action === 'update_user_quota') {
        $targetUserId = $_POST['user_id'] ?? '';
        $newQuotaMb = (int)($_POST['quota_mb'] ?? 1024);
        $userDoc = $storage->readJson("users/{$targetUserId}.json");
        if ($userDoc) {
            $userDoc['storage_quota_bytes'] = $newQuotaMb * 1024 * 1024;
            $storage->writeJson("users/{$targetUserId}.json", $userDoc);
            $flashMessage = "Kuota penyimpanan akun '{$userDoc['username']}' diubah menjadi {$newQuotaMb} MB.";
        }
    }

    // 7. Hapus Akun Pengguna & Semua File Cloud-nya
    if ($action === 'delete_user') {
        $targetUserId = $_POST['user_id'] ?? '';
        $userDoc = $storage->readJson("users/{$targetUserId}.json");
        if ($userDoc) {
            $username = $userDoc['username'];
            // Hapus file fisik
            $storageDir = $storage->getDataDir() . "/storage/{$targetUserId}";
            if (is_dir($storageDir)) {
                $files = glob($storageDir . '/*');
                foreach ($files as $f) @unlink($f);
                @rmdir($storageDir);
            }
            // Hapus file metadata
            $filesMetaDir = $storage->getDataDir() . "/files/{$targetUserId}";
            if (is_dir($filesMetaDir)) {
                $mFiles = glob($filesMetaDir . '/*');
                foreach ($mFiles as $mf) @unlink($mf);
                @rmdir($filesMetaDir);
            }
            // Hapus dari index
            $userIndex = $storage->readJson('users_index.json', []);
            unset($userIndex[$username]);
            $storage->writeJson('users_index.json', $userIndex);
            // Hapus dokumen user
            $storage->deleteFile("users/{$targetUserId}.json");

            $flashMessage = "Akun '$username' dan semua data file telah dihapus permanen.";
            $storage->logAudit('USER_DELETE', $_SESSION['admin_username'], ['target' => $username]);
        }
    }

    // 8. Hapus File Cloud Tertentu (Admin File Management)
    if ($action === 'delete_cloud_file') {
        $fUserId = $_POST['file_user_id'] ?? '';
        $fId = $_POST['file_id'] ?? '';
        $metaPath = "files/{$fUserId}/{$fId}.json";
        $meta = $storage->readJson($metaPath);
        if ($meta) {
            $binPath = $storage->getDataDir() . "/storage/{$fUserId}/{$fId}.dat";
            @unlink($binPath);
            $storage->deleteFile($metaPath);

            // Sesuaikan kuota pengguna
            $uDoc = $storage->readJson("users/{$fUserId}.json");
            if ($uDoc) {
                $uDoc['used_bytes'] = max(0, ($uDoc['used_bytes'] ?? 0) - ($meta['size'] ?? 0));
                $uDoc['file_count'] = max(0, ($uDoc['file_count'] ?? 0) - 1);
                $storage->writeJson("users/{$fUserId}.json", $uDoc);
            }
            $flashMessage = "File '{$meta['name']}' berhasil dihapus dari cloud.";
            $storage->logAudit('ADMIN_DELETE_FILE', $_SESSION['admin_username'], ['file_id' => $fId]);
        }
    }
}

// Ambil Data Statistik Nyata dari File Storage
$userIndex = $storage->readJson('users_index.json', []);
$totalUsersCount = count($userIndex);
$activeUsersCount = 0;
$totalStorageBytes = 0;
$totalFilesCount = 0;
$usersList = [];

foreach ($userIndex as $uname => $uid) {
    $uData = $storage->readJson("users/{$uid}.json");
    if ($uData) {
        if (($uData['status'] ?? 'active') === 'active') $activeUsersCount++;
        $totalStorageBytes += (int)($uData['used_bytes'] ?? 0);
        $totalFilesCount += (int)($uData['file_count'] ?? 0);
        $usersList[] = $uData;
    }
}

// Ambil Semua File Cloud untuk File Explorer
$allCloudFiles = [];
foreach ($usersList as $u) {
    $uFilesDir = $storage->getDataDir() . "/files/{$u['id']}";
    if (is_dir($uFilesDir)) {
        $metaPaths = glob($uFilesDir . '/*.json');
        foreach ($metaPaths as $mp) {
            $m = json_decode(@file_get_contents($mp), true);
            if ($m) {
                $m['owner_username'] = $u['username'];
                $allCloudFiles[] = $m;
            }
        }
    }
}
usort($allCloudFiles, fn($a, $b) => ($b['uploaded_at'] ?? 0) <=> ($a['uploaded_at'] ?? 0));

// Ambil Audit Logs
$auditLogs = [];
$auditLogFile = $storage->getDataDir() . '/logs/audit.log';
if (file_exists($auditLogFile)) {
    $lines = file($auditLogFile, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES);
    $auditLogs = array_reverse(array_slice($lines, -50));
}

function formatBytes(int $bytes): string {
    if ($bytes >= 1073741824) return number_format($bytes / 1073741824, 2) . ' GB';
    if ($bytes >= 1048576) return number_format($bytes / 1048576, 2) . ' MB';
    if ($bytes >= 1024) return number_format($bytes / 1024, 2) . ' KB';
    return $bytes . ' B';
}
?>
<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><?= htmlspecialchars($config['app_name']) ?> - Dashboard Administrator</title>
    <style>
        :root {
            --bg-color: #070c12;
            --sidebar-bg: #0b1320;
            --card-bg: #0f172a;
            --primary: #00b4d8;
            --primary-hover: #0096c7;
            --text-main: #e6f1ff;
            --text-muted: #8892b0;
            --border: #1e293b;
            --danger: #ef476f;
            --success: #06d6a0;
            --warning: #ffb703;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen, Ubuntu, Cantarell, sans-serif; }
        body { background: var(--bg-color); color: var(--text-main); display: flex; min-height: 100vh; }
        
        /* Sidebar */
        aside { width: 250px; background: var(--sidebar-bg); border-right: 1px solid var(--border); display: flex; flex-direction: column; flex-shrink: 0; }
        .brand { padding: 24px 20px; border-bottom: 1px solid var(--border); display: flex; align-items: center; gap: 12px; }
        .brand-icon { width: 36px; height: 36px; border-radius: 8px; background: rgba(0, 180, 216, 0.15); border: 1px solid var(--primary); display: flex; align-items: center; justify-content: center; font-size: 18px; }
        .brand h2 { font-size: 16px; font-weight: 700; color: #fff; }
        .brand span { font-size: 11px; color: var(--primary); display: block; font-weight: 600; }
        .nav-menu { padding: 16px 10px; flex: 1; display: flex; flex-direction: column; gap: 4px; }
        .nav-item { display: flex; align-items: center; gap: 12px; padding: 12px 14px; border-radius: 8px; color: var(--text-muted); text-decoration: none; font-size: 14px; font-weight: 500; transition: all 0.2s; }
        .nav-item:hover, .nav-item.active { background: rgba(0, 180, 216, 0.12); color: var(--primary); }
        .sidebar-footer { padding: 16px; border-top: 1px solid var(--border); }
        .logout-btn { display: flex; align-items: center; gap: 8px; color: var(--danger); text-decoration: none; font-size: 13px; font-weight: 600; padding: 8px 12px; border-radius: 6px; }
        .logout-btn:hover { background: rgba(239, 71, 111, 0.1); }

        /* Main Content */
        main { flex: 1; padding: 30px; overflow-y: auto; }
        .topbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
        .topbar h1 { font-size: 24px; font-weight: 700; }
        .badge { display: inline-block; padding: 4px 10px; border-radius: 6px; font-size: 11px; font-weight: 700; text-transform: uppercase; }
        .badge-success { background: rgba(6, 214, 160, 0.15); color: var(--success); }
        .badge-danger { background: rgba(239, 71, 111, 0.15); color: var(--danger); }
        .badge-warning { background: rgba(255, 183, 3, 0.15); color: var(--warning); }
        
        /* Alerts */
        .alert { padding: 14px 18px; border-radius: 8px; font-size: 14px; margin-bottom: 20px; }
        .alert-success { background: rgba(6, 214, 160, 0.15); border: 1px solid var(--success); color: #72f8c8; }
        .alert-error { background: rgba(239, 71, 111, 0.15); border: 1px solid var(--danger); color: #ff8fa3; }

        /* Stat Grid */
        .stat-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; margin-bottom: 28px; }
        .stat-card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 12px; padding: 20px; }
        .stat-card h3 { font-size: 12px; font-weight: 600; text-transform: uppercase; color: var(--text-muted); margin-bottom: 8px; }
        .stat-card .val { font-size: 26px; font-weight: 700; color: #fff; margin-bottom: 4px; }
        .stat-card .sub { font-size: 12px; color: var(--primary); }

        /* Cards & Tables */
        .card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 12px; padding: 24px; margin-bottom: 24px; }
        .card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; }
        .card-header h2 { font-size: 17px; font-weight: 700; }
        table { width: 100%; border-collapse: collapse; font-size: 14px; }
        th { text-align: left; padding: 12px 14px; font-size: 12px; text-transform: uppercase; color: var(--text-muted); border-bottom: 1px solid var(--border); }
        td { padding: 14px; border-bottom: 1px solid var(--border); }
        tr:hover { background: rgba(255, 255, 255, 0.02); }

        /* Forms */
        .form-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 16px; }
        .form-group { margin-bottom: 16px; }
        label { display: block; font-size: 12px; font-weight: 600; text-transform: uppercase; color: var(--text-muted); margin-bottom: 6px; }
        input[type="text"], input[type="password"], input[type="number"], textarea, select { width: 100%; background: #070c12; border: 1px solid var(--border); border-radius: 8px; padding: 10px 14px; color: #fff; font-size: 14px; outline: none; }
        input:focus, textarea:focus { border-color: var(--primary); }
        .btn { display: inline-flex; align-items: center; gap: 8px; background: var(--primary); color: #001f29; border: none; border-radius: 8px; padding: 10px 18px; font-size: 14px; font-weight: 700; cursor: pointer; transition: all 0.2s; text-decoration: none; }
        .btn:hover { background: var(--primary-hover); }
        .btn-danger { background: var(--danger); color: #fff; }
        .btn-sm { padding: 6px 12px; font-size: 12px; }
        .audit-terminal { background: #000; border: 1px solid var(--border); border-radius: 8px; padding: 16px; font-family: monospace; font-size: 12px; color: #06d6a0; height: 320px; overflow-y: auto; line-height: 1.6; }

        @media(max-width: 768px) {
            body { flex-direction: column; }
            aside { width: 100%; height: auto; }
            .nav-menu { flex-direction: row; flex-wrap: wrap; }
            main { padding: 16px; }
        }
    </style>
</head>
<body>
    <!-- Sidebar Navigasi -->
    <aside>
        <div class="brand">
            <div class="brand-icon">🌐</div>
            <div>
                <h2>NetSubnet</h2>
                <span>NON-SQL ADMIN</span>
            </div>
        </div>

        <nav class="nav-menu">
            <a href="?tab=dashboard" class="nav-item <?= $activeTab === 'dashboard' ? 'active' : '' ?>">📊 Ringkasan</a>
            <a href="?tab=users" class="nav-item <?= $activeTab === 'users' ? 'active' : '' ?>">👥 Pengguna (<?= $totalUsersCount ?>)</a>
            <a href="?tab=files" class="nav-item <?= $activeTab === 'files' ? 'active' : '' ?>">☁️ File Cloud (<?= count($allCloudFiles) ?>)</a>
            <a href="?tab=telegram" class="nav-item <?= $activeTab === 'telegram' ? 'active' : '' ?>">🤖 Bot Telegram</a>
            <a href="?tab=settings" class="nav-item <?= $activeTab === 'settings' ? 'active' : '' ?>">⚙️ Pengaturan</a>
            <a href="?tab=audit" class="nav-item <?= $activeTab === 'audit' ? 'active' : '' ?>">📜 Log Audit</a>
        </nav>

        <div class="sidebar-footer">
            <div style="font-size: 11px; color: var(--text-muted); margin-bottom: 8px;">
                Admin: <b><?= htmlspecialchars($_SESSION['admin_username']) ?></b>
            </div>
            <a href="logout.php" class="logout-btn">🚪 Keluar (Logout)</a>
        </div>
    </aside>

    <!-- Konten Utama -->
    <main>
        <div class="topbar">
            <div>
                <h1>
                    <?php
                    switch ($activeTab) {
                        case 'users': echo 'Manajemen Pengguna'; break;
                        case 'files': echo 'Penjelajah File Cloud Privat'; break;
                        case 'telegram': echo 'Konfigurasi 2FA Telegram'; break;
                        case 'settings': echo 'Pengaturan Sistem & Keamanan'; break;
                        case 'audit': echo 'Catatan Audit Aktivitas'; break;
                        default: echo 'Ringkasan Sistem'; break;
                    }
                    ?>
                </h1>
                <span style="font-size: 13px; color: var(--text-muted);">Sistem Berjalan Tanpa Database SQL (Arsitektur File JSON Atomik)</span>
            </div>
            <div>
                <?php if ($config['maintenance_mode']): ?>
                    <span class="badge badge-warning">MAINTENANCE MODE ON</span>
                <?php else: ?>
                    <span class="badge badge-success">SERVER ONLINE</span>
                <?php endif; ?>
            </div>
        </div>

        <?php if ($flashMessage): ?>
            <div class="alert alert-success"><?= htmlspecialchars($flashMessage) ?></div>
        <?php endif; ?>
        <?php if ($flashError): ?>
            <div class="alert alert-error"><?= htmlspecialchars($flashError) ?></div>
        <?php endif; ?>

        <!-- TAB: DASHBOARD -->
        <?php if ($activeTab === 'dashboard'): ?>
            <div class="stat-grid">
                <div class="stat-card">
                    <h3>Total Akun Pengguna</h3>
                    <div class="val"><?= number_format($totalUsersCount) ?></div>
                    <div class="sub"><?= $activeUsersCount ?> pengguna aktif</div>
                </div>
                <div class="stat-card">
                    <h3>File Disimpan di Cloud</h3>
                    <div class="val"><?= number_format($totalFilesCount) ?></div>
                    <div class="sub">Tersinkronisasi dari Android</div>
                </div>
                <div class="stat-card">
                    <h3>Penggunaan Penyimpanan</h3>
                    <div class="val"><?= formatBytes($totalStorageBytes) ?></div>
                    <div class="sub">Total ukuran file privat</div>
                </div>
                <div class="stat-card">
                    <h3>Status Bot Telegram</h3>
                    <div class="val"><?= !empty($config['telegram_bot_token']) ? '🟢 Aktif' : '🔴 Belum Set' ?></div>
                    <div class="sub"><?= !empty($config['telegram_admin_chat_id']) ? 'Chat ID Siap' : 'Chat ID Kosong' ?></div>
                </div>
            </div>

            <div class="card">
                <div class="card-header">
                    <h2>Informasi Lingkungan Server</h2>
                </div>
                <table>
                    <tr><td width="220"><b>Arsitektur Database</b></td><td>Tanpa Database SQL (Zero-SQL Engine, Berbasis JSON Aman & flock)</td></tr>
                    <tr><td><b>Versi PHP</b></td><td><?= phpversion() ?></td></tr>
                    <tr><td><b>Batas Unggahan PHP</b></td><td>upload_max_filesize: <?= ini_get('upload_max_filesize') ?>, post_max_size: <?= ini_get('post_max_size') ?></td></tr>
                    <tr><td><b>Direktori Penyimpanan</b></td><td><code><?= htmlspecialchars($storage->getDataDir()) ?></code> (.htaccess Terpasang)</td></tr>
                    <tr><td><b>Batas Ukuran Upload Aplikasi</b></td><td><?= $config['max_upload_size_mb'] ?> MB per file</td></tr>
                    <tr><td><b>Pemberitahuan Sistem</b></td><td><?= htmlspecialchars($config['announcement']) ?></td></tr>
                </table>
            </div>

            <div class="card">
                <div class="card-header">
                    <h2>Aktivitas Terbaru</h2>
                </div>
                <div class="audit-terminal">
                    <?php if (empty($auditLogs)): ?>
                        > Belum ada riwayat aktivitas tercatat.
                    <?php else: ?>
                        <?php foreach (array_slice($auditLogs, 0, 10) as $log): ?>
                            <?= htmlspecialchars($log) ?><br>
                        <?php endforeach; ?>
                    <?php endif; ?>
                </div>
            </div>
        <?php endif; ?>

        <!-- TAB: MANAJEMEN PENGGUNA -->
        <?php if ($activeTab === 'users'): ?>
            <div class="card">
                <div class="card-header">
                    <h2>Daftar Akun Terdaftar (<?= count($usersList) ?>)</h2>
                </div>
                <?php if (empty($usersList)): ?>
                    <p style="color: var(--text-muted); font-size: 14px;">Belum ada pengguna terdaftar melalui aplikasi Android.</p>
                <?php else: ?>
                    <div style="overflow-x: auto;">
                        <table>
                            <thead>
                                <tr>
                                    <th>Username</th>
                                    <th>Status</th>
                                    <th>Kapasitas Terpakai</th>
                                    <th>Jumlah File</th>
                                    <th>Terdaftar</th>
                                    <th>Aksi</th>
                                </tr>
                            </thead>
                            <tbody>
                                <?php foreach ($usersList as $u): ?>
                                    <tr>
                                        <td><b><?= htmlspecialchars($u['username']) ?></b></td>
                                        <td>
                                            <?php if (($u['status'] ?? 'active') === 'active'): ?>
                                                <span class="badge badge-success">AKTIF</span>
                                            <?php else: ?>
                                                <span class="badge badge-danger">SUSPENDED</span>
                                            <?php endif; ?>
                                        </td>
                                        <td>
                                            <?= formatBytes((int)($u['used_bytes'] ?? 0)) ?> / <?= formatBytes((int)($u['storage_quota_bytes'] ?? 1073741824)) ?>
                                        </td>
                                        <td><?= number_format((int)($u['file_count'] ?? 0)) ?> file</td>
                                        <td><?= date('d-m-Y H:i', (int)($u['created_at'] ?? time())) ?></td>
                                        <td>
                                            <div style="display: flex; gap: 8px;">
                                                <form method="POST" style="display: inline;">
                                                    <input type="hidden" name="action" value="toggle_user_status">
                                                    <input type="hidden" name="user_id" value="<?= htmlspecialchars($u['id']) ?>">
                                                    <button type="submit" class="btn btn-sm">
                                                        <?= ($u['status'] ?? 'active') === 'active' ? 'Suspend' : 'Aktifkan' ?>
                                                    </button>
                                                </form>

                                                <form method="POST" style="display: inline;" onsubmit="return confirm('Apakah Anda yakin ingin menghapus akun ini beserta seluruh file privat miliknya?');">
                                                    <input type="hidden" name="action" value="delete_user">
                                                    <input type="hidden" name="user_id" value="<?= htmlspecialchars($u['id']) ?>">
                                                    <button type="submit" class="btn btn-sm btn-danger">Hapus</button>
                                                </form>
                                            </div>
                                        </td>
                                    </tr>
                                <?php endforeach; ?>
                            </tbody>
                        </table>
                    </div>
                <?php endif; ?>
            </div>
        <?php endif; ?>

        <!-- TAB: FILE CLOUD -->
        <?php if ($activeTab === 'files'): ?>
            <div class="card">
                <div class="card-header">
                    <h2>Seluruh File Cloud Privat (<?= count($allCloudFiles) ?>)</h2>
                </div>
                <?php if (empty($allCloudFiles)): ?>
                    <p style="color: var(--text-muted); font-size: 14px;">Belum ada file yang diunggah ke Cloud.</p>
                <?php else: ?>
                    <div style="overflow-x: auto;">
                        <table>
                            <thead>
                                <tr>
                                    <th>Nama File</th>
                                    <th>Pemilik Akun</th>
                                    <th>Tipe Konten</th>
                                    <th>Ukuran</th>
                                    <th>Waktu Unggah</th>
                                    <th>Checksum MD5</th>
                                    <th>Aksi</th>
                                </tr>
                            </thead>
                            <tbody>
                                <?php foreach ($allCloudFiles as $f): ?>
                                    <tr>
                                        <td><b><?= htmlspecialchars($f['name']) ?></b></td>
                                        <td><code><?= htmlspecialchars($f['owner_username']) ?></code></td>
                                        <td><?= htmlspecialchars($f['mime_type']) ?></td>
                                        <td><?= formatBytes((int)$f['size']) ?></td>
                                        <td><?= date('d-m-Y H:i', (int)$f['uploaded_at']) ?></td>
                                        <td><code style="font-size: 11px;"><?= htmlspecialchars(substr($f['checksum'] ?? '', 0, 10)) ?>...</code></td>
                                        <td>
                                            <form method="POST" onsubmit="return confirm('Hapus file ini dari cloud?');">
                                                <input type="hidden" name="action" value="delete_cloud_file">
                                                <input type="hidden" name="file_user_id" value="<?= htmlspecialchars($f['user_id']) ?>">
                                                <input type="hidden" name="file_id" value="<?= htmlspecialchars($f['id']) ?>">
                                                <button type="submit" class="btn btn-sm btn-danger">Hapus File</button>
                                            </form>
                                        </td>
                                    </tr>
                                <?php endforeach; ?>
                            </tbody>
                        </table>
                    </div>
                <?php endif; ?>
            </div>
        <?php endif; ?>

        <!-- TAB: TELEGRAM SETTINGS -->
        <?php if ($activeTab === 'telegram'): ?>
            <div class="card">
                <div class="card-header">
                    <h2>Konfigurasi Bot Telegram (2FA Admin)</h2>
                </div>
                <form method="POST">
                    <input type="hidden" name="action" value="save_telegram_config">

                    <div class="form-group">
                        <label>Telegram Bot Token</label>
                        <input type="text" name="telegram_bot_token" value="<?= htmlspecialchars($config['telegram_bot_token']) ?>" placeholder="Contoh: 123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ">
                        <span style="font-size: 12px; color: var(--text-muted); display: block; margin-top: 4px;">Dapatkan bot token dari @BotFather di Telegram.</span>
                    </div>

                    <div class="form-group">
                        <label>Chat ID Admin Telegram</label>
                        <input type="text" name="telegram_admin_chat_id" value="<?= htmlspecialchars($config['telegram_admin_chat_id']) ?>" placeholder="Contoh: 987654321">
                        <span style="font-size: 12px; color: var(--text-muted); display: block; margin-top: 4px;">Dapatkan Chat ID Anda via bot @userinfobot atau @getidsbot di Telegram.</span>
                    </div>

                    <div class="form-group">
                        <label>Masa Berlaku Kode OTP (Menit)</label>
                        <input type="number" name="otp_expiry_minutes" value="<?= (int)($config['otp_expiry_minutes'] ?? 5) ?>" min="1" max="30">
                    </div>

                    <button type="submit" class="btn">Simpan Konfigurasi Telegram</button>
                </form>
            </div>

            <div class="card">
                <div class="card-header">
                    <h2>Uji Koneksi Bot Telegram</h2>
                </div>
                <p style="font-size: 14px; color: var(--text-muted); margin-bottom: 16px;">
                    Kirim pesan tes ke Telegram untuk memastikan Bot Token dan Chat ID bekerja dengan benar.
                </p>
                <form method="POST">
                    <input type="hidden" name="action" value="test_telegram">
                    <button type="submit" class="btn">Kirim Pesan Uji Coba Telegram</button>
                </form>
            </div>
        <?php endif; ?>

        <!-- TAB: SETTINGS & SECURITY -->
        <?php if ($activeTab === 'settings'): ?>
            <div class="card">
                <div class="card-header">
                    <h2>Pengaturan Tampilan & Aplikasi</h2>
                </div>
                <form method="POST">
                    <input type="hidden" name="action" value="save_app_settings">

                    <div class="form-grid">
                        <div class="form-group">
                            <label>Nama Aplikasi</label>
                            <input type="text" name="app_name" value="<?= htmlspecialchars($config['app_name']) ?>" required>
                        </div>
                        <div class="form-group">
                            <label>Batas Ukuran Upload per File (MB)</label>
                            <input type="number" name="max_upload_size_mb" value="<?= (int)$config['max_upload_size_mb'] ?>" required>
                        </div>
                        <div class="form-group">
                            <label>Kuota Default Pengguna Baru (MB)</label>
                            <input type="number" name="default_user_quota_mb" value="<?= (int)$config['default_user_quota_mb'] ?>" required>
                        </div>
                    </div>

                    <div class="form-group">
                        <label>Teks Pengumuman Aplikasi</label>
                        <textarea name="announcement" rows="3"><?= htmlspecialchars($config['announcement']) ?></textarea>
                    </div>

                    <div class="form-group">
                        <label style="display: flex; align-items: center; gap: 8px; cursor: pointer;">
                            <input type="checkbox" name="maintenance_mode" <?= $config['maintenance_mode'] ? 'checked' : '' ?>>
                            <span>Aktifkan Maintenance Mode (Hentikan Akses API Sementara)</span>
                        </label>
                    </div>

                    <button type="submit" class="btn">Simpan Pengaturan Aplikasi</button>
                </form>
            </div>

            <div class="card">
                <div class="card-header">
                    <h2>Keamanan Akun Administrator</h2>
                </div>
                <form method="POST">
                    <input type="hidden" name="action" value="save_admin_security">

                    <div class="form-grid">
                        <div class="form-group">
                            <label>Username Admin Baru</label>
                            <input type="text" name="admin_username" value="<?= htmlspecialchars($config['admin_username']) ?>" required>
                        </div>
                        <div class="form-group">
                            <label>Password Admin Baru (Kosongkan jika tidak diubah)</label>
                            <input type="password" name="admin_password" placeholder="Minimal 6 karakter">
                        </div>
                    </div>

                    <button type="submit" class="btn">Perbarui Kredensial Admin</button>
                </form>
            </div>
        <?php endif; ?>

        <!-- TAB: AUDIT LOGS -->
        <?php if ($activeTab === 'audit'): ?>
            <div class="card">
                <div class="card-header">
                    <h2>Catatan Audit Keamanan (50 Aktivitas Terakhir)</h2>
                </div>
                <div class="audit-terminal">
                    <?php if (empty($auditLogs)): ?>
                        > Belum ada catatan log aktivitas.
                    <?php else: ?>
                        <?php foreach ($auditLogs as $line): ?>
                            <?= htmlspecialchars($line) ?><br>
                        <?php endforeach; ?>
                    <?php endif; ?>
                </div>
            </div>
        <?php endif; ?>
    </main>
</body>
</html>
