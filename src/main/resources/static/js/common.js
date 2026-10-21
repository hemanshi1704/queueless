// Shared helpers. All dynamic text is inserted via textContent / text nodes (never innerHTML),
// because customer names are untrusted input.

const $ = (selector, root = document) => root.querySelector(selector);

function h(tag, attrs = {}, ...kids) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (k === 'class') el.className = v;
    else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
    else if (v !== false && v != null) el.setAttribute(k, v);
  }
  for (const kid of kids.flat()) {
    if (kid == null) continue;
    el.append(kid.nodeType ? kid : document.createTextNode(String(kid)));
  }
  return el;
}

const Auth = {
  get token() { try { return localStorage.getItem('ql_token'); } catch { return null; } },
  set token(v) { try { v ? localStorage.setItem('ql_token', v) : localStorage.removeItem('ql_token'); } catch { /* private mode */ } },
};

async function api(path, { method = 'GET', body, auth = false } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (auth && Auth.token) headers.Authorization = 'Bearer ' + Auth.token;
  const res = await fetch(path, { method, headers, body: body ? JSON.stringify(body) : undefined });
  const data = await res.json().catch(() => null);
  if (res.status === 401 && auth) {
    Auth.token = null;
    if (typeof onSessionExpired === 'function') onSessionExpired();
  }
  if (!res.ok) throw new Error((data && data.error) || res.statusText || 'Request failed');
  return data;
}

function fmtEta(seconds) {
  if (seconds == null) return '—';
  if (seconds < 45) return 'under a minute';
  const m = Math.round(seconds / 60);
  if (m < 60) return '~' + m + ' min';
  return '~' + Math.floor(m / 60) + 'h ' + (m % 60) + 'm';
}

function fmtClock(seconds) {
  const s = Math.max(0, Math.round(seconds));
  return String(Math.floor(s / 60)).padStart(2, '0') + ':' + String(s % 60).padStart(2, '0');
}

// Live updates over STOMP/WebSocket. Pages also poll, so this is an enhancement, not a dependency.
function subscribe(slug, onMessage) {
  if (!window.StompJs) return;
  try {
    const proto = location.protocol === 'https:' ? 'wss' : 'ws';
    const client = new StompJs.Client({
      brokerURL: proto + '://' + location.host + '/ws',
      reconnectDelay: 3000,
      onConnect: () => client.subscribe('/topic/shops/' + slug, m => onMessage(JSON.parse(m.body))),
    });
    client.activate();
  } catch { /* polling continues */ }
}
