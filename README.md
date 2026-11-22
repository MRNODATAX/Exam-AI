# ExamAI ✦ · by nodata

Asisten AI mengambang (*floating overlay*) untuk Android (Kotlin, `minSdk 26`) yang membantu membaca dan menjawab soal Google Form di **Google Chrome** secara otomatis, dilengkapi fitur **Crop-to-AI**, **Crop-to-Google**, dan **Floating Browser**.

---

## Fitur Utama

- **Auto-Answer Chrome (`FormAutoAccessibilityService`)**:
  - Membaca teks soal dan pilihan jawaban yang tampil di `com.android.chrome`.
  - Mengirim soal ke AI dengan *system prompt* ketat, lalu otomatis mengklik opsi jawaban (*radio/checkbox*) atau mengisi kolom teks (`ACTION_SET_TEXT`).
- **Floating Mini-Panel & Crop Layar (`FloatingWidgetService` + `CropOverlayView`)**:
  - Tombol melayang (*draggable*) dengan pengaturan transparansi (*opacity*).
  - **Crop Layar ke AI / Google**: Ambil *screenshot* layar (Android 11+), pilih area soal, lalu kirim ke **Gemini Vision** untuk dijawab langsung atau dicari di **Floating Google WebView**.
  - **Mini Chat**: Tanya jawab cepat langsung di atas layar tanpa keluar dari Chrome.
- **Multi-Provider AI (`AiApiHelper`)**:
  - Mendukung **Gemini API (Google)**, **OmniRoute API**, dan **Standard OpenAI-Compatible** lengkap dengan tombol **Tes API Key**.
- **5-Tab Navigasi & Riwayat**:
  - **Beranda**, **Auto**, **Chat**, **Laporan** (riwayat jawaban otomatis lokal), dan **Atur**.

---

## Cara Install & Penggunaan

1. Unduh **`Exam-AI.apk`** dari halaman **Releases (`v1.0`)** di repository ini.
2. Install di perangkat Android (minimal Android 8.0 / API 26; fitur *Screenshot Crop* membutuhkan Android 11+).
3. Buka aplikasi, masukkan nama Anda, lalu aktifkan **Izin Overlay** dan **Accessibility Service** (`Exam AI Form Auto-Reader`).
4. Masuk ke tab **Atur**, pilih provider AI, masukkan **API Key**, lalu tekan **Tes API Key** dan **Simpan**.
5. Tekan **Jalankan Floating Widget** di tab **Beranda**, buka Google Form di **Google Chrome**, dan ketuk ikon **ExamAI** yang mengambang.
