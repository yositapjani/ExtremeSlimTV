# Panduan Pemula — Bikin Mi TV Stick Kamu Kurus & Kencang

*Bahasa paling sederhana, langkah demi langkah. Tidak perlu pengalaman ngoprek.*

> **English summary:** This is the step-by-step beginner guide (in Indonesian) for applying the
> ExtremeSlimTV makeover to a Mi TV Stick FHD: turn on developer options, connect with ADB over
> Wi-Fi, back up, disable the bloat from the profile in this repo, install the launcher from
> Releases, and undo everything if you change your mind. Windows and Mac/Linux commands included.

---

## Ini apa sih?

Mi TV Stick itu isinya banyak aplikasi bawaan yang **nggak pernah kamu pakai** tapi jalan terus
di belakang layar. Stick-nya cuma punya RAM 1 GB, jadi dia gampang lemot.

Di panduan ini kita akan:

1. **Mematikan** aplikasi bawaan yang nggak perlu (47 aplikasi). Dimatikan saja, **bukan dihapus** —
   jadi bisa dinyalakan lagi kapan pun. Aman.
2. **Mengganti layar utama (launcher)** dengan yang bersih: cuma aplikasi yang kamu pakai,
   tanpa iklan, tanpa baris rekomendasi yang nggak bisa dihilangkan.
3. Bonus: launcher-nya punya **OLED Care** — fitur yang menjaga layar OLED dari *burn-in*.

**Yang TIDAK kita lakukan:** tidak root, tidak bongkar stick, tidak ada yang dihapus permanen.
Kalau di tengah jalan kamu berubah pikiran, ada bab "Cara Balikin Seperti Semula" di bawah.

**Sudah dites di:** Mi TV Stick FHD (model MDZ-24-AA / MiTV-AESP0), Android TV 9, firmware PI.2055.
Kalau stick kamu beda tipe (misalnya yang 4K), bab mematikan aplikasi tetap bisa dipakai dengan
hati-hati, tapi launcher-nya belum dites di sana.

**Waktu yang dibutuhkan:** sekitar 30–45 menit santai.

---

## Yang kamu butuhkan

- Mi TV Stick FHD + TV-nya, menyala normal.
- **Satu komputer/laptop** (Windows, Mac, atau Linux). Nanti kita mengetik perintah dari sini.
- Stick dan komputer harus nyambung ke **Wi-Fi yang sama**.
- Remote stick (buat pencet-pencet di TV).

---

## Langkah 1 — Catat dulu tipe stick kamu

Di TV, pakai remote:

1. Buka **Pengaturan** (ikon gerigi) → **Tentang** (About).
2. Catat tulisan **Model** dan **Versi** (firmware). Contoh punya kami: model `MiTV-AESP0`,
   versi `PI.2055`.
3. Kalau model kamu bukan MiTV-AESP0, jangan lanjutkan bab launcher — bab mematikan aplikasi
   masih boleh, tapi kerjakan pelan-pelan dan siap balikin (ada caranya di bawah).

---

## Langkah 2 — Nyalakan "mode tukang oprek" (Opsi Pengembang)

Ini pintu masuknya. Tenang, ini fitur resmi Android, bukan hack.

1. Buka **Pengaturan** → **Tentang**.
2. Cari baris **Build** (di paling bawah).
3. **Tekan tombol OK di remote pada baris Build sebanyak 7 kali.**
   Di layar akan muncul hitungan mundur, lalu tulisan *"Anda kini seorang developer!"*
4. Tekan **Kembali**. Sekarang ada menu baru: **Pengaturan** → **Preferensi Perangkat** →
   **Opsi Pengembang** (Developer Options).

## Langkah 3 — Nyalakan "Debugging USB"

1. Masuk **Opsi Pengembang**.
2. Cari **Debugging USB** (USB debugging), nyalakan (geser ke ON).
   Walaupun namanya "USB", di stick ini dia juga membuka pintu lewat Wi-Fi. Itulah yang kita pakai.
3. Kalau ada pilihan **Install via USB** atau **Verifikasi aplikasi via USB**, biarkan saja apa
   adanya — tidak perlu diubah.

## Langkah 4 — Cari alamat IP stick kamu

Anggap IP itu "alamat rumah" stick kamu di jaringan Wi-Fi.

1. Buka **Pengaturan** → **Jaringan & Internet**.
2. Klik nama Wi-Fi yang sedang tersambung.
3. Catat **Alamat IP**-nya. Contoh: `192.168.1.105`. Tulis persis, jangan sampai salah angka.

---

## Langkah 5 — Siapkan "remote ajaib" di komputer (ADB)

ADB adalah program kecil resmi dari Google buat ngobrol sama Android dari komputer.

1. Unduh **SDK Platform-Tools** dari situs resmi Google:
   https://developer.android.com/tools/releases/platform-tools
   (pilih yang sesuai komputer kamu: Windows / Mac / Linux).
2. Ekstrak file ZIP-nya ke folder yang gampang diingat, misalnya `C:\platform-tools`
   (Windows) atau `~/platform-tools` (Mac/Linux).
3. Buka jendela buat mengetik perintah:
   - **Windows:** buka folder `platform-tools`, klik bar alamat folder, ketik `powershell`,
     tekan Enter.
   - **Mac/Linux:** buka aplikasi **Terminal**, lalu ketik
     `cd ~/platform-tools` lalu Enter.
4. Tes ADB-nya hidup: ketik perintah ini lalu Enter:

   ```
   .\adb version        (Windows)
   ./adb version        (Mac/Linux)
   ```

   Kalau muncul tulisan versi (misalnya `Android Debug Bridge version 36.x`), berarti beres.

> Mulai sekarang, contoh perintah ditulis tanpa `.\` / `./` — sesuaikan dengan komputer kamu:
> di Windows ketik `.\adb ...`, di Mac/Linux `./adb ...`.

## Langkah 6 — Sambungkan komputer ke stick

1. Di jendela perintah tadi, ketik (ganti IP dengan IP stick kamu dari Langkah 4):

   ```
   adb connect 192.168.1.105:5555
   ```

2. **Lihat TV kamu!** Akan muncul kotak izin: *"Izinkan debugging USB?"*
   Centang **"Selalu izinkan dari komputer ini"**, lalu pilih **Izinkan / OK** pakai remote.
3. Kembali ke komputer, ketik:

   ```
   adb devices
   ```

   Kalau muncul baris berisi IP stick kamu dan kata `device`, **kamu sudah tersambung.** 🎉
   (Kalau tulisannya `unauthorized`, ulangi langkah ini — kotaknya mungkin kelewat di TV.
   Kalau `offline`, ketik `adb kill-server` lalu ulangi dari `adb connect`.)

---

## Langkah 7 — BACKUP DULU. Wajib, jangan dilewati!

Ini jaring pengaman kamu. Cuma 1 menit.

Di jendela perintah, ketik:

```
adb shell pm list packages > daftar-paket-saya.txt
```

File `daftar-paket-saya.txt` sekarang berisi daftar semua aplikasi di stick kamu.
**Simpan file ini baik-baik** (kirim ke email kamu sendiri juga boleh). Kalau nanti ada apa-apa,
daftar ini yang menolong.

---

## Langkah 8 — Matikan aplikasi bawaan yang nggak perlu

1. Unduh file daftar bloatware dari repo ini:
   **[profiles/mitv-aesp0-mi-tv-stick-fhd.txt](https://github.com/yositapjani/ExtremeSlimTV/blob/main/profiles/mitv-aesp0-mi-tv-stick-fhd.txt)**
   (klik tombol **Download raw file** / ikon unduh di kanan atas halaman file-nya),
   lalu pindahkan file itu ke folder `platform-tools` kamu.
2. Buka file itu dengan Notepad/TextEdit dan **baca dulu**. Isinya kelompok-kelompok aplikasi
   dengan penjelasan bahasa manusia. Baris yang diawali tanda `#` itu catatan, bukan perintah.
   Kelompok yang berlabel **OPTIONAL** (misalnya suara Google dan firmware update) —
   **lewati dulu** untuk percobaan pertama kamu.
3. Jalankan perintah sakti ini dari folder `platform-tools`:

   **Windows (PowerShell):**
   ```powershell
   Get-Content .\mitv-aesp0-mi-tv-stick-fhd.txt | Where-Object { $_ -match '^[a-z][a-z0-9_.]+' } | ForEach-Object { $p = ($_ -split '\s+')[0]; adb shell pm disable-user --user 0 $p }
   ```

   **Mac / Linux (Terminal):**
   ```bash
   grep -E '^[a-z]' mitv-aesp0-mi-tv-stick-fhd.txt | awk '{print $1}' | while read p; do ./adb shell pm disable-user --user 0 $p; done
   ```

   Layar akan menampilkan banyak tulisan `disabled` — itu normal, biarkan sampai selesai.
4. **Restart stick:** cabut kabel USB stick dari TV/listrik, tunggu 5 detik, colok lagi.
5. Coba stick-nya: buka aplikasi yang biasa kamu pakai (Netflix, YouTube, dll). Semua harus
   normal, dan stick terasa lebih ringan. Kalau ada aplikasi favoritmu yang hilang/aneh,
   jangan panik — baca bab "Cara Balikin" di bawah, nyalakan lagi paket terakhir yang kamu
   matikan.

> **Tips aman:** kalau mau ekstra hati-hati, matikan 5–10 baris dulu, restart, coba sehari.
> Besok lanjutkan sisanya. Cara ini yang kami pakai waktu pertama mengerjakan stick kami.

---

## Langkah 9 — Pasang launcher baru (layar utama yang bersih)

1. Unduh APK launcher dari halaman rilis:
   **https://github.com/yositapjani/ExtremeSlimTV/releases** → pilih rilis terbaru →
   unduh file `ExtremeSlimTV-Launcher-v1.0.0.apk`, pindahkan ke folder `platform-tools`.
2. Pasang ke stick:

   ```
   adb install ExtremeSlimTV-Launcher-v1.0.0.apk
   ```

   Tunggu sampai muncul tulisan `Success`.
3. Di TV, tekan tombol **Home** di remote. TV akan bertanya mau pakai layar utama yang mana.
   Pilih **ATV Home** (itu launcher barunya), lalu pilih **Selalu / Always**.
4. Selesai! Layar utama kamu sekarang bersih: jam besar, tanggal, dan deretan aplikasi pilihan
   di bawah. Aplikasi di deretan bawah diambil dari aplikasi yang terpasang di stick kamu —
   pasang/copot aplikasi seperti biasa, tampilannya menyesuaikan.

**Mau ganti aplikasi yang tampil di deretan bawah?** Di versi ini deretannya otomatis dari
aplikasi terpasang. Copot aplikasi yang tidak kamu pakai (Pengaturan → Aplikasi) supaya
deretannya pendek dan rapi.

## Langkah 10 — (Opsional) Video latar yang bergerak

Launcher ini bisa menampilkan video pemandangan sebagai latar (di stick kami: video udara
pantai/pegunungan yang berganti sendiri). Tanpa video pun tetap cantik — tampil gradasi warna.

Kalau mau pakai video:

1. Siapkan 1–10 video MP4 pendek (resolusi 720p cukup, ukuran kecil lebih baik).
2. Buat folder di stick lalu kirim videonya:

   ```
   adb shell mkdir -p /sdcard/Android/data/com.kulwant.atvhome/files/backgrounds
   adb push video1.mp4 /sdcard/Android/data/com.kulwant.atvhome/files/backgrounds/
   ```

   Ulangi `adb push` untuk video lain. Video akan diputar bergantian otomatis.

---

## Tentang OLED Care (penjaga layar OLED)

Kalau TV kamu **OLED**, ada musuh namanya *burn-in*: gambar diam yang itu-itu saja bisa
"membekas" permanen di layar. Launcher ini melawan dengan dua cara, otomatis, tanpa kamu
apa-apakan:

- **Geser-geser mikro:** setiap 4 menit, seluruh tampilan (jam, ikon, deretan aplikasi)
  bergeser beberapa piksel secara halus. Nggak terasa, tapi tidak ada gambar yang diam
  di piksel yang sama terus-menerus. Videonya sendiri tidak ikut bergeser.
- **Redup saat diam:** kalau remote tidak dipencet 3 menit, tampilan meredup ke 65%.
  Pencet tombol apa pun, langsung terang lagi.

Kalau TV kamu bukan OLED (LED/LCD biasa), fitur ini tidak merugikan apa-apa — biarkan saja.

---

## Cara Balikin Seperti Semula

Semua yang kita lakukan bisa dibatalkan. Ada 4 tingkat — mulai dari yang paling ringan.
Pakai tingkat seringan mungkin; factory reset itu pilihan terakhir.

### Tingkat 1 — Nyalakan lagi SATU aplikasi yang tadi dimatikan

```
adb shell pm enable nama.paket.aplikasinya
```
(lihat nama paketnya di file profil, atau di file backup `daftar-paket-saya.txt`)

### Tingkat 2 — Nyalakan lagi SEMUA aplikasi sekaligus

Windows (PowerShell):
```powershell
Get-Content .\mitv-aesp0-mi-tv-stick-fhd.txt | Where-Object { $_ -match '^[a-z][a-z0-9_.]+' } | ForEach-Object { $p = ($_ -split '\s+')[0]; adb shell pm enable $p }
```
Mac / Linux:
```bash
grep -E '^[a-z]' mitv-aesp0-mi-tv-stick-fhd.txt | awk '{print $1}' | while read p; do ./adb shell pm enable $p; done
```

### Tingkat 3 — Kembali ke layar utama bawaan Xiaomi

Pengaturan → Aplikasi → **ATV Home** → Copot pemasangan (Uninstall).
Layar utama bawaan Xiaomi otomatis jadi yang utama lagi.

### Tingkat 4 — FACTORY RESET: stick kembali persis seperti baru beli

Ini menghapus **semuanya**: akun Google, aplikasi yang kamu unduh/pasang (termasuk launcher
baru dan Vidio hasil sideload), video latar, dan semua pengaturan. Aplikasi bawaan yang tadi
dimatikan akan **aktif lagi otomatis**, dan Opsi Pengembang ikut mati. Setelah ini stick
benar-benar seperti hari pertama keluar dari kardus.

**Sebelum mulai, pahami dulu:**
- Kamu harus **login ulang** akun Google (dan akun aplikasi lain: Netflix, Vidio, dll).
- Siapkan password Wi-Fi — akan diminta lagi.
- Tidak ada data pribadi penting di stick biasanya, tapi kalau ada aplikasi yang menyimpan
  sesuatu lokal, itu ikut hilang.

**Caranya, pelan-pelan pakai remote:**

1. Tekan tombol **Home**, buka **Pengaturan** (ikon gerigi di pojok kanan atas).
2. Pilih **Preferensi Perangkat** (Device Preferences).
3. Gulir ke bawah, pilih **Reset**.
4. Pilih **Reset ke setelan pabrik** (Factory data reset).
5. TV akan menampilkan peringatan. Pilih **Reset ke setelan pabrik** sekali lagi,
   lalu **Hapus semua** (Erase everything).
6. Stick akan restart sendiri. Layar jadi hitam sebentar, lalu muncul logo Mi dan
   layar penyiapan awal. **Jangan cabut listrik selama proses ini** (sekitar 3–10 menit).
7. Ikuti penyiapan awal seperti waktu pertama membeli: pilih bahasa, sambungkan Wi-Fi,
   login akun Google, selesai. Stick kamu sudah perawan lagi.

**Kalau layar utama tidak bisa dibuka sama sekali** (misalnya stick berputar-putar di logo
terus), factory reset masih bisa dipicu dari komputer selama ADB masih tersambung:

```
adb reboot recovery
```

Stick akan masuk ke menu *recovery* (layar hitam dengan tulisan kecil). Di sana pilih
**Wipe data / factory reset** lalu **Yes**. Buat memilih di menu ini biasanya perlu keyboard
USB (colok pakai kabel OTG, karena port stick cuma satu dan dipakai listrik — pakai OTG
*cable* bercabang/Y supaya listrik tetap masuk). Setelah selesai pilih
**Reboot system now**. Selesai, lanjutkan penyiapan awal seperti langkah 7 di atas.

---

## Kalau Ada Masalah (FAQ)

- **`adb connect` gagal / timeout** — Pastikan stick dan komputer di Wi-Fi yang sama. Matikan
  VPN di komputer. Cek lagi IP stick (kadang IP berubah setelah restart; ulangi Langkah 4).
- **Kotak izin tidak muncul di TV** — Matikan lalu nyalakan lagi Debugging USB (Langkah 3),
  lalu ulangi `adb connect`. Pastikan kamu mencentang "Selalu izinkan".
- **Tulisannya `unauthorized` terus** — Di stick: masuk Opsi Pengembang, cari
  **Cabut otorisasi debugging USB** (Revoke USB debugging authorizations), klik, lalu ulangi
  Langkah 6 dari awal.
- **Setelah dimatikan, satu aplikasi favoritku error** — Nyalakan lagi paket yang terakhir
  kamu matikan (perintah `pm enable` di atas), restart, coba lagi. Itulah gunanya mematikan
  bertahap.
- **Stick malah lemot aneh setelah restart pertama** — Normal sesaat (Android sedang
  menata ulang). Tunggu 5–10 menit, restart sekali lagi.
- **Vidio crash saat dibuka padahal dari file APK "resmi"** — Itu jebakan file APK tunggal:
  Vidio butuh file pendamping. Penjelasan + solusinya ada di
  [docs/vidio-xapk-fix.md](vidio-xapk-fix.md).
- **IP stick berubah-ubah** — Di pengaturan router kamu ada fitur "DHCP reservation" buat
  mengunci IP untuk satu perangkat. Cari MAC stick di Pengaturan → Tentang → Status.

---

## Penutup

Panduan ini ditulis dari pengalaman nyata mengoprek satu Mi TV Stick sampai tuntas —
semua langkah di atas sudah dibuktikan di perangkat beneran, bukan teori.

Silakan dipakai, disalin, dan disebarluaskan. Kalau stick kamu jadi lebih kencang dan
panduan ini menolong, itu sudah lebih dari cukup. 🙏

*— Yosi (Bruce) Tapjani, Depok, Indonesia*
*Repo: https://github.com/yositapjani/ExtremeSlimTV — lisensi MIT, pakai sebebasnya.*
