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
- ✅ **Encrypted request pipeline** (`ApiPipeline`, unit-tested): token-mode body decrypt →
  `ReplayGuard` (window+nonce) → operation dispatch → re-encrypted response; generic error codes
- ✅ **Authenticated `POST /graphql` route**: `c-id` → `DSession` lookup → session-token decrypt →
  pipeline → encrypted response
- 🟡 Operation dispatch is a pragmatic name+variables **`ApiRegistry`** (spec's vendored kGraphQL
  fork is a documented later item; we control both ends of the contract until the SPA is built)
- ✅ **Registered event socket + WS event fan-out**: browser proves it holds the session token
  (`XChaCha20(sessionToken, "register")`), joins the **`WsHub`** (unit-tested), and receives pushed
  events (`[4-byte type] + XChaCha20(perConnectionToken, payload)`). Demonstrated by `updateDeviceName`
  emitting `DEVICE_NAME_UPDATED`. `setClip` added too.
- ⬜ Per-call "404 for all but /health when web disabled" gating; CORS dynamic private-origin policy

## Phase 3 — First domains: Files, Media, Device 🟡

- ✅ **Device**: `deviceInfo` (model/OS/ABIs/storage/memory) + `battery` (level/charging/temp/health)
  via `DeviceInfoProvider`, served over the encrypted API
- ✅ **Files (read)**: `mounts`, `files` (list), `fileInfo` via `FileService` — every path is
  **OS-canonicalized then sandbox-checked** (`PathSandbox`) before access
- ✅ **Files (write)**: `deleteFiles`/`createDir`/`renameFile`/`copyFile`/`moveFile`/`writeTextFile`
  operations (sandboxed, atomic text write)
- ✅ **`GET /fs`** byte-range streaming (PartialContent) with `dl=1` download disposition;
  **`GET /zip/dir`** + **`GET /zip/files`** (Semaphore(1), streamed); **`POST /upload`** (streamed to
  a temp sibling then atomically renamed) — all authorized by the rotating opaque **urlToken**
  (constant-time) and path-sandboxed
- ⬜ `/upload_chunk` + `mergeChunks`/`uploadedChunks` (resumable chunked upload); thumbnails,
  HEIF→PNG, 3gp→MP4, `content://`/`pkgicon://` sources
- ✅ **Media**: `images`/`videos`/`audios` (+ `imageCount`/`videoCount`/`audioCount`) and
  `mediaBuckets` via `MediaProvider` (MediaStore, index-guarded across API 28–36, Bundle pagination
  on API 30+); items expose `path` for streaming/thumbnails through `/fs`. Pagination clamped by the
  unit-tested `MediaQuery`.
- ⬜ Media playback control (`playAudio`, playlist ops), AI image search, trash/restore

## Phase 3 — First domains: Files, Media, Device ⬜

- ⬜ Files (GraphQL + `/fs`, `/upload`, `/upload_chunk`, `/zip`), sandboxed, byte-range, thumbnails
- ⬜ Media (images/videos/audios + counts/buckets, streaming)
- ⬜ Device (deviceInfo/battery/app + clipboard/relaunch/updateDeviceName)

## Phase 4 — Comms 🟡

- ✅ **Contacts**: `contacts` (paged, phones+emails batched to avoid N+1), `contactCount`,
  `contactSources`, `contactGroups`, `deleteContacts` via `ContactsProvider`. Phone numbers
  normalized by the unit-tested `PhoneNumbers`.
- ⬜ Contacts create/update; SMS/MMS (conversations, send, multi-SIM); Calls (log + control);
  Notifications (mirror + reply); Apps (list/(un)install)

## Phases 5–9 ⬜

4. Comms (cont.) — SMS/MMS, Calls (+control), Notifications (+reply), Apps
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
