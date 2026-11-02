<?php
/**
 * NetSubnet Web Admin - Halaman Login dengan 2FA Bot Telegram
 * Autentikasi 2-Tahap: Kredensial Admin -> Kode OTP Telegram -> Masuk Dashboard
 */

session_start();
require_once __DIR__ . '/config.php';

$error = null;
$info = null;
$step = $_SESSION['login_step'] ?? 'credentials';

// Generate CSRF token
if (empty($_SESSION['csrf_token'])) {
    $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
}

// Jika sudah login, langsung ke dashboard
if (!empty($_SESSION['admin_logged_in'])) {
    header('Location: index.php');
    exit;
}

// Tangani POST
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $submittedCsrf = $_POST['csrf_token'] ?? '';
    if (!hash_equals($_SESSION['csrf_token'], $submittedCsrf)) {
        $error = 'Validasi keamanan CSRF gagal. Silakan muat ulang halaman.';
    } else {
        $action = $_POST['action'] ?? '';

        // TAHAP 1: Validasi Username & Password
        if ($action === 'verify_credentials') {
            $username = trim($_POST['username'] ?? '');
            $password = $_POST['password'] ?? '';

            if ($authService->verifyAdminCredentials($username, $password, $config)) {
                // Periksa apakah fitur 2FA Telegram diaktifkan atau token belum disetel
                if (empty($config['enable_telegram_2fa']) || empty($config['telegram_bot_token']) || empty($config['telegram_admin_chat_id'])) {
                    $_SESSION['admin_logged_in'] = true;
                    $_SESSION['admin_username'] = $username;
                    unset($_SESSION['login_step']);
                    $storage->logAudit('ADMIN_LOGIN_DIRECT', $username, ['note' => 'Login langsung tanpa OTP Telegram']);
                    header('Location: index.php');
                    exit;
                }

                // Kirim OTP via Telegram
                $otpRes = $telegramService->generateAndSendOtp($username);
                if ($otpRes['success']) {
                    $_SESSION['pending_admin_user'] = $username;
                    $_SESSION['pending_otp_id'] = $otpRes['otp_id'];
                    $_SESSION['login_step'] = 'otp';
                    $step = 'otp';
                    if (!empty($otpRes['is_emergency'])) {
                        $info = "<b>Perhatian:</b> Hosting Anda (InfinityFree) memblokir koneksi keluar ke api.telegram.org.<br>"
                              . "Agar Anda tidak terkunci, gunakan <b>Kode OTP Darurat:</b> <code style='font-size:18px; color:#ffb703;'>" . htmlspecialchars($otpRes['emergency_otp']) . "</code><br>"
                              . "Masukkan kode ini di bawah untuk masuk ke dashboard:";
                    } else {
                        $info = "Kode OTP verifikasi telah dikirim ke Telegram Chat ID Admin. Masukkan kode di bawah ini:";
                    }
                } else {
                    $error = $otpRes['error'];
                }
            } else {
                $error = 'Username atau password administrator salah.';
                $storage->logAudit('ADMIN_LOGIN_FAILED', $username);
            }
        }

        // TAHAP 2: Validasi OTP Telegram
        if ($action === 'verify_otp') {
            $otpInput = trim($_POST['otp_code'] ?? '');
            $otpId = $_SESSION['pending_otp_id'] ?? '';

            if (empty($otpId) || empty($otpInput)) {
                $error = 'Sesi OTP tidak valid. Silakan ulangi login dari awal.';
                $_SESSION['login_step'] = 'credentials';
                $step = 'credentials';
            } else {
                $verifyRes = $telegramService->verifyOtp($otpId, $otpInput);
                if ($verifyRes['valid']) {
                    // Sukses login!
                    session_regenerate_id(true);
                    $_SESSION['admin_logged_in'] = true;
                    $_SESSION['admin_username'] = $verifyRes['username'];
                    unset($_SESSION['login_step'], $_SESSION['pending_otp_id'], $_SESSION['pending_admin_user']);

                    $storage->logAudit('ADMIN_LOGIN_SUCCESS', $verifyRes['username'], ['otp_verified' => true]);
                    header('Location: index.php');
                    exit;
                } else {
                    $error = $verifyRes['error'];
                }
            }
        }

        // Batal / Ulangi Login
        if ($action === 'cancel_otp') {
            unset($_SESSION['login_step'], $_SESSION['pending_otp_id'], $_SESSION['pending_admin_user']);
            $step = 'credentials';
        }
    }
}
?>
<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Login Administrator - <?= htmlspecialchars($config['app_name']) ?></title>
    <style>
        :root {
            --bg-color: #070c12;
            --card-bg: #0f172a;
            --primary: #00b4d8;
            --primary-hover: #0096c7;
            --text-main: #e6f1ff;
            --text-muted: #8892b0;
            --border: #1e293b;
            --danger: #ef476f;
            --success: #06d6a0;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen, Ubuntu, Cantarell, sans-serif; }
        body { background: var(--bg-color); color: var(--text-main); min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 20px; }
        .login-card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 16px; width: 100%; max-width: 440px; padding: 36px 32px; box-shadow: 0 20px 40px rgba(0,0,0,0.5); }
        .logo-box { width: 56px; height: 56px; border-radius: 12px; background: rgba(0, 180, 216, 0.15); border: 1px solid var(--primary); display: flex; align-items: center; justify-content: center; margin: 0 auto 18px; font-size: 28px; }
        h1 { font-size: 22px; font-weight: 700; text-align: center; margin-bottom: 6px; }
        .subtitle { font-size: 13px; color: var(--text-muted); text-align: center; margin-bottom: 24px; }
        .alert { padding: 12px 16px; border-radius: 8px; font-size: 13px; margin-bottom: 20px; }
        .alert-error { background: rgba(239, 71, 111, 0.15); border: 1px solid var(--danger); color: #ff8fa3; }
        .alert-info { background: rgba(6, 214, 160, 0.15); border: 1px solid var(--success); color: #72f8c8; }
        .form-group { margin-bottom: 18px; }
        label { display: block; font-size: 12px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 8px; color: var(--text-muted); }
        input[type="text"], input[type="password"] { width: 100%; background: #070c12; border: 1px solid var(--border); border-radius: 8px; padding: 12px 14px; color: #fff; font-size: 15px; outline: none; transition: border-color 0.2s; }
        input:focus { border-color: var(--primary); }
        .otp-input { font-size: 24px !important; letter-spacing: 8px; text-align: center; font-weight: 700; font-family: monospace; }
        .btn { width: 100%; background: var(--primary); color: #001f29; border: none; border-radius: 8px; padding: 12px; font-size: 15px; font-weight: 700; cursor: pointer; transition: background 0.2s; }
        .btn:hover { background: var(--primary-hover); }
        .btn-secondary { background: transparent; color: var(--text-muted); border: 1px solid var(--border); margin-top: 10px; }
        .btn-secondary:hover { background: rgba(255,255,255,0.05); color: #fff; }
        .badge { display: inline-block; background: rgba(0, 180, 216, 0.15); color: var(--primary); font-size: 11px; padding: 3px 8px; border-radius: 4px; font-weight: 600; margin-bottom: 12px; }
        .footer-note { font-size: 12px; color: var(--text-muted); text-align: center; margin-top: 24px; line-height: 1.5; }
    </style>
</head>
<body>
    <div class="login-card">
        <div class="logo-box">🌐</div>
        <h1>NetSubnet Web Admin</h1>
        <p class="subtitle">Panel Kontrol & Cloud Storage Non-SQL</p>

        <?php if ($error): ?>
            <div class="alert alert-error"><?= htmlspecialchars($error) ?></div>
        <?php endif; ?>

        <?php if ($info): ?>
            <div class="alert alert-info"><?= htmlspecialchars($info) ?></div>
        <?php endif; ?>

        <?php if ($step === 'credentials'): ?>
            <!-- Form Kredensial Tahap 1 -->
            <form method="POST">
                <input type="hidden" name="csrf_token" value="<?= htmlspecialchars($_SESSION['csrf_token']) ?>">
                <input type="hidden" name="action" value="verify_credentials">

                <div class="form-group">
                    <label>Username Administrator</label>
                    <input type="text" name="username" placeholder="Masukkan username admin" required autofocus value="admin">
                </div>

                <div class="form-group">
                    <label>Password Administrator</label>
                    <input type="password" name="password" placeholder="Masukkan password admin" required>
                </div>

                <button type="submit" class="btn">Lanjutkan Verifikasi (2FA)</button>
            </form>
            <div class="footer-note">
                Kredensial Default Awal: <code>admin</code> / <code>Admin@1234</code><br>
                Harap ganti password setelah berhasil masuk dashboard.
            </div>

        <?php else: ?>
            <!-- Form OTP Telegram Tahap 2 -->
            <div style="text-align: center; margin-bottom: 16px;">
                <span class="badge">VERIFIKASI 2FA TELEGRAM</span>
            </div>
            <form method="POST">
                <input type="hidden" name="csrf_token" value="<?= htmlspecialchars($_SESSION['csrf_token']) ?>">
                <input type="hidden" name="action" value="verify_otp">

                <div class="form-group">
                    <label style="text-align: center;">KODE OTP 6-DIGIT</label>
                    <input type="text" name="otp_code" class="otp-input" placeholder="000000" maxlength="6" required autofocus autocomplete="off">
                </div>

                <button type="submit" class="btn">Verifikasi & Masuk Dashboard</button>
            </form>

            <form method="POST">
                <input type="hidden" name="csrf_token" value="<?= htmlspecialchars($_SESSION['csrf_token']) ?>">
                <input type="hidden" name="action" value="cancel_otp">
                <button type="submit" class="btn btn-secondary">Batalkan & Kembali</button>
            </form>
        <?php endif; ?>
    </div>
</body>
</html>
