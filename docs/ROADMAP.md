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

## Phase 2 — Web server core + auth ⬜

- ⬜ Ktor/Netty `EmbeddedServer` as a foreground service (HTTP :8080 + SSL :8443, fallback pools)
- ⬜ TLS self-signed X.509 in a BKS keystore (BouncyCastle; atomic write; regen on corruption)
- ⬜ Handshake: `POST /init`, WS auth, 2FA pending + on-device approval, session-token mint
- ⬜ `ReplayGuard` wired into token-mode requests; login rate limiting (5/min per-peer + 20/min global)
- ⬜ CORS allowlist, path sandbox, SSRF guard
- ⬜ GraphQL wired (vendored kGraphQL fork); introspection debug-only + 15s timeout
- ⬜ HTTP `/health`, `/shutdown` (loopback); WebSocket `[4-byte type] + XChaCha20(payload)` framing
- ⬜ Serve the placeholder SPA from the classpath with `__SERVER_TIME__` / `X-Server-Time`

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
