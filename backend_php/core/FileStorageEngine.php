<?php
/**
 * FileStorageEngine - Mesin Penyimpanan Berbasis File (Non-SQL)
 * Menggunakan penguncian file (flock), penulisan atomik (temp file + rename),
 * dan pembagian direktori untuk performa dan keamanan tinggi tanpa database SQL.
 */

namespace NetSubnet\Core;

class FileStorageEngine {
    private string $baseDataDir;

    public function __construct(?string $dataDir = null) {
        $this->baseDataDir = $dataDir ?? dirname(__DIR__) . '/data';
        $this->ensureDirectories();
    }

    private function ensureDirectories(): void {
        $dirs = [
            $this->baseDataDir,
            $this->baseDataDir . '/users',
            $this->baseDataDir . '/sessions',
            $this->baseDataDir . '/files',
            $this->baseDataDir . '/storage',
            $this->baseDataDir . '/otp',
            $this->baseDataDir . '/config',
            $this->baseDataDir . '/logs'
        ];

        foreach ($dirs as $dir) {
            if (!is_dir($dir)) {
                @mkdir($dir, 0750, true);
            }
        }

        // Pastikan .htaccess terpasang untuk melindungi direktori data dari akses langsung web
        $htaccessPath = $this->baseDataDir . '/.htaccess';
        if (!file_exists($htaccessPath)) {
            @file_put_contents($htaccessPath, "Order Allow,Deny\nDeny from all\n<IfModule mod_authz_core.c>\n    Require all denied\n</IfModule>\n");
        }
    }

    public function getDataDir(): string {
        return $this->baseDataDir;
    }

    /**
     * Membaca file JSON dengan file locking bersama (LOCK_SH)
     */
    public function readJson(string $relativePath, $default = null) {
        $fullPath = $this->baseDataDir . '/' . ltrim($relativePath, '/');
        if (!file_exists($fullPath)) {
            return $default;
        }

        $fp = @fopen($fullPath, 'rb');
        if (!$fp) {
            return $default;
        }

        flock($fp, LOCK_SH);
        $content = stream_get_contents($fp);
        flock($fp, LOCK_UN);
        fclose($fp);

        if ($content === false || trim($content) === '') {
            return $default;
        }

        $decoded = json_decode($content, true);
        return (json_last_error() === JSON_ERROR_NONE) ? $decoded : $default;
    }

    /**
     * Menulis file JSON secara atomik menggunakan file sementara dan file locking eksklusif (LOCK_EX)
     */
    public function writeJson(string $relativePath, $data): bool {
        $fullPath = $this->baseDataDir . '/' . ltrim($relativePath, '/');
        $dir = dirname($fullPath);
        if (!is_dir($dir)) {
            @mkdir($dir, 0750, true);
        }

        $tempPath = $dir . '/temp_' . bin2hex(random_bytes(8)) . '.tmp';
        $json = json_encode($data, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
        if ($json === false) {
            return false;
        }

        $fp = @fopen($tempPath, 'wb');
        if (!$fp) {
            return false;
        }

        flock($fp, LOCK_EX);
        fwrite($fp, $json);
        fflush($fp);
        flock($fp, LOCK_UN);
        fclose($fp);

        // Rename atomik
        return @rename($tempPath, $fullPath);
    }

    /**
     * Menghapus file secara aman
     */
    public function deleteFile(string $relativePath): bool {
        $fullPath = $this->baseDataDir . '/' . ltrim($relativePath, '/');
        if (file_exists($fullPath)) {
            return @unlink($fullPath);
        }
        return true;
    }

    /**
     * Mencatat audit log
     */
    public function logAudit(string $action, string $actor, array $context = []): void {
        $logFile = $this->baseDataDir . '/logs/audit.log';
        $entry = sprintf(
            "[%s] [%s] [%s] %s %s\n",
            date('Y-m-d H:i:s'),
            $_SERVER['REMOTE_ADDR'] ?? '127.0.0.1',
            $actor,
            strtoupper($action),
            json_encode($context, JSON_UNESCAPED_SLASHES)
        );
        @file_put_contents($logFile, $entry, FILE_APPEND | LOCK_EX);
    }
}
