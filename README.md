# MWI — Mobile Web Interface

Turn an Android phone into a **self-hosted management hub** reachable from any web browser on the
same network. No accounts, no cloud, no telemetry, no ads. The phone runs an embedded HTTP/HTTPS
server; you open its LAN address in a desktop browser and get a full dashboard to manage the device
— files, photos/videos/audio, contacts, SMS, calls, apps, notifications, a live screen mirror with
remote control, and more. MWI is also a capable standalone Android app (notes, RSS reader, media
players, P2P chat, DLNA casting, Pomodoro, sound meter, …).

**Core value:** your personal data never leaves your network. Every browser⇄phone byte is
password-authenticated and end-to-end encrypted (TLS plus an app-layer XChaCha20-Poly1305 channel).

MWI is a rebrand of / built on the open-source [PlainApp](https://github.com/ismartcoding/plain-app);
the `applicationId`/namespace stays `com.ismartcoding.plain`.

> **Status — Android app broadly feature-complete; web SPA pending.** Built up in the phased order
> in [`docs/ROADMAP.md`](docs/ROADMAP.md). Done and building: the KMP foundation + **security/crypto
> core** (tested), the **Ktor/TLS web server** with login + **on-device 2FA**, the **encrypted +
> replay-protected API** and **WS event push**, and domain backends for **Files, Media, Device,
> Contacts, SMS, Calls, Apps, Notifications, Notes/RSS/Bookmarks/Tags/Pomodoro, Screen Mirror,
> Nearby (mDNS), and DLNA**. The standalone app has **real in-app screens for 26 of 27 home-grid
> tiles**, a Quick Settings tile, and i18n scaffolding (English + Spanish/French started). Every
> commit builds an installable APK and keeps a green unit-test suite (60 tests).
>
> **Still pending:** the browser **dashboard SPA** (currently a placeholder page — the largest
> remaining piece), the DLNA receiver / BLE·Wi-Fi-Aware peer transports, optional AI image search,
> the rest of the ~18 locales, and release hardening. See the roadmap for the exact done/pending
> breakdown.

---

## Platform & constraints

* **Primary target: Android (minSdk 28 / Android 9+)** — the only fully shippable target.
* Structured as **Kotlin Multiplatform** (`commonMain` + `androidMain`) so shared logic and the
  Compose UI are portable, but the web server is Android-only. iOS is intentionally **out of scope**
  for now (no `iosMain`); the KMP layout keeps a future port a drop-in.
* Preserved limitations (by design): cellular-call audio can't reach the desktop on non-rooted
  Android (speaker toggle only); keyboard/text injection edits the focused editable node via
  AccessibilityService (no `INJECT_EVENTS`/root); **no phone-home/analytics/ads, ever**.

## Tech stack

Kotlin 2.3.10 · Gradle 8.14.3 · AGP 8.13.2 · JVM 17 · KSP 2.3.10 · compileSdk 36 / minSdk 28.
Modules: `:app` (thin Android host) + `:shared` (KMP, ~all logic).

Compose Multiplatform + Material3 · Room 2.8 (KMP) + BundledSQLite · DataStore · Ktor 3.5 on Netty
(Android-only) · Google Tink 1.23 + BouncyCastle (crypto) · kotlinx serialization/coroutines/datetime.

> The product spec pins some **unreleased/aspirational** toolchain versions (AGP 9.2.x, Kotlin
> 2.4.10, compileSdk 37, Gradle 9.6.1). This repo uses the newest **mutually-compatible, actually
> released** versions instead, so it builds today. Every deviation and its reason is documented in
> [`docs/TOOLCHAIN.md`](docs/TOOLCHAIN.md).

## Repository layout

```
:app        Android application module (thin host)
  src/main/AndroidManifest.xml     permissions + components (§9)
  src/main/kotlin/.../MainApp.kt   the Application (the module's one Kotlin file)
  src/main/res/                    launcher icon, host theme, FileProvider paths
  src/main/resources/web/          the compiled web SPA (PWA) — placeholder for now
:shared     KMP library — all logic
  commonMain/  crypto, db (Room), preferences, ui (Compose), platform (expect)
  androidMain/ actuals, MainActivity (+ webserver/services in later phases)
  androidUnitTest/ crypto + replay-guard tests (run on the JVM)
```

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the module seam and the security model.

## Building

Prerequisites: JDK 17+ and an Android SDK (compileSdk 36, build-tools 36). Point `local.properties`
at your SDK (`sdk.dir=…`) or set `ANDROID_HOME`.

```bash
# Run the security-core unit tests (compile + test, JVM — no device needed)
./gradlew :shared:testDebugUnitTest

# Assemble the debug APK (github flavor)
./gradlew :app:assembleGithubDebug
```

Release builds are **signed** from `keystore.properties` (see `keystore.properties.example`) and ship
with **R8/minify/shrink OFF** — the reflection-heavy Ktor + kGraphQL stack crashes when minified
without a fully verified keep set. Comprehensive keep rules live in `proguard-rules.pro` for optional
later use.

## Privacy

MWI collects nothing and phones nobody. Read the full, verifiable statement in
[`PRIVACY.md`](PRIVACY.md).

## License

See the upstream PlainApp project for licensing; MWI preserves it.
