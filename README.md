# Jelyvo — personal fork

<img src="fastlane/metadata/android/en-US/images/icon.png" height="72" alt="Jelyvo app icon">

A **Material YouTube Music player** for Android, Wear OS and macOS Apple Silicon, built with **Kotlin** and **Compose**.

Current verification and remaining limits: [8 October 2026 audit](docs/PROJECT_AUDIT_2026-10-08.md).

> This is a personal hard fork of **[ilianoKokoro/umihi-music](https://github.com/ilianoKokoro/umihi-music)**,
> licensed GPL-3.0. It has diverged substantially from the original and is **neither supported nor
> endorsed by the original author**. Please do not take fork problems to the upstream project.

## Status ⚠️

**Sideload only.** Signed APK releases are published on the dedicated public
[`elie00/jelyvo-releases`](https://github.com/elie00/jelyvo-releases/releases) repository; Jelyvo is
not distributed through Google Play, F-Droid, IzzyOnDroid or Obtainium.

- Published APKs use Jelyvo's release key and the app can notify, download and install newer
  versions from the dedicated releases repository.
- Local release builds use the configured release key when `keystore.properties` is present and
  otherwise fall back to the machine's debug key. A debug-signed build cannot update an officially
  signed installation (and vice versa).
- It keeps upstream's application id (`ca.ilianokokoro.umihi.music`), so it **cannot be installed
  alongside** the original app.

JVM tests, Android lint, recent Room migrations and Watch storage tests are exercised locally.
Debug and minified release APKs and a macOS DMG build. macOS streaming has also been exercised in
the packaged app. Physical phone/watch pairing, Chromecast and macOS notarization remain separate
release gates; see the dated audit for exact evidence.

## Features ✨

Inherited from upstream: play your YouTube / YT Music playlists, search and play without signing
in, brand-account login, offline downloads, Android Auto browsing, English and French UI. Streams come from
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

**Wear OS** — Galaxy Watch companion plus standalone playback: search, local favorites, history,
Wi-Fi-constrained downloads, shuffle/repeat and a launcher tile. The companion controls the phone
through the Wear OS Data Layer. See [the Galaxy Watch guide](docs/GALAXY_WATCH.md).

**macOS** — search tracks, videos, albums and artists; open public YouTube playlist links; stream
audio; edit, reorder and save the queue as local playlists; keep local favorites and history;
shuffle, repeat, volume and keyboard shortcuts. Account cookies are stored in Keychain; the Account
screen can read YouTube likes. Local favorites/playlists do not synchronize to the phone.

**Public playlist import on Android** — Spotify, Apple Music and Deezer shared links can be matched
to YouTube tracks and saved locally. Only tracks visible in the providers' public pages are imported;
private libraries and full provider OAuth synchronization are not implemented.

### Not implemented

- **Editing YouTube Music playlists themselves.** Creating and deleting a remote playlist works;
  adding or removing tracks inside one does not. On-device playlists are fully editable.
- **macOS system integrations** — native Now Playing/media keys, AirPlay, offline downloads and
  Developer ID notarization are not shipped. Cmd/Ctrl+Space and Cmd/Ctrl+Left/Right control playback
  while the application has focus. Cmd/Ctrl+1–4 opens Home, Search, Library and Queue.

## Build it yourself 🔨

Requires **JDK 21** and the Android SDK (compileSdk 37, minSdk 24).

```bash
./gradlew assembleStandaloneRelease
# → app/build/outputs/apk/standalone/release/Jelyvo.apk

./gradlew :wear:assembleRelease
# → wear/build/outputs/apk/release/Jelyvo-Watch.apk

./gradlew :desktopApp:packageDmg
# → desktopApp/build/compose/binaries/main/dmg/Jelyvo-1.1.0.dmg
```

The macOS build requires an Apple Silicon Mac. It is an ad hoc signed local preview, not a
notarized public distribution. JavaFX uses a progressive MP4 stream including video bytes for
compatibility. A missing compatible rendition is reported as a playback error.

Useful properties:

| Property | Effect |
|---|---|
| `-Pbeta=false` | Drops the `-beta` suffix from the version name (the suffix is **on by default**) |
| `-PtargetAbi=arm64-v8a` | Packages native libs for that ABI only — a noticeably smaller APK. Unset means universal |
| `-PgitHash=<sha>` | Stamps a commit hash into `BuildConfig` |

Without a `keystore.properties` at the repo root, release builds fall back to your local **debug**
keystore. Share that keystore between machines if you want to install over an existing build.

## Credits 🎨

- Original project and the overwhelming majority of this codebase: **[ilianoKokoro](https://github.com/ilianoKokoro)** — [umihi-music](https://github.com/ilianoKokoro/umihi-music).
- **Jelyvo identity and app logo**: created for this fork.
- **Word-timed lyrics parsing, lyrics source matching and translation, reading lyrics from audio files,
  the listening party server and protocol, the Studio theme's layout and type scale, and the Studio
  components (sheets, menus, rows, buttons, alerts, controls)**: adapted from
  [BitChord](https://github.com/kushagrasinghx/BitChord) by Kushagra Singh and contributors
  (GPL-3.0). Adapted files say so in their header.
- **Automix** (`automix/`, under the AGPL-3.0): the transition planner comes from BitChord and
  [Orchard](https://github.com/SFG5453/Orchard), Copyright (C) 2026 SFG545 and Kushagra Singh. See
  `automix/NOTICE.md`.
- **DM Sans**, the Studio theme's typeface: The DM Sans Project Authors, under the SIL Open Font
  License (`music-ui/licenses/OFL-DMSans.txt`).
- **Translations**: the app speaks French only. The other inherited locales
  are archived in `app/translations-legacy` until their translations are complete. This fork
  no longer synchronizes with upstream Crowdin. See [the translation policy](docs/I18N.md). Thank you to
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

The bundled **Nunito** typeface is a separate work by the Nunito Project Authors, used under the
SIL Open Font License 1.1 — see [music-ui/licenses/OFL-Nunito.txt](music-ui/licenses/OFL-Nunito.txt).
It ships as a variable font in `:music-ui` so the Android and macOS clients render identically;
before that it was an Android downloadable font, which meant devices without Google Play Services
silently fell back to the system typeface.
