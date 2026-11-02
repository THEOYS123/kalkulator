<?php
/**
 * AuthService - Manajemen Autentikasi Pengguna & Admin (Non-SQL)
 * Menggunakan password_hash (Argon2id/Bcrypt), token bearer untuk mobile,
 * dan sesi aman untuk web admin.
 */

namespace NetSubnet\Core;

class AuthService {
    private FileStorageEngine $storage;

    public function __construct(FileStorageEngine $storage) {
        $this->storage = $storage;
    }

    /**
     * Mendaftarkan akun pengguna mobile baru
     */
    public function registerUser(string $username, string $password): array {
        $cleanUsername = strtolower(trim($username));
        if (strlen($cleanUsername) < 3 || strlen($cleanUsername) > 30) {
            return ['success' => false, 'error' => 'Username harus antara 3 - 30 karakter alfanumerik.'];
        }

        if (strlen($password) < 6) {
            return ['success' => false, 'error' => 'Password minimal harus 6 karakter.'];
        }

        $userIndex = $this->storage->readJson('users_index.json', []);
        if (isset($userIndex[$cleanUsername])) {
            return ['success' => false, 'error' => 'Username sudah digunakan, silakan pilih nama lain.'];
        }

        $userId = 'usr_' . bin2hex(random_bytes(10));
        $userDoc = [
            'id' => $userId,
            'username' => $cleanUsername,
            'password_hash' => password_hash($password, PASSWORD_DEFAULT),
            'status' => 'active',
            'created_at' => time(),
            'storage_quota_bytes' => 1073741824, // 1 GB default
            'used_bytes' => 0,
            'file_count' => 0
        ];

        // Tulis dokumen pengguna
        $this->storage->writeJson("users/{$userId}.json", $userDoc);

        // Update index
        $userIndex[$cleanUsername] = $userId;
        $this->storage->writeJson('users_index.json', $userIndex);

        $this->storage->logAudit('USER_REGISTER', $cleanUsername, ['user_id' => $userId]);

        return ['success' => true, 'user_id' => $userId];
    }

    /**
     * Login pengguna mobile dan menghasilkan token bearer
     */
    public function loginUser(string $username, string $password): array {
        $cleanUsername = strtolower(trim($username));
        $userIndex = $this->storage->readJson('users_index.json', []);

        if (!isset($userIndex[$cleanUsername])) {
            return ['success' => false, 'error' => 'Username atau password salah.'];
        }

        $userId = $userIndex[$cleanUsername];
        $userDoc = $this->storage->readJson("users/{$userId}.json");

        if (!$userDoc) {
            return ['success' => false, 'error' => 'Data pengguna tidak ditemukan.'];
        }

        if (($userDoc['status'] ?? 'active') !== 'active') {
            return ['success' => false, 'error' => 'Akun dinonaktifkan oleh administrator.'];
        }

        if (!password_verify($password, $userDoc['password_hash'])) {
            return ['success' => false, 'error' => 'Username atau password salah.'];
        }

        // Buat session token
        $token = bin2hex(random_bytes(32));
        $sessionDoc = [
            'token' => $token,
            'user_id' => $userId,
            'username' => $cleanUsername,
            'created_at' => time(),
            'expires_at' => time() + (30 * 86400), // 30 hari
            'ip' => $_SERVER['REMOTE_ADDR'] ?? '127.0.0.1'
        ];

        $this->storage->writeJson("sessions/{$token}.json", $sessionDoc);
        $this->storage->logAudit('USER_LOGIN', $cleanUsername, ['ip' => $sessionDoc['ip']]);

        return [
            'success' => true,
            'token' => $token,
            'user' => [
                'id' => $userId,
                'username' => $cleanUsername,
                'storage_quota' => $userDoc['storage_quota_bytes'],
                'used_bytes' => $userDoc['used_bytes'] ?? 0,
                'file_count' => $userDoc['file_count'] ?? 0
            ]
        ];
    }

    /**
     * Memverifikasi Bearer Token dari header HTTP
     */
    public function authenticateBearerToken(?string $authHeader): ?array {
        if (!$authHeader || !preg_match('/Bearer\s+(\S+)/i', $authHeader, $matches)) {
            return null;
        }

        $token = $matches[1];
        $sessionDoc = $this->storage->readJson("sessions/{$token}.json");

        if (!$sessionDoc) {
            return null;
        }

        if (time() > $sessionDoc['expires_at']) {
            $this->storage->deleteFile("sessions/{$token}.json");
            return null;
        }

        $userDoc = $this->storage->readJson("users/{$sessionDoc['user_id']}.json");
        if (!$userDoc || ($userDoc['status'] ?? 'active') !== 'active') {
            return null;
        }

        return $userDoc;
    }

    /**
     * Verifikasi Kredensial Admin Web
     */
    public function verifyAdminCredentials(string $username, string $password, array $config): bool {
        $adminUser = $config['admin_username'] ?? 'admin';
        $adminHash = $config['admin_password_hash'] ?? '';

        if (trim($username) !== $adminUser) {
            return false;
        }

        // Jika hash belum ada di config, gunakan default 'Admin@1234'
        if (empty($adminHash)) {
            return $password === 'Admin@1234';
        }

        return password_verify($password, $adminHash);
    }
}
