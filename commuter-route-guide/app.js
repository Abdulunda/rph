/* RoutePH front end. Talks to the Java back end (/api/...). */
const $ = (s) => document.querySelector(s);
const $$ = (s) => [...document.querySelectorAll(s)];
const MODES = {
  train: ['Train', '#2a50cf'], walk: ['Walk', '#6b7385'], jeep: ['Jeep', '#d6336c'],
  bus: ['Bus', '#16a34a'], tricycle: ['Tricycle', '#e8890c'], ebike: ['E-Bike', '#8a3ffc']
};
const ORD = ['First', 'Second', 'Third'];
const S = { token: localStorage.getItem('token') || '', user: null, dest: 'Bulacan', route: null, email: '', otpMode: 'reset', resetToken: '' };

/* ---------- helpers ---------- */
async function api(path, body) {
  const res = await fetch('/api' + path, {
    method: body ? 'POST' : 'GET',
    headers: { 'Content-Type': 'application/json', ...(S.token ? { Authorization: 'Bearer ' + S.token } : {}) },
    body: body ? JSON.stringify(body) : undefined
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    if (res.status === 401 && S.token && !path.startsWith('/login')) logout(true);
    throw new Error(data.error || 'Something went wrong.');
  }
  return data;
}
let toastTimer;
function toast(msg, err) {
  const t = $('#toast'); t.textContent = msg; t.className = 'toast' + (err ? ' err' : ''); t.hidden = false;
  clearTimeout(toastTimer); toastTimer = setTimeout(() => (t.hidden = true), 3600);
}
function show(id, bar) {
  $$('.view').forEach((v) => (v.hidden = v.id !== id));
  $('#appbar').hidden = !bar;
  window.scrollTo(0, 0);
}
function el(tag, props = {}, ...kids) {
  const e = document.createElement(tag);
  Object.assign(e, props);
  kids.forEach((k) => e.append(k));
  return e;
}
async function run(btn, fn) {
  if (btn) btn.disabled = true;
  try { return await fn(); } catch (e) { toast(e.message, true); } finally { if (btn) btn.disabled = false; }
}
function when(s, withDate) {            // "2026-11-09 14:01:00" -> "11/9/2026, 2:01pm"
  const m = /(\d+)-(\d+)-(\d+)[ T](\d+):(\d+)/.exec(s || '');
  if (!m) return s || '';
  let h = +m[4]; const ap = h >= 12 ? 'pm' : 'am'; h = h % 12 || 12;
  const t = `${h}:${m[5]}${withDate ? '' : ' '}${ap}`;
  return withDate ? `${+m[2]}/${+m[3]}/${m[1]}, ${t}` : t;
}
const THUMB = '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"><path d="M7 11v9H4v-9h3zm0 0 4-8c1.7 0 2.6 1.2 2.3 2.8L12.9 9H19a2 2 0 0 1 2 2.4l-1.4 7A2 2 0 0 1 17.6 20H7"/></svg>';

/* ---------- auth screens ---------- */
const LOGIN_SIDE = ['From PUP Sta Mesa to your location.', 'Step-by-step commute routes, shared tips from other commuters.'];
const LOCK_SIDE = ['Locked out? It happens.', 'We will send a code to your email to confirm it is you.'];
function go(p) {
  $$('.panel').forEach((f) => (f.hidden = f.dataset.p !== p));
  const side = ['login', 'register'].includes(p) || S.otpMode === 'verify' && p === 'otp' ? LOGIN_SIDE : LOCK_SIDE;
  $('#sideTitle').textContent = side[0]; $('#sideText').textContent = side[1];
  show('viewAuth', false);
  const first = $(`.panel[data-p=${p}] input`); if (first) first.focus();
}
$$('[data-go]').forEach((a) => a.addEventListener('click', (e) => { e.preventDefault(); stopTimer(); go(a.dataset.go); }));

$('#fLogin').addEventListener('submit', (e) => {
  e.preventDefault();
  const email = $('#lEmail').value.trim(), password = $('#lPass').value;
  if (!email || !password) return toast('Please enter your email and password.', true);
  run(e.submitter, async () => {
    const r = await api('/login', { email, password });
    if (r.needVerify) { S.email = r.email; openOtp('verify'); toast('Please verify your email first. We sent you a code.'); }
    else signedIn(r);
  });
});

$('#fRegister').addEventListener('submit', (e) => {
  e.preventDefault();
  const name = $('#rName').value.trim(), email = $('#rEmail').value.trim(), p = $('#rPass').value;
  if (!name || !email) return toast('Please fill in all fields.', true);
  if (p.length < 8) return toast('Password must be at least 8 characters.', true);
  if (p !== $('#rPass2').value) return toast('Passwords do not match.', true);
  run(e.submitter, async () => {
    const r = await api('/register', { name, email, password: p });
    S.email = r.email || email; openOtp('verify', r.emailSent);
  });
});

$('#fForgot').addEventListener('submit', (e) => {
  e.preventDefault();
  const email = $('#fEmail').value.trim();
  if (!email) return toast('Please enter your email.', true);
  run(e.submitter, async () => {
    const r = await api('/forgot', { email });
    S.email = r.email || email; openOtp('reset', r.emailSent);
  });
});

/* ----- 6-box OTP ----- */
const boxes = () => $$('#boxes input');
function openOtp(mode, emailSent) {
  S.otpMode = mode;
  $('#otpStep').textContent = mode === 'reset' ? 'STEP 2 OF 3' : 'VERIFY YOUR EMAIL';
  $('#otpEmail').textContent = S.email;
  boxes().forEach((b) => (b.value = ''));
  go('otp'); boxes()[0].focus(); startTimer();
  if (emailSent === false) toast('Dev mode: your OTP is printed in the Java console.');
}
$('#boxes').addEventListener('input', (e) => {
  const i = boxes().indexOf(e.target);
  e.target.value = e.target.value.replace(/\D/g, '').slice(-1);
  if (e.target.value && i < 5) boxes()[i + 1].focus();
});
$('#boxes').addEventListener('keydown', (e) => {
  const i = boxes().indexOf(e.target);
  if (e.key === 'Backspace' && !e.target.value && i > 0) boxes()[i - 1].focus();
  if (e.key === 'ArrowLeft' && i > 0) boxes()[i - 1].focus();
  if (e.key === 'ArrowRight' && i < 5) boxes()[i + 1].focus();
});
$('#boxes').addEventListener('paste', (e) => {
  const d = (e.clipboardData.getData('text') || '').replace(/\D/g, '').slice(0, 6);
  if (!d) return; e.preventDefault();
  boxes().forEach((b, i) => (b.value = d[i] || ''));
  boxes()[Math.min(d.length, 5)].focus();
});
$('#fOtp').addEventListener('submit', (e) => {
  e.preventDefault();
  const code = boxes().map((b) => b.value).join('');
  if (code.length < 6) return toast('Please enter the 6-digit code.', true);
  run(e.submitter, async () => {
    if (S.otpMode === 'verify') { signedIn(await api('/verify-otp', { email: S.email, code })); }
    else { S.resetToken = (await api('/verify-reset', { email: S.email, code })).resetToken; stopTimer(); go('reset'); }
  });
});
$('#otpBack').addEventListener('click', (e) => { e.preventDefault(); stopTimer(); go(S.otpMode === 'reset' ? 'forgot' : 'login'); });
$('#otpOther').addEventListener('click', (e) => { e.preventDefault(); stopTimer(); go(S.otpMode === 'reset' ? 'forgot' : 'register'); });

let timer, left = 0;
function startTimer() {
  stopTimer(); left = 30; paintTimer();
  timer = setInterval(() => { left--; paintTimer(); if (left <= 0) stopTimer(); }, 1000);
}
function stopTimer() { clearInterval(timer); timer = null; }
function paintTimer() {
  const box = $('#resendTxt');
  if (left > 0) { box.textContent = `Resend in 0:${String(left).padStart(2, '0')}`; return; }
  box.innerHTML = '<a href="#" class="lnk" id="doResend">Resend code</a>';
  $('#doResend').addEventListener('click', (ev) => {
    ev.preventDefault();
    run(null, async () => { await api('/resend-otp', { email: S.email }); toast('A new code was sent.'); startTimer(); });
  });
}

$('#fReset').addEventListener('submit', (e) => {
  e.preventDefault();
  const p = $('#nPass').value;
  if (p.length < 8) return toast('Password must be at least 8 characters.', true);
  if (p !== $('#nPass2').value) return toast('Passwords do not match.', true);
  run(e.submitter, async () => {
    await api('/reset-password', { resetToken: S.resetToken, password: p });
    $('#nPass').value = $('#nPass2').value = ''; go('login'); toast('Password updated. You can log in now.');
  });
});

function signedIn(r) {
  S.token = r.token; S.user = r.user; localStorage.setItem('token', S.token);
  $('#lPass').value = '';
  enterApp();
}
function logout(silent) {
  if (!silent && S.token) api('/logout', {}).catch(() => {});
  S.token = ''; S.user = null; localStorage.removeItem('token'); endNav(true); go('login');
}
$('#btnLogout').addEventListener('click', (e) => { e.preventDefault(); logout(); });

async function boot() {
  if (S.token) { try { S.user = await api('/me'); return enterApp(); } catch (e) { /* fall through */ } }
  go('login');
}
function enterApp() {
  $('#who').textContent = S.user.name;
  goHome(); refreshBadge(); loadBanner();
}
function goHome() { endNav(true); show('viewHome', true); loadRoutes(S.dest); refreshBadge(); }
$('#logo').addEventListener('click', (e) => { e.preventDefault(); if (S.user) goHome(); });

/* ---------- home: destination + routes ---------- */
$$('.dest').forEach((b) => b.addEventListener('click', () => {
  S.dest = b.dataset.dest; loadRoutes(S.dest);
  if (innerWidth < 860) $('.right-col').scrollIntoView({ behavior: 'smooth' });
}));
async function loadRoutes(dest) {
  $$('.dest').forEach((b) => b.classList.toggle('sel', b.dataset.dest === dest));
  const box = $('#routeList');
  try {
    const routes = await api('/routes?destination=' + encodeURIComponent(dest));
    box.innerHTML = '';
    routes.forEach((r, i) => {
      const c = el('button', { className: 'rcard' + (i === 0 ? ' sel' : '') },
        el('div', { className: 'top' }, el('h3', { textContent: r.title }), el('span', { className: 'cnt', textContent: r.stops + ' steps' })),
        el('div', { className: 's', textContent: r.summary }),
        el('div', { className: 'chips' }, ...r.modes.map((m) => el('span', { className: 'c', textContent: MODES[m][0] }))),
        el('div', { className: 'vr', textContent: 'View Route →' }));
      c.addEventListener('mouseenter', () => $$('.rcard').forEach((x) => x.classList.toggle('sel', x === c)));
      c.addEventListener('click', () => openRoute(r.no));
      box.append(c);
    });
  } catch (e) { toast(e.message, true); }
}
async function loadBanner() {
  try {
    S.adv = await api('/advisories');
    $('#bannerText').textContent = S.adv.length ? `“${S.adv[0].title}”` : '“No new updates”';
  } catch (e) { /* ignore */ }
}
$('#bannerAll').addEventListener('click', (e) => { e.preventDefault(); openNotifications(); });
$('.banner > div').classList.add('btxt');
$('.banner > div').addEventListener('click', () => { if (S.adv && S.adv.length) openAdvisory(S.adv[0]); });

/* ---------- advisory pop-up ---------- */
let advRoutes = [];
function openAdvisory(a) {
  $('#advT').textContent = a.title;
  $('#advB').textContent = a.details || a.body;
  advRoutes = (a.routes || '').split(',').map((x) => x.trim().split(':')).filter((x) => x[0] && +x[1]).map((x) => ({ dest: x[0], no: +x[1] }));
  $('#advR').hidden = !advRoutes.length; $('#advView').hidden = !advRoutes.length;
  $('#advLinks').replaceChildren(...advRoutes.map((r) => {
    const b = el('button', { className: 'advlink', textContent: `${ORD[r.no - 1] || 'Route ' + r.no} Route to ${r.dest}` });
    b.addEventListener('click', () => goAdvRoute(r)); return b;
  }));
  $('#advModal').hidden = false;
  api('/advisories/read', { id: a.id }).then(refreshBadge).catch(() => {});
}
function goAdvRoute(r) { $('#advModal').hidden = true; S.dest = r.dest; openRoute(r.no); }
function closeAdvisory() { $('#advModal').hidden = true; if (!$('#viewNotif').hidden) openNotifications(); }
$('#advView').addEventListener('click', () => advRoutes[0] && goAdvRoute(advRoutes[0]));
$('#advX').addEventListener('click', closeAdvisory);
$('#advModal').addEventListener('click', (e) => { if (e.target.id === 'advModal') closeAdvisory(); });
document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && !$('#advModal').hidden) closeAdvisory(); });

/* ---------- route page ---------- */
let map, layers, pins = [], youMarker, watchId = null, navIdx = -1;

async function openRoute(no) {
  try {
    endNav(true);
    const r = S.route = await api(`/route?destination=${encodeURIComponent(S.dest)}&no=${no}`);
    api('/history', { destination: S.dest, route: no }).catch(() => {});
    $('#rTitle').textContent = `${r.title} to ${S.dest}`;
    $('#pillText').textContent = `PUP Sta Mesa → ${S.dest}`;
    $('#rSummary').textContent = r.summary; $('#rSteps').textContent = r.stops + ' steps';
    $('#rChips').replaceChildren(...r.modes.map((m) => el('span', { className: 'c', textContent: MODES[m][0] })));
    const ol = $('#steps'); ol.innerHTML = '';
    r.steps.forEach((s, i) => {
      const li = el('li', { id: 'step' + i }, el('span', { className: 't', textContent: s.text }), el('span', { className: 'm', textContent: MODES[s.mode][0] }));
      li.addEventListener('click', () => { if (navIdx >= 0) return; markStep(i); if (map) map.flyTo([s.lat, s.lng], 15, { duration: .7 }); });
      ol.append(li);
    });
    show('viewRoute', true);
    drawMap(); loadComments();
  } catch (e) { toast(e.message, true); }
}
$('#routeBack').addEventListener('click', (e) => { e.preventDefault(); goHome(); });
$('#btnAnother').addEventListener('click', () => goHome());
$('#btnChange').addEventListener('click', () => goHome());

function pinIcon(n, color, cur) {
  return L.divIcon({ className: '', html: `<div class="pin${cur ? ' cur' : ''}" style="background:${color}">${n}</div>`, iconSize: [26, 26], iconAnchor: [13, 13] });
}
async function drawMap() {
  if (typeof L === 'undefined') { $('#map').textContent = 'Map could not load. Check your internet connection.'; return; }
  if (!map) {
    map = L.map('map', { scrollWheelZoom: false, attributionControl: true }).setView([14.62, 121.0], 11);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19, attribution: '&copy; OpenStreetMap' }).addTo(map);
    layers = L.layerGroup().addTo(map);
  }
  setTimeout(() => map.invalidateSize(), 60);
  layers.clearLayers(); pins = [];
  const r = S.route, o = r.origin, pts = [[o.lat, o.lng], ...r.steps.map((s) => [s.lat, s.lng])];
  L.marker(pts[0], { icon: pinIcon('A', '#213a9b') }).addTo(layers).bindTooltip('PUP Sta Mesa (start)');
  r.steps.forEach((s, i) => pins.push(L.marker([s.lat, s.lng], { icon: pinIcon(s.n, MODES[s.mode][1]) }).addTo(layers).bindTooltip(s.text)));
  map.fitBounds(pts, { padding: [24, 24] });
  for (let i = 0; i < r.steps.length; i++) {          // each leg; road legs use the free OSRM routing API
    const a = pts[i], b = pts[i + 1], mode = r.steps[i].mode;
    let line = [a, b], dashed = mode === 'walk';
    if (['jeep', 'bus', 'tricycle', 'ebike'].includes(mode)) {
      try {
        const j = await (await fetch(`https://router.project-osrm.org/route/v1/driving/${a[1]},${a[0]};${b[1]},${b[0]}?overview=full&geometries=geojson`)).json();
        if (j.routes && j.routes[0]) line = j.routes[0].geometry.coordinates.map((c) => [c[1], c[0]]);
      } catch (e) { dashed = true; }
    }
    if (S.route !== r) return;
    L.polyline(line, { color: MODES[mode][1], weight: 5, opacity: .85, dashArray: dashed ? '8 8' : null }).addTo(layers);
  }
}
function markStep(i, doneBefore) {
  $$('#steps li').forEach((li, k) => { li.classList.toggle('active', k === i); li.classList.toggle('done', !!doneBefore && k < i); });
}

/* ---------- in-page navigation: the map opens full screen here, never on Google Maps ---------- */
$('#btnNav').addEventListener('click', startNav);
$('#navEnd').addEventListener('click', () => endNav());
$('#navNext').addEventListener('click', () => navTo(navIdx + 1));
$('#navPrev').addEventListener('click', () => navTo(Math.max(navIdx - 1, 0)));
document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && navIdx >= 0) endNav(); });

function startNav() {
  if (!map) return toast('The map is not available right now.', true);
  $('#mapCard').classList.add('nav'); document.body.style.overflow = 'hidden';
  $('#navSheet').hidden = false; $('#navEnd').hidden = false;
  map.scrollWheelZoom.enable();
  setTimeout(() => { map.invalidateSize(); navTo(0); }, 80);
  if (!navigator.geolocation) { $('#navInfo').textContent = 'GPS is not available. Use the Next button to move through the steps.'; return; }
  watchId = navigator.geolocation.watchPosition(onPos, () => {
    $('#navInfo').textContent = 'Location is blocked. Allow GPS in your browser, or use the Next button.';
  }, { enableHighAccuracy: true, maximumAge: 5000, timeout: 20000 });
}
function endNav(silent) {
  if (watchId !== null) { navigator.geolocation.clearWatch(watchId); watchId = null; }
  if (youMarker && map) { map.removeLayer(youMarker); youMarker = null; }
  navIdx = -1;
  $('#mapCard').classList.remove('nav'); document.body.style.overflow = '';
  $('#navSheet').hidden = true; $('#navEnd').hidden = true;
  if (map) { map.scrollWheelZoom.disable(); setTimeout(() => { map.invalidateSize(); if (S.route && !silent) map.fitBounds([[S.route.origin.lat, S.route.origin.lng], ...S.route.steps.map((s) => [s.lat, s.lng])], { padding: [24, 24] }); }, 80); }
  if (S.route) { markStep(-1); pins.forEach((p, i) => p.setIcon(pinIcon(i + 1, MODES[S.route.steps[i].mode][1]))); }
}
function navTo(i) {
  const steps = S.route.steps; navIdx = i;
  markStep(i, true);
  pins.forEach((p, k) => p.setIcon(pinIcon(k + 1, MODES[steps[k].mode][1], k === i)));
  if (i >= steps.length) {
    $('#navCount').textContent = 'Arrived'; $('#navText').textContent = 'You have reached your destination!';
    $('#navInfo').textContent = ''; $('#navNext').disabled = true; return;
  }
  $('#navNext').disabled = false;
  const s = steps[i];
  map.flyTo([s.lat, s.lng], Math.max(map.getZoom(), 15), { duration: .8 });
  $('#navCount').textContent = `Step ${s.n} of ${steps.length} · ${MODES[s.mode][0]}`;
  $('#navText').textContent = s.text;
  $('#navInfo').textContent = youMarker ? '' : 'Waiting for GPS… you can also tap Next step.';
}
function onPos(p) {
  const ll = [p.coords.latitude, p.coords.longitude];
  if (!youMarker) youMarker = L.marker(ll, { icon: L.divIcon({ className: '', html: '<div class="you"></div>', iconSize: [18, 18], iconAnchor: [9, 9] }), zIndexOffset: 1000 }).addTo(map).bindTooltip('You are here');
  else youMarker.setLatLng(ll);
  if (navIdx < 0 || navIdx >= S.route.steps.length) return;
  const t = S.route.steps[navIdx], d = dist(ll, [t.lat, t.lng]);
  $('#navInfo').textContent = `About ${d < 1000 ? Math.round(d) + ' m' : (d / 1000).toFixed(1) + ' km'} to this stop.`;
  if (d < 120) { toast('Arrived at step ' + t.n); navTo(navIdx + 1); }
}
function dist(a, b) {
  const R = 6371000, k = Math.PI / 180, dLa = (b[0] - a[0]) * k, dLo = (b[1] - a[1]) * k;
  const x = Math.sin(dLa / 2) ** 2 + Math.cos(a[0] * k) * Math.cos(b[0] * k) * Math.sin(dLo / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(x));
}

/* ---------- comments, likes, replies ---------- */
$('#fComment').addEventListener('submit', (e) => {
  e.preventDefault();
  const text = $('#cText').value.trim();
  if (!text) return toast('No comment entered.', true);
  run(e.submitter, async () => {
    const r = await api('/comments', { destination: S.dest, route: S.route.no, text });
    $('#cText').value = ''; toast(r.message); loadComments();
  });
});
async function loadComments() {
  const box = $('#comments'); box.innerHTML = '';
  try {
    const list = await api(`/comments?destination=${encodeURIComponent(S.dest)}&route=${S.route.no}`);
    if (!list.length) box.append(el('p', { className: 'empty', textContent: 'No comments yet. Be the first!' }));
    list.forEach((c) => {
      const like = el('button', { className: 'like' + (c.liked ? ' on' : ''), title: 'Like' });
      like.innerHTML = THUMB + `<span>${c.likes || ''}</span>`;
      like.addEventListener('click', () => run(like, async () => {
        const r = await api('/like', { commentId: c.id });
        like.classList.toggle('on', r.liked); like.lastChild.textContent = r.likes || '';
      }));
      box.append(el('div', { className: 'cm' },
        el('div', { className: 'h' }, el('b', { textContent: '@' + c.user }), document.createTextNode('  ' + when(c.at, true))),
        el('p', { textContent: c.text }), el('div', { className: 'acts' }, like)));
    });
  } catch (e) { box.append(el('p', { className: 'empty', textContent: e.message })); }
}

/* ---------- notifications ---------- */
async function refreshBadge() {
  try { const n = await api('/notifications'); $('#dot').hidden = !n.unread; return n; } catch (e) { return null; }
}
$('#btnBell').addEventListener('click', openNotifications);
async function openNotifications() {
  endNav(true);
  show('viewNotif', false);
  const box = $('#notifList'); box.innerHTML = '';
  const n = await refreshBadge();
  if (!n) { box.append(el('p', { className: 'empty', textContent: 'Could not load notifications.' })); return; }
  n.advisories.forEach((a) => {
    const c = el('div', { className: 'n adv' + (a.unread ? ' unread' : '') });
    c.innerHTML = '<div class="av"><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="#2a3f9e" stroke-width="2" stroke-linejoin="round"><path d="M11 5 6 9H3v6h3l5 4V5z"/><path d="M15.5 8.5a5 5 0 0 1 0 7" stroke-linecap="round"/></svg></div><div><div class="k"></div><div class="tt"></div><div class="q"></div><div class="when"></div></div>' + (a.unread ? '<i class="nd"></i>' : '');
    c.querySelector('.k').textContent = 'Service advisory'; c.querySelector('.tt').textContent = a.title;
    c.querySelector('.q').textContent = `“${a.body}”`; c.querySelector('.when').textContent = isToday(a.at) ? 'Today' : when(a.at, true);
    c.tabIndex = 0; c.setAttribute('role', 'button');
    c.addEventListener('click', () => openAdvisory(a));
    box.append(c);
  });
  n.items.forEach((i) => {
    const c = el('div', { className: 'n like' + (i.isRead ? '' : ' unread') });
    c.innerHTML = '<div class="av"></div><div><b class="nm"></b><div class="q"></div><div class="when"></div></div>';
    c.querySelector('.av').textContent = (i.fromName || '?')[0].toUpperCase();
    c.querySelector(".nm").textContent = "@" + i.fromName;
    c.querySelector('.q').textContent = i.type === 'like' ? 'like your post' : 'replied: ' + i.text;
    c.querySelector('.when').textContent = `${ORD[i.route - 1] || 'Route ' + i.route} route to ${i.destination} · ${when(i.at)}`;
    c.addEventListener('click', () => { S.dest = i.destination; openRoute(i.route); });
    box.append(c);
  });
  if (!n.advisories.length && !n.items.length) box.append(el('p', { className: 'empty', textContent: 'No new notifications.' }));
}
function isToday(s) { const d = new Date(), m = /(\d+)-(\d+)-(\d+)/.exec(s || ''); return !!m && +m[1] === d.getFullYear() && +m[2] === d.getMonth() + 1 && +m[3] === d.getDate(); }
$('#markAll').addEventListener('click', (e) => { e.preventDefault(); run(null, async () => { await api('/notifications/read', {}); await openNotifications(); }); });
$('#notifBack').addEventListener('click', (e) => { e.preventDefault(); goHome(); });
$('#notifHome').addEventListener('click', (e) => { e.preventDefault(); goHome(); });

boot();
