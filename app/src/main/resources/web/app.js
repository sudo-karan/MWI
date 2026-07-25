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
    { id: 'mirror', label: 'Screen', ico: '🖥️' },
    { id: 'chat', label: 'Chat', ico: '🗨️' },
    { id: 'cast', label: 'Cast', ico: '📺' },
    { id: 'nearby', label: 'Nearby', ico: '📡' },
    { id: 'notifications', label: 'Alerts', ico: '📣' },
    { id: 'notes', label: 'Notes', ico: '📝' },
    { id: 'bookmarks', label: 'Bookmarks', ico: '🔖' },
    { id: 'feeds', label: 'Feeds', ico: '📰' },
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
        el('div', { class: 'lock' }, [ el('span', { class: 'dot', id: 'conn-dot' }), el('span', { id: 'conn-label', text: 'Encrypted' }) ]),
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
  function resetSubnav() { files.stack = []; messages.thread = null; chat.channel = null; rss.feed = null; }

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

  // A view may register a teardown (e.g. Screen Mirror closes its decoder + event subscription).
  let viewCleanup = null;
  function runViewCleanup() { if (viewCleanup) { try { viewCleanup(); } catch (e) {} viewCleanup = null; } }

  async function renderView(host) {
    runViewCleanup();
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
      else if (state.view === 'mirror') node = await viewMirror();
      else if (state.view === 'chat') node = await viewChat();
      else if (state.view === 'cast') node = await viewCast();
      else if (state.view === 'nearby') node = await viewNearby();
      else if (state.view === 'notifications') node = await viewNotifications();
      else if (state.view === 'notes') node = await viewNotes();
      else if (state.view === 'bookmarks') node = await viewBookmarks();
      else if (state.view === 'feeds') node = await viewFeeds();
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
    wrap.appendChild(filesHeader(cur));
    wrap.appendChild(renderCrumbs());
    let entries;
    try { entries = await api('files', { path: cur.path }); }
    catch (e) { wrap.appendChild(errBox(e)); return wrap; }
    entries = (entries || []).slice().sort((a, b) => (b.isDir - a.isDir) || a.name.localeCompare(b.name));
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
      row.appendChild(entryActions(f));
      return row;
    }));
    if (!entries.length) wrap.appendChild(el('div', { class: 'empty', text: 'Empty folder — drop files here or use Upload.' }));
    wrap.appendChild(list);
    enableDrop(list, cur);
    return wrap;
  }

  function haltEvent(ev) { ev.stopPropagation(); ev.preventDefault && ev.preventDefault(); }
  function joinPath(dir, name) { return dir.replace(/\/+$/, '') + '/' + name; }

  function filesHeader(cur) {
    const actions = el('div', { class: 'actions' });
    const picker = el('input', { type: 'file', multiple: 'multiple', style: 'display:none' });
    picker.addEventListener('change', () => { uploadFiles(cur, picker.files); });
    actions.appendChild(el('button', { class: 'btn small', text: '+ Folder', onclick: async () => {
      const n = prompt('New folder name');
      if (n) { try { await api('createDir', { path: joinPath(cur.path, n) }); renderView($('#content')); } catch (e) { alert(e.message); } }
    } }));
    actions.appendChild(el('button', { class: 'btn small', text: '⬆ Upload', onclick: () => picker.click() }));
    actions.appendChild(picker);
    if (state.urlToken) actions.appendChild(el('a', { class: 'btn small', text: '⬇ zip', href: Api.zipDirUrl(state.urlToken, cur.path) }));
    return el('div', { class: 'page-head' }, [el('h2', { text: 'Files' }), actions]);
  }

  function entryActions(f) {
    const box = el('div', { style: 'display:flex;gap:6px;margin-left:8px;flex-wrap:wrap' });
    if (state.urlToken && !f.isDir) {
      box.appendChild(el('a', { class: 'btn small', text: 'Open', href: Api.fsUrl(state.urlToken, f.path, false), target: '_blank', rel: 'noopener', onclick: (e) => e.stopPropagation() }));
      box.appendChild(el('a', { class: 'btn small', text: '⬇', title: 'Download', href: Api.fsUrl(state.urlToken, f.path, true), onclick: (e) => e.stopPropagation() }));
    }
    if (state.urlToken && f.isDir) {
      box.appendChild(el('a', { class: 'btn small', text: '⬇ zip', href: Api.zipDirUrl(state.urlToken, f.path), onclick: (e) => e.stopPropagation() }));
    }
    box.appendChild(el('button', { class: 'btn small', text: 'Rename', onclick: async (ev) => {
      ev.stopPropagation();
      const nn = prompt('Rename to', f.name);
      if (nn && nn !== f.name) { try { await api('renameFile', { path: f.path, newName: nn }); renderView($('#content')); } catch (e) { alert(e.message); } }
    } }));
    box.appendChild(el('button', { class: 'btn small danger', text: 'Delete', onclick: async (ev) => {
      ev.stopPropagation();
      if (confirm('Delete "' + f.name + '"?')) { try { await api('deleteFiles', { paths: [f.path] }); renderView($('#content')); } catch (e) { alert(e.message); } }
    } }));
    return box;
  }

  async function uploadFiles(cur, fileList) {
    if (!state.urlToken) { alert('No file token — reconnect.'); return; }
    if (!fileList || !fileList.length) return;
    const host = $('#content');
    const bar = el('div', { class: 'hint', style: 'margin:6px 0' });
    if (host.firstChild) host.insertBefore(bar, host.firstChild); else host.appendChild(bar);
    let done = 0, fail = 0;
    for (let i = 0; i < fileList.length; i++) {
      const f = fileList[i];
      bar.textContent = 'Uploading ' + (i + 1) + '/' + fileList.length + ' — ' + f.name;
      try { await Api.upload(state.urlToken, joinPath(cur.path, f.name), f); done++; }
      catch (e) { fail++; }
    }
    bar.textContent = 'Uploaded ' + done + (fail ? (', ' + fail + ' failed') : '') + '.';
    renderView(host);
  }

  function enableDrop(node, cur) {
    node.addEventListener('dragover', (e) => { e.preventDefault(); node.classList.add('drop'); });
    node.addEventListener('dragleave', () => node.classList.remove('drop'));
    node.addEventListener('drop', (e) => {
      e.preventDefault(); node.classList.remove('drop');
      if (e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files.length) uploadFiles(cur, e.dataTransfer.files);
    });
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

  // ---------------------------------------------------------------- Notifications view

  async function viewNotifications() {
    const wrap = el('div', {});
    const list = await api('notifications');
    const clearable = (list || []).filter((n) => n.clearable).map((n) => n.key);
    wrap.appendChild(pageHead('Notifications', clearable.length ? [
      el('button', { class: 'btn small', text: 'Dismiss all', onclick: async () => {
        try { await api('cancelNotifications', { keys: clearable }); renderView($('#content')); } catch (e) { alert(e.message); }
      } }),
    ] : []));
    if (!list || !list.length) { wrap.appendChild(el('div', { class: 'empty', text: 'No notifications (or the listener is not enabled in the app).' })); return wrap; }
    const rows = el('div', { class: 'list' }, list.map((n) => {
      const actions = el('div', { style: 'display:flex;gap:6px;margin-left:8px' });
      if (n.canReply) actions.appendChild(el('button', { class: 'btn small', text: 'Reply', onclick: async () => {
        const t = prompt('Reply to ' + (n.title || n.packageName));
        if (t) { try { await api('replyNotification', { key: n.key, text: t }); } catch (e) { alert(e.message); } }
      } }));
      if (n.clearable) actions.appendChild(el('button', { class: 'btn small danger', text: '✕', title: 'Dismiss', onclick: async () => {
        try { await api('cancelNotifications', { keys: [n.key] }); renderView($('#content')); } catch (e) { alert(e.message); }
      } }));
      return el('div', { class: 'row' }, [
        el('div', { class: 'ico', text: '📣' }),
        el('div', { class: 'main' }, [
          el('div', { class: 'name', text: n.title || n.packageName }),
          el('div', { class: 'sub', text: (n.text || '') + (n.subText ? ' · ' + n.subText : '') }),
        ]),
        el('div', { class: 'trail', text: shortDate(n.postTime) }),
        actions,
      ]);
    }));
    wrap.appendChild(rows);
    return wrap;
  }

  // ---------------------------------------------------------------- Nearby view

  async function viewNearby() {
    const wrap = el('div', {});
    const devices = await api('nearbyDevices');
    wrap.appendChild(pageHead('Nearby', [
      el('button', { class: 'btn small', text: 'Scan', onclick: async () => { try { await api('startNearbyDiscovery'); setTimeout(() => renderView($('#content')), 1200); } catch (e) { alert(e.message); } } }),
      el('button', { class: 'btn small', text: 'Stop', onclick: () => api('stopNearbyDiscovery').catch(() => {}) }),
    ]));
    wrap.appendChild(el('p', { class: 'hint', text: 'Discovers other MWI devices on the LAN via mDNS / DNS-SD.' }));
    if (!devices || !devices.length) { wrap.appendChild(el('div', { class: 'empty', text: 'No devices found yet. Press Scan.' })); return wrap; }
    const list = el('div', { class: 'list' }, devices.map((d) =>
      el('div', { class: 'row' }, [
        el('div', { class: 'ico', text: '📡' }),
        el('div', { class: 'main' }, [
          el('div', { class: 'name', text: d.name || '(device)' }),
          el('div', { class: 'sub', text: d.resolved ? (d.host + ':' + d.port) : 'resolving…' }),
        ]),
        el('div', { class: 'trail', text: d.resolved ? 'ready' : '' }),
      ])));
    wrap.appendChild(list);
    return wrap;
  }

  // ---------------------------------------------------------------- Bookmarks view

  async function viewBookmarks() {
    const wrap = el('div', {});
    const [groups, marks] = await Promise.all([api('bookmarkGroups'), api('bookmarks', {})]);
    wrap.appendChild(pageHead('Bookmarks', [
      el('button', { class: 'btn small', text: '+ Group', onclick: async () => {
        const n = prompt('Group name');
        if (n) { try { await api('createBookmarkGroup', { name: n }); renderView($('#content')); } catch (e) { alert(e.message); } }
      } }),
      el('button', { class: 'btn small primary', text: '+ Bookmark', onclick: async () => {
        const title = prompt('Title'); if (!title) return;
        const url = prompt('URL (https://…)'); if (!url) return;
        try { await api('createBookmark', { title: title, url: url }); renderView($('#content')); } catch (e) { alert(e.message); }
      } }),
    ]));
    const groupName = {}; (groups || []).forEach((g) => { groupName[g.id] = g.name; });
    if (!marks || !marks.length) { wrap.appendChild(el('div', { class: 'empty', text: 'No bookmarks yet.' })); return wrap; }
    const list = el('div', { class: 'list' }, marks.map((b) =>
      el('div', { class: 'row' }, [
        el('div', { class: 'ico', text: '🔖' }),
        el('div', { class: 'main' }, [
          el('a', { class: 'name', href: b.url, target: '_blank', rel: 'noopener', text: b.title || b.url, style: 'text-decoration:none;color:inherit' }),
          el('div', { class: 'sub', text: (groupName[b.groupId] ? groupName[b.groupId] + ' · ' : '') + b.url }),
        ]),
        el('button', { class: 'btn small danger', text: 'Delete', onclick: async () => {
          try { await api('deleteBookmark', { id: b.id }); renderView($('#content')); } catch (e) { alert(e.message); }
        } }),
      ])));
    wrap.appendChild(list);
    return wrap;
  }

  // ---------------------------------------------------------------- Feeds (RSS) view

  const rss = { feed: null };

  async function viewFeeds() {
    const wrap = el('div', {});
    if (rss.feed) return viewFeedEntries(wrap);
    const feeds = await api('feeds');
    wrap.appendChild(pageHead('Feeds', [
      el('button', { class: 'btn small primary', text: '+ Feed', onclick: async () => {
        const url = prompt('Feed URL (RSS/Atom)'); if (!url) return;
        const name = prompt('Name (optional)') || '';
        try { await api('createFeed', { url: url, name: name }); renderView($('#content')); } catch (e) { alert(e.message); }
      } }),
    ]));
    if (!feeds || !feeds.length) { wrap.appendChild(el('div', { class: 'empty', text: 'No feeds. Add an RSS/Atom URL to subscribe.' })); return wrap; }
    const list = el('div', { class: 'list' }, feeds.map((f) =>
      el('div', { class: 'row clickable', onclick: () => { rss.feed = { id: f.id, name: f.name || f.url }; renderView($('#content')); } }, [
        el('div', { class: 'ico', text: '📰' }),
        el('div', { class: 'main' }, [ el('div', { class: 'name', text: f.name || f.url }), el('div', { class: 'sub', text: f.url }) ]),
        el('button', { class: 'btn small danger', text: 'Delete', onclick: async (ev) => {
          ev.stopPropagation();
          try { await api('deleteFeed', { id: f.id }); renderView($('#content')); } catch (e) { alert(e.message); }
        } }),
      ])));
    wrap.appendChild(list);
    return wrap;
  }

  async function viewFeedEntries(wrap) {
    const f = rss.feed;
    wrap.appendChild(el('div', { class: 'page-head' }, [
      el('button', { class: 'btn small', text: '‹ Back', onclick: () => { rss.feed = null; renderView($('#content')); } }),
      el('h2', { text: f.name, style: 'font-size:1.1rem' }),
    ]));
    const entries = await api('feedEntries', { feedId: f.id, limit: 100, offset: 0 });
    if (!entries || !entries.length) { wrap.appendChild(el('div', { class: 'empty', text: 'No entries yet — the app fetches feed content in the background.' })); return wrap; }
    const list = el('div', { class: 'list' }, entries.map((e) =>
      el('div', { class: 'row' }, [
        el('div', { class: 'ico', text: e.read ? '○' : '●' }),
        el('div', { class: 'main' }, [
          el('a', { class: 'name', href: e.url, target: '_blank', rel: 'noopener', text: e.title || '(untitled)', style: 'text-decoration:none;color:inherit' }),
          el('div', { class: 'sub', text: (e.author ? e.author + ' · ' : '') + shortDate(e.publishedAt) + (e.description ? ' · ' + stripHtml(e.description).slice(0, 90) : '') }),
        ]),
      ])));
    wrap.appendChild(list);
    return wrap;
  }

  function stripHtml(s) { return String(s || '').replace(/<[^>]*>/g, '').replace(/\s+/g, ' ').trim(); }

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
    wrap.appendChild(pageHead('Messages', [
      el('button', { class: 'btn small', text: '+ New', onclick: async () => {
        const addr = prompt('Send to (phone number)');
        if (!addr) return;
        const body = prompt('Message');
        if (!body) return;
        try { await api('sendSms', { address: addr, body: body }); renderView($('#content')); } catch (e) { alert('Send failed: ' + e.message); }
      } }),
    ]));
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
    wrap.appendChild(smsComposer(t.address));
    return wrap;
  }

  function smsComposer(address) {
    const box = el('input', { class: 'input', placeholder: 'Text message', style: 'flex:1' });
    const send = el('button', { class: 'btn primary', text: 'Send' });
    async function doSend() {
      const body = box.value.trim();
      if (!body) return;
      box.value = ''; send.disabled = true;
      try { await api('sendSms', { address: address, body: body }); renderView($('#content')); }
      catch (e) { send.disabled = false; alert('Send failed: ' + e.message); }
    }
    send.addEventListener('click', doSend);
    box.addEventListener('keydown', (e) => { if (e.key === 'Enter') doSend(); });
    return el('div', { style: 'display:flex;gap:8px;margin-top:12px' }, [box, send]);
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

  // ---------------------------------------------------------------- Screen Mirror view

  const NAV_KEYS = [
    { key: 'back', label: '‹ Back' }, { key: 'home', label: '⌂ Home' },
    { key: 'recents', label: '▭ Recents' }, { key: 'lock', label: '🔒 Lock' },
  ];

  async function viewMirror() {
    const wrap = el('div', {});
    const info = await api('screenMirrorState'); // { state, quality, controlEnabled }
    const status = el('span', { class: 'hint' });
    const canvas = el('canvas', { class: 'mirror-canvas', width: '2', height: '2' });
    const ctrl = createMirror(canvas, status);
    viewCleanup = function () { ctrl.detach(); };

    const startBtn = el('button', { class: 'btn primary small', text: info.state === 'running' ? 'Restart' : 'Start mirroring' });
    const stopBtn = el('button', { class: 'btn small', text: 'Stop', disabled: info.state === 'running' ? null : 'disabled' });
    const quality = el('select', { class: 'input', style: 'width:auto;padding:6px 10px' }, ['720p', '1080p'].map(function (q) {
      return el('option', { value: q, selected: q === info.quality ? 'selected' : null, text: q });
    }));

    startBtn.addEventListener('click', async function () {
      ctrl.attach();                 // subscribe to frames BEFORE the encoder emits its one-time SPS
      status.textContent = 'Requesting capture consent on the phone…';
      try { await api('startScreenMirror'); } catch (e) { status.textContent = 'Start failed: ' + e.message; }
      stopBtn.disabled = false;
    });
    stopBtn.addEventListener('click', async function () {
      try { await api('stopScreenMirror'); } catch (e) {}
      ctrl.detach(); status.textContent = 'Stopped.';
    });
    quality.addEventListener('change', function () { api('updateScreenMirrorQuality', { quality: quality.value }).catch(function () {}); });

    wrap.appendChild(el('div', { class: 'page-head' }, [
      el('h2', { text: 'Screen Mirror' }),
      el('div', { class: 'actions' }, [quality, startBtn, stopBtn]),
    ]));

    if (!window.VideoDecoder) {
      wrap.appendChild(el('div', { class: 'err-box', text: 'This browser has no WebCodecs VideoDecoder, so the H.264 stream cannot be decoded here. Use a recent Chrome, Edge, or Safari 16.4+.' }));
    }
    if (!info.controlEnabled) {
      wrap.appendChild(el('div', { class: 'card', style: 'margin-bottom:12px' }, [
        el('div', { class: 'kv' }, [
          el('span', { class: 'k', text: 'Remote control is off (Accessibility service not enabled).' }),
          el('button', { class: 'btn small', text: 'Open settings on phone', onclick: function () { api('openAccessibilitySettings').catch(function () {}); } }),
        ]),
      ]));
    }

    const stage = el('div', { class: 'mirror-stage' }, [canvas]);
    bindMirrorControls(canvas, info.controlEnabled);
    wrap.appendChild(stage);
    wrap.appendChild(el('div', { style: 'margin-top:10px;display:flex;gap:8px;flex-wrap:wrap;align-items:center' },
      [status].concat(NAV_KEYS.map(function (k) {
        return el('button', { class: 'btn small', text: k.label, disabled: info.controlEnabled ? null : 'disabled',
          onclick: function () { api('sendScreenMirrorControl', { type: 'key', key: k.key }).catch(function () {}); } });
      }))));

    if (info.controlEnabled) {
      const textBox = el('input', { class: 'input', placeholder: 'Type into the focused field on the phone…', style: 'flex:1;min-width:180px' });
      function sendText() { if (!textBox.value) return; api('sendScreenMirrorControl', { type: 'text', text: textBox.value }).catch(function () {}); textBox.value = ''; }
      textBox.addEventListener('keydown', function (e) { if (e.key === 'Enter') { sendText(); api('sendScreenMirrorControl', { type: 'key', key: 'enter' }).catch(function () {}); } });
      wrap.appendChild(el('div', { style: 'margin-top:8px;display:flex;gap:8px;flex-wrap:wrap' }, [
        textBox,
        el('button', { class: 'btn small', text: 'Send text', onclick: sendText }),
        el('button', { class: 'btn small', text: 'Enter ⏎', onclick: function () { api('sendScreenMirrorControl', { type: 'key', key: 'enter' }).catch(function () {}); } }),
      ]));
    }

    if (info.state === 'running') ctrl.attach(); // best-effort resync (may need Restart to recover SPS)
    return wrap;
  }

  /** Wire an H.264 (Annex-B) access-unit stream from event 301 into a WebCodecs decoder -> canvas. */
  function createMirror(canvas, status) {
    const H = window.MwiH264;
    const ctx = canvas.getContext('2d');
    let decoder = null, configured = false, sawKey = false, ts = 0, params = null, unsub = null;

    function ensureDecoder(codecStr) {
      decoder = new window.VideoDecoder({
        output: function (frame) {
          if (canvas.width !== frame.displayWidth) canvas.width = frame.displayWidth;
          if (canvas.height !== frame.displayHeight) canvas.height = frame.displayHeight;
          ctx.drawImage(frame, 0, 0, canvas.width, canvas.height);
          frame.close();
        },
        error: function (e) { status.textContent = 'Decoder error: ' + e; },
      });
      decoder.configure({ codec: codecStr, optimizeForLatency: true });
      configured = true;
      status.textContent = 'Live';
    }

    function feed(au) {
      if (!window.VideoDecoder) return;
      const types = H.nalTypeSet(au);
      const hasSps = types.has(7);
      const hasIdr = types.has(5);
      const hasVcl = types.has(1) || types.has(5);
      if (hasSps) {
        params = H.paramSets(au);
        if (!configured) { const cs = H.codecString(au); if (cs) { try { ensureDecoder(cs); } catch (e) { status.textContent = 'Configure failed: ' + e.message; return; } } }
      }
      if (!configured || !hasVcl) return;       // wait for SPS; skip config/SEI-only units
      let data = au;
      if (hasIdr && !hasSps && params) data = H.concat(params, au); // self-contained keyframe
      if (!sawKey) { if (!hasIdr) return; sawKey = true; }          // must start on a keyframe
      try { decoder.decode(new EncodedVideoChunk({ type: hasIdr ? 'key' : 'delta', timestamp: ts, data: data })); ts += 33333; }
      catch (e) { status.textContent = 'Decode error: ' + e.message; }
    }

    return {
      attach: function () {
        if (unsub) return; // already attached
        sawKey = false;
        unsub = onEventType(300 /* SCREEN_MIRRORING */, function () { status.textContent = 'Live'; });
        const unsubV = onEventType(301 /* SCREEN_MIRROR_VIDEO */, feed);
        const prev = unsub;
        unsub = function () { prev(); unsubV(); };
        status.textContent = 'Connecting…';
      },
      detach: function () {
        if (unsub) { unsub(); unsub = null; }
        try { if (decoder && decoder.state !== 'closed') decoder.close(); } catch (e) {}
        decoder = null; configured = false; sawKey = false;
      },
    };
  }

  /** Translate pointer gestures on the canvas into normalized tap/swipe controls. */
  function bindMirrorControls(canvas, enabled) {
    if (!enabled) return;
    let down = null;
    function norm(ev) {
      const r = canvas.getBoundingClientRect();
      return { x: clamp01((ev.clientX - r.left) / r.width), y: clamp01((ev.clientY - r.top) / r.height), t: Date.now() };
    }
    canvas.addEventListener('pointerdown', function (ev) { down = norm(ev); canvas.setPointerCapture(ev.pointerId); });
    canvas.addEventListener('pointerup', function (ev) {
      if (!down) return;
      const up = norm(ev);
      const dist = Math.hypot(up.x - down.x, up.y - down.y);
      const held = up.t - down.t;
      let ctl;
      if (dist < 0.02) ctl = { type: held > 500 ? 'longpress' : 'tap', x: down.x, y: down.y };
      else ctl = { type: 'swipe', x: down.x, y: down.y, x2: up.x, y2: up.y, durationMs: Math.min(800, Math.max(50, held)) };
      down = null;
      api('sendScreenMirrorControl', ctl).catch(function () {});
    });
  }
  function clamp01(v) { return v < 0 ? 0 : v > 1 ? 1 : v; }

  // ---------------------------------------------------------------- Chat view

  const chat = { channel: null }; // { id, name }

  async function viewChat() {
    const wrap = el('div', {});
    if (chat.channel) return viewChatChannel(wrap);
    const channels = await api('chatChannels');
    wrap.appendChild(pageHead('Chat', [
      el('button', { class: 'btn small', text: '+ New channel', onclick: async function () {
        const name = prompt('Channel name');
        if (name) { try { await api('createChatChannel', { name: name }); renderView($('#content')); } catch (e) { alert(e.message); } }
      } }),
    ]));
    if (!channels || !channels.length) { wrap.appendChild(el('div', { class: 'empty', text: 'No channels yet. Create one to start a local, on-device conversation.' })); return wrap; }
    const list = el('div', { class: 'list' }, channels.map(function (c) {
      return el('div', { class: 'row clickable', onclick: function () { chat.channel = { id: c.id, name: c.name }; renderView($('#content')); } }, [
        el('div', { class: 'avatar', text: initials(c.name) }),
        el('div', { class: 'main' }, [ el('div', { class: 'name', text: c.name || '(unnamed)' }), el('div', { class: 'sub', text: 'Updated ' + fmtDate(c.updatedAt) }) ]),
        el('button', { class: 'btn small danger', text: 'Delete', onclick: async function (ev) {
          ev.stopPropagation();
          try { await api('deleteChatChannel', { id: c.id }); renderView($('#content')); } catch (e) { alert(e.message); }
        } }),
      ]);
    }));
    wrap.appendChild(list);
    return wrap;
  }

  async function viewChatChannel(wrap) {
    const ch = chat.channel;
    wrap.appendChild(el('div', { class: 'page-head' }, [
      el('button', { class: 'btn small', text: '‹ Back', onclick: function () { chat.channel = null; renderView($('#content')); } }),
      el('h2', { text: ch.name || 'Channel', style: 'font-size:1.1rem' }),
    ]));
    const items = await api('chatItems', { channelId: ch.id, limit: 300, offset: 0 });
    const ordered = (items || []).slice().sort(function (a, b) { return Number(a.createdAt) - Number(b.createdAt); });
    const thread = el('div', { class: 'thread' }, ordered.length ? ordered.map(function (m) {
      return el('div', { class: 'bubble ' + (m.isMe ? 'me' : 'them') }, [
        el('div', { class: 'txt', text: chatText(m.content) }),
        el('div', { class: 'stamp', text: fmtDate(m.createdAt) }),
      ]);
    }) : [el('div', { class: 'empty', text: 'No messages yet.' })]);
    wrap.appendChild(thread);

    const box = el('input', { class: 'input', placeholder: 'Message', style: 'flex:1' });
    const send = el('button', { class: 'btn primary', text: 'Send' });
    async function doSend() {
      const text = box.value.trim();
      if (!text) return;
      box.value = ''; send.disabled = true;
      try { await api('sendChat', { channelId: ch.id, text: text }); renderView($('#content')); }
      catch (e) { send.disabled = false; alert(e.message); }
    }
    send.addEventListener('click', doSend);
    box.addEventListener('keydown', function (e) { if (e.key === 'Enter') doSend(); });
    wrap.appendChild(el('div', { style: 'display:flex;gap:8px;margin-top:12px' }, [box, send]));
    return wrap;
  }

  function chatText(content) {
    if (!content) return '';
    if (typeof content.text === 'string') return content.text;
    if (content.items) return '📎 ' + content.items.length + ' attachment' + (content.items.length === 1 ? '' : 's');
    return '(message)';
  }

  // ---------------------------------------------------------------- Cast (DLNA) view

  async function viewCast() {
    const wrap = el('div', {});
    const renderers = await api('dlnaRenderers');
    wrap.appendChild(pageHead('Cast', [
      el('button', { class: 'btn small', text: 'Scan', onclick: async function () { try { await api('startDlnaDiscovery'); setTimeout(function () { renderView($('#content')); }, 1500); } catch (e) { alert(e.message); } } }),
      el('button', { class: 'btn small', text: 'Stop scan', onclick: function () { api('stopDlnaDiscovery').catch(function () {}); } }),
    ]));
    wrap.appendChild(el('p', { class: 'hint', text: 'Discovers DLNA/UPnP renderers (smart TVs, receivers) on the LAN via SSDP. To cast, give a media URL the TV can fetch directly — note a self-signed HTTPS URL may be rejected by some TVs.' }));
    if (!renderers || !renderers.length) { wrap.appendChild(el('div', { class: 'empty', text: 'No renderers found yet. Press Scan.' })); return wrap; }
    const list = el('div', { class: 'list' }, renderers.map(function (r) {
      const urlBox = el('input', { class: 'input', placeholder: 'Media URL (http://…)', style: 'flex:1;min-width:160px' });
      return el('div', { class: 'row', style: 'flex-wrap:wrap;gap:8px' }, [
        el('div', { class: 'ico', text: '📺' }),
        el('div', { class: 'main' }, [ el('div', { class: 'name', text: r.name || '(renderer)' }), el('div', { class: 'sub', text: r.location } ) ]),
        el('div', { style: 'display:flex;gap:8px;flex:1 1 100%;margin-top:6px' }, [
          urlBox,
          el('button', { class: 'btn small primary', text: 'Cast', onclick: async function () {
            if (!urlBox.value) return;
            try { await api('dlnaCast', { controlUrl: r.controlUrl, url: urlBox.value }); } catch (e) { alert(e.message); }
          } }),
          el('button', { class: 'btn small', text: 'Stop', onclick: function () { api('dlnaStop', { controlUrl: r.controlUrl }).catch(function () {}); } }),
        ]),
      ]);
    }));
    wrap.appendChild(list);
    return wrap;
  }

  // ---------------------------------------------------------------- live events

  let eventSocket = null;
  const eventSubs = {}; // event code -> [fn(payloadUint8)]
  // Which views should re-render when a given event code arrives.
  const EVENT_VIEWS = {
    100: ['messages', 'chat'], 101: ['messages', 'chat'], 102: ['messages', 'chat'], // MESSAGE_*
    200: ['feeds'],       // FEEDS_FETCHED
    400: ['notifications'], 401: ['notifications'], // NOTIFICATION(_DELETED)
    600: ['bookmarks'],   // BOOKMARK_UPDATED
    900: ['chat'],        // CHANNELS_UPDATED
    1200: ['device'],     // DEVICE_NAME_UPDATED
    1400: ['nearby'], 1401: ['nearby'], // NEARBY_DEVICE_FOUND/LOST
    1500: ['calls'],      // CALL_STATE_CHANGED
  };

  /** Subscribe to a raw event code; returns an unsubscribe fn. Used by Screen Mirror for video frames. */
  function onEventType(code, fn) {
    (eventSubs[code] = eventSubs[code] || []).push(fn);
    return function () { eventSubs[code] = (eventSubs[code] || []).filter(function (f) { return f !== fn; }); };
  }

  let eventsWanted = false;
  let reconnectTimer = null;
  let reconnectDelay = 2000;

  function setConn(status) {
    const dot = $('#conn-dot'); const label = $('#conn-label');
    if (!dot || !label) return;
    if (status === 'open') { dot.style.background = 'var(--ok)'; label.textContent = 'Encrypted'; }
    else if (status === 'reconnecting') { dot.style.background = '#e0a800'; label.textContent = 'Reconnecting…'; }
    else { dot.style.background = 'var(--text-muted)'; label.textContent = 'Offline'; }
  }

  function openEventSocket() {
    if (!state.token) return;
    try {
      eventSocket = Api.events(state.token, function (type, payload) {
        const subs = eventSubs[type];
        if (subs) subs.slice().forEach(function (fn) { try { fn(payload); } catch (e) {} });
        const views = EVENT_VIEWS[type];
        if (views && views.indexOf(state.view) !== -1) {
          const host = $('#content');
          if (host) renderView(host);
        }
      }, function (st) {
        if (st === 'open') { reconnectDelay = 2000; setConn('open'); }
        else if (st === 'close') { setConn(eventsWanted ? 'reconnecting' : 'offline'); scheduleReconnect(); }
      });
    } catch (e) { scheduleReconnect(); }
  }

  function scheduleReconnect() {
    if (!eventsWanted || reconnectTimer) return;
    setConn('reconnecting');
    reconnectTimer = setTimeout(function () {
      reconnectTimer = null;
      if (!eventsWanted) return;
      reconnectDelay = Math.min(reconnectDelay * 2, 15000); // backoff, capped
      openEventSocket();
    }, reconnectDelay);
  }

  function startEvents() {
    if (!state.token) return;
    eventsWanted = true;
    reconnectDelay = 2000;
    openEventSocket();
  }
  function stopEvents() {
    eventsWanted = false;
    if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null; }
    try { if (eventSocket) eventSocket.close(); } catch (e) {}
    eventSocket = null;
  }

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
