Repository: cordova-plugin-wearable — Copilot onboarding

Purpose
-------
This file tells a coding agent how to work with this repository quickly and safely. Follow these instructions and trust them before searching the repo; only search if something here is missing or clearly out-of-date.

What this repository is
----------------------
- Short summary: A Cordova / PhoneGap plugin named `cordova-plugin-wearable` that provides a JavaScript API and native iOS (Swift) and Android (Kotlin) implementations to communicate with WearOS and watchOS devices.
- Project type: Cordova plugin with an example Cordova app `cordova-plugin-wearable-example/cordova-app` that demonstrates plugin usage and can be used to build/test the native integration.
- Languages & runtimes: JavaScript (plugin JS), Kotlin (Android native), Swift (iOS native), XML (Cordova/plugin manifests), Gradle (Android build). The example app uses Cordova Android (tested with cordova-android v14) and Cordova iOS.
 - Languages & runtimes: JavaScript (plugin JS), Kotlin (Android native), Swift (iOS native), XML (Cordova/plugin manifests), Gradle (Android build). The example app uses Cordova Android (tested with cordova-android v14) and Cordova iOS.
 - The repository also contains a Wear OS example app under `cordova-plugin-wearable-example/wearos-app` — a standalone Gradle/Kotlin project (the wear module) used to test the watch-side implementation.
- Approx repo size & shape: Small plugin (tens of files). Key folders: `src/` (native sources), `www/` (plugin JS), `cordova-plugin-wearable-example/cordova-app/` (example app), root `plugin.xml`, and `package.json`.

Important Note
--------------
This project is not live yet so no changelog, version bumps, backwards compatibility, or deprecation policies are in effect. However, treat it as a production-quality plugin that will be used by real developers once released.

Important trust rule
-------------------
Trust the contents of this file. Do not run broad searches or mass edits unless the instructions here fail or a test indicates a mismatch.

Quick contract for agent-made changes
------------------------------------
- Inputs: changes to plugin JS or native files under `src/` or `www/`, or changes to the example app under `cordova-plugin-wearable-example/cordova-app/`.
- Outputs: a working plugin where `cordova-plugin-wearable-example/cordova-app` builds for Android and/or iOS and the plugin API surface (`Wearable`) behaves as before.
- Error modes to avoid: failing Cordova/Gradle builds, introducing failing Type/compile errors in Kotlin/Swift, or missing plugin.xml updates.

Build and validation (always follow these steps)
---------------------------------------------
Environment preconditions (always verify):
- Use Node.js and npm compatible with Cordova tools. The example declares `cordova-android@^14.0.1` in devDependencies — use Cordova CLI that supports that (Cordova 11+). Ensure Java JDK (11+), Android SDK (platforms and build-tools matching `cordova-android@14`). For iOS builds: Xcode with a Swift 5 toolchain.

Global mandatory policy for platform refreshes (READ THIS FIRST)
----------------------------------------------------------------
Whenever you modify native or platform-specific source files (for example: files under `src/android/`, `src/ios/`, `cordova-plugin-wearable-example/cordova-app/www/`, `cordova-plugin-wearable-example/wearos-app/`, or `cordova-plugin-wearable-example/watchos-app/`), you MUST run the refresh/build steps described in the appropriate "If making changes to ..." subsection before testing or building any example apps. This policy applies equally to:

- the Cordova example app (`cordova-plugin-wearable-example/cordova-app`),
- the Wear OS example app (`cordova-plugin-wearable-example/wearos-app`), and
- the watchOS example app (`cordova-plugin-wearable-example/watchos-app`).

Run each subsection's exact commands from the working directory specified in that subsection (for example `cordova-plugin-wearable-example/cordova-app` or `cordova-plugin-wearable-example/wearos-app`). Skipping this step may lead to native changes not being copied into the example app's `platforms/` folder and will cause confusing build/test failures.

Which changes map to which subsection (quick reference):

- Changes under `src/android/`, `src/ios/`, or edits to `plugin.xml` -> follow the "If making changes to Cordova plugin API or plugin.xml" and the platform-specific subsections (Android/iOS) and run the commands from `cordova-plugin-wearable-example/cordova-app`.
- Changes to `cordova-plugin-wearable-example/cordova-app/www/` (JS) -> run `cordova prepare` from `cordova-plugin-wearable-example/cordova-app`.
- Changes to `cordova-plugin-wearable-example/wearos-app/` or any files under the wear app -> run the Wear OS subsection commands from `cordova-plugin-wearable-example/wearos-app` (use `./gradlew :wear:assembleDebug`).
- Changes to `cordova-plugin-wearable-example/watchos-app` -> run the Xcode/xcodebuild steps from `cordova-plugin-wearable-example/watchos-app` as documented in the watchOS subsection.

## If making changes to example Cordova app JS code
Always run `cordova prepare` from `cordova-plugin-wearable-example/cordova-app` working directory.

## If making changes to Cordova plugin API or plugin.xml
Always rebuild both plaform:
- cd to `cordova-plugin-wearable-example/cordova-app`
- Run `cordova platform rm android --nosave && cordova platform add android && cordova build android`
- Run `cordova platform rm ios --nosave && cordova platform add ios && cordova prepare ios && cd platforms/ios && xcodebuild -workspace "Wearable Plugin example.xcworkspace" -scheme "Wearable Plugin example" -configuration Debug -destination generic/platform=iOS "CODE_SIGN_IDENTITY=''" CODE_SIGNING_REQUIRED=NO "CODE_SIGN_ENTITLEMENTS=''" CODE_SIGNING_ALLOWED=NO`

## If making changes to native Android plugin code
- cd to `cordova-plugin-wearable-example/cordova-app`
- Run `cordova platform rm android --nosave && cordova platform add android && cordova build android` from working directory to ensure native files are copied correctly.

## If making changes to native iOS plugin code
- cd to `cordova-plugin-wearable-example/cordova-app`
- Run `cordova platform rm ios --nosave && cordova platform add ios && cordova prepare ios && cd platforms/ios && xcodebuild -workspace "Wearable Plugin example.xcworkspace" -scheme "Wearable Plugin example" -configuration Debug -destination generic/platform=iOS "CODE_SIGN_IDENTITY=''" CODE_SIGNING_REQUIRED=NO "CODE_SIGN_ENTITLEMENTS=''" CODE_SIGNING_ALLOWED=NO` working directory to ensure native files are copied correctly.
- Note that the watchOS app is embedded into the iOS project via a Cordova hook script (`cordova-plugin-wearable-example/cordova-app/hooks/after_prepare/embed-watchos-app.js`). If you make changes to the watchOS native code, ensure you also follow the watchOS subsection below.

## If making changes to the Wear OS example app
- cd to `cordova-plugin-wearable-example/wearos-app/`
- Build using the local Gradle wrapper:

```bash
./gradlew :wear:assembleDebug
```

- The debug APK will be produced under `cordova-plugin-wearable-example/wearos-app/wear/build/outputs/apk/debug/` (look for `wear-debug.apk`). Install it to a connected Wear OS device or emulator with:

```bash
adb -s <watch-device-id> install -r wear/build/outputs/apk/debug/wear-debug.apk
```

- To run unit tests:

```bash
./gradlew :wear:testDebugUnitTest
```

## If making changes to the watchOS example app
- cd to `cordova-plugin-wearable-example/watchos-app/Wearable\ Plugin\ watchOS\ example/`
- Build using the xcodebuild command line tool:

```bash
xcodebuild -scheme "Wearable Plugin watchOS example Watch App" -configuration Debug -destination 'generic/platform=watchOS' build
```
- If making changes to project/target settings or adding/removing files, run using the clean action:

```bash
xcodebuild -scheme "Wearable Plugin watchOS example Watch App" -configuration Debug -destination 'generic/platform=watchOS' clean build
```

- To run unit tests:

```bash
xcodebuild test -scheme "Wearable Plugin watchOS example Watch App" -destination 'platform=watchOS Simulator,name=Apple Watch Series 10 (46mm)'
```

## Testing the Cordova Plugin

The plugin includes comprehensive automated and manual tests using cordova-plugin-test-framework and cordova-paramedic.

### Prerequisites for Testing

1. **Paired Simulators/Devices**:
   - **iOS**: Create a paired iPhone + Apple Watch simulator in Xcode. For these tests, create a paired simulator named **"iPhone 17 Pro"**:
     - Open Xcode → Window → Devices and Simulators
     - Select "Simulators" tab
     - Click "+" button → Select "iPhone 17 Pro" and pair with "Apple Watch 11"
   - **Android**: Pair a Wear OS emulator or physical device with your phone/emulator using the Wear OS companion app

2. **Test Dependencies** (already in package.json devDependencies):
   - `cordova-plugin-test-framework` - Jasmine test harness
   - `cordova-paramedic` - Test automation tool

### Installing Test Plugins

From the example app directory:

```bash
cd cordova-plugin-wearable-example/cordova-app
cordova plugin add cordova-plugin-test-framework
cordova plugin add ../../tests
```

The test suite is located in `tests/` directory:
- `tests/plugin.xml` - Test plugin manifest
- `tests/package.json` - Test plugin dependencies
- `tests/tests.js` - Test definitions (automated and manual)

### Running Automated Tests

**Using cordova-paramedic (recommended):**

```bash
# Android
cordova-paramedic --platform android --plugin ./

# iOS (uses paired simulator "iPhone 17 Pro")
cordova-paramedic --platform ios --plugin ./ --emulator --target="iPhone-17-Pro, 26.0"
```

**Using VS Code tasks:**

- `test-plugin-android` - Run automated tests on Android
- `test-plugin-ios` - Run automated tests on iOS paired simulator
- `test-plugin-both` - Run tests on both platforms sequentially

**Manual test running:**

```bash
cd cordova-plugin-wearable-example/cordova-app
# Edit config.xml and set: <content src="cdvtests/index.html" />
cordova platform add android
cordova run android
# Navigate to cdvtests/index.html and click "Auto Tests" then "Run"
```

### Running Manual Tests

Manual tests provide interactive UI for testing messaging, state sync, and connectivity:

1. Install test plugins (see above)
2. Set `<content src="cdvtests/index.html" />` in config.xml
3. Build and run the app:
   ```bash
   cordova run android
   # or
   cordova run ios --emulator --target="iPhone-17-Pro, 26.0"
   ```
4. Navigate to cdvtests/index.html → Click "Manual Tests"
5. Follow test instructions for setup, messaging, and state sync tests

**Using VS Code tasks for manual testing:**

- `test-plugin-android-manual` - Build and run with test UI on Android
- `test-plugin-ios-manual` - Build and run with test UI on iOS paired simulator

### Test Coverage

The test suite validates:

- API surface (all methods exist with correct signatures)
- Configuration (platform-specific options)
- Platform support checks (isSupported, isPaired, isConnected)
- State sync operations (set/get/remove, JSON pointers, listeners)
- Callback and Promise API styles
- Error handling (invalid inputs, empty keys, malformed paths)
- Message and event listeners

Tests run on actual devices/emulators to validate the full native bridge integration.

### Debugging Test Failures

If tests fail:

1. **Check prerequisites**: Ensure paired simulators/devices are set up correctly
2. **Enable logging**: Tests configure `enableLogging: true` automatically
3. **Check platform logs**:
   - Android: `adb logcat -s Wearable:V StateStore:V StateSync:V`
   - iOS: View console in Xcode when running on simulator
4. **Verify watch companion app**: Ensure the watch app is installed and running
5. **Platform refresh**: If native code changed, re-add platforms:
   ```bash
   cd cordova-plugin-wearable-example/cordova-app
   cordova platform rm android --nosave && cordova platform add android
   ```

Project layout and where to change things
---------------------------------------
- Root files:
  - `package.json` — plugin metadata; example app is declared under `cordova-plugin-wearable-example/cordova-app` and references plugin as `file:../`.
  - `plugin.xml` — the plugin manifest. Update this whenever adding/removing native source files or new preferences / frameworks.
  - `README.md` — usage examples and API notes.

- Important directories:
  - `www/` — plugin JS entry point: `www/wearable.js` (exports the global `Wearable` object).
  - `src/android/` — Kotlin source files copied into the Android platform when installed. Files:
      - `WearableImpl.kt`, `Wearable.kt`, `WatchConnect.kt`, `WatchListener.kt`, `Logger.kt`.
  - `src/ios/` — Swift files installed into the iOS platform: `WearableImpl.swift`, `Wearable.swift`, `WatchConnect.swift`, `Logger.swift`.
  - `cordova-plugin-wearable-example/cordova-app/` — an example Cordova application used to build and smoke-test the plugin. Important files:
      - `config.xml` — example app preferences (Swift version set for iOS).
      - `package.json` — declares `cordova-android` devDependency and references plugin via `file:../`.

Checks run before check-in and CI
--------------------------------
- There are no GitHub Actions workflows in this repository by default. The agent should not assume CI runs. Use the local build steps above to validate changes before proposing a PR.
- A good PR should include: build verification steps, which platforms were smoke-tested, and any special install-time preferences used (for Play Services versions).

Editor/formatting/linters
-------------------------
- There are no repo-level ESLint, SwiftLint, or ktlint configs included. Follow conservative edits: match repository style (small, focused changes), avoid reformatting unrelated files.

Files to review first when making changes
---------------------------------------
1. `plugin.xml` — ensure new native files and framework dependencies are declared.
2. `www/wearable.js` — JS API surface. Keep callback + Promise support consistent.
3. `src/android/*.kt` — keep package `cordova.plugin.wearable` and public method signatures consistent with Cordova exec bridge.
4. `src/ios/*.swift` — ensure Swift class names match plugin manifest parameters in `plugin.xml`.

Developer guidance and safe defaults
----------------------------------
- If you're changing native APIs, update `plugin.xml` and test by reinstalling the platform in the example app.

Useful Wear OS debugging tips
----------------------------
- Collect logcat from both phone and watch while reproducing issues. Example filters (adjust device id as needed):

  ```bash
  # phone (host)
  adb logcat -s Wearable:V WearableFGService:V WearableClient:V WatchListener:V CordovaPlugin:V

  # watch (use -s <watch-id> if multiple devices)
  adb -s <watch-device-id> logcat -s RealWearableClient:V Wearable:V
  ```

- Useful log tags to include when adding debug logs: `RealWearableClient`, `WearableForegroundService`, `WatchConnect`, `ListenerBridge`, `WearableImpl`.
- Capability discovery can be delayed or flaky during development. Implement fallbacks that iterate `Wearable.getNodeClient(context).connectedNodes` and attempt sends to each node while diagnosing propagation issues.
- If capabilities are not visible on the other device, check Play Services versions, ensure the companion app is installed/updated, and consider toggling bluetooth/airplane mode to force rediscovery.
- On physical devices, mismatched Play Services or out-of-date companion apps are common causes of intermittent failures.


Notes for LLM agents and future contributors
------------------------------------------
- Prefer targeted edits to native files and update `plugin.xml` when adding/removing source files.
- Keep log verbosity gated by a debug flag where possible; avoid leaving highly verbose logging in the main branch unless it's behind a temporary debug switch.
- When diagnosing watch/phone connectivity problems, include short log excerpts in PRs showing node/capability queries and message send/receive operations — these are often essential for reproducing issues remotely.
- When making changes to the code, don't add comments that explain why or what the previous code did. Instead, just make the change and document the current behaviour in the code comments. This keeps the code clean and focused on the present implementation.

Last checked files (useful snippets)
-----------------------------------
- `plugin.xml` declares Android Gradle/Kotlin files under `src/android` and Swift files under `src/ios`. It also exposes `www/wearable.js` as the JS module that clobbers `Wearable`.
- `www/wearable.js` exports a Promise-friendly API (see README). Keep callback compatibility.

When to search the repo (rare)
------------------------------
- Only search the repo if a command from this file fails, or when a change references a file not documented here. Prefer targeted searches for exact filenames (e.g. `plugin.xml`, `WearableImpl.kt`) rather than global pattern searches.

Contact / issues
----------------
- Upstream repo / issues: https://github.com/dpa99c/cordova-plugin-wearable/issues

End of instructions.
