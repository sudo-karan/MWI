# Toolchain — spec vs. what this repo actually uses

The product spec pins a very specific, partly **unreleased** toolchain. To deliver a project that
**builds today**, MWI uses the newest *actually-released, mutually-compatible* versions. This file
records every deviation and why it was forced. Nothing here is a preference — each row is dictated by
what resolves from Google's Maven and Maven Central as of mid-2026.

| Component | Spec pins | This repo | Why the deviation |
|---|---|---|---|
| Android Gradle Plugin | 9.2.x | **8.13.2** | The AGP 9.x line is **alpha-only** (latest `9.4.0-alphaNN`). 8.13.x is the newest stable line. |
| Kotlin | 2.4.10 | **2.3.10** | KSP (required by Room) has **no 2.4.x build** — it tops out at `2.3.10`. Using Kotlin 2.4.10 would break the Room/KSP processor. 2.3.10 is the newest Kotlin with a matching KSP. |
| KSP | 2.3.7 | **2.3.10** | Must match the Kotlin version exactly; 2.3.10 is the pair for Kotlin 2.3.10 (2.3.7 pairs with an older Kotlin). |
| Gradle | 9.6.1 | **8.14.3** | Gradle 9.6.1 does not exist; 8.14.3 is current and is what AGP 8.13.x supports. |
| compileSdk / targetSdk | 37 | **36** | API 37 (Android 17) is unreleased. 36 (Android 16) is the newest available platform. |
| Compose Multiplatform | 1.11.x | **1.11.1** | Matches (`1.11.1` is the latest stable in the 1.11 line). |
| Ktor | 3.5.x | **3.5.1** | Matches. |
| Room | 2.8.x | **2.8.4** | Matches. |
| Google Tink | 1.23 | **1.23.0** | Matches. |
| BouncyCastle | 1.70 | **1.81** (`jdk18on`) | 1.70 is end-of-life; the maintained `bcprov/bcpkix-jdk18on` line is used. API-compatible for our usage (secp256r1 ECDH + X.509). |
| minSdk | 28 | **28** | Matches. |
| kotlinx serialization / coroutines / datetime | — | 1.11.0 / 1.11.0 / 0.7.1 | Latest compatible with Kotlin 2.3.10. |

## Consequences of the Kotlin pin

Because Kotlin is pinned to **2.3.10** (to keep KSP/Room working), everything that participates in
compilation must be 2.3.10-compatible:

* The Compose compiler is the Kotlin-bundled `org.jetbrains.kotlin.plugin.compose` at `2.3.10`
  (decoupled from the Compose Multiplatform *runtime* version, which stays `1.11.1`).
* `kotlin-reflect` is pinned to `2.3.10` to match the compiler.

## When the spec's versions become available

When stable AGP 9.x, a KSP build for Kotlin 2.4.x, and Android API 37 ship, bumping is a
catalog-only change in `gradle/libs.versions.toml` plus a Gradle wrapper bump — the source is written
against stable APIs and should need little to no change. Re-run `:shared:testDebugUnitTest` and
`:app:assembleGithubDebug` after any bump.

## Build hygiene (unchanged from spec)

* R8 / minify / shrink are **OFF** for release (`app/build.gradle.kts`, `gradle.properties`). The
  reflection-heavy Ktor + kGraphQL + kotlinx.serialization stack crashes when minified without a
  fully verified keep set. Keep rules are maintained in `proguard-rules.pro` for optional later use.
* Release is signed from `keystore.properties` (see `keystore.properties.example`), non-debuggable.
* Default ABI `arm64-v8a`; flavors `github` / `google` / `fdroid` on the `channel` dimension.
