# VidFix 📱🎬

**Android APK — Video Enhancer.** Pick a video, choose presets, tap Start. That's it.
**APK Android — pemperbagus video.** Pilih video, pilih preset, tekan Mulai. Selesai.

> Built automatically by GitHub Actions — no PC needed. 🤖

---

## ✨ Features / Fitur

- 🔍 **Upscaling** — Keep / 720p / 1080p / 1440p / 4K (Lanczos + unsharp mask)
- 🎨 **Color Grading** — Auto-Fix / Vivid / Cinematic (gamma, S-curve, colorbalance)
- ⏱️ **FPS Interpolation** — Keep / 30 / 60 / 120 fps (blend or motion-compensated)
- 🧠 **Content auto-detect** — real / anime / game via noise-color-edge math on 5 sample frames
- 📱 **RAM adaptive** — auto-detects device RAM, caps interpolation on 3–4 GB devices
- 🇬🇧🇮🇩 **Bilingual** — English + Bahasa Indonesia, switchable in-app
- 🛡️ **Safe storage** — results saved to `Movies/VidFix` (no storage permission needed)

Engine: FFmpeg 8.1 via the maintained [ffmpeg-kit](https://central.sonatype.com/artifact/dev.ffmpegkit-maintained/ffmpeg-kit-full-gpl) drop-in replacement (x264/x265 GPL encoders included).

---

## 📲 Get the APK / Cara Dapat APK

### Option 1 — Download a ready APK from Releases
1. Buka tab **Releases** di repo ini
2. Download `VidFix-v1.0.0.apk`
3. Install (izinkan "install from unknown sources" bila diminta)

### Option 2 — Fork & let GitHub Actions build it for you
1. **Fork** this repository (atau push zip ini ke repo barumu)
2. Buka tab **Actions** → workflow *Build APK* jalan otomatis setiap push
3. Setelah selesai (±10 menit): buka run terbaru → **Artifacts** → `VidFix-APK`
4. Push tag `v1.0.1` (atau versi apa pun) → APK otomatis nongol di **Releases** ✨

---

## 🚀 Usage / Cara Pakai

1. **Choose video** — pilih video dari galeri/files
2. Set preset:
   - *Content*: Auto-detect / Real / Anime / Game
   - *Upscaling*: Keep / 720p / 1080p / 1440p / 4K
   - *Color*: Keep / Auto-Fix / Vivid / Cinematic
   - *FPS*: Keep / 30 / 60 / 120
   - *RAM*: Auto-detect (default) atau pilih manual
3. **Start enhancing** → progress bar jalan
4. **Open result** — video tersimpan di `Movies/VidFix/`

---

## ⚠️ Honest notes / Catatan jujur

- Processing is **CPU-bound**: a 1-min 1080p/60fps clip can take 5–20 min. Keep the screen on (the app holds a wake flag) or plug in the charger.
- The CI keystore is generated fresh each build → **updating the app may require uninstall first**. Fork dan pasang keystore-mu sendiri via repo secrets untuk tanda tangan permanen.
- APK size ±40 MB (bundled FFmpeg full-gpl).
- GPL-3.0 notice: the bundled FFmpeg engine is GPL — distributed APKs follow GPL terms (see LICENSE).

---

## 🗺️ Roadmap

- [x] v1.0 — core engine, presets, auto-detect, RAM adaptive, bilingual
- [ ] v1.1 — custom resolution + custom FPS inputs
- [ ] v1.2 — batch queue
- [ ] v2.0 — AI upscaling (experimental)

## 📄 License

MIT for the app code (see LICENSE). FFmpeg engine under LGPL/GPL-3.0 as bundled.
