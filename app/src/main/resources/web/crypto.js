/*
 * MWI web-console crypto — XChaCha20-Poly1305 + helpers.
 *
 * Must be byte-compatible with the server's Google Tink implementation:
 *   ciphertext layout = nonce(24) || ChaCha20 ciphertext || Poly1305 tag(16)
 *   AEAD = XChaCha20-Poly1305 (RFC 8439 construction with an HChaCha20 subkey).
 *
 * SHA-256/512 use WebCrypto (available on https origins). Randomness uses the CSPRNG.
 * A self-test (validated against the draft-irtf-cfrg-xchacha test vector) runs via selfTest().
 *
 * Works in the browser (window.MwiCrypto) and in Node (module.exports) for offline testing.
 */
(function (root) {
  'use strict';

  // ---------------------------------------------------------------- hex / random

  function toHex(bytes) {
    let s = '';
    for (let i = 0; i < bytes.length; i++) s += bytes[i].toString(16).padStart(2, '0');
    return s;
  }
  function fromHex(hex) {
    const out = new Uint8Array(hex.length / 2);
    for (let i = 0; i < out.length; i++) out[i] = parseInt(hex.substr(i * 2, 2), 16);
    return out;
  }
  function randomBytes(n) {
    const b = new Uint8Array(n);
    (root.crypto || root.msCrypto).getRandomValues(b);
    return b;
  }
  function utf8(s) { return new TextEncoder().encode(s); }
  function fromUtf8(b) { return new TextDecoder().decode(b); }

  // -------------------------------------------------------------------- ChaCha20

  function rotl(x, n) { return ((x << n) | (x >>> (32 - n))) >>> 0; }

  function quarter(s, a, b, c, d) {
    s[a] = (s[a] + s[b]) >>> 0; s[d] = rotl(s[d] ^ s[a], 16);
    s[c] = (s[c] + s[d]) >>> 0; s[b] = rotl(s[b] ^ s[c], 12);
    s[a] = (s[a] + s[b]) >>> 0; s[d] = rotl(s[d] ^ s[a], 8);
    s[c] = (s[c] + s[d]) >>> 0; s[b] = rotl(s[b] ^ s[c], 7);
  }

  const CONST = [0x61707865, 0x3320646e, 0x79622d32, 0x6b206574];

  function u32le(bytes, off) {
    return (bytes[off] | (bytes[off + 1] << 8) | (bytes[off + 2] << 16) | (bytes[off + 3] << 24)) >>> 0;
  }

  // Core: 20 rounds over a 16-word state; returns the working state (no feed-forward for HChaCha).
  function rounds(state) {
    const s = state.slice(0);
    for (let i = 0; i < 10; i++) {
      quarter(s, 0, 4, 8, 12); quarter(s, 1, 5, 9, 13);
      quarter(s, 2, 6, 10, 14); quarter(s, 3, 7, 11, 15);
      quarter(s, 0, 5, 10, 15); quarter(s, 1, 6, 11, 12);
      quarter(s, 2, 7, 8, 13); quarter(s, 3, 4, 9, 14);
    }
    return s;
  }

  function chachaBlock(key, counter, nonce12) {
    const state = new Uint32Array(16);
    state[0] = CONST[0]; state[1] = CONST[1]; state[2] = CONST[2]; state[3] = CONST[3];
    for (let i = 0; i < 8; i++) state[4 + i] = u32le(key, i * 4);
    state[12] = counter >>> 0;
    state[13] = u32le(nonce12, 0); state[14] = u32le(nonce12, 4); state[15] = u32le(nonce12, 8);
    const s = rounds(state);
    const out = new Uint8Array(64);
    for (let i = 0; i < 16; i++) {
      const v = (s[i] + state[i]) >>> 0;
      out[i * 4] = v & 0xff; out[i * 4 + 1] = (v >>> 8) & 0xff;
      out[i * 4 + 2] = (v >>> 16) & 0xff; out[i * 4 + 3] = (v >>> 24) & 0xff;
    }
    return out;
  }

  function chacha20(key, counter, nonce12, data) {
    const out = new Uint8Array(data.length);
    for (let i = 0; i < data.length; i += 64) {
      const block = chachaBlock(key, counter + (i / 64), nonce12);
      const n = Math.min(64, data.length - i);
      for (let j = 0; j < n; j++) out[i + j] = data[i + j] ^ block[j];
    }
    return out;
  }

  function hchacha20(key, nonce16) {
    const state = new Uint32Array(16);
    state[0] = CONST[0]; state[1] = CONST[1]; state[2] = CONST[2]; state[3] = CONST[3];
    for (let i = 0; i < 8; i++) state[4 + i] = u32le(key, i * 4);
    for (let i = 0; i < 4; i++) state[12 + i] = u32le(nonce16, i * 4);
    const s = rounds(state);
    const out = new Uint8Array(32);
    const words = [s[0], s[1], s[2], s[3], s[12], s[13], s[14], s[15]];
    for (let i = 0; i < 8; i++) {
      out[i * 4] = words[i] & 0xff; out[i * 4 + 1] = (words[i] >>> 8) & 0xff;
      out[i * 4 + 2] = (words[i] >>> 16) & 0xff; out[i * 4 + 3] = (words[i] >>> 24) & 0xff;
    }
    return out;
  }

  // -------------------------------------------------------------------- Poly1305

  function leToBig(bytes) {
    let n = 0n;
    for (let i = bytes.length - 1; i >= 0; i--) n = (n << 8n) | BigInt(bytes[i]);
    return n;
  }
  function bigToLe16(n) {
    const out = new Uint8Array(16);
    for (let i = 0; i < 16; i++) { out[i] = Number(n & 0xffn); n >>= 8n; }
    return out;
  }

  function poly1305(macKey, msg) {
    const P = (1n << 130n) - 5n;
    const r = leToBig(macKey.slice(0, 16)) & 0x0ffffffc0ffffffc0ffffffc0fffffffn;
    const s = leToBig(macKey.slice(16, 32));
    let acc = 0n;
    for (let i = 0; i < msg.length; i += 16) {
      const block = msg.slice(i, i + 16);
      const n = leToBig(block) + (1n << BigInt(block.length * 8));
      acc = ((acc + n) * r) % P;
    }
    acc = (acc + s) & ((1n << 128n) - 1n);
    return bigToLe16(acc);
  }

  // --------------------------------------------------------- XChaCha20-Poly1305 AEAD

  function pad16(len) { return (16 - (len % 16)) % 16; }
  function le64(n) {
    const out = new Uint8Array(8);
    let v = BigInt(n);
    for (let i = 0; i < 8; i++) { out[i] = Number(v & 0xffn); v >>= 8n; }
    return out;
  }

  function concat(arrays) {
    let total = 0; arrays.forEach((a) => (total += a.length));
    const out = new Uint8Array(total);
    let off = 0;
    arrays.forEach((a) => { out.set(a, off); off += a.length; });
    return out;
  }

  function macData(aad, ciphertext) {
    return concat([
      aad, new Uint8Array(pad16(aad.length)),
      ciphertext, new Uint8Array(pad16(ciphertext.length)),
      le64(aad.length), le64(ciphertext.length),
    ]);
  }

  // Core AEAD over a fixed 24-byte nonce.
  function sealWithNonce(key, nonce24, plaintext, aad) {
    aad = aad || new Uint8Array(0);
    const subkey = hchacha20(key, nonce24.slice(0, 16));
    const nonce12 = new Uint8Array(12);
    nonce12.set(nonce24.slice(16, 24), 4); // 4 zero bytes || last 8 nonce bytes
    const otk = chachaBlock(subkey, 0, nonce12).slice(0, 32);
    const ciphertext = chacha20(subkey, 1, nonce12, plaintext);
    const tag = poly1305(otk, macData(aad, ciphertext));
    return concat([nonce24, ciphertext, tag]);
  }

  function open(key, sealed, aad) {
    aad = aad || new Uint8Array(0);
    if (sealed.length < 24 + 16) return null;
    const nonce24 = sealed.slice(0, 24);
    const ciphertext = sealed.slice(24, sealed.length - 16);
    const tag = sealed.slice(sealed.length - 16);
    const subkey = hchacha20(key, nonce24.slice(0, 16));
    const nonce12 = new Uint8Array(12);
    nonce12.set(nonce24.slice(16, 24), 4);
    const otk = chachaBlock(subkey, 0, nonce12).slice(0, 32);
    const expected = poly1305(otk, macData(aad, ciphertext));
    let diff = 0;
    for (let i = 0; i < 16; i++) diff |= expected[i] ^ tag[i];
    if (diff !== 0) return null;
    return chacha20(subkey, 1, nonce12, ciphertext);
  }

  // Public API: encrypt prepends a fresh random 24-byte nonce (matches Tink).
  function encrypt(key, plaintext, aad) {
    return sealWithNonce(key, randomBytes(24), plaintext, aad);
  }
  function decrypt(key, sealed, aad) { return open(key, sealed, aad); }

  // ---------------------------------------------------------------------- hashing

  async function sha(algo, bytes) {
    const buf = await (root.crypto || root.msCrypto).subtle.digest(algo, bytes);
    return new Uint8Array(buf);
  }
  async function sha256(bytes) { return sha('SHA-256', bytes); }
  async function sha512(bytes) { return sha('SHA-512', bytes); }

  /** WS handshake key: first 32 bytes of SHA-512(password). */
  async function handshakeToken(password) {
    return (await sha512(utf8(password))).slice(0, 32);
  }

  // ----------------------------------------------------------------- self-test

  // draft-irtf-cfrg-xchacha-03 §A.3 known-answer test.
  function selfTest() {
    const key = fromHex('808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f');
    const nonce = fromHex('404142434445464748494a4b4c4d4e4f5051525354555657');
    const aad = fromHex('50515253c0c1c2c3c4c5c6c7');
    const plaintext = utf8(
      "Ladies and Gentlemen of the class of '99: If I could offer you only one tip for the future, sunscreen would be it.");
    const sealed = sealWithNonce(key, nonce, plaintext, aad);
    // Strip the 24-byte nonce prefix to compare with the vector's ciphertext||tag.
    const body = toHex(sealed.slice(24));
    const expected =
      'bd6d179d3e83d43b9576579493c0e939572a1700252bfaccbed2902c21396cbb' +
      '731c7f1b0b4aa6440bf3a82f4eda7e39ae64c6708c54c216cb96b72e1213b452' +
      '2f8c9ba40db5d945b11b69b982c1bb9e3f3fac2bc369488f76b2383565d3fff9' +
      '21f9664c97637da9768812f615c68b13b52e' +
      'c0875924c1c7987947deafd8780acf49';
    const ok = body === expected;
    // Round-trip check too.
    const back = open(key, sealed, aad);
    const rt = back && toHex(back) === toHex(plaintext);
    return ok && !!rt;
  }

  const api = {
    toHex, fromHex, randomBytes, utf8, fromUtf8,
    encrypt, decrypt, sealWithNonce, open,
    sha256, sha512, handshakeToken, selfTest,
    _internal: { chacha20, hchacha20, poly1305, chachaBlock },
  };

  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  root.MwiCrypto = api;
})(typeof window !== 'undefined' ? window : globalThis);
