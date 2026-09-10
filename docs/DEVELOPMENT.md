# Development

## Prerequisites

- JDK 17; Gradle 8.13 is provided by the wrapper.
- Android SDK platform 36, build-tools 36.0.0 and platform-tools.
- Python 3 for the repository hygiene check only.
- Android 8.0/API 26 or newer; packaged speech native libraries target ARM64 and ARMv7.
  Do not assume an x86 emulator can run offline speech.

Install SDK packages using Android Studio's SDK Manager or `sdkmanager` after reviewing the Android SDK license.
Set `JAVA_HOME` and `ANDROID_HOME` to your own installation paths. Alternatively set `sdk.dir` in
an untracked `local.properties`. Never commit machine-specific paths.

```sh
sdkmanager 'platforms;android-36' 'build-tools;36.0.0' 'platform-tools'
python3 scripts/check_repository.py
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew :app:assembleRelease
```

On Windows use `gradlew.bat`. The debug APK is `app/build/outputs/apk/debug/app-debug.apk`.
Release output is unsigned unless you configure signing privately. No production keystore or credentials
are provided. Do not distribute a development signing key as the project's release identity.

The checked-in speech AAR is approximately 55 MB; its license and checksum are documented in
[third-party notices](../THIRD_PARTY_NOTICES.md). Model weights are downloaded on first offline voice use,
not during Gradle compilation. Dependencies and Android test runtimes require network access on a cold cache.

## Tests

Unit/Robolectric tests cover local networking, TLS chain validation policy, incremental protocol parsing,
session ownership, prompt submission, presentation and voice-mode selection. No production server is
contacted by the normal suite. Hardware audio and OEM input behavior require a physical device.

`HermesProtocolLiveTest` is deliberately skipped unless `CATGO_LIVE_BASE`, `CATGO_LIVE_USER` and
`CATGO_LIVE_PASSWORD` are supplied in the shell environment. It creates a marked conversation, sends
test messages and can incur model charges. Run it only against a service/account you are authorized to use.
Do not put these variables in tracked files or GitHub pull-request jobs.

Optional fixture regeneration requires Python packages `prompt_toolkit==3.0.52` and `wcwidth==0.8.3`;
review the generated JSON before replacing `app/src/test/resources/hermes-prompt-vt.json`.

## README screenshots

The committed PNGs are captured from production Compose screens using local demo data, not a backend.
To regenerate them deliberately on a machine with the test dependencies available:

```sh
CATGO_SCREENSHOT_DIR="$PWD/docs/images" ./gradlew :app:testDebugUnitTest --tests 'win.catgo.gpt.ui.ReadmeScreenshotsTest' --rerun-tasks
```

Normal tests do not write screenshots unless that variable is set. Review images for accidental private
data and visual correctness before committing. Native Robolectric rendering is not proof of physical-device behavior.

## Device checklist

- First launch defaults to English; explicit Chinese/English preferences survive restart.
- The grey example hostname is not a filled value; saved server settings take precedence.
- Login, restart, lock/unlock, reconnect, and manual reconfiguration preserve the correct server/session.
- New conversation, session switch during generation, cancellation, and incoming prompts stay isolated.
- IME resize keeps prior conversation visible; image picker and uploads work.
- Offline voice permission, first download, cancellation, automatic sending, manual system switch and Chinese TTS.
- Command approval requires explicit action; password fields are masked and excluded from chat history.
- Upgrade from 1.3.1 has no SSH UI and retains chat configuration while clearing old SSH preferences.

CI runs static checks, local tests, debug/release builds and lint with read-only repository permissions.
The workflow cannot be considered verified on GitHub until the repository is created and its first run succeeds.
