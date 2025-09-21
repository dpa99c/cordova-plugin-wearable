Contributing to cordova-plugin-wearable
======================================

Thanks for your interest in improving the project! This guide explains the
expected workflow, coding standards, and validation steps for contributions to
`cordova-plugin-wearable` and its example applications.

By submitting code you agree that your contribution is licensed under the
project's license (GNU GPL v3.0 or later).

## Getting Started

- Fork the repository and create a feature branch: `git checkout -b feat/my-change`.
- Install dependencies according to `README.md` and `.github/copilot-instructions.md`.
- Keep the plugin repo (`cordova-plugin-wearable/`) and the example repo (`cordova-plugin-wearable-example/`) as sibling folders so the example apps can reference the plugin locally.

## Development Workflow

- Keep pull requests focused: prefer a single feature or fix per PR. Large changes should be broken into logical commits.
- When adding, removing, or renaming native sources update `plugin.xml` and verify the example app platforms are refreshed so the changes copy into `platforms/`.
- For documentation-only updates, ensure all affected READMEs are kept in sync (main plugin + example projects) and update the Table of Contents if headings change.

## Coding Standards

- **JavaScript (www/ and example web assets)**
  - Write modern, Promise-friendly code and preserve callback compatibility when modifying the public API.
  - Factor shared string literals (paths `/data`, `/state`, `/op`, `/snapshot`, `/ack`, keys `heartbeat`, `message`, `type`, `alpha`, `beta`, timeouts `4000`, `3000`, etc.) into module-level constants.
  - Keep inline logging lightweight; prefer `Logger` utilities in native layers.

- **Kotlin (src/android and Wear OS app)**
  - Use `private const val` for string/time constants and define them near the top of the file or in a companion object.
  - Add KDoc to public classes/functions describing parameters and return values. Include short comments for non-obvious private helpers.
  - Route logs through the shared `Logger` class and guard verbose output behind `enableLogging` where possible.

- **Swift (src/ios and watchOS app)**
  - Prefer `enum` or `struct` namespaces for constants (`static let pathData = "/data"`).
  - Document public APIs with Swift-style doc comments (`///` describing parameters, returns, and notes).
  - Keep private helper comments concise, explaining the why rather than restating code.

- **General**
  - Follow existing formatting; avoid reformatting unrelated code.
  - Update type definitions in `types/wearable.d.ts` whenever the JavaScript API changes.
  - When adding dependencies, document the rationale in the relevant README.

## Testing Expectations

Before submitting a PR, run the relevant tests and include the results in your description:

- **Cordova automated tests**: From `cordova-plugin-wearable-example/cordova-app`, run `cordova-paramedic --platform <android|ios> --plugin ../..` or the matching npm scripts/tasks (`npm run test:android`, `npm run test:ios`, `npm run test:both`).
- **Manual harness**: For UI or connectivity changes, install `cordova-plugin-test-framework` and the local `tests/` plugin, set `<content src="cdvtests/index.html" />`, then exercise the manual scenarios on paired devices/emulators.
- **Wear OS companion**: Run `./gradlew :wear:assembleDebug` (and optionally `:wear:testDebugUnitTest`) after modifying Wear OS code.
- **watchOS companion**: Run `xcodebuild -scheme "Wearable Plugin watchOS example Watch App" -destination 'generic/platform=watchOS' build` (and `test`) after modifying watch targets.
- **Lint/build**: Ensure Cordova (`cordova build android`, `cordova build ios`) and/or Capacitor (`npx cap sync`, Xcode/Gradle build) succeed when your changes affect native code.

Add a brief "Testing" section to your PR description detailing the commands/devices used.

## Documentation

- Update `README.md`, example READMEs, and `TODO - General.md` checklists when behavior or workflows change.
- Keep diagrams or screenshots current; store assets in `img/` and reference them with relative paths.
- Document new configuration flags or plugin preferences in both the main README and the example app README.

## Commit Messages & Pull Requests

- Use meaningful commit messages (e.g., `fix(android): handle null capability nodes`).
- Reference related issues in the PR body (`Fixes #123`).
- Describe the motivation, approach, and testing in the PR template or body.
- Be prepared to iterate on feedback; maintainers may request additional tests or documentation.

## Reporting Issues

- Include detailed reproduction steps, device/emulator info, OS versions, plugin version, and logs (`adb logcat`, Xcode console) when filing issues.
- Attach minimal sample projects when possible; the example repo is a good starting point for reproduction cases.

## Code of Conduct

Be respectful and collaborate constructively. Discriminatory or harassing behavior will not be tolerated. The maintainers reserve the right to close or reject contributions that violate these expectations.

Thank you for helping make `cordova-plugin-wearable` better!

