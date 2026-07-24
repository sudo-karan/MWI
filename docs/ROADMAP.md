# MWI Build Roadmap

MWI is built in the phased order from the product spec (§13). Each phase is a self-contained,
buildable increment. This file tracks what is **done**, **in progress**, and **scaffolded**.

Legend: ✅ done · 🟡 in progress · ⬜ scaffolded/planned

## Phase 1 — Foundation ✅

- ✅ KMP project skeleton (`:app` + `:shared`, Android-only targets)
- ✅ Version catalog with a buildable toolchain (see `docs/TOOLCHAIN.md`)
- ✅ `AndroidManifest.xml` with the full permission set (§9) and app scaffolding
- ✅ Compose app shell: `App()`, `MwiTheme`, `HomeScreen` (home feature grid, §7A)
- ✅ i18n via Compose Resources (duplicate-key-failing string catalog)
- ✅ Room data model: all entities (§10), DAOs, `AppDatabase`, converters, `buildAppDatabase`
- ✅ DataStore preferences (`AppPreferences`)
- ✅ **Security/crypto core (BUILD FIRST):** `Crypto` (XChaCha20-Poly1305, Ed25519, ECDH secp256r1
  with on-curve validation, CSPRNG, `randomPassword`), `AuthTokens` (two token layers), `ReplayGuard`
- ✅ Unit tests for the crypto core and replay guard (run on the JVM)
- ✅ README, PRIVACY.md, TOOLCHAIN.md, ARCHITECTURE.md, keep rules, CI workflow

## Phase 2 — Web server core + auth 🟡

- ✅ Ktor/Netty `EmbeddedServer` as a foreground service (`HttpServerService`), HTTP + SSL with
  fallback port pools (8080… / 8443…), `runningLimit` 1000, all plugins installed (WebSockets,
  Compression, ContentNegotiation/json, Caching/Conditional headers, CORS, PartialContent,
  AutoHeadResponse)
- ✅ TLS self-signed X.509 in a BKS keystore (`TlsKeystore`, BouncyCastle; atomic write; regen on
  corruption; SHA-256 fingerprint for TOFU)
- ✅ `RateLimiter` (5/min per real peer + 20/min global), `PathSandbox`, `SsrfGuard` — all unit-tested
- ✅ WebSocket frame codec `[4-byte type] + XChaCha20(payload)` + `TokenEnvelope` parser + event
  types + auth models — all unit-tested
- ✅ HTTP `/health`, `/shutdown` (loopback-only), SPA served from the classpath with `__SERVER_TIME__`
  injection + `X-Server-Time`
- ✅ **Web Console screen wired end-to-end**: start/stop the server from the app, shows the LAN URL
- ✅ **Auth core** (`AuthManager`, unit-tested): constant-time password verify, on-device 2FA
  pending-approvals with await/approve/reject, session-token mint, `DSession` persistence via
  `RoomAuthStore` (Room + DataStore login password)
- ✅ **WS login handshake wired**: browser sends `XChaCha20(SHA-512(pw)[..32], AuthRequest)`; server
  verifies, replies encrypted; 2FA holds the socket until on-device approval (2-min timeout). The
  Web Console screen shows the login password and **approve/reject** cards for pending logins.
- 🟡 `POST /init` (basic response in place) — cached-token pairing path to come
- ⬜ Registered event socket (session-token registration + WS event fan-out) — lands with domains
- ⬜ `ReplayGuard` wired into token-mode request bodies (lands with GraphQL/domain routes)
- ⬜ GraphQL wired (vendored kGraphQL fork); introspection debug-only + 15s timeout
- ⬜ Per-call "404 for all but /health when web disabled" gating; CORS dynamic private-origin policy

## Phase 3 — First domains: Files, Media, Device ⬜

- ⬜ Files (GraphQL + `/fs`, `/upload`, `/upload_chunk`, `/zip`), sandboxed, byte-range, thumbnails
- ⬜ Media (images/videos/audios + counts/buckets, streaming)
- ⬜ Device (deviceInfo/battery/app + clipboard/relaunch/updateDeviceName)

## Phases 4–9 ⬜

4. Comms — Contacts, SMS/MMS, Calls (+control), Notifications (+reply), Apps
5. Screen mirror — MediaProjection + MediaCodec + WS + Accessibility control + keyboard
6. Standalone tools — Notes, RSS, players, QR, Pomodoro, Sound Meter, tags, browsers, settings
7. P2P + casting — BLE / Wi-Fi Aware, peer chat/channels/pairing, DLNA, mDNS
8. AI image search (optional; github/google flavors)
9. Polish — i18n locales, PWA frontend, dev tools, backup/restore, hardening, docs, release workflow

## Acceptance criteria (spec §14)

The end state: a browser on the same Wi-Fi hits `https://<ip>:8443`, authenticates with a password +
on-device 2FA, and manages files/photos/videos/audio/contacts/SMS/calls/apps/notifications; every API
call is XChaCha20-encrypted and replay-protected; the screen mirror streams live with remote control;
the standalone app runs its tools without the server; the release APK (minify off) installs and
launches without reflection crashes; no analytics/ads; `allowBackup=false`; PRIVACY.md accurate.
