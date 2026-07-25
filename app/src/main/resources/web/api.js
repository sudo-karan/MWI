/*
 * MWI web-console API client. Implements the encrypted protocol the Android server speaks
 * (spec §5/§6), built on the verified MwiCrypto (XChaCha20-Poly1305):
 *
 *   - login(): WS handshake — send XChaCha20(SHA-512(pw)[:32], AuthRequest); on 2FA, wait for the
 *     on-device approval; receive the minted session token.
 *   - call(): POST /graphql with `c-id`; body = XChaCha20(sessionToken, "TS|NONCE|{op,vars}");
 *     response re-decrypted with the same token. Timestamps use the server-time offset so the
 *     ReplayGuard's ±30s window is respected regardless of client clock skew.
 *   - events(): register the WS event socket and decode [4-byte type] + XChaCha20(token, payload).
 */
(function (root) {
  'use strict';
  const Crypto = root.MwiCrypto;

  // Stable per-tab client id used across auth, register, and requests. Persisted in sessionStorage
  // so a page reload keeps the same id (and therefore the same server-side session), letting the UI
  // resume with a saved token instead of forcing a fresh login on every refresh.
  function loadClientId() {
    try {
      const store = root.sessionStorage;
      let v = store && store.getItem('mwi.cid');
      if (!v) { v = Crypto.toHex(Crypto.randomBytes(16)); if (store) store.setItem('mwi.cid', v); }
      return v;
    } catch (e) {
      return Crypto.toHex(Crypto.randomBytes(16));
    }
  }
  const clientId = loadClientId();

  // Server-time offset (server epoch ms minus local now), so replay timestamps line up.
  let serverOffset = typeof root.__SERVER_TIME__ === 'number' ? root.__SERVER_TIME__ - Date.now() : 0;
  function now() { return Date.now() + serverOffset; }

  function origin() { return { host: location.hostname, port: location.port || (location.protocol === 'https:' ? '443' : '80') }; }
  function wsBase() { return (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host; }

  const AuthStatus = { PENDING: 'PENDING', GRANTED: 'GRANTED', DENIED: 'DENIED', REJECTED: 'REJECTED' };

  /**
   * Log in with a password. Resolves { status, token, sessionId } — GRANTED carries the session
   * token. onPending() is called if the phone must approve (2FA).
   */
  function login(password, onPending) {
    return new Promise(function (resolve, reject) {
      Crypto.handshakeToken(password).then(function (token) {
        const ws = new WebSocket(wsBase() + '/?cid=' + encodeURIComponent(clientId) + '&auth=1');
        ws.binaryType = 'arraybuffer';
        let sawPending = false;

        ws.onopen = function () {
          const req = JSON.stringify({
            password: password,
            name: navigator.userAgent.slice(0, 80),
            osName: navigator.platform || '',
            browserName: (navigator.userAgentData && navigator.userAgentData.brands &&
              navigator.userAgentData.brands.map(function (b) { return b.brand; }).join(' ')) || 'Browser',
          });
          ws.send(Crypto.encrypt(token, Crypto.utf8(req)));
        };

        ws.onmessage = function (ev) {
          const plain = Crypto.decrypt(token, new Uint8Array(ev.data));
          if (!plain) { ws.close(); reject(new Error('handshake decrypt failed')); return; }
          const res = JSON.parse(Crypto.fromUtf8(plain));
          if (res.status === AuthStatus.PENDING) {
            sawPending = true;
            if (onPending) onPending();
            return; // wait for GRANTED/REJECTED
          }
          ws.close();
          if (res.status === AuthStatus.GRANTED) resolve(res);
          else reject(new Error(res.status || 'denied'));
        };

        ws.onerror = function () { reject(new Error('connection error')); };
        ws.onclose = function () { if (sawPending) { /* resolved/rejected already or user closed */ } };
      }).catch(reject);
    });
  }

  /** Invoke an API operation over the encrypted /graphql endpoint. Returns parsed { data, error }. */
  function call(sessionToken, operation, variables) {
    const key = Crypto.fromHex(sessionToken);
    const nonce = Crypto.toHex(Crypto.randomBytes(12));
    const envelope = now() + '|' + nonce + '|' + JSON.stringify({ operation: operation, variables: variables || null });
    const body = Crypto.encrypt(key, Crypto.utf8(envelope));
    return fetch('/graphql', {
      method: 'POST',
      headers: { 'c-id': clientId, 'Content-Type': 'application/octet-stream' },
      body: body,
    }).then(function (resp) {
      if (resp.status === 401) throw new Error('unauthorized');
      return resp.arrayBuffer();
    }).then(function (buf) {
      const plain = Crypto.decrypt(key, new Uint8Array(buf));
      if (!plain) throw new Error('response decrypt failed');
      return JSON.parse(Crypto.fromUtf8(plain));
    });
  }

  /** Build an authorized /fs URL (opaque urlToken) for streaming/downloads. */
  function fsUrl(urlToken, path, download) {
    const u = '/fs?path=' + encodeURIComponent(path) + '&token=' + encodeURIComponent(urlToken);
    return download ? u + '&dl=1' : u;
  }

  /** Open the registered event socket; onEvent(type, payloadBytes) fires per frame. */
  function events(sessionToken, onEvent) {
    const key = Crypto.fromHex(sessionToken);
    const ws = new WebSocket(wsBase() + '/?cid=' + encodeURIComponent(clientId));
    ws.binaryType = 'arraybuffer';
    ws.onopen = function () { ws.send(Crypto.encrypt(key, Crypto.utf8('register'))); };
    ws.onmessage = function (ev) {
      const frame = new Uint8Array(ev.data);
      if (frame.length < 4) return;
      const type = (frame[0] << 24) | (frame[1] << 16) | (frame[2] << 8) | frame[3];
      const plain = Crypto.decrypt(key, frame.slice(4));
      if (plain && onEvent) onEvent(type >>> 0, plain);
    };
    return ws;
  }

  root.MwiApi = { clientId: clientId, login: login, call: call, fsUrl: fsUrl, events: events, AuthStatus: AuthStatus, setServerTime: function (t) { serverOffset = t - Date.now(); } };
})(typeof window !== 'undefined' ? window : globalThis);
