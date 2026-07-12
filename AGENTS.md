# BV repository guide

## Project overview

- This is an Android TV client for Bilibili, written primarily in Kotlin and Jetpack Compose for TV.
- The Android application is in `app/`.
- Bilibili HTTP/repository/WebSocket code is in `bili-api/`.
- The Media3/ExoPlayer abstraction is in `bv-player/`.
- Other supporting modules include `bili-api-grpc/` and `bili-subtitle/`.
- The packaged application ID is `dev.frost819.bv`; Kotlin/Android namespace code is under `dev.aaa1115910.bv`.
- The TV launcher activity is `dev.aaa1115910.bv.activities.MainActivity`.

## Local build environment

- Run commands from the repository root.
- Use JDK 21 for both Gradle and compilation. On this machine it is installed at:
  `/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home`
- Do not rely on the shell's default `JAVA_HOME`; it may point to JDK 17.
- Release builds expect the local, ignored `signing.properties`. Never print or commit signing credentials or keystores.
- Build the full/default signed release with:

  ```sh
  env JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home \
    ./gradlew :app:assembleDefaultRelease
  ```

- For compact failure-only output when iterating locally:

  ```sh
  env JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home \
    ./gradlew :app:assembleDefaultRelease -q 2>&1 | grep -E "^e:|FAILED" | head
  ```

- Release R8/minification can take several minutes. Do not assume it is hung while the Gradle daemon is consuming CPU.
- `versionCode` is `git rev-list --count HEAD`, and the version name includes the current commit hash. APKs are written under `app/build/outputs/apk/default/release/` with names like:
  `BV_<versionCode>_<versionName>.release_default_universal.apk`.
- `buildSrc`/Gradle daemons can retain an old Git-derived version code. If the APK name has a stale version, run `./gradlew --stop` and rebuild with JDK 21.
- The project currently uses compile/target SDK 36. AGP 8.8 may print a compileSdk 35 compatibility warning; it is a warning, not a build failure.

## Android TV / ADB workflow

- Development TV: NVIDIA Shield (`SHIELD_Android_TV`), ADB address `192.168.1.6:5555`.
- Connect and confirm the device:

  ```sh
  adb connect 192.168.1.6:5555
  adb devices -l
  ```

- Install the newly built release without clearing user data:

  ```sh
  adb -s 192.168.1.6:5555 install -r \
    app/build/outputs/apk/default/release/BV_<version>_*.apk
  ```

  Resolve the wildcard to the exact APK path before invoking `adb` if the shell/tool does not expand it safely.
- If installation reports `INSTALL_FAILED_VERSION_DOWNGRADE`, first compare the installed version with the current Git commit count. Prefer rebuilding with the correct newer version code; do not uninstall the app or use a destructive/data-clearing workaround unless the user explicitly requests it.
- Launch the TV app after installation:

  ```sh
  adb -s 192.168.1.6:5555 shell monkey \
    -p dev.frost819.bv -c android.intent.category.LEANBACK_LAUNCHER 1
  ```

- Useful verification commands:

  ```sh
  adb -s 192.168.1.6:5555 shell dumpsys package dev.frost819.bv
  adb -s 192.168.1.6:5555 shell pidof dev.frost819.bv
  adb -s 192.168.1.6:5555 shell dumpsys activity activities
  ```

  Confirm the expected `versionCode`/`versionName`, a running PID, and `MainActivity` as the resumed activity.

## Relevant implementation notes

- Live playback UI: `app/src/main/kotlin/dev/aaa1115910/bv/screen/LivePlayerScreen.kt`.
- Live playback state, buffering watchdog, and stream reconnection: `app/src/main/kotlin/dev/aaa1115910/bv/viewmodel/player/LivePlayerViewModel.kt`.
- ExoPlayer adapter and callback mapping: `bv-player/src/main/kotlin/dev/aaa1115910/bv/player/impl/exo/ExoMediaPlayer.kt`.
- Live room list UI/view model: `LiveContent.kt` and `LiveViewModel.kt`.
- Live danmaku WebSocket parsing: `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/websocket/LiveDataWebSocket.kt`.
- ExoPlayer can remain in `STATE_BUFFERING` without emitting an error or end event. The live player therefore displays buffering state and uses a 15-second watchdog to fetch a fresh signed stream URL, with up to five reconnect attempts.
- Reset the reconnect-attempt counter only after `onPlay()`, not merely `onReady()`: `READY` can occur before frames actually resume.
- Live playback must honor `Prefs.apiType`; the selected backend is authoritative and must not silently fall back to the other backend.
- Web live playback uses `/xlive/web-room/v2/index/getRoomPlayInfo`, Web headers, and the live.bilibili.com referer.
- App live playback is HTTP REST rather than gRPC. It uses `/xlive/app-room/v2/index/getRoomPlayInfo`, the regular Android live-client identity, App headers, and no Web referer. The repository-wide `android_hd` identity is rejected by this particular live endpoint.
- Both live play-info endpoints map into the same `RoomPlayInfoV2Data`/`LiveStreamInfo` model. The App access token is optional for basic playback but should be sent when available for account-entitled qualities.
- Live quality: the player requests `Prefs.liveQuality` (default `qn=10000`, original). Pressing OK/Menu in `LivePlayerScreen` opens a quality menu; a manual pick persists to `Prefs.liveQuality`. Three stream drops within 60 seconds auto-downgrade one quality level for the session only (short successful plays reset the reconnect counter, so failure *frequency*, not count, detects the "plays 5 seconds then dies" loop). Bilibili may still return a lower `current_qn` than requested; trust the returned value.
- Live CDN: bilibili serves live exclusively from its own `gotcha` nodes (`ov-` prefixed for overseas); the VOD CDN preference (`Prefs.preferredCdn`, upos mirrors/Akamai) does not apply to live. Overseas nodes often cannot sustain 原画 bitrate (~25 Mbps) transpacifically — that is what the auto-downgrade exists for. `LiveRepository.getLiveStream` returns every returned CDN URL (format × mirror host); on reconnect the player rotates to the next candidate, but only when `Prefs.preferredCdn == CdnType.Auto` — a specific CDN choice means "no rotation".
- Vertical (phone-camera) live streams: `BvVideoPlayer` uses `RESIZE_MODE_FILL`, so callers must wrap it in `Modifier.aspectRatio(...)` themselves. `LivePlayerViewModel` captures `videoWidth`/`videoHeight` from the player in `onReady`, and `LivePlayerScreen` centers the surface at that ratio (16:9 fallback), matching what `VideoPlayerV3Screen` does for normal videos.
- Live danmaku WebSocket: since 2023 the handshake JSON must include the real `uid` and `buvid` matching the cookies used to fetch the danmaku token, or the server accepts the connection but silently sends no danmaku. Token discovery (`getDanmuInfo`) is wbi-signed and requires the buvid3 cookie.
- Only live play-info/stream URL acquisition follows `Prefs.apiType`. Live browsing currently uses a deliberate mixture of Web and App HTTP endpoints, while danmaku token discovery remains Web HTTP and messages use WebSocket.
- Watch history (`HistoryData`/`HistoryViewModel`) includes live rooms (`business == "live"`, gRPC `CARD_LIVE`). Live entries carry `liveRoomId` in `VideoCardData` and open `LivePlayerActivity` directly; watch-later/detail actions are hidden for them.
- Keep `AppModule` in its own `AppModule.kt` file. Defining it in `BVApp.kt`, which also references the generated `.module` extension, can cause Koin KSP to defer generation and break release compilation.
- Load `AppModule().module` and `BiliApiModule().module` side by side in `BVApp`; avoid restoring the cross-module annotation include that triggered the KSP generation issue.

## Bilibili risk control (gaia)

Bilibili's server-side risk control gates the Web (cookie) API and changes without notice; this is a recurring cat-and-mouse. Symptoms: `code=0` responses whose `data` contains only `v_voucher` and no results (search), or `code=-352`.

- Requests to paths containing `wbi` are automatically signed (`wts` + `w_rid`) by the interceptor in `bili-api/.../http/util/ApiSign.kt`, using keys from the nav endpoint (`updateWbi`).
- `buvid3` is generated locally on first launch (`Prefs.buvid3`) and must be *activated* at startup by POSTing a fake browser fingerprint to `/x/internal/gaia-gateway/ExClimbWuzhi` (`BiliHttpApi.activateBuvid`), otherwise wbi endpoints reject it.
- Since 2026-07, an activated buvid3 alone is no longer enough for wbi search: requests must carry the full browser cookie set — `buvid3`, `buvid4` (issued by `/x/frontend/finger/spi` at startup), plus locally generated `b_nut`, `_uuid`, `b_lsid`. Use `BiliHttpApi.fingerprintCookie()` for this; do not hand-build `buvid3=...` cookies for risk-controlled endpoints.
- When search breaks again, reproduce the app's exact request flow from a desktop (fresh buvid3, activation, signed request) before touching app code — it distinguishes a server-side policy change from a client bug. The likely next escalation is requiring a logged-in `SESSDATA` cookie on search, which the app can add since users are logged in.
- Live-area room listing deliberately uses the cookie-free App endpoint `room/v3/area/getRoomList?platform=android` because the Web equivalent is strictly risk-controlled.

## Validation and Git

- Before committing, run `git diff --check` and the signed default release build.
- After installation, verify the installed version and foreground activity over ADB.
- Preserve unrelated user changes in a dirty worktree.
- The maintained working branch for these patches is `my-patches`, pushed to `origin` (`git@github.com:cxfcxf/bv.git`).
