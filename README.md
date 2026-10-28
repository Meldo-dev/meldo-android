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

---

> 🇩🇪 Meldo ist ein kostenloser Schulplaner für Schülerinnen und Schüler: Meldungen zählen, Stundenplan (optional aus WebUntis), Noten nach 1–6 oder 0–15 Punkten, Hausaufgaben und Klausuren. Alle Daten bleiben auf deinem Gerät.

## Features

- **Count hand-raises in one tap:** in the app, from a widget or the Quick Settings tile. After each lesson Meldo asks once, for schools where phones stay in the bag.
- **Timetable:** enter it yourself or import it from your school's WebUntis server, with substitutions, cancellations and room changes. Live Update for the running lesson.
- **Grades:** averages per subject on the German 1–6 or 0–15 point scale, with your school's weighting and rounding rules as configuration.
- **Homework and exams:** reminders, exam calendar, worksheet scanning to PDF, optional import of Microsoft Teams assignments, excuse notes.
- **Private by design:** everything lives on the device. An optional backup and sync goes to *your own* Google Drive, encrypted on the device first (AES-256-GCM, PBKDF2-SHA256 with 600,000 rounds). The format is the same as on iPhone, so both can sync. Meldo has no server.
- **German and English**, light and dark, Material 3 Expressive.

## Download

The APK of the latest version is under [Releases](../../releases/latest) (Android 8 or newer). Website: [meldo.dev](https://meldo.dev). The iPhone app lives in [meldo-ios](https://github.com/meldo-dev/meldo-ios).

## How it's built

```
app/        Kotlin + Jetpack Compose app: core, data, model, ui (features, navigation, theme), surfaces (widgets, tile, notifications)
strings/    Android-only strings, merged with the shared catalog at build time
core/       meldo-core as a git submodule: Rust crates and the shared string catalog
scripts/    Security tests, MobSF scan
```

The grade math, timetable derivation, WebUntis parsing and statistics are written once in Rust ([meldo-core](https://github.com/meldo-dev/meldo-core)) and shared with the iPhone app. Gradle builds the Rust core for `arm64-v8a` and `x86_64` with `cargo-ndk` and generates the Kotlin bindings.

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

After pulling core changes, run `git submodule update`.

### Google Drive and Teams (optional)

OAuth client IDs are not in the repository. Without them the app builds and runs, just without Drive backup and Teams import. To enable them with your own Google Cloud / Azure apps, add `MELDO_GOOGLE_CLIENT_ID` and `MELDO_TEAMS_CLIENT_ID` to `~/.gradle/gradle.properties`.

## Contributing

Issues and pull requests are welcome. Please keep every user-facing string localized in German and English, run `./gradlew testDebugUnitTest`, and never commit secrets, keys or client IDs. Security issues: see [SECURITY.md](SECURITY.md).

## Privacy

Meldo collects no data. See the [privacy policy](https://meldo.dev/datenschutz.html) ([English](https://meldo.dev/privacy.html)).

## Disclaimer

Meldo is an independent project and is not affiliated with, endorsed by or connected to Untis GmbH, Microsoft or Google. WebUntis is a trademark of Untis GmbH. Timetables and grades shown in Meldo can differ from your school's records; your school's information is always authoritative.

## License

[MIT](LICENSE) © 2026 Yavuz Selim Kolbasar
