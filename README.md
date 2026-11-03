# Radiant

[English](README.md) | [Čeština](README.cs.md) | [Deutsch](README.de.md) | [Español](README.es.md) | [Français](README.fr.md) | [日本語](README.ja.md) | [Polski](README.pl.md) | [Русский](README.ru.md) | [中文](README.zh.md)

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/I5I829ZD02) <a href="https://www.buymeacoffee.com/radiant_player"><img src="https://img.buymeacoffee.com/button-api/?text=Support%20the%20project&emoji=%F0%9F%92%BB&slug=radiant_player&button_colour=FFDD00&font_colour=000000&font_family=Comic&outline_colour=000000&coffee_colour=ffffff" alt="Buy Me a Coffee" height="36"></a>

A simple, native Android music player for your **local audio files**. It plays music only from the one folder you choose, so ringtones, voice messages and other sounds on your phone never end up in your library.

No ads, no accounts, no internet access required. Everything stays on your device; the internet is used only if you choose to download music from your own cloud (Dropbox) or to download the AI models for stems (once; they can also be imported from files).

> **Languages:** English, German, Russian, Ukrainian, Chinese, Japanese, French, Czech, Spanish and Polish. Choose one under **Settings → App language**, or leave it on *System* to follow the phone (English is used if the phone language isn't supported).

## Features

- **Your music, your folder.** Pick a folder once; Radiant scans it and its subfolders and rescans every time you open the app. It uses Android's Storage Access Framework, so it cannot see anything outside that folder.
- **Cloud download (optional).** In *Settings → Music folder* you can connect your Dropbox and download music from a cloud folder you choose into a separate `Dropbox` folder inside your music folder. It works one way only: nothing is uploaded, changed or deleted in the cloud, and nothing on your device is deleted or overwritten. You sign in through your browser, so the app never sees your password.
- **Library:** Playlists, Albums (grouped automatically from file tags) and Tracks, with search and sorting in every section.
- **Playlists:** create, rename, delete, add and remove tracks. *Liked tracks* is always pinned at the top.
- **Queues:** a fourth section with exactly four fixed queues (they can't be created or deleted). Add tracks from the player button or the "⋮" menu, remove them, and reorder by long-press drag or "move up / down".
- **Drag to reorder** playlists, albums and tracks with a long press.
- **Background playback** with a media notification, lock screen controls and headset button support.
- **Player:** play/pause, next/previous, seek, shuffle, repeat (off / all / one), cover art, and a quality badge for the file format and bitrate.
- **Lyrics:** a button under the "i" in the player opens the lyrics. Radiant looks for lyrics in the file tags (ID3 USLT/SYLT, Vorbis `LYRICS`, M4A `©lyr`) and in `.lrc` / `.txt` files with the same name next to the track, and checks again every time you open the app. Synced (`.lrc`) lyrics scroll along with the song, highlight the current line and fade the lines already sung; tap a line to jump to it. Plain lyrics are shown in a compact view.
- **Playback speed** from 3× slower to 3× faster in 0.1 steps, with pitch preserved.
- **Sound settings:** mono/stereo, volume normalization, bass and treble boost, subsonic filter, and a 12-band equalizer with 10 presets. The equalizer can apply one curve to all tracks or remember a separate curve for each track.
- **Interface language:** *Settings → App language* opens a window with *System* pinned on top and the ten languages below it in alphabetical order. The change applies at once, without interrupting playback.
- **Themes:** light, true-black dark (great for OLED screens) or system default, with dynamic colors on Android 12+.
- **App lock** (Settings → Security, off by default): a 4-digit, 6-digit or custom code is asked every time you open the app. If you forget it, a recovery file with 12 random words unlocks the app. Details are in the *App lock* section below.
- **Power saving** (Settings): *Off*, *Medium* (almost no animations, rarer progress updates) or *Intensive* (also forced black theme, volume capped at 40%, mono only, EQ/bass/treble/subsonic/normalization bypassed, wired or phone-speaker output only with no Bluetooth, manual folder scan, smaller cover cache). **AI stems are unavailable in Medium and Intensive** (and a running job is cancelled when you switch to them); use *Off* to create stems.
- **Track details:** tags, format, bitrate, sample rate, channels, file size and more.
- **Stems (AI):** the *Create stems* item in a track's "⋮" menu splits it into **vocals, drums, bass and the rest** with Open-Unmix (umxhq, ONNX), and mixes the parts you tick into **one track**: everything except bass gives the full track without bass, everything except vocals gives the instrumental. It runs on the device, not in real time, with a progress bar, remaining-time estimate and cancel; it keeps going in the background with a notification. The result is a 16-bit / 44.1 kHz WAV in `Stems/<artist - title>/` inside your music folder (for example `… - No Bass.wav`), so it appears in the library right away. See "Stems (AI)" below.
- **Formats:** MP3, FLAC, OGG/Opus, M4A/AAC, ALAC, WAV and anything else Android can decode.

## App lock

Optional protection for the app, **off by default**. Turn it on under **Settings → Security**.

- **Three kinds of code:** 4 digits, 6 digits, or a custom code (Latin letters and digits, 4–64 characters, no spaces).
- **When it asks:** on every launch and every time the app comes back from the background. Rotating the screen or changing the app language does not lock it.
- **Lock screen:** a plain white or pure black background (following the theme) with a code field. Digit codes use a minimalist on-screen keypad; a custom code uses the normal system keyboard. The eye button shows or hides what you typed. Screenshots and screen recording are blocked on this screen only.
- **Change or turn off:** *Settings → Security → Change code*, or switch the lock off. Both ask for the current code first.

### How the protection works

- **No code is ever stored.** Radiant keeps only a random salt and a PBKDF2-HMAC-SHA256 hash (30,000 iterations), compared in constant time.
- **Brute-force protection.** After every 5 wrong codes in a row you have to wait: 30 seconds, then 1, 2, 4… minutes, up to 15 minutes. The pause survives restarting the app. In the worst case at the longest pause, trying all 10,000 four-digit codes takes about 3 weeks, and all 1,000,000 six-digit codes over 5 years. Short codes get their strength from this limit rather than from entropy: a 4-digit code is about 13 bits and a 6-digit code about 20 bits. For more, choose a custom code: every random letter or digit adds about 6 bits, so 8 random characters are about 48 bits.
- **High-entropy recovery file.** When you create a code, Radiant generates **12 random words** from a built-in list of 700 English words, picked with a cryptographically secure random generator. That is 700¹² ≈ 2¹¹³ combinations, about **113 bits of entropy**, which is far out of reach of brute force. You save the words as a `.txt` file wherever you like. The lock turns on only after the file has been written, and the app keeps just a hash of the words, not the words themselves.
- **Forgot the code?** Tap *Forgot password* on the lock screen and pick the recovery file. If it matches, the lock is turned off and you can enable it again with a new code and a new file. Changing the code also creates a new file, and the old one stops working.
- **Keep the file safe.** Anyone who has it can remove the lock, so don't leave it where others can find it. If you lose both the code and the file, the only way back is clearing the app's data, which also deletes playlists and settings. Nobody, including the developer, can recover the code: there are no accounts and no servers.
- **What it does not do.** The lock protects the app's interface, not the audio files on your storage. Music keeps playing while the app is locked, and the notification controls keep working.

## Stems (AI)

- **What you get.** The four parts (vocals, drums, bass, other) add up exactly to the original, so any selection is a clean sub-mix: tick the parts you want to keep and they are mixed into one WAV. Re-running with the same selection overwrites the file.
- **Models.** Four ONNX files of ~34 MB each (vocals, drums, bass, other) are not bundled in the APK. In *Settings → Stems (AI)* you can download them (SHA-256 verified) or import them from files if you fetched them beforehand (`model.onnx` from the Hugging Face repos `MixDirective/open-unmix-umxhq-{vocals,drums,bass,other}-onnx`; files are recognised by checksum, so the name does not matter). After that everything works offline.
- **How it works.** The track is decoded by the system decoder to 44.1 kHz stereo, cut into ~12 s blocks with 2 s of context on each side, each block goes through the networks (STFT 4096/1024) and the results are stitched seamlessly. Memory use does not depend on track length.
- **Quality.** All four networks always run together and their estimates are balanced with a soft mask (v² / Σv²), so the parts add up to the original and none is too quiet. That needs all four models and about 4 passes of the network per block; processing needs roughly 250 MB of RAM.
- **Speed.** Typically a few minutes per track on a recent phone; CPU and battery are used heavily. Work runs as a foreground service with a notification (with a *Cancel* button) and keeps the CPU awake until it finishes. Stems are blocked in Medium and Intensive power saving.
- **Limitations.** Plain ratio mask without Wiener filtering (slightly below reference umxhq quality); vocals, drums and bass are empty above ~16 kHz (the networks were trained up to that frequency); multichannel files use the first two channels; formats Android cannot decode (e.g. ALAC on some devices) cannot be separated. Write access to the music folder is required (choose the folder again if the app asks).

## Requirements

- Android 8.0 (API 26) or newer
- For stems (optional): power saving set to *Off*; about 142 MB of free storage for the four models (~34 MB each); about 250 MB of free RAM while processing; temporary disk space of about 85 MB per 4 minutes of audio (removed afterwards); and the result is a WAV of about 42 MB per 4 minutes. ONNX Runtime is included in the APK, nothing else needs installing.

## Installation

The latest stable version is always available on the **[Releases](../../releases/latest)** page as an `.apk` file.

1. Download the `.apk` from the latest release to your phone.
2. Open it and allow installation from unknown sources if Android asks.
3. Launch Radiant.

New versions install over old ones without uninstalling, so your playlists and settings are kept.

## Getting started

1. Open the app and tap **Choose folder**.
2. Select the folder with your music. The library fills automatically.
3. Browse **Playlists**, **Albums** and **Tracks** and tap a track to play it.
4. To build a playlist, tap **⋮** next to any track and choose **Add to playlist**.

## Building from source

You need JDK 17 and either Android Studio or the Android SDK with Gradle 8.9+.

- **Android Studio:** open the project, switch the build variant to **release** (debug builds are noticeably slower), then choose *Build → Build APK(s)*.
- **Command line:** run `gradle assembleRelease`.
- **GitHub Actions:** fork the repository and run the **Build APK** workflow; the APK is attached to the run as an artifact.

The APK is written to `app/build/outputs/apk/release/app-release.apk`.

> **Signing.** Official releases are signed with a private key that is not part of this repository. A build you make yourself is signed with the standard Android debug key instead, so it installs fine but will not update over an official release (uninstall one before installing the other). If you want your own signed builds in GitHub Actions, add the repository secrets `KEYSTORE_BASE64` (your `.jks` file encoded in base64) and `KEYSTORE_PASSWORD`; the key alias defaults to `radiant`.

## Built with

Kotlin, Jetpack Compose (Material 3) and AndroidX Media3 (ExoPlayer).

## Contributing

Issues and pull requests are welcome. For larger changes, please open an issue first to discuss what you would like to change. By submitting a pull request you agree that your contribution is provided under the same license as the project.

## Third-party components

ALAC (Apple Lossless) playback uses the FFmpeg decoder from AndroidX Media3. The **Build APK** workflow builds it from the unmodified [FFmpeg](https://ffmpeg.org) 6.0 source (only the ALAC decoder, no GPL components enabled) together with the Media3 1.5.1 extension (Apache-2.0), and links it into the app as a native library.

FFmpeg stays under its own license, [LGPL 2.1 or later](https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html), not PolyForm Noncommercial. The source code is available at <https://github.com/FFmpeg/FFmpeg> (branch `release/6.0`). You can rebuild the app with your own modified FFmpeg by changing the workflow. Builds made without this step (for example locally, without an `.aar` in `app/libs`) work normally but cannot play ALAC.

Stem separation uses [ONNX Runtime](https://onnxruntime.ai) (MIT) and the [Open-Unmix](https://github.com/sigsep/open-unmix-pytorch) umxhq models (MIT, © 2019 Inria) as an ONNX export by MixDirective. The umxhq weights were trained on MUSDB18-HQ; check the terms of the training data and weights if you plan to use the app beyond personal use.

## License

Radiant is licensed under the [PolyForm Noncommercial License 1.0.0](LICENSE.md).

You are free to use, study, modify and share it for any **noncommercial** purpose, including personal use, hobby projects, education and non-profit organizations. **Commercial use is not permitted** without a separate agreement, so you may not sell the app or publish it for profit. If you would like to use Radiant commercially, please get in touch by opening an issue in this repository.

Please keep the `LICENSE.md` file and its `Required Notice` line with any copy you share.
