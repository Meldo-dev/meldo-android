# AGENTS.md

Guide for AI coding agents working in meldo-android, the Android app of Meldo.

## What Meldo is

Meldo is a free, open source school planner for students in Germany. Its core feature counts how often a student raises their hand in class (Meldungen). It also has a timetable, homework, exams, grades, a calendar and widgets.

- Apps for iOS, iPadOS and Android. All three share one Rust core (meldo-core).
- No backend and no account. Data stays on the device.
- Optional integrations: WebUntis timetable import, Microsoft Teams assignments, and Google Drive backup and sync. Sync data is encrypted on the device before upload (AES-256-GCM, key from PBKDF2-SHA256) and stored in the `drive.appdata` scope.
- German is the default language, English is the second one.
- Website: meldo.dev.

Repos in the Meldo-dev org: `meldo-core`, `meldo-ios`, `meldo-android` (public), `meldo-site`, `meldo-media` (private), `.github` (org profile).

## How this repo works

meldo-android is the Kotlin and Jetpack Compose app (Android 8 and newer, Material 3 Expressive).

| Path | Contents |
|---|---|
| `app/` | Composition root: `MainActivity`, `MeldoApp`, the feature list `MeldoFeatures.kt`, `RootScreen`, manifest, signing, R8 |
| `feature/*` | One module per feature: timetable, participation, tasks, exams, grades, calendar, statistics, holidays, subjects, dashboard, onboarding, settings |
| `integration/untis` | WebUntis import, Abwesenheiten |
| `integration/teams` | Microsoft Teams assignments |
| `integration/drive` | Google Drive sync, encrypted on the device (`DriveSync.kt`) |
| `surfaces/` | Widgets, Quick Settings tile, notifications, live lesson, background refresh |
| `core-android/engine` | Builds the Rust core with `cargo-ndk` and generates the Kotlin bindings |
| `core-android/l10n` | Merges the shared string catalog into `assets/strings.json` |
| `core-android/model` | `AppData`, storage, sync format |
| `core-android/state` | `AppModel` |
| `core-android/ui` | Design system, shared rows, navigation, feature registry |
| `build-logic/` | Convention plugins `meldo.android.app`, `meldo.android.library`, `meldo.android.feature` |
| `strings/` | Android-only strings, one JSON file per feature with `de` and `en` |
| `core/` | meldo-core as a git submodule |
| `scripts/` | Security tests, MobSF scan |

How it fits together:

- Dependencies only point down: `app` to features, integrations and `surfaces`, all of them to `core-android`. A feature never depends on another feature.
- Each feature exposes a `MeldoFeature`. The bar, tablet sidebar, Mehr and Einstellungen are built from `MeldoFeatures.kt`. Links between features go by id.
- Grade math, timetable derivation, WebUntis parsing and statistics come from the Rust core through the generated bindings. Don't reimplement them in Kotlin.
- Strings: shared ones in `core/l10n/Localizable.xcstrings` (edit them in meldo-core); only strings that exist on Android alone go in `strings/<feature>.json`.
- `AppData` and the sync format stay whole in `core-android/model`. The format must match MeldoDrive in meldo-ios, so the two apps sync with each other.

## Build, test and release

Needs JDK 21, the Android SDK with NDK 29, Rust and `cargo-ndk`.

```bash
git submodule update --init
cargo install cargo-ndk
./gradlew testDebugUnitTest assembleDebug
bash scripts/security-tests.sh     # security regression tests as JVM unit tests
```

Drive and Teams need `MELDO_GOOGLE_CLIENT_ID` and `MELDO_TEAMS_CLIENT_ID` in `~/.gradle/gradle.properties`. Without them the app builds without those features.

Workflows:

- `android.yml`: unit tests and APK on pushes to `main` and on pull requests.
- `release.yml`: on a `vX.Y.Z` tag on `main`, `beta` or `alpha`, builds the APK signed with the release key and publishes a GitHub pre-release. Notes come from `.github/release-notes/<tag>.md`; `{{APK_SHA256}}` is replaced with the APK hash. Only release runs get the signing key.
- `hygiene.yml`.

## How to commit

- Commit under your own name and GitHub no-reply email, so the history shows who made each change. Never commit as an AI tool or under someone else's name, and don't change another person's git config.
- If AI helped with a commit, end the message with the trailer `Assisted-by: AI`. No `Co-Authored-By` lines for AI tools and no "Generated with" footers.
- Short imperative commit messages in plain English that say why, for example "Keep the timetable grid within one day".
- Core changes: commit and push meldo-core first, then bump the `core/` submodule in both meldo-ios and meldo-android.
- Every user-facing string goes in `meldo-core/l10n/Localizable.xcstrings`, German first, with an English translation.
- Don't claim a build or test passed unless you ran it and saw the output.

## Allowed and not allowed

- Never put secrets in the repo, code, logs, commits or chat: OAuth client IDs (Google iOS, Google Android, Microsoft Teams), keystore passwords, API keys, tokens. Say where they come from instead: the gitignored `Config/Secrets.xcconfig` on iOS, `~/.gradle/gradle.properties` on Android, GitHub secrets in CI.
- No mention of AI tools, assistant names, private notes or decision record numbers in code, comments or commits. The `hygiene` workflow in the public repos fails on this.
- Participation counts are not grades. Grade logic goes through the scale type (1 to 6, lower is better; 0 to 15, higher is better). Grading rules are configuration, not constants.
- The sync format must stay identical on iOS and Android.
- No new dependency without a stated reason. No mock data shown as real.
- Don't push to `main`, `beta` or `alpha`, and don't create tags or releases, unless the owner asked for it. Work on a branch and open a pull request.
- `beta` and `alpha` differ from `main` on purpose. Don't merge `main` into them, and don't force-push.
- Don't touch the owner's devices or real data.
- Site: no third-party requests, cookies or trackers. Keep the `/de/` and `/en/` URLs and the redirects. Legal pages: German is authoritative, English matches it.
