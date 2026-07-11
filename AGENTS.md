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
- Keep `AppModule` in its own `AppModule.kt` file. Defining it in `BVApp.kt`, which also references the generated `.module` extension, can cause Koin KSP to defer generation and break release compilation.
- Load `AppModule().module` and `BiliApiModule().module` side by side in `BVApp`; avoid restoring the cross-module annotation include that triggered the KSP generation issue.

## Validation and Git

- Before committing, run `git diff --check` and the signed default release build.
- After installation, verify the installed version and foreground activity over ADB.
- Preserve unrelated user changes in a dirty worktree.
- The maintained working branch for these patches is `my-patches`, pushed to `origin` (`git@github.com:cxfcxf/bv.git`).
