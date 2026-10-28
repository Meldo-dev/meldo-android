<p align="center">
  <img src=".github/readme/icon.png" width="112" height="112" alt="Meldo app icon">
</p>

<h1 align="center">Meldo for Android</h1>

<p align="center">
  <strong>The school planner that counts your hand-raises.</strong><br>
  Timetable, grades, homework and exams for students in Germany. No account, no ads, no tracking.
</p>

<p align="center">
  <a href="LICENSE"><img alt="MIT License" src="https://img.shields.io/badge/license-MIT-orange"></a>
  <img alt="Android 8+" src="https://img.shields.io/badge/Android-8%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4">
  <img alt="Rust core" src="https://img.shields.io/badge/core-Rust-B7410E?logo=rust">
</p>

> Meldo ist ein kostenloser Schulplaner für Schülerinnen und Schüler: Meldungen zählen, Stundenplan (optional aus WebUntis), Noten nach 1–6 oder 0–15 Punkten, Hausaufgaben und Klausuren. Alle Daten bleiben auf deinem Gerät.

## Features

- Count a hand-raise with one tap in the app, from a widget or from the Quick Settings tile. If phones have to stay in the bag at your school, Meldo asks once after each lesson instead.
- Type in your timetable or import it from your school's WebUntis server, including substitutions, cancellations and room changes. The current lesson shows as a Live Update.
- See your average per subject on the German 1–6 or 0–15 point scale. Your school's weighting and rounding rules are settings, so they can match what your school uses.
- Keep track of homework and exams with reminders and an exam calendar. You can scan worksheets to PDF, import Microsoft Teams assignments if you want, and write excuse notes.
- Your data stays on the device, and Meldo has no server. If you turn on backup and sync, it goes to *your own* Google Drive and is encrypted on the device before it leaves (AES-256-GCM, PBKDF2-SHA256 with 600,000 rounds). The format matches the iPhone app, so the two can sync with each other.
- German and English, light and dark mode, Material 3 Expressive.

## Download

Get the APK of the latest version from [Releases](../../releases/latest). It needs Android 8 or newer. The website is [meldo.dev](https://meldo.dev), and the iPhone app is in [meldo-ios](https://github.com/meldo-dev/meldo-ios).

## How it's built

```
app/        Kotlin + Jetpack Compose app: core, data, model, ui (features, navigation, theme), surfaces (widgets, tile, notifications)
strings/    Android-only strings, merged with the shared catalog at build time
core/       meldo-core as a git submodule: Rust crates and the shared string catalog
scripts/    Security tests, MobSF scan
```

Grade math, timetable derivation, WebUntis parsing and statistics are written once in Rust in [meldo-core](https://github.com/meldo-dev/meldo-core), and the iPhone app uses the same code. Gradle builds the Rust core for `arm64-v8a` and `x86_64` with `cargo-ndk` and generates the Kotlin bindings.

## Building (JDK 21, Android SDK with NDK 29, Rust)

```bash
git clone --recurse-submodules https://github.com/meldo-dev/meldo-android.git
```

```bash
cargo install cargo-ndk
```

```bash
cd meldo-android && ./gradlew :app:assembleDebug
```

When you pull changes to the core, run `git submodule update`.

### Google Drive and Teams (optional)

The OAuth client IDs are not in the repository. The app builds and runs without them; you just won't have Drive backup or the Teams import. To turn those on with your own Google Cloud and Azure apps, add `MELDO_GOOGLE_CLIENT_ID` and `MELDO_TEAMS_CLIENT_ID` to `~/.gradle/gradle.properties`.

## Contributing

Issues and pull requests are welcome. Please localize every user-facing string in German and English, run `./gradlew testDebugUnitTest` before you open a pull request, and don't commit secrets, keys or client IDs. For security issues, see [SECURITY.md](SECURITY.md).

## Privacy

Meldo collects no data. The [privacy policy](https://meldo.dev/datenschutz.html) has the details ([English](https://meldo.dev/privacy.html)).

## Disclaimer

Meldo is an independent project and is not affiliated with, endorsed by or connected to Untis GmbH, Microsoft or Google. WebUntis is a trademark of Untis GmbH. Timetables and grades in Meldo can differ from your school's records, and your school's records always take precedence.

## License

[MIT](LICENSE) © 2026 Yavuz Selim Kolbasar
