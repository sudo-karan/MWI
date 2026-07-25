/*
 * MWI web console — minimal H.264 (Annex-B) helpers for screen mirroring.
 *
 * The phone broadcasts each encoded access unit as a SCREEN_MIRROR_VIDEO event whose payload is raw
 * Annex-B: NAL units separated by 00 00 01 / 00 00 00 01 start codes. MediaCodec emits the SPS/PPS
 * once (as a codec-config access unit) ahead of the first IDR. To decode via WebCodecs we need to:
 *   - know which NAL types a unit contains (SPS=7, PPS=8, IDR=5, non-IDR slice=1),
 *   - lift the SPS/PPS so they can be prepended to later IDRs (self-contained keyframes), and
 *   - derive the `avc1.PPCCLL` codec string from the SPS.
 *
 * These functions are pure (no DOM), so they are unit-tested in Node against a synthetic stream.
 * Works in the browser (window.MwiH264) and in Node (module.exports).
 */
(function (root) {
  'use strict';

  function hex2(b) { return (b & 0xff).toString(16).padStart(2, '0'); }

  /** Positions of every NAL start code: [{ sc, hdr }] where sc = start-code index, hdr = NAL header byte. */
  function startCodePositions(au) {
    const pos = [];
    const n = au.length;
    let i = 0;
    while (i + 2 < n) {
      if (au[i] === 0 && au[i + 1] === 0) {
        if (au[i + 2] === 1) { pos.push({ sc: i, hdr: i + 3 }); i += 3; continue; }
        if (i + 3 < n && au[i + 2] === 0 && au[i + 3] === 1) { pos.push({ sc: i, hdr: i + 4 }); i += 4; continue; }
      }
      i++;
    }
    return pos;
  }

  /** The set of NAL types (header & 0x1f) present in the access unit. */
  function nalTypeSet(au) {
    const set = new Set();
    startCodePositions(au).forEach((p) => { if (p.hdr < au.length) set.add(au[p.hdr] & 0x1f); });
    return set;
  }

  /** Concatenated SPS(7)+PPS(8) NAL units (each including its start code), or null if none present. */
  function paramSets(au) {
    const pos = startCodePositions(au);
    const n = au.length;
    const parts = [];
    let total = 0;
    for (let k = 0; k < pos.length; k++) {
      const type = au[pos[k].hdr] & 0x1f;
      if (type === 7 || type === 8) {
        const end = k + 1 < pos.length ? pos[k + 1].sc : n;
        const slice = au.subarray(pos[k].sc, end);
        parts.push(slice); total += slice.length;
      }
    }
    if (!parts.length) return null;
    const out = new Uint8Array(total);
    let off = 0;
    parts.forEach((p) => { out.set(p, off); off += p.length; });
    return out;
  }

  /** `avc1.PPCCLL` from the SPS (profile_idc, constraint flags, level_idc), or null if no SPS. */
  function codecString(au) {
    const pos = startCodePositions(au);
    for (let k = 0; k < pos.length; k++) {
      if ((au[pos[k].hdr] & 0x1f) === 7 && pos[k].hdr + 3 < au.length) {
        const h = pos[k].hdr; // h = NAL header (0x67); h+1..h+3 = profile, constraints, level
        return 'avc1.' + hex2(au[h + 1]) + hex2(au[h + 2]) + hex2(au[h + 3]);
      }
    }
    return null;
  }

  function concat(a, b) {
    const out = new Uint8Array(a.length + b.length);
    out.set(a, 0); out.set(b, a.length);
    return out;
  }

  const api = { startCodePositions, nalTypeSet, paramSets, codecString, concat };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  root.MwiH264 = api;
})(typeof window !== 'undefined' ? window : globalThis);
