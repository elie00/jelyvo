# Jelyvo — personal fork

<img src="fastlane/metadata/android/en-US/images/icon.png" height="72" alt="Jelyvo app icon">

A **Material YouTube Music player** for Android, built with **Kotlin** and **Jetpack Compose**.

> This is a personal hard fork of **[ilianoKokoro/umihi-music](https://github.com/ilianoKokoro/umihi-music)**,
> licensed GPL-3.0. It has diverged substantially from the original and is **neither supported nor
> endorsed by the original author**. Please do not take fork problems to the upstream project.

## Status ⚠️

**Sideload only.** This fork is not published anywhere — no GitHub release, no F-Droid, no
IzzyOnDroid, no Obtainium. You build it yourself and install the APK by hand.

- The APK is **debug-signed**, so Play Protect warns on install ("Install anyway").
- Updating means rebuilding and installing over the top **with the same debug key**. The in-app
  updater was removed, so nothing updates itself.
- It keeps upstream's application id (`ca.ilianokokoro.umihi.music`), so it **cannot be installed
  alongside** the original app.

Verification is partial and stated honestly: **126 JVM unit tests across 21 classes** pass, both
debug and minified release APKs build, and the release APK has been installed and smoke-tested on
an arm64 Android emulator. There is still no instrumented test suite; Chromecast hand-off still
needs a physical receiver test and the full matrix needs a pass on the target phone.

## Features ✨

Inherited from upstream: play your YouTube / YT Music playlists, search and play without signing
in, brand-account login, offline downloads, Android Auto browsing, 31 languages. Streams come from
the InnerTube API, so no ads are served — that is a consequence of how it plays, not a feature this
fork implements.

Added by this fork:

**Playback** — crossfade (a volume fade around gapless transitions, 0–12 s) · A–B loop · sleep
timer, including "end of current track" · playback speed · equalizer with a **separate profile per
audio output** (speaker / wired / Bluetooth) · loudness normalization · resume where you left off ·
three audio-quality levels and a mobile-data saver · seeded radio and autoplay, with suggestions
kept visually separate from your own queue · swipe a title to like it or add it to the queue ·
exclude unwanted autoplay suggestions.

**Video** — Song ⇄ Video toggle on the player · fullscreen landscape · picture-in-picture.

**Cast** — Chromecast device picker in the player · queue and position hand-off between phone and
receiver · signed stream refresh before the receiver consumes queued tracks · explicit handling of
local-only files and unsupported remote controls.

**Offline & local files** — auto-download of liked songs under a **storage quota** (auto-downloads
are purge-eligible, manual ones never are) · Wi-Fi-only and metered-network switches · plays audio
files already on the phone (MediaStore).

**Library & playlists** — on-device playlists you can create, rename, reorder by dragging, edit and
delete, search and sort, with **JSON export/import** via SAF · save the current queue as a playlist ·
account library (liked songs / saved albums / followed artists) with filter chips · accent- and
case-insensitive search inside liked songs · new-release notifications for followed artists.

**Discovery** — tabbed search with a Videos tab, top-result card, spell corrections, removable
recent searches, moods & genres browsing · voice search · home refocused on discovery.

**Listening statistics** (local, on-device) — Week / Month / Year / All-time top tracks and artists,
a 26-week heatmap, and an annual "Wrapped" card.

**Look & feel** — player theme derived from the current artwork, with a blurred drifting backdrop ·
7 selectable theme colours including dynamic colour · docked mini-player that auto-hides on scroll ·
synced lyrics via LRCLIB, no account needed.

**System** — home-screen playback widget (Glance) · opens shared YouTube links (`/watch`, `/v/`,
`/embed/`, `youtu.be`, `m.youtube.com`) · **backup / restore to a JSON file** covering history,
local playlists, queue and preferences. Session cookies are deliberately excluded from backups: they
are encrypted with a device-bound Keystore key and could never be decrypted after a restore.

### Not implemented

- **Editing YouTube Music playlists themselves.** Creating and deleting a remote playlist works;
  adding or removing tracks inside one does not. On-device playlists are fully editable.
- **Spotify import** — the code is present but hidden and unfinished. It needs a client id at build
  time (`-PspotifyClientId=…`) *and* the OAuth redirect is still not wired up, so it does not work
  end to end even when enabled.
- **Wear OS** — no `:wear` module.

## Build it yourself 🔨

Requires **JDK 21** and the Android SDK (compileSdk 37, minSdk 24).

```bash
./gradlew assembleStandaloneRelease
# → app/build/outputs/apk/standalone/release/Jelyvo.apk
```

Useful properties:

| Property | Effect |
|---|---|
| `-Pbeta=false` | Drops the `-beta` suffix from the version name (the suffix is **on by default**) |
| `-PtargetAbi=arm64-v8a` | Packages native libs for that ABI only — a noticeably smaller APK. Unset means universal |
| `-PspotifyClientId=<id>` | Enables the (unfinished) Spotify import entry point |
| `-PgitHash=<sha>` | Stamps a commit hash into `BuildConfig` |

Without a `keystore.properties` at the repo root, release builds fall back to your local **debug**
keystore. Share that keystore between machines if you want to install over an existing build.

## Credits 🎨

- Original project and the overwhelming majority of this codebase: **[ilianoKokoro](https://github.com/ilianoKokoro)** — [umihi-music](https://github.com/ilianoKokoro/umihi-music).
- **Jelyvo identity and app logo**: created for this fork.
- **Translations**: inherited from the upstream project's Crowdin, and still shipped here. This fork
  no longer syncs with it, so the list is frozen as of the fork point — thank you to
  [ilianoKokoro](https://crowdin.com/profile/ilianoKokoro),
  [Tusk](https://crowdin.com/profile/frittusk),
  [Mickael81](https://crowdin.com/profile/Mickael81),
  [Alina Ye-jin](https://crowdin.com/profile/Alinayejin),
  [j.bosch](https://crowdin.com/profile/j.bosch),
  [Vladik01-11](https://crowdin.com/profile/Vladik01-11),
  [Smashik](https://crowdin.com/profile/Smashik),
  [jackgr545](https://crowdin.com/profile/jackgr545),
  [Ori](https://crowdin.com/profile/GerOriki) and
  [corrigo](https://crowdin.com/profile/corrigo).

## License 📄

GPL-3.0, inherited from the upstream project. See [LICENSE](LICENSE).
