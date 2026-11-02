<?php
/**
 * NetSubnet - Konfigurasi Utama Sistem (Non-SQL)
 */

require_once __DIR__ . '/core/FileStorageEngine.php';
require_once __DIR__ . '/core/AuthService.php';
require_once __DIR__ . '/core/TelegramService.php';

use NetSubnet\Core\FileStorageEngine;
use NetSubnet\Core\AuthService;
use NetSubnet\Core\TelegramService;

$storage = new FileStorageEngine(__DIR__ . '/data');

// Muat konfigurasi dinamis dari file JSON aman
$defaultConfig = [
    'app_name' => 'NetSubnet Admin & Cloud',
    'admin_username' => 'admin',
    // Default password: 'Admin@1234' (dihash dengan bcrypt)
    'admin_password_hash' => password_hash('Admin@1234', PASSWORD_DEFAULT),
    'telegram_bot_token' => '',
    'telegram_admin_chat_id' => '',
    'otp_expiry_minutes' => 5,
    'default_user_quota_mb' => 1024, // 1 GB
    'max_upload_size_mb' => 50,
    'maintenance_mode' => false,
    'announcement' => 'Selamat datang di panel kontrol NetSubnet non-SQL Cloud!'
];

$savedConfig = $storage->readJson('config/settings.json', []);
$config = array_merge($defaultConfig, $savedConfig);

// Helper fungsi untuk menyimpan perubahan konfigurasi
function saveSystemConfig(FileStorageEngine $storage, array $newConfig): bool {
    return $storage->writeJson('config/settings.json', $newConfig);
}

$authService = new AuthService($storage);
$telegramService = new TelegramService($storage, $config);
