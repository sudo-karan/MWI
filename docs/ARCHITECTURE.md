# MWI Architecture

## Modules & the KMP seam

MWI is two Gradle modules:

* **`:app`** — a thin Android application host. It owns the `AndroidManifest.xml` (permissions and
  component declarations), the launcher icon and host theme, the compiled web SPA under
  `src/main/resources/web/`, and exactly one Kotlin file: the `MainApp` `Application`, which hands
  the process-wide `Application` to `:shared` and does nothing else.
* **`:shared`** — a Kotlin Multiplatform library that holds *all* logic.

Inside `:shared`:

```
commonMain/   platform-agnostic logic + the Compose UI
  crypto/       the security core (expect Crypto + ReplayGuard + AuthTokens)
  db/           Room entities, DAOs, AppDatabase, converters
  preferences/  DataStore-backed AppPreferences
  platform/     ~expect declarations (Time, Lock, Ids, ... the "seam")
  ui/           Compose: App(), HomeScreen (feature grid), theme
androidMain/  the actual implementations + Android-only subsystems
  crypto/       Crypto.android.kt (Tink + BouncyCastle)
  platform/     Time/Lock/Ids/AndroidApp actuals
  db/           buildAppDatabase actual (Room + BundledSQLite)
  preferences/  DataStore factory actual
  MainActivity  the single Compose Activity host
  (webserver/, services/, workers/, receivers/ — added in later phases)
androidUnitTest/  JVM tests for the crypto core and the replay guard
```

The rule of the seam: `commonMain` never references a JVM/Android/Tink type directly. Anything
platform-specific is an `expect` in `platform/` (or the `Crypto` object) with an `actual` in
`androidMain`. This keeps the web/auth/domain logic portable and, crucially, **testable** — the
crypto actuals are deliberately free of any `android.*` call, so they run under a plain JVM unit
test.

## Security model (spec §5 — "BUILD FIRST")

The cryptographic core is implemented and tested first because everything else depends on it.

### Primitives (`crypto/Crypto.kt` ↔ `Crypto.android.kt`)

* **Body cipher:** XChaCha20-Poly1305, 32-byte keys, random 24-byte nonce prepended to the
  ciphertext, AEAD instances cached per key. Backed by Tink's `subtle.XChaCha20Poly1305`.
* **Signatures:** Ed25519 (Tink `subtle.Ed25519Sign`/`Ed25519Verify`).
* **Key agreement:** ECDH over **secp256r1**, with explicit **on-curve validation** of the peer's
  public key (invalid-curve attack defense) before agreement. Session key = `SHA-256(shared_secret)`.
  Backed by BouncyCastle.
* **Randomness:** CSPRNG everywhere — `secureRandomBytes` for general use, `SecureRandom.getInstanceStrong()`
  for key material, and `randomPassword` via **rejection sampling** over a fixed **54-char** alphabet
  (never `kotlin.random.Random` for secrets).
* **Constant-time compare:** the only comparison used on secrets/tokens/MACs.

### Two token layers (`crypto/AuthTokens.kt`)

* A server-wide **URL token** (opaque `/fs` and `/media` URLs) that **rotates on each server start**.
* **Per-session** API tokens, persisted as `DSession` rows.

The WS handshake key is `first 32 bytes of SHA-512(password)`. The login password is stored only as
`SHA-256(password)` (hex), compared in constant time. The password itself is a high-entropy
machine-generated secret, exchanged out-of-band (QR / on-device display) — which is why a fast KDF is
the documented, correct design here rather than a slow password hash.

### Replay protection (`crypto/ReplayGuard.kt`)

Token-mode API bodies decrypt to `TIMESTAMP|NONCE|{json}`. `ReplayGuard` accepts a request only if
its timestamp is within ±30s of now **and** its nonce has not been seen in that window; nonces are
pruned as the window slides, bounding memory. Covered by `ReplayGuardTest`.

### Handshake (delivered in Phase 2, on top of this core)

1. `POST /init` (`c-id` header): decrypt with the cached token → paired; else issue a rate-limited
   random password.
2. WS `/?cid=…&auth=1`: browser sends `XChaCha20(AuthRequest)` keyed by the handshake token.
3. If 2FA is on (default): `AuthStatus.PENDING` + on-device approval → mint a random session token,
   persist `DSession`, return it encrypted.
4. Browser reconnects and registers by decrypting with the token.

### Defenses layered in Phase 2

Login rate limit (5/min per real socket peer + 20/min global — no `ForwardedHeaders`); strict CORS
allowlist (never `*`); file-path sandbox (canonicalize; deny `/data /proc /sys /system /apex /vendor
/dev /root`); SSRF guard on the proxy (reject loopback/link-local/metadata, RFC1918-only client);
DB-browser denylist (`sessions, peers, chat_channels`); GraphQL introspection debug-only + 15s
timeout; `/shutdown` loopback-only; no decrypted-body logging; generic errors; `allowBackup=false`.
TLS uses a self-signed X.509 in a BKS keystore at `filesDir` (atomic write, regen on corruption),
with the cert fingerprint exposed for TOFU.

## Data model (`db/`, spec §10)

One Room database (KMP, BundledSQLite driver, `exportSchema = true`). A fresh install starts the
schema at **version 1** — the rebrand begins its own migration history rather than inheriting
PlainApp's ~v16. Entities are also `@Serializable` so rows project straight into GraphQL/WS payloads.
Timestamps are epoch-millis `Long`s; chat content, channel members, and string lists round-trip
through kotlinx.serialization `TypeConverter`s. `sessions`, `peers`, and `chat_channels` are
denylisted from the developer DB browser.

## UI (`ui/`)

Compose Multiplatform + Material3. `App()` is the root; `HomeScreen` renders the home **feature grid**
(spec §7A) from a single `AllFeatures` list, badging which tiles are already implemented vs.
scaffolded. i18n is via **Compose Resources** (`composeResources/values/strings.xml`) — the build
fails on duplicate string keys, as required.

## What runs where

The embedded Ktor/Netty server, MediaProjection screen mirror, telephony, DLNA/BLE, and the
foreground services are **Android-only** and live in `androidMain` (added in later phases). The web
request/response logic that is platform-agnostic lives in `commonMain/web/` behind `HttpCall` /
`WsSession` abstractions, so only the Ktor/Netty/SSL plumbing is Android-specific.
