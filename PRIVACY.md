# MWI Privacy Statement

MWI is designed so that **your personal data never leaves your network**. This document states
exactly what the app does and does not do. It is meant to be verifiable against the source.

## What MWI collects

**Nothing.** MWI has no analytics, no telemetry, no crash-reporting SDK, no advertising, and no
cloud backend. There is no account system and no user identifier. The project ships with **no**
Firebase, Google Analytics, Sentry, or `google-services.json`.

## Where your data lives

All of your data — files, media, contacts, messages, call logs, notes, feeds, chat history — stays
**on the device**, in app-private storage and the on-device database. It is exposed only to:

1. **Password-authenticated browsers on your LAN.** Access to the web console requires a
   machine-generated password and (by default) an on-device approval for each new session. Every
   request/response between the browser and the phone is encrypted end-to-end with
   XChaCha20-Poly1305 *on top of* TLS, and is replay-protected.
2. **Explicit peer-to-peer connections you initiate.** P2P chat / file sharing is end-to-end
   encrypted and only to devices you have paired.

`android:allowBackup="false"` is set so device/cloud backups never copy MWI's data off the phone.
Logs may contain decrypted request contents for debugging, which is exactly why they are kept
app-private and excluded from backup.

## The only network traffic MWI ever originates

MWI does not "phone home". The **only** optional outbound connections, each triggered by you, are:

| Outbound call | When | What is sent |
|---|---|---|
| GitHub update check | If you check for updates | A version query. No personal data. |
| Model download (HuggingFace) | Only if you enable AI photo search (github/google flavors) | Downloads a model. **Your photos are never uploaded.** |
| RSS feeds you add | When feeds refresh | A request to the feed URLs you chose. |
| Remote images in feeds/markdown | When you view content that embeds them | A request to those image URLs. |
| DLNA cast | When you cast to a renderer | Media streamed to a device on your LAN. |

There is no other outbound traffic. Photos used for on-device AI image search are indexed
**locally**; embeddings live in the on-device database and are never transmitted.

## Permissions

MWI requests broad device permissions (storage, contacts, SMS, call log, telephony, notifications,
etc.) because its purpose is to let *you* manage *your own* device from your own browser. Each
permission maps to a feature you choose to use; none is used to collect or transmit data to us or
any third party. The accessibility and notification-listener bindings are user-granted and used only
to power remote control and notification mirroring on your own LAN.

## Verifying these claims

* Search the source for network clients — the only ones are the update check, the model download,
  the RSS/feed fetchers, and DLNA/LAN discovery.
* There is no third-party analytics dependency in `gradle/libs.versions.toml`.
* The web server binds to the LAN and refuses unauthenticated requests (see
  [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md), "Security model").

If you find anything that contradicts this statement, please open an issue — it's a bug.
