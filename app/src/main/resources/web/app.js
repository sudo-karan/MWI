/*
 * MWI web console — UI. A tiny hash-free SPA on top of MwiApi (the encrypted client).
 *
 * Flow: password login over the WS handshake (with on-device 2FA support) -> session token ->
 * a sidebar dashboard whose sections call encrypted operations (deviceInfo/battery, files, notes).
 * The session token + urlToken are kept in sessionStorage keyed to the tab's stable clientId, so a
 * reload resumes without re-authenticating.
 *
 * Every operation goes through api() which unwraps the server's { data, error } envelope; nothing
 * here ever sees plaintext leave the phone unencrypted — MwiApi seals every byte with XChaCha20.
 */
(function () {
  'use strict';
  const Api = window.MwiApi;
  const $ = (sel, root) => (root || document).querySelector(sel);
  const el = (tag, attrs, children) => {
    const n = document.createElement(tag);
    if (attrs) for (const k in attrs) {
      if (k === 'class') n.className = attrs[k];
      else if (k === 'html') n.innerHTML = attrs[k];
      else if (k === 'text') n.textContent = attrs[k];
      else if (k.startsWith('on') && typeof attrs[k] === 'function') n.addEventListener(k.slice(2), attrs[k]);
      else if (attrs[k] != null) n.setAttribute(k, attrs[k]);
    }
    (children || []).forEach((c) => n.appendChild(typeof c === 'string' ? document.createTextNode(c) : c));
    return n;
  };

  const store = {
    get token() { try { return sessionStorage.getItem('mwi.token'); } catch (e) { return null; } },
    set token(v) { try { v ? sessionStorage.setItem('mwi.token', v) : sessionStorage.removeItem('mwi.token'); } catch (e) {} },
    get urlToken() { try { return sessionStorage.getItem('mwi.urlToken') || ''; } catch (e) { return ''; } },
    set urlToken(v) { try { sessionStorage.setItem('mwi.urlToken', v || ''); } catch (e) {} },
  };

  const state = { token: null, urlToken: '', view: 'device' };

  /** Invoke an operation, unwrapping { data, error }. Throws on error / lost session. */
  async function api(operation, variables) {
    const res = await Api.call(state.token, operation, variables);
    if (res.error) throw new Error(res.error);
    return res.data;
  }

  // ---------------------------------------------------------------- formatting helpers

  function fmtBytes(n) {
    n = Number(n) || 0;
    if (n < 1024) return n + ' B';
    const u = ['KB', 'MB', 'GB', 'TB'];
    let i = -1;
    do { n /= 1024; i++; } while (n >= 1024 && i < u.length - 1);
    return n.toFixed(n < 10 ? 1 : 0) + ' ' + u[i];
  }
  function fmtDate(ms) {
    if (!ms) return '';
    try { return new Date(Number(ms)).toLocaleString(); } catch (e) { return ''; }
  }
  function fileIcon(f) { return f.isDir ? '📁' : '📄'; }

  // ---------------------------------------------------------------- login view

  function renderLogin(prefillErr) {
    const status = el('div', { class: 'status-line' + (prefillErr ? ' err' : '') , text: prefillErr || '' });
    const pw = el('input', { class: 'input', type: 'password', placeholder: 'Console password', autocomplete: 'current-password' });
    const btn = el('button', { class: 'btn primary', text: 'Unlock', style: 'width:100%' });

    async function submit() {
      const password = pw.value;
      if (!password) { status.className = 'status-line err'; status.textContent = 'Enter the password shown in the app.'; return; }
      btn.disabled = true; pw.disabled = true;
      status.className = 'status-line pending';
      status.textContent = 'Connecting…';
      try {
        const res = await Api.login(password, function onPending() {
          status.className = 'status-line pending';
          status.innerHTML = '<span class="spinner"></span>Waiting for approval on the phone…';
        });
        state.token = res.token; store.token = res.token;
        await afterLogin();
      } catch (e) {
        btn.disabled = false; pw.disabled = false;
        status.className = 'status-line err';
        status.textContent = loginError(e && e.message);
        pw.focus();
      }
    }
    btn.addEventListener('click', submit);
    pw.addEventListener('keydown', (e) => { if (e.key === 'Enter') submit(); });

    const card = el('div', { class: 'login-card' }, [
      el('div', { class: 'brand' }, [
        el('div', { class: 'logo', text: 'MWI' }),
        el('div', {}, [ el('h1', { text: 'Web Console' }), el('p', { text: 'Local, encrypted device access' }) ]),
      ]),
      el('div', { class: 'field' }, [ el('label', { text: 'Password' }), pw ]),
      btn,
      status,
      el('p', { class: 'hint', style: 'margin-top:18px', text:
        'Open the MWI app on your phone to see the password. Every byte between this browser and the phone is end-to-end encrypted (XChaCha20-Poly1305) over TLS.' }),
    ]);
    mount(el('div', { class: 'login-wrap' }, [card]));
    setTimeout(() => pw.focus(), 30);
  }

  function loginError(msg) {
    switch (msg) {
      case 'DENIED': return 'Wrong password.';
      case 'REJECTED': return 'Approval was declined on the phone.';
      case 'connection error': return 'Could not reach the server.';
      case 'handshake decrypt failed': return 'Wrong password.';
      default: return msg ? ('Login failed: ' + msg) : 'Login failed.';
    }
  }

  async function afterLogin() {
    // Fetch the file-URL token once so Files/Media can build /fs stream+download links. It is
    // regenerated on each server start, so we always refresh it (a resumed session may be stale).
    try { state.urlToken = String(await api('urlToken')); store.urlToken = state.urlToken; }
    catch (e) { state.urlToken = store.urlToken; }
    renderShell();
    startEvents();
  }

  // ---------------------------------------------------------------- app shell

  const NAV = [
    { id: 'device', label: 'Device', ico: '📱' },
    { id: 'files', label: 'Files', ico: '🗂️' },
    { id: 'media', label: 'Media', ico: '🖼️' },
    { id: 'contacts', label: 'Contacts', ico: '👤' },
    { id: 'messages', label: 'Messages', ico: '💬' },
    { id: 'calls', label: 'Calls', ico: '📞' },
    { id: 'notes', label: 'Notes', ico: '📝' },
    { id: 'apps', label: 'Apps', ico: '📦' },
  ];

  function renderShell() {
    const contentEl = el('div', { class: 'content', id: 'content' });
    const sideEl = el('nav', { class: 'sidebar' }, NAV.map((n) =>
      el('button', {
        class: 'nav-item' + (n.id === state.view ? ' active' : ''),
        'data-id': n.id,
        onclick: () => { state.view = n.id; resetSubnav(); refreshNav(); renderView(contentEl); },
      }, [ el('span', { class: 'ico', text: n.ico }), el('span', { class: 'label', text: n.label }) ])));

    const shell = el('div', { class: 'shell' }, [
      el('header', { class: 'topbar' }, [
        el('div', { class: 'logo', text: 'MWI' }),
        el('div', { class: 'title', text: 'Web Console' }),
        el('div', { class: 'lock' }, [ el('span', { class: 'dot' }), el('span', { text: 'Encrypted' }) ]),
        el('button', { class: 'btn small', text: 'Lock', style: 'margin-left:14px', onclick: logout }),
      ]),
      sideEl,
      contentEl,
    ]);
    mount(shell);
    renderView(contentEl);

    function refreshNav() {
      sideEl.querySelectorAll('.nav-item').forEach((b) =>
        b.classList.toggle('active', b.getAttribute('data-id') === state.view));
    }
  }

  // Reset per-section drill-down state so a sidebar click opens the section at its top level.
  function resetSubnav() { files.stack = []; messages.thread = null; }

  function logout() {
    stopEvents();
    state.token = null; store.token = null;
    renderLogin();
  }

  function pageHead(title, actions) {
    return el('div', { class: 'page-head' }, [
      el('h2', { text: title }),
      el('div', { class: 'actions' }, actions || []),
    ]);
  }

  function errBox(e) {
    const msg = (e && e.message) || String(e);
    if (msg === 'unauthorized') { logout(); return el('div'); }
    const friendly = msg === 'internal_error'
      ? 'The phone could not complete this request — the matching permission may not be granted in the app.'
      : msg;
    return el('div', { class: 'err-box', text: friendly });
  }

  function loading() { return el('div', { class: 'empty', html: '<span class="spinner"></span> Loading…' }); }

  async function renderView(host) {
    host.innerHTML = '';
    host.appendChild(loading());
    try {
      let node;
      if (state.view === 'device') node = await viewDevice();
      else if (state.view === 'files') node = await viewFiles();
      else if (state.view === 'media') node = await viewMedia();
      else if (state.view === 'contacts') node = await viewContacts();
      else if (state.view === 'messages') node = await viewMessages();
      else if (state.view === 'calls') node = await viewCalls();
      else if (state.view === 'notes') node = await viewNotes();
      else if (state.view === 'apps') node = await viewApps();
      else node = el('div', { class: 'empty', text: 'Nothing here.' });
      host.innerHTML = '';
      host.appendChild(node);
    } catch (e) {
      host.innerHTML = '';
      host.appendChild(pageHead(titleFor(state.view)));
      host.appendChild(errBox(e));
    }
  }
  function titleFor(id) { const n = NAV.find((x) => x.id === id); return n ? n.label : ''; }

  // ---------------------------------------------------------------- Device view

  async function viewDevice() {
    const [info, batt] = await Promise.all([api('deviceInfo'), api('battery').catch(() => null)]);
    const wrap = el('div', {}, [pageHead('Device')]);
    const grid = el('div', { class: 'grid' });

    const usedStore = Number(info.totalStorage) - Number(info.availableStorage);
    const usedMem = Number(info.totalMemory) - Number(info.availableMemory);
    grid.appendChild(card('Identity', [
      kv('Name', info.deviceName),
      kv('Model', info.model),
      kv('Manufacturer', info.manufacturer),
      kv('Android', info.osVersion + ' (SDK ' + info.sdkInt + ')'),
      kv('ABIs', (info.abis || []).join(', ')),
    ]));
    grid.appendChild(meterCard('Storage', usedStore, Number(info.totalStorage)));
    grid.appendChild(meterCard('Memory', usedMem, Number(info.totalMemory)));
    if (batt) {
      grid.appendChild(card('Battery', [
        kv('Level', batt.level + '%'),
        kv('State', batt.charging ? 'Charging' : 'Discharging'),
        kv('Temperature', (batt.temperatureC != null ? batt.temperatureC.toFixed(1) : '?') + ' °C'),
        kv('Health', batt.health || '—'),
        kv('Technology', batt.technology || '—'),
      ]));
    }
    wrap.appendChild(grid);
    return wrap;
  }

  function card(title, rows) { return el('div', { class: 'card' }, [el('h3', { text: title })].concat(rows)); }
  function kv(k, v) {
    return el('div', { class: 'kv' }, [ el('span', { class: 'k', text: k }), el('span', { class: 'v', text: v == null ? '—' : String(v) }) ]);
  }
  function meterCard(title, used, total) {
    const pct = total > 0 ? Math.min(100, Math.round((used / total) * 100)) : 0;
    return el('div', { class: 'card' }, [
      el('h3', { text: title }),
      kv('Used', fmtBytes(used) + ' / ' + fmtBytes(total)),
      kv('Free', fmtBytes(total - used)),
      el('div', { class: 'meter' }, [ el('span', { style: 'width:' + pct + '%' }) ]),
    ]);
  }

  // ---------------------------------------------------------------- Files view

  const files = { stack: [] }; // breadcrumb stack of { name, path }

  async function viewFiles() {
    const wrap = el('div', {});
    if (files.stack.length === 0) {
      wrap.appendChild(pageHead('Files'));
      const mounts = await api('mounts');
      if (!mounts || !mounts.length) { wrap.appendChild(el('div', { class: 'empty', text: 'No storage volumes reported.' })); return wrap; }
      const list = el('div', { class: 'list' }, mounts.map((m) =>
        el('div', {
          class: 'row clickable',
          onclick: () => openDir(m.name, m.path),
        }, [
          el('div', { class: 'ico', text: m.removable ? '💾' : '📀' }),
          el('div', { class: 'main' }, [
            el('div', { class: 'name', text: m.name }),
            el('div', { class: 'sub', text: fmtBytes(Number(m.totalBytes) - Number(m.availableBytes)) + ' used of ' + fmtBytes(m.totalBytes) }),
          ]),
          el('div', { class: 'trail', text: '›' }),
        ])));
      wrap.appendChild(list);
      return wrap;
    }

    const cur = files.stack[files.stack.length - 1];
    wrap.appendChild(pageHead('Files'));
    wrap.appendChild(renderCrumbs());
    let entries;
    try { entries = await api('files', { path: cur.path }); }
    catch (e) { wrap.appendChild(errBox(e)); return wrap; }
    entries = (entries || []).slice().sort((a, b) => (b.isDir - a.isDir) || a.name.localeCompare(b.name));
    if (!entries.length) { wrap.appendChild(el('div', { class: 'empty', text: 'Empty folder.' })); return wrap; }

    const list = el('div', { class: 'list' }, entries.map((f) => {
      const trail = f.isDir ? (f.childCount ? f.childCount + ' items' : '') : fmtBytes(f.size);
      const row = el('div', { class: 'row' + (f.isDir ? ' clickable' : '') }, [
        el('div', { class: 'ico', text: fileIcon(f) }),
        el('div', { class: 'main' }, [
          el('div', { class: 'name', text: f.name }),
          el('div', { class: 'sub', text: fmtDate(f.updatedAt) }),
        ]),
        el('div', { class: 'trail', text: trail }),
      ]);
      if (f.isDir) row.addEventListener('click', () => openDir(f.name, f.path));
      else row.appendChild(fileActions(f));
      return row;
    }));
    wrap.appendChild(list);
    return wrap;
  }

  function fileActions(f) {
    const box = el('div', { style: 'display:flex;gap:6px;margin-left:8px' });
    if (state.urlToken) {
      box.appendChild(el('a', { class: 'btn small', text: 'Open', href: Api.fsUrl(state.urlToken, f.path, false), target: '_blank', rel: 'noopener' }));
      box.appendChild(el('a', { class: 'btn small', text: 'Download', href: Api.fsUrl(state.urlToken, f.path, true) }));
    }
    return box;
  }

  function renderCrumbs() {
    const c = el('div', { class: 'crumbs' });
    c.appendChild(el('button', { text: '⌂ Volumes', onclick: () => { files.stack = []; renderView($('#content')); } }));
    files.stack.forEach((s, i) => {
      c.appendChild(el('span', { class: 'sep', text: '/' }));
      c.appendChild(el('button', { text: s.name, onclick: () => { files.stack = files.stack.slice(0, i + 1); renderView($('#content')); } }));
    });
    return c;
  }

  function openDir(name, path) { files.stack.push({ name: name, path: path }); renderView($('#content')); }

  // ---------------------------------------------------------------- Notes view

  async function viewNotes() {
    const wrap = el('div', {});
    const notes = await api('notes', { limit: 200, offset: 0 });
    const editor = noteEditor(() => renderView($('#content')));
    wrap.appendChild(pageHead('Notes', [
      el('button', { class: 'btn small', text: editor.open ? 'Close' : '+ New note', onclick: () => { editor.toggle(); } }),
    ]));
    wrap.appendChild(editor.node);
    if (!notes || !notes.length) { wrap.appendChild(el('div', { class: 'empty', text: 'No notes yet.' })); return wrap; }
    const list = el('div', { class: 'list' }, notes.map((n) => {
      const title = (n.title && n.title.trim()) || firstLine(n.content) || '(untitled)';
      return el('div', { class: 'row' }, [
        el('div', { class: 'ico', text: '📝' }),
        el('div', { class: 'main' }, [
          el('div', { class: 'name', text: title }),
          el('div', { class: 'sub', text: 'Updated ' + fmtDate(n.updatedAt) }),
        ]),
        el('button', {
          class: 'btn small danger', text: 'Delete',
          onclick: async (ev) => {
            ev.stopPropagation();
            try { await api('deleteNote', { id: n.id }); renderView($('#content')); }
            catch (e) { alert('Delete failed: ' + e.message); }
          },
        }),
      ]);
    }));
    wrap.appendChild(list);
    return wrap;
  }

  function noteEditor(onSaved) {
    const title = el('input', { class: 'input', placeholder: 'Title (optional)' });
    const body = el('textarea', { class: 'input', placeholder: 'Write a note…' });
    const save = el('button', { class: 'btn primary', text: 'Save note' });
    const node = el('div', { class: 'note-editor', hidden: 'hidden' }, [title, body, el('div', {}, [save])]);
    let open = false;
    save.addEventListener('click', async () => {
      if (!title.value && !body.value) return;
      save.disabled = true;
      try { await api('createNote', { title: title.value, content: body.value }); title.value = ''; body.value = ''; onSaved(); }
      catch (e) { save.disabled = false; alert('Save failed: ' + e.message); }
    });
    return {
      node,
      get open() { return open; },
      toggle() { open = !open; node.hidden = !open; if (open) title.focus(); },
    };
  }
  function firstLine(s) { return s ? String(s).split('\n')[0].slice(0, 60) : ''; }

  // ---------------------------------------------------------------- Apps view

  async function viewApps() {
    const wrap = el('div', {});
    const [count, pkgs] = await Promise.all([
      api('packageCount').catch(() => null),
      api('packages', { limit: 300, offset: 0 }),
    ]);
    wrap.appendChild(pageHead('Apps', count != null ? [el('span', { class: 'hint', text: count + ' installed' })] : []));
    if (!pkgs || !pkgs.length) { wrap.appendChild(el('div', { class: 'empty', text: 'No apps reported.' })); return wrap; }
    const list = el('div', { class: 'list' }, pkgs.map((p) =>
      el('div', { class: 'row' }, [
        el('div', { class: 'ico', text: '📦' }),
        el('div', { class: 'main' }, [
          el('div', { class: 'name', text: p.name || p.label || p.packageName }),
          el('div', { class: 'sub', text: p.packageName + (p.versionName ? ' · v' + p.versionName : '') }),
        ]),
        el('div', { class: 'trail', text: p.size ? fmtBytes(p.size) : '' }),
      ])));
    wrap.appendChild(list);
    return wrap;
  }

  // ---------------------------------------------------------------- Media view

  const media = { tab: 'IMAGE' };
  const MEDIA_TABS = [
    { key: 'IMAGE', label: 'Photos', op: 'images', count: 'imageCount' },
    { key: 'VIDEO', label: 'Videos', op: 'videos', count: 'videoCount' },
    { key: 'AUDIO', label: 'Audio', op: 'audios', count: 'audioCount' },
  ];

  async function viewMedia() {
    const wrap = el('div', {});
    const tabs = el('div', { class: 'actions' }, MEDIA_TABS.map((t) =>
      el('button', {
        class: 'btn small' + (t.key === media.tab ? ' primary' : ''),
        text: t.label,
        onclick: () => { media.tab = t.key; renderView($('#content')); },
      })));
    wrap.appendChild(el('div', { class: 'page-head' }, [el('h2', { text: 'Media' }), tabs]));

    if (!state.urlToken) { wrap.appendChild(el('div', { class: 'err-box', text: 'No file token available — reconnect to enable media streaming.' })); return wrap; }
    const spec = MEDIA_TABS.find((t) => t.key === media.tab);
    const items = await api(spec.op, { limit: 120, offset: 0 });
    if (!items || !items.length) { wrap.appendChild(el('div', { class: 'empty', text: 'Nothing in this library.' })); return wrap; }

    if (media.tab === 'AUDIO') { wrap.appendChild(audioList(items)); return wrap; }
    wrap.appendChild(mediaGrid(items, media.tab === 'VIDEO'));
    return wrap;
  }

  function mediaGrid(items, isVideo) {
    return el('div', { class: 'media-grid' }, items.map((m) => {
      const src = Api.fsUrl(state.urlToken, m.path, false);
      const tile = el('a', { class: 'tile', href: src, target: '_blank', rel: 'noopener', title: m.title });
      if (isVideo) {
        tile.classList.add('video');
        tile.appendChild(el('div', { class: 'play', text: '▶' }));
        tile.appendChild(el('div', { class: 'cap', text: (m.duration ? fmtDuration(m.duration) : '') }));
      } else {
        tile.appendChild(el('img', { loading: 'lazy', src: src, alt: m.title }));
      }
      return tile;
    }));
  }

  function audioList(items) {
    return el('div', { class: 'list' }, items.map((m) =>
      el('div', { class: 'row' }, [
        el('div', { class: 'ico', text: '🎵' }),
        el('div', { class: 'main' }, [
          el('div', { class: 'name', text: m.title }),
          el('div', { class: 'sub', text: fmtBytes(m.size) + (m.duration ? ' · ' + fmtDuration(m.duration) : '') }),
        ]),
        el('audio', { controls: 'controls', preload: 'none', src: Api.fsUrl(state.urlToken, m.path, false), style: 'max-width:230px;height:34px' }),
      ])));
  }

  function fmtDuration(ms) {
    const s = Math.round(Number(ms) / 1000);
    const m = Math.floor(s / 60), sec = s % 60;
    if (m >= 60) { const h = Math.floor(m / 60); return h + ':' + String(m % 60).padStart(2, '0') + ':' + String(sec).padStart(2, '0'); }
    return m + ':' + String(sec).padStart(2, '0');
  }

  // ---------------------------------------------------------------- Contacts view

  async function viewContacts() {
    const wrap = el('div', {});
    const [count, contacts] = await Promise.all([
      api('contactCount').catch(() => null),
      api('contacts', { limit: 500, offset: 0 }),
    ]);
    wrap.appendChild(pageHead('Contacts', count != null ? [el('span', { class: 'hint', text: count + ' contacts' })] : []));
    if (!contacts || !contacts.length) { wrap.appendChild(el('div', { class: 'empty', text: 'No contacts (or permission not granted in the app).' })); return wrap; }
    const list = el('div', { class: 'list' }, contacts.map((c) => {
      const phone = (c.phones && c.phones[0] && c.phones[0].value) || (c.emails && c.emails[0] && c.emails[0].value) || '';
      return el('div', { class: 'row' }, [
        el('div', { class: 'avatar', text: initials(c.displayName) }),
        el('div', { class: 'main' }, [
          el('div', { class: 'name', text: (c.starred ? '★ ' : '') + (c.displayName || '(no name)') }),
          el('div', { class: 'sub', text: phone }),
        ]),
        el('div', { class: 'trail', text: (c.phones && c.phones.length > 1) ? ('+' + (c.phones.length - 1) + ' more') : '' }),
      ]);
    }));
    wrap.appendChild(list);
    return wrap;
  }

  function initials(name) {
    if (!name) return '?';
    const parts = name.trim().split(/\s+/);
    return ((parts[0] ? parts[0][0] : '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
  }

  // ---------------------------------------------------------------- Messages (SMS) view

  const messages = { thread: null }; // { threadId, address }

  async function viewMessages() {
    const wrap = el('div', {});
    if (messages.thread) return viewSmsThread(wrap);
    const convos = await api('smsConversations', { limit: 200, offset: 0 });
    wrap.appendChild(pageHead('Messages'));
    if (!convos || !convos.length) { wrap.appendChild(el('div', { class: 'empty', text: 'No conversations (or SMS permission not granted).' })); return wrap; }
    const list = el('div', { class: 'list' }, convos.map((c) =>
      el('div', {
        class: 'row clickable',
        onclick: () => { messages.thread = { threadId: c.threadId, address: c.address }; renderView($('#content')); },
      }, [
        el('div', { class: 'avatar', text: initials(c.address) }),
        el('div', { class: 'main' }, [
          el('div', { class: 'name', text: c.address || '(unknown)' }),
          el('div', { class: 'sub', text: c.snippet || '' }),
        ]),
        el('div', { class: 'trail' }, [
          el('div', { text: shortDate(c.date) }),
          c.unreadCount ? el('div', { class: 'badge', text: String(c.unreadCount) }) : el('span'),
        ]),
      ])));
    wrap.appendChild(list);
    return wrap;
  }

  async function viewSmsThread(wrap) {
    const t = messages.thread;
    wrap.appendChild(el('div', { class: 'page-head' }, [
      el('button', { class: 'btn small', text: '‹ Back', onclick: () => { messages.thread = null; renderView($('#content')); } }),
      el('h2', { text: t.address || 'Conversation', style: 'font-size:1.1rem' }),
    ]));
    const msgs = await api('sms', { threadId: t.threadId, limit: 300, offset: 0 });
    if (!msgs || !msgs.length) { wrap.appendChild(el('div', { class: 'empty', text: 'No messages.' })); return wrap; }
    const ordered = msgs.slice().sort((a, b) => Number(a.date) - Number(b.date));
    const thread = el('div', { class: 'thread' }, ordered.map((m) => {
      const mine = m.type === 'sent' || m.type === 'outbox' || m.type === 'queued';
      return el('div', { class: 'bubble ' + (mine ? 'me' : 'them') }, [
        el('div', { class: 'txt', text: m.body || '' }),
        el('div', { class: 'stamp', text: fmtDate(m.date) }),
      ]);
    }));
    wrap.appendChild(thread);
    return wrap;
  }

  // ---------------------------------------------------------------- Calls view

  const CALL_ICONS = { incoming: '📥', outgoing: '📤', missed: '⚠️', rejected: '⛔', blocked: '🚫', voicemail: '📩' };

  async function viewCalls() {
    const wrap = el('div', {});
    const [count, calls] = await Promise.all([
      api('callCount').catch(() => null),
      api('calls', { limit: 300, offset: 0 }),
    ]);
    wrap.appendChild(pageHead('Calls', count != null ? [el('span', { class: 'hint', text: count + ' calls' })] : []));
    if (!calls || !calls.length) { wrap.appendChild(el('div', { class: 'empty', text: 'No call log (or permission not granted).' })); return wrap; }
    const list = el('div', { class: 'list' }, calls.map((c) =>
      el('div', { class: 'row' }, [
        el('div', { class: 'ico', text: CALL_ICONS[c.type] || '📞' }),
        el('div', { class: 'main' }, [
          el('div', { class: 'name', text: c.name || c.number || '(unknown)' }),
          el('div', { class: 'sub', text: (c.name ? c.number + ' · ' : '') + c.type + (c.duration ? ' · ' + fmtDuration(Number(c.duration) * 1000) : '') }),
        ]),
        el('div', { class: 'trail', text: shortDate(c.date) }),
      ])));
    wrap.appendChild(list);
    return wrap;
  }

  function shortDate(ms) {
    if (!ms) return '';
    try {
      const d = new Date(Number(ms)); const now = new Date();
      const sameDay = d.toDateString() === now.toDateString();
      return sameDay ? d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : d.toLocaleDateString();
    } catch (e) { return ''; }
  }

  // ---------------------------------------------------------------- live events

  let eventSocket = null;
  // Which views should refresh when a given event code arrives.
  const EVENT_VIEWS = {
    100: ['messages'], 101: ['messages'], 102: ['messages'], // MESSAGE_*
    1200: ['device'],                                        // DEVICE_NAME_UPDATED
    1500: ['calls'],                                         // CALL_STATE_CHANGED
  };
  function startEvents() {
    if (!state.token) return;
    try {
      eventSocket = Api.events(state.token, function (type) {
        const views = EVENT_VIEWS[type];
        if (views && views.indexOf(state.view) !== -1) {
          const host = $('#content');
          if (host) renderView(host);
        }
      });
    } catch (e) { /* events are best-effort; the app works without them */ }
  }
  function stopEvents() { try { if (eventSocket) eventSocket.close(); } catch (e) {} eventSocket = null; }

  // ---------------------------------------------------------------- bootstrap

  function mount(node) {
    const app = $('#app');
    app.innerHTML = '';
    app.appendChild(node);
  }

  async function boot() {
    const saved = store.token;
    if (saved) {
      state.token = saved;
      state.urlToken = store.urlToken;
      // Validate the resumed session with a cheap call; fall back to login on failure.
      try { await api('deviceInfo'); await afterLogin(); return; }
      catch (e) { state.token = null; store.token = null; }
    }
    renderLogin();
  }

  document.addEventListener('DOMContentLoaded', boot);
})();
