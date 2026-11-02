# PANDUAN DEPLOYMENT & INSTALASI BACKEND PHP NON-SQL NETSUBNET

Dokumen ini menjelaskan tata cara instalasi dan deployment sistem backend dan web admin NetSubnet pada hosting PHP (termasuk shared hosting gratis seperti InfinityFree, cPanel, Apache, atau VPS).

Sistem ini **100% NON-SQL** dan tidak memerlukan database MySQL, MariaDB, SQLite, PostgreSQL, ataupun proses impor tabel SQL.

---

## 1. STRUKTUR FOLDER BACKEND

Unggah seluruh isi direktori `backend_php` ke folder publik hosting Anda (misalnya `htdocs` pada InfinityFree atau `public_html` pada cPanel):

```text
htdocs/
  ├── api.php                 <-- REST API endpoint untuk aplikasi Android
  ├── index.php               <-- Web Admin Dashboard
  ├── login.php               <-- Halaman login admin dengan 2FA Telegram
  ├── logout.php              <-- Skrip keluar sesi admin
  ├── config.php              <-- Loader konfigurasi
  ├── core/
  │   ├── FileStorageEngine.php  <-- Mesin penyimpanan file atomik & flock
  │   ├── AuthService.php        <-- Manajemen password hash & token Bearer
  │   └── TelegramService.php    <-- Integrasi Bot Telegram OTP
  └── data/
      ├── .htaccess           <-- Proteksi: menolak akses langsung dari web
      ├── users/              <-- JSON data profil pengguna & password hash
      ├── sessions/           <-- JSON token sesi mobile aktif
      ├── files/              <-- Metadata file per pengguna
      ├── storage/            <-- Penyimpanan biner file cloud privat terenkripsi
      ├── otp/                <-- Data sementara OTP 2FA
      ├── config/             <-- settings.json (pengaturan dinamis)
      └── logs/               <-- audit.log (catatan keamanan)
```

---

## 2. CARA INSTALASI PADA HOSTING GRATIS (INFINITYFREE / CPANEL)

1. **Login ke File Manager / FTP:**
   - Gunakan File Manager cPanel atau klien FTP (seperti FileZilla).
   - Masuk ke direktori `htdocs` atau `public_html`.
2. **Unggah Berkas:**
   - Unggah seluruh folder dan berkas dari `backend_php`.
3. **Izin Berkas (File Permissions):**
   - Pastikan folder `data/` memiliki izin baca-tulis (chmod `0750` atau `0755` jika diperlukan oleh shared hosting).
   - Pastikan file `.htaccess` berada di dalam folder `data/` untuk mencegah pihak luar mengunduh file JSON atau file privat secara langsung.
4. **Buka Web Admin Pertama Kali:**
   - Akses melalui browser: `https://domain-anda.com/login.php`
   - Kredensial awal default:
     - **Username:** `admin`
     - **Password:** `Admin@1234`
   - Pada login awal sebelum Telegram dikonfigurasi, sistem akan mengizinkan Anda masuk untuk menyetel Bot Telegram terlebih dahulu.

---

## 3. PANDUAN MEMBUAT BOT TELEGRAM UNTUK KODE OTP 2FA

1. **Buat Bot Telegram:**
   - Buka aplikasi Telegram, cari akun `@BotFather`.
   - Kirim pesan `/newbot`.
   - Masukkan nama bot dan username bot (harus berakhiran `bot`, contoh: `netsubnet_auth_bot`).
   - BotFather akan memberikan **API Token** (contoh: `7123456789:AAH...`).
2. **Dapatkan Chat ID Anda:**
   - Mulai chat dengan bot Anda (tekan tombol **Start**).
   - Cari bot `@userinfobot` atau `@getidsbot` di Telegram, lalu tekan Start. Bot tersebut akan menampilkan **ID** Anda (berupa angka, contoh: `987654321`).
3. **Simpan ke Web Admin:**
   - Buka Web Admin -> Masuk ke Tab **Bot Telegram**.
   - Masukkan **Telegram Bot Token** dan **Admin Chat ID**.
   - Tekan **Simpan Konfigurasi**.
   - Tekan tombol **Kirim Pesan Uji Coba Telegram** untuk memverifikasi.
   - Setelah tersimpan, setiap kali Anda login ke Web Admin, sistem akan membuat kode OTP 6-digit acak dan mengirimkannya ke akun Telegram Anda.

---

## 4. MENGHUBUNGKAN APLIKASI ANDROID KE BACKEND

1. Buka aplikasi **NetSubnet** di perangkat Android.
2. Buka **Ruang Pribadi** (tekan ikon gembok pada kalkulator atau masukkan PIN default `1234`).
3. Tekan ikon **Pengaturan (Gear)** di sudut kanan atas brankas.
4. Pada kolom **URL Endpoint API PHP**, masukkan URL lengkap file `api.php` di hosting Anda:
   - Contoh: `https://domain-anda.infinityfreeapp.com/api.php`
   - Atau untuk emulator lokal: `http://10.0.2.2/backend_php/api.php`
5. Tekan tombol **Simpan URL Server**.
6. Masuk ke bagian **Daftar Akun Cloud Baru**, masukkan username dan password yang diinginkan, lalu tekan **Daftar Akun**.
7. Setelah berhasil mendaftar, lakukan login.
8. Sekarang brankas Anda siap mengunggah dan menyinkronkan file privat ke Cloud secara otomatis tanpa database SQL!
