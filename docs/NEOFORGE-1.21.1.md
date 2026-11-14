# SkyLyrics 0.4.0-neoforge — Minecraft 1.21.1

Based on original main commit `83f8e135a1b902f5a1665fd1627aeb0568dfd1ab`.
All changes belong to `neoforge-1.21.1`; main and the historical Forge distributions are preserved.

## Implementation

Reuses the original world anchors, placement geometry, cinematic animations, Cormorant Garamond font and its OFL license, LRC parser, LRCLIB exact metadata lookup, asynchronous session worker, and disk cache.
NeoForge client-only mod entry point, config, lifecycle events, rendering buffer API, resource identifiers and configuration screen now target 1.21.1/Java 21.
No HUD renderer, packet channel, Mixins, shader replacement or third-party JAR patches are introduced. Lyrics use an independent buffer at AFTER_PARTICLES, with pose/depth state restoration.

Default billboarding is false: yaw/pitch/roll and world position are captured at birth and stay fixed as the camera moves. SIDES now alternates left/right on successful placements. Other existing placement modes remain configurable. Smooth original fade/scale transitions remain.

## Playback decision

Inspected the installed SpotiCraft `1.21.1-0.0.1-release-neoforge-all.jar` with javap.
`SpotifyScreen` owns private progress/duration/playing/metadata fields; metadata is truncated for its UI. `init()` creates its update timer, and `onClose()` cancels it. It exposes a public static SpotifyApi object, but no independent immutable playback snapshots/events. Calling `syncData()` also performs network and UI work. Reading private fields would be fragile and stale after closing its screen; reusing the API would couple SkyLyrics to mutable authenticated state and token refresh. Neither is used.

The existing independent Windows GSMTC bridge is reused, with Spotify sessions preferred and other OS media sessions used if none exists. No Spotify token, secret, login or account file is read. The PowerShell 5.1 child is read-only, polls every 500 ms, and is destroyed on disconnect/shutdown. Requires Windows 10 1809+ or Windows 11 and an application exposing media metadata/timeline. Desktop Spotify is the intended first validation target. Browser sessions and players without duration/artist may not supply enough data. Remote Spotify Connect devices are not promised to appear locally.

Snapshots compensate transport delay, freeze on pause, reconcile seeks, identify track changes, and expire after two seconds without updates. No media session produces no lyrics. Unsupported platforms report WINDOWS_REQUIRED; Linux/macOS automatic media providers remain pending. `/skylyrics lrc` can test manually supplied files without OS integration.

LRCLIB calls run off the client/render thread with 6 s connection/10 s request limits, revision-tagged cancellation, 429 backoff, and exact title/artist/album/duration validation. Cached synchronized lyrics expire after 30 days; missing/instrumental/unsynchronized results after one day. Read failures/offline responses yield an empty safe result, and matching unexpired cache works offline. LRCLIB identity uses the real public repository URL. No live service result is claimed by the offline suite.

## New configuration and controls

`config/skylyrics-client.toml` is created by NeoForge; existing choices are preserved. Relevant defaults:

| Setting | Default | Purpose |
|---|---|---|
| enabled | true | Enable sky lyrics |
| spotify | true | Enable the independent Windows media bridge |
| lrclibIdentity | https://github.com/AngelAleman15/SkyLyrics | Public LRCLIB User-Agent identity |
| enableBillboarding | false | Keep orientation independent of camera |
| placementMode | SIDES | Alternate lateral appearances |
| fontStyle | CUSTOM | Original Cormorant Garamond font |
| syncOffsetMs | 0 | Positive values display lyrics earlier |
| maxVisibleLyrics | 4 | Bound simultaneous phrases |

Other original visual settings remain in this new mod's TOML. Cache/imports live under `config/skylyrics/{cache,local,imports}`. Each client sees its own local session; the server receives no music information.

Only one key mapping: `key.skylyrics.toggle` (SkyLyrics category), **unbound by default**, normal in-game context, no modifier required. This avoids assigning the original L over the approved sculk-transmitter L. Players may bind it later in Controls. No approved modpack bindings are changed. Client-only commands `/skylyrics config`, `status`, `spotify`, `stop`, `demo`, `anchor <text>`, `lrc <filename> <seconds>` and `import <filename>` are retained.

## Build and checks

Standalone development: Java 21, `./gradlew build` (ModDev 2.0.107, NeoForge 21.1.266). This Gradle path is configured but was not executed in the migration environment.

The delivered JAR was compiled against the existing patched NeoForge 21.1.266 libraries, avoiding another Minecraft download:

```sh
python tools/build-local.py /path/to/client/libraries
python tools/test-local.py /path/to/junit-platform-console-standalone-1.10.2.jar
```

The local compiler uses `-source 21 -target 21` because this environment's Java image has no release symbol archive. The output is Java class version 65. No new runtime library is bundled or required; Gson is supplied by Minecraft.

28 offline JUnit tests passed: parser, cache, playback clock/pause/seek, anchors, geometry, cinematic transitions, strict lateral alternation, bridge message validation, and asynchronous track replacement/missing-file safety. Live LRCLIB test is opt-in and excluded from this count. Tests do not launch Minecraft.

Pending functional validation: Windows PowerShell/WinRT bridge and real Spotify timeline, live LRCLIB songs, rendering/font on the active Sable/Veil/Create/Aeronautics client, screen controls/reload, multiplayer isolation, performance and resource cleanup under real play. API compilation/static checks are not proof of render compatibility or measured performance. Historical screenshots/tests in other documents describe Forge, not this port.
