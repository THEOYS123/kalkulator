<?php
/**
 * NetSubnet REST API - Endpoint untuk Aplikasi Android
 * Mendukung autentikasi Bearer, kuota penyimpanan, upload/download/delete file privat
 * Sepenuhnya bebas dari database SQL.
 */

header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

require_once __DIR__ . '/config.php';

$action = $_GET['action'] ?? '';
$authHeader = $_SERVER['HTTP_AUTHORIZATION'] ?? '';

// Helper pengirim response JSON
function jsonResponse(array $data, int $statusCode = 200): void {
    http_response_code($statusCode);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode($data, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    exit;
}

// 1. Endpoint Konfigurasi Umum
if ($action === 'config') {
    jsonResponse([
        'success' => true,
        'app_name' => $config['app_name'],
        'announcement' => $config['announcement'],
        'maintenance_mode' => $config['maintenance_mode'],
        'max_upload_size_mb' => $config['max_upload_size_mb']
    ]);
}

// Periksa mode maintenance
if ($config['maintenance_mode'] && $action !== 'config') {
    jsonResponse(['success' => false, 'error' => 'Server sedang dalam pemeliharaan (Maintenance Mode).'], 503);
}

// 2. Registrasi Pengguna
if ($action === 'register') {
    $rawInput = file_get_contents('php://input');
    $body = json_decode($rawInput, true) ?? $_POST;
    $username = $body['username'] ?? '';
    $password = $body['password'] ?? '';

    $res = $authService->registerUser($username, $password);
    if ($res['success']) {
        jsonResponse(['success' => true, 'message' => 'Registrasi akun berhasil. Silakan login.']);
    } else {
        jsonResponse(['success' => false, 'error' => $res['error']], 400);
    }
}

// 3. Login Pengguna
if ($action === 'login') {
    $rawInput = file_get_contents('php://input');
    $body = json_decode($rawInput, true) ?? $_POST;
    $username = $body['username'] ?? '';
    $password = $body['password'] ?? '';

    $res = $authService->loginUser($username, $password);
    if ($res['success']) {
        jsonResponse([
            'success' => true,
            'token' => $res['token'],
            'username' => $res['user']['username'],
            'quota' => [
                'used_bytes' => $res['user']['used_bytes'],
                'total_bytes' => $res['user']['storage_quota'],
                'file_count' => $res['user']['file_count']
            ]
        ]);
    } else {
        jsonResponse(['success' => false, 'error' => $res['error']], 401);
    }
}

// === Endpoint Terproteksi Token Bearer ===
$currentUser = $authService->authenticateBearerToken($authHeader);
if (!$currentUser) {
    jsonResponse(['success' => false, 'error' => 'Akses ditolak: Token autentikasi tidak valid atau telah kedaluwarsa.'], 401);
}

$userId = $currentUser['id'];

// 4. Kuota Penyimpanan Pengguna
if ($action === 'quota') {
    // Muat ulang data terbaru
    $userDoc = $storage->readJson("users/{$userId}.json");
    jsonResponse([
        'success' => true,
        'username' => $userDoc['username'],
        'quota' => [
            'used_bytes' => $userDoc['used_bytes'] ?? 0,
            'total_bytes' => $userDoc['storage_quota_bytes'] ?? 1073741824,
            'file_count' => $userDoc['file_count'] ?? 0
        ]
    ]);
}

// 5. Daftar File Cloud Pengguna
if ($action === 'list_files') {
    $userFilesDir = $storage->getDataDir() . "/files/{$userId}";
    $files = [];

    if (is_dir($userFilesDir)) {
        $metaFiles = glob($userFilesDir . '/*.json');
        foreach ($metaFiles as $metaPath) {
            $meta = json_decode(@file_get_contents($metaPath), true);
            if ($meta) {
                $files[] = [
                    'id' => $meta['id'],
                    'name' => $meta['name'],
                    'original_name' => $meta['original_name'],
                    'mime_type' => $meta['mime_type'],
                    'size' => $meta['size'],
                    'uploaded_at' => $meta['uploaded_at'],
                    'checksum' => $meta['checksum']
                ];
            }
        }
    }

    // Urutkan terbaru
    usort($files, fn($a, $b) => $b['uploaded_at'] <=> $a['uploaded_at']);

    jsonResponse(['success' => true, 'files' => $files]);
}

// 6. Upload File ke Cloud
if ($action === 'upload') {
    if (!isset($_FILES['file']) || $_FILES['file']['error'] !== UPLOAD_ERR_OK) {
        jsonResponse(['success' => false, 'error' => 'File tidak ditemukan dalam form upload atau terjadi kesalahan transfer.'], 400);
    }

    $uploadedFile = $_FILES['file'];
    $fileSize = (int)$uploadedFile['size'];
    $originalName = basename($uploadedFile['name']);
    $displayName = $_POST['name'] ?? $originalName;
    $mimeType = $_POST['mime_type'] ?? mime_content_type($uploadedFile['tmp_name']) ?: 'application/octet-stream';

    // Validasi batas ukuran file per unggahan
    $maxBytes = ((int)$config['max_upload_size_mb']) * 1024 * 1024;
    if ($fileSize > $maxBytes) {
        jsonResponse(['success' => false, 'error' => "Ukuran file ({$fileSize} bytes) melebihi batas maksimal {$config['max_upload_size_mb']} MB."], 400);
    }

    // Validasi kuota pengguna
    $userDoc = $storage->readJson("users/{$userId}.json");
    $currentUsed = (int)($userDoc['used_bytes'] ?? 0);
    $maxQuota = (int)($userDoc['storage_quota_bytes'] ?? 1073741824);

    if (($currentUsed + $fileSize) > $maxQuota) {
        jsonResponse(['success' => false, 'error' => 'Kapasitas penyimpanan cloud akun Anda sudah penuh.'], 400);
    }

    // Buat direktori penyimpanan privat pengguna
    $userStorageDir = $storage->getDataDir() . "/storage/{$userId}";
    if (!is_dir($userStorageDir)) {
        @mkdir($userStorageDir, 0750, true);
    }

    $fileId = 'cld_' . bin2hex(random_bytes(12));
    $destBinaryPath = $userStorageDir . "/{$fileId}.dat";

    if (!move_uploaded_file($uploadedFile['tmp_name'], $destBinaryPath)) {
        jsonResponse(['success' => false, 'error' => 'Gagal memindahkan file ke penyimpanan privat server.'], 500);
    }

    $checksum = md5_file($destBinaryPath);

    // Simpan metadata file
    $fileMeta = [
        'id' => $fileId,
        'user_id' => $userId,
        'name' => $displayName,
        'original_name' => $originalName,
        'mime_type' => $mimeType,
        'size' => $fileSize,
        'checksum' => $checksum,
        'uploaded_at' => time()
    ];

    $storage->writeJson("files/{$userId}/{$fileId}.json", $fileMeta);

    // Perbarui kuota dan jumlah file pengguna
    $userDoc['used_bytes'] = $currentUsed + $fileSize;
    $userDoc['file_count'] = ((int)($userDoc['file_count'] ?? 0)) + 1;
    $storage->writeJson("users/{$userId}.json", $userDoc);

    $storage->logAudit('FILE_UPLOAD', $currentUser['username'], ['file_id' => $fileId, 'size' => $fileSize]);

    jsonResponse([
        'success' => true,
        'message' => 'File berhasil disimpan di Cloud.',
        'file_id' => $fileId,
        'checksum' => $checksum
    ]);
}

// 7. Download File dari Cloud
if ($action === 'download') {
    $fileId = $_GET['id'] ?? '';
    if (empty($fileId)) {
        jsonResponse(['success' => false, 'error' => 'Parameter ID file diperlukan.'], 400);
    }

    $fileMeta = $storage->readJson("files/{$userId}/{$fileId}.json");
    if (!$fileMeta) {
        jsonResponse(['success' => false, 'error' => 'File tidak ditemukan atau Anda tidak memiliki akses.'], 404);
    }

    $binaryPath = $storage->getDataDir() . "/storage/{$userId}/{$fileId}.dat";
    if (!file_exists($binaryPath)) {
        jsonResponse(['success' => false, 'error' => 'File fisik tidak ditemukan di server.'], 404);
    }

    header('Content-Description: File Transfer');
    header('Content-Type: ' . ($fileMeta['mime_type'] ?: 'application/octet-stream'));
    header('Content-Disposition: attachment; filename="' . addslashes($fileMeta['name']) . '"');
    header('Expires: 0');
    header('Cache-Control: must-revalidate');
    header('Pragma: public');
    header('Content-Length: ' . filesize($binaryPath));
    readfile($binaryPath);
    exit;
}

// 8. Hapus File dari Cloud
if ($action === 'delete') {
    $fileId = $_GET['id'] ?? '';
    if (empty($fileId)) {
        jsonResponse(['success' => false, 'error' => 'Parameter ID file diperlukan.'], 400);
    }

    $fileMeta = $storage->readJson("files/{$userId}/{$fileId}.json");
    if (!$fileMeta) {
        jsonResponse(['success' => false, 'error' => 'File tidak ditemukan.'], 404);
    }

    $fileSize = (int)($fileMeta['size'] ?? 0);
    $binaryPath = $storage->getDataDir() . "/storage/{$userId}/{$fileId}.dat";

    // Hapus binary dan metadata
    @unlink($binaryPath);
    $storage->deleteFile("files/{$userId}/{$fileId}.json");

    // Perbarui kuota pengguna
    $userDoc = $storage->readJson("users/{$userId}.json");
    if ($userDoc) {
        $userDoc['used_bytes'] = max(0, ((int)($userDoc['used_bytes'] ?? 0)) - $fileSize);
        $userDoc['file_count'] = max(0, ((int)($userDoc['file_count'] ?? 0)) - 1);
        $storage->writeJson("users/{$userId}.json", $userDoc);
    }

    $storage->logAudit('FILE_DELETE', $currentUser['username'], ['file_id' => $fileId]);

    jsonResponse(['success' => true, 'message' => 'File berhasil dihapus dari cloud.']);
}

// Aksi tidak dikenali
jsonResponse(['success' => false, 'error' => "Action '$action' tidak valid."], 404);
