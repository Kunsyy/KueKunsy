# RbxTool — Project Documentation

> Dokumentasi ini dibuat untuk AI / developer yang ingin memahami, melanjutkan, atau mengembangkan project ini di masa depan.

---

## Gambaran Umum

**RbxTool** adalah aplikasi Android native (Kotlin) untuk:
1. **Grab** — mengambil cookie `.ROBLOSECURITY` dari Roblox yang sedang login di device
2. **Inject** — menyuntikkan cookie ke Roblox tanpa login manual
3. **Accounts** — menyimpan & mengelola akun Roblox, termasuk auto-refresh cookie via login API

**Use case utama:**
- Bisnis **jokian** (Roblox commission work) — inject cookie klien ke Redfinger untuk mengerjakan pesanan
- **Bot farming** — simpan banyak akun bot, refresh cookie sekaligus setelah Redfinger reset atau Roblox update

**Target device:** Android rooted — terutama **Redfinger** (cloud Android emulator, Android 10 & 12)

---

## Cara Kerja Cookie Injection

### Mengapa bisa bekerja
Roblox Android menyimpan sesi login di **Chrome WebView SQLite database**:
```
/data/data/com.roblox.client/app_webview/Default/Cookies
```
File ini adalah SQLite DB dengan tabel `cookies`. Baris `.ROBLOSECURITY` di tabel inilah yang menentukan akun siapa yang login.

### Proses Inject (CookieManager.kt)
1. `am force-stop $pkg` — matikan Roblox
2. Deteksi path Cookies DB (Android 10: `Default/Cookies`, Android 12: `Default/Network/Cookies`)
3. Copy DB ke cache app
4. Edit SQLite — `DELETE` baris `.ROBLOSECURITY` lama, `INSERT` cookie baru
5. Copy balik ke path asli dengan: `chmod 600`, `chown $appUid:$appUid`, `restorecon -R` (SELinux fix)
6. Return `true`

**Catatan penting:**
- **JANGAN `pm clear`** sebelum inject — ini menghapus semua data Roblox termasuk WebView DB yang kita butuhkan
- `restorecon` wajib di Android 12 karena SELinux lebih strict
- Penemuan ini didapat dengan reverse-engineer service pebletz.xyz menggunakan `diff -rq` sebelum/sesudah inject

### Proses Grab (CookieManager.kt)
1. Copy Cookies DB ke temp file
2. Buka dengan `SQLiteDatabase.OPEN_READONLY`
3. Query: `SELECT value FROM cookies WHERE name='.ROBLOSECURITY'`
4. Return cookie string

---

## Arsitektur App

```
app/
└── src/main/java/com/rbxtool/app/
    ├── MainActivity.kt          # Bottom navigation, fragment hide/show
    ├── GrabFragment.kt          # Tab Grab: scan + grab cookie semua Roblox instance
    ├── InjectFragment.kt        # Tab Inject: paste cookie + pilih package + inject
    ├── AccountsFragment.kt      # Tab Accounts: list akun, inject/refresh/delete/add manual
    ├── AccountAdapter.kt        # RecyclerView adapter untuk kartu akun
    ├── data/
    │   ├── Account.kt           # Data class: id, username, displayName, robux, cookie, packageName, password
    │   └── AccountStorage.kt   # SharedPreferences + Gson untuk simpan list akun
    └── util/
        ├── CookieManager.kt    # Core: grabCookie() dan injectCookie()
        ├── RootUtils.kt        # exec() shell command via su, getRobloxPackages()
        ├── RobloxApi.kt        # getUser(cookie): validasi cookie + ambil info user
        ├── RobloxAuth.kt       # login(username, password): auto-login via Roblox API
        ├── CaptchaSolver.kt    # Captcha solver abstraction (10 solver support)
        ├── DiscordHelper.kt    # sendMessage() ke Discord webhook
        └── Constants.kt        # DISCORD_WEBHOOK URL
```

### Fragment Management
`MainActivity` pakai pola **hide/show** (bukan replace) untuk fragment. Ini berarti `onResume()` tidak dipanggil saat pindah tab. Solusinya: tiap fragment yang butuh refresh data implement `onHiddenChanged(hidden: Boolean)`.

---

## Fitur per Tab

### Tab GRAB
- Scan semua package Android yang namanya mengandung "roblox"
- Grab cookie tiap package via SQLite
- Validasi cookie ke Roblox API (dapat username, robux)
- Per hasil: tombol **COPY** (clipboard), **SAVE** (simpan ke Accounts), **DISCORD** (kirim ke webhook)
- Output Discord: format Markdown dengan username, userID, robux, cookie dalam code block

### Tab INJECT
- Input cookie (EditText multiline, monospace)
- Dropdown pilih package Roblox target
- Validasi cookie ke API dulu sebelum inject
- Auto-clear field setelah berhasil
- Tidak auto-launch Roblox (user buka manual)

### Tab ACCOUNTS
- List akun tersimpan (dari Grab > Save atau tambah manual)
- Tiap kartu: username, robux, package + icon 🔑 jika password tersimpan
- Tombol per akun: **INJECT** (loading → "✓ OK"), **REFRESH** (minta password jika belum ada), **✕** (dengan konfirmasi)
- **Tombol +** — tambah akun manual via username+password, auto-login dapat cookie
- **REFRESH SEMUA** — loop semua akun yang punya password, refresh cookie satu per satu, tampilkan progress
- **HAPUS SEMUA** — dengan konfirmasi dialog
- Auto-refresh list saat tab dibuka (via `onHiddenChanged`)

### Tab SETTINGS
- Root status (cek via `id` command, expect `uid=0`)
- Discord Webhook test
- Captcha Solver: dropdown 10 pilihan + input API key

---

## Captcha Solver (CaptchaSolver.kt)

Roblox pakai **FunCaptcha (Arkose Labs)**. Tidak semua solver support ini.

| Solver | Format API | Harga/1000 |
|--------|-----------|------------|
| 2Captcha | 2Captcha (in.php/res.php) | ~$1.45 |
| CapMonster | 2Captcha compatible | ~$2.00 |
| AZcaptcha | 2Captcha compatible | ~$2.99 |
| SolveCaptcha | 2Captcha compatible | ~$2.99 |
| Anti-Captcha | createTask/getTaskResult | ~$3.00 |
| Capsolver | createTask/getTaskResult | ~$1.60 |
| EzCaptcha | createTask/getTaskResult | ~$1.20 |
| NextCaptcha | createTask/getTaskResult | ~est. $1-2 |
| YesCaptcha | createTask (FunCaptchaClassification) | Points/CNY |
| Solvex | createTask/getTaskResult | ~$0.80 |

**Roblox FunCaptcha public key:** `476068BF-9607-4799-B53D-966BE98E2B81`

Ada dua format API yang diimplementasi:
- **Group 1** (2Captcha-style): POST ke `/in.php`, poll `/res.php`
- **Group 2** (createTask-style): POST ke `/createTask`, poll `/getTaskResult`

---

## Auto-Login Flow (RobloxAuth.kt)

```
getCsrfToken()
  → POST https://auth.roblox.com/v2/login (dummy, get 403 + x-csrf-token header)

attemptLogin(username, password, csrf)
  → POST https://auth.roblox.com/v2/login
  → HTTP 200: extract .ROBLOSECURITY dari Set-Cookie header
  → HTTP 403 + "code":2: captcha required
  → HTTP 401: wrong credentials

Jika captcha + solver configured:
  → CaptchaSolver.solve() → dapat token
  → Retry attemptLogin dengan rblx-challenge-type header
```

**Catatan:** Akun Roblox buatan 2023 ke bawah umumnya tidak kena captcha saat login.

---

## Output & Storage

### Discord Webhook
- URL hardcoded di `Constants.kt`
- Format pesan: Markdown bold, cookie dalam code block (``` ``` ```)
- Kirim via `DiscordHelper.sendMessage()`

### Account Storage
- `SharedPreferences` key: `rbx_accounts`
- Format: JSON array via Gson
- Field Account: `id` (userId), `username`, `displayName`, `robux`, `cookie`, `packageName`, `savedAt`, `password`
- Password disimpan plaintext lokal (acceptable karena device personal)

### Settings Storage
- `SharedPreferences` key: `rbx_settings`
- Keys: `captcha_solver_type` (SolverType enum name), `captcha_solver_key`

---

## Build & Deploy

**GitHub Actions** (`.github/workflows/build.yml`):
- Trigger: push ke `main` atau manual `workflow_dispatch`
- Build: `gradle assembleDebug`
- Output: `app/build/outputs/apk/debug/RbxTool.apk`
- Auto-publish ke GitHub Releases tag `latest`

**Download APK:**
```
https://github.com/Kunsyy/KueKunsy/releases/latest/download/RbxTool.apk
```

**Requirements:**
- Android 8.0+ (minSdk 26)
- Device harus rooted
- Roblox harus pernah login minimal sekali (agar WebView Cookies DB sudah dibuat)

---

## Hal yang Perlu Diperhatikan

1. **Path Cookies DB** bisa berbeda tergantung versi Chrome WebView:
   - Android 10: `app_webview/Default/Cookies`
   - Android 12+: `app_webview/Default/Network/Cookies`
   - `CookieManager.injectCookie()` sudah auto-detect keduanya

2. **SELinux** di Android 12 strict — `restorecon -R` pada folder cookieDir wajib setelah copy file

3. **Multiple Roblox instance** — didukung via `getRobloxPackages()` yang scan semua package dengan nama mengandung "roblox" (contoh: `com.roblox.client`, `com.roblox.client2`, dll)

4. **`pm clear` akan merusak inject** — jangan pernah wipe data Roblox sebelum inject karena WebView DB ikut terhapus

5. **Cookie lifetime** — `.ROBLOSECURITY` valid ~1 tahun tapi bisa hangus kapanpun jika: ganti password, logout manual, atau Roblox detect suspicious login

---

## Tech Stack

- **Language:** Kotlin
- **UI:** Material Components (MaterialCardView, MaterialButton, BottomNavigationView)
- **Binding:** ViewBinding + DataBinding
- **Async:** Kotlin Coroutines (lifecycleScope + Dispatchers.IO)
- **Storage:** SharedPreferences + Gson
- **Database:** Android SQLiteDatabase (baca/tulis Cookies DB Roblox)
- **Network:** HttpURLConnection (no OkHttp/Retrofit dependency)
- **Build:** Gradle 8.4, JDK 17, Android SDK 33
