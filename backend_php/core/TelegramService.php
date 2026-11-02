<?php
/**
 * TelegramService - Layanan Autentikasi 2FA & Notifikasi Telegram
 * Mengirimkan kode One-Time Password (OTP) ke Chat ID Administrator yang diotorisasi.
 */

namespace NetSubnet\Core;

class TelegramService {
    private FileStorageEngine $storage;
    private array $config;

    public function __construct(FileStorageEngine $storage, array $config) {
        $this->storage = $storage;
        $this->config = $config;
    }

    public function sendMessage(string $text): array {
        $botToken = $this->config['telegram_bot_token'] ?? '';
        $chatId = $this->config['telegram_admin_chat_id'] ?? '';

        if (empty($botToken) || empty($chatId)) {
            return [
                'success' => false,
                'error' => 'Token Bot Telegram atau Chat ID Admin belum dikonfigurasi.'
            ];
        }

        $url = "https://api.telegram.org/bot" . urlencode($botToken) . "/sendMessage";
        $payload = [
            'chat_id' => $chatId,
            'text' => $text,
            'parse_mode' => 'HTML',
            'disable_web_page_preview' => true
        ];

        // Gunakan cURL jika tersedia, atau fallback ke stream_context
        if (function_exists('curl_init')) {
            $ch = curl_init($url);
            curl_setopt_array($ch, [
                CURLOPT_POST => true,
                CURLOPT_POSTFIELDS => http_build_query($payload),
                CURLOPT_RETURNTRANSFER => true,
                CURLOPT_TIMEOUT => 15,
                CURLOPT_SSL_VERIFYPEER => true
            ]);
            $response = curl_exec($ch);
            $err = curl_error($ch);
            $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
            curl_close($ch);

            if ($err) {
                return ['success' => false, 'error' => "cURL Error: $err"];
            }
        } else {
            $opts = [
                'http' => [
                    'method' => 'POST',
                    'header' => "Content-type: application/x-www-form-urlencoded\r\n",
                    'content' => http_build_query($payload),
                    'timeout' => 15
                ]
            ];
            $context = stream_context_create($opts);
            $response = @file_get_contents($url, false, $context);
            if ($response === false) {
                return ['success' => false, 'error' => 'Gagal menghubungkan ke Telegram Bot API'];
            }
        }

        $decoded = json_decode($response, true);
        if ($decoded && ($decoded['ok'] ?? false)) {
            return ['success' => true, 'response' => $decoded];
        }

        $msg = $decoded['description'] ?? 'Gagal mengirim pesan Telegram';
        return ['success' => false, 'error' => $msg];
    }

    /**
     * Membuat kode OTP 6-digit baru dan mengirimkannya via Telegram
     */
    public function generateAndSendOtp(string $adminUsername): array {
        // Hasilkan kode 6 digit
        $otpCode = sprintf('%06d', random_int(100000, 999999));
        $otpId = bin2hex(random_bytes(16));
        $expiryMinutes = (int)($this->config['otp_expiry_minutes'] ?? 5);
        $expiresAt = time() + ($expiryMinutes * 60);

        $otpData = [
            'id' => $otpId,
            'username' => $adminUsername,
            'otp_hash' => password_hash($otpCode, PASSWORD_DEFAULT),
            'expires_at' => $expiresAt,
            'attempts' => 0,
            'max_attempts' => 3,
            'used' => false,
            'ip' => $_SERVER['REMOTE_ADDR'] ?? '127.0.0.1'
        ];

        // Simpan OTP ke file
        $this->storage->writeJson("otp/{$otpId}.json", $otpData);

        // Pesan ke Telegram
        $serverHost = $_SERVER['HTTP_HOST'] ?? 'NetSubnet Server';
        $ip = $_SERVER['REMOTE_ADDR'] ?? 'Unknown';
        $timeStr = date('d-m-Y H:i:s');
        $msg = "🔐 <b>KODE OTP LOGIN ADMIN NETSUBNET</b>\n\n"
             . "Kode Verifikasi: <code>{$otpCode}</code>\n"
             . "Berlaku: {$expiryMinutes} Menit\n\n"
             . "📍 Server: {$serverHost}\n"
             . "🌐 IP Pemohon: {$ip}\n"
             . "🕒 Waktu: {$timeStr}\n\n"
             . "<i>Jangan berikan kode ini kepada siapa pun!</i>";

        $sendResult = $this->sendMessage($msg);

        if (!$sendResult['success']) {
            $this->storage->deleteFile("otp/{$otpId}.json");
            return [
                'success' => false,
                'error' => 'Gagal mengirim OTP ke Telegram: ' . $sendResult['error']
            ];
        }

        return [
            'success' => true,
            'otp_id' => $otpId,
            'expires_at' => $expiresAt
        ];
    }

    /**
     * Verifikasi kode OTP yang dimasukkan administrator
     */
    public function verifyOtp(string $otpId, string $inputOtp): array {
        $otpData = $this->storage->readJson("otp/{$otpId}.json");
        if (!$otpData) {
            return ['valid' => false, 'error' => 'Permintaan OTP tidak ditemukan atau sudah kedaluwarsa.'];
        }

        if ($otpData['used']) {
            return ['valid' => false, 'error' => 'Kode OTP ini sudah digunakan sebelumnya.'];
        }

        if (time() > $otpData['expires_at']) {
            $this->storage->deleteFile("otp/{$otpId}.json");
            return ['valid' => false, 'error' => 'Kode OTP telah kedaluwarsa. Silakan minta kode baru.'];
        }

        if ($otpData['attempts'] >= $otpData['max_attempts']) {
            $this->storage->deleteFile("otp/{$otpId}.json");
            return ['valid' => false, 'error' => 'Batas percobaan OTP terlampaui. Permintaan dibatalkan.'];
        }

        // Cek kecocokan kode hash
        if (!password_verify($inputOtp, $otpData['otp_hash'])) {
            $otpData['attempts']++;
            $this->storage->writeJson("otp/{$otpId}.json", $otpData);
            $sisa = $otpData['max_attempts'] - $otpData['attempts'];
            return ['valid' => false, 'error' => "Kode OTP salah! Sisa percobaan: $sisa."];
        }

        // Tandai sudah dipakai dan hapus
        $otpData['used'] = true;
        $this->storage->deleteFile("otp/{$otpId}.json");

        return [
            'valid' => true,
            'username' => $otpData['username']
        ];
    }
}
