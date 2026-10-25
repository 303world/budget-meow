/**
 * 预算喵同步服务（Cloudflare Worker + D1）
 *
 * 只做三件事：账号、令牌、每用户一份账目快照。
 * 合并策略放在客户端（按记录 updatedAt 逐条合并），服务端只用 rev 做并发保护：
 * 客户端带着 baseRev 提交，rev 不匹配就返回 409 + 最新快照，让客户端重新合并。
 *
 * 部署方式见 server/README.md
 */

const REPO = '303world/budget-meow';
const MAX_PAYLOAD_BYTES = 4 * 1024 * 1024;
const PBKDF2_ITERATIONS = 100000; // Workers 的 WebCrypto 上限就是 10 万
const TOKEN_TTL_DAYS = 180;
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const method = request.method;

    if (method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: corsHeaders() });
    }

    try {
      if (url.pathname === '/health' && method === 'GET') {
        return json({ ok: true, service: 'budget-meow', time: new Date().toISOString() });
      }
      if (url.pathname === '/v1/register' && method === 'POST') return await register(request, env);
      if (url.pathname === '/v1/login' && method === 'POST') return await login(request, env);
      if (url.pathname === '/v1/logout' && method === 'POST') return await logout(request, env);
      if (url.pathname === '/v1/me' && method === 'GET') return await me(request, env);
      if (url.pathname === '/v1/dataset' && method === 'GET') return await getDataset(request, env);
      if (url.pathname === '/v1/dataset' && method === 'PUT') return await putDataset(request, env);
      if (url.pathname === '/v1/release/latest' && method === 'GET') return await latestRelease(url);
      if (url.pathname === '/v1/release/download' && method === 'GET') return await downloadAsset(url);
      if (url.pathname === '/v1/password' && method === 'POST') return await changePassword(request, env);
      if (url.pathname === '/v1/account' && method === 'DELETE') return await deleteAccount(request, env);
      return json({ error: 'not_found' }, 404);
    } catch (err) {
      return json({ error: 'server_error', message: String(err && err.message ? err.message : err) }, 500);
    }
  },
};

// ---------------------------------------------------------------- handlers

async function register(request, env) {
  const body = await readJson(request);
  const email = normalizeEmail(body.email);
  const password = String(body.password || '');
  if (!EMAIL_RE.test(email)) return json({ error: 'invalid_email' }, 400);
  if (password.length < 8) return json({ error: 'weak_password' }, 400);

  const existing = await env.DB.prepare('SELECT id FROM users WHERE email = ?').bind(email).first();
  if (existing) return json({ error: 'email_taken' }, 409);

  const salt = randomHex(16);
  const hash = await hashPassword(password, salt);
  const userId = crypto.randomUUID();
  const now = new Date().toISOString();

  await env.DB.prepare(
    'INSERT INTO users (id, email, pass_hash, pass_salt, iterations, created_at) VALUES (?, ?, ?, ?, ?, ?)',
  ).bind(userId, email, hash, salt, PBKDF2_ITERATIONS, now).run();

  const token = await issueToken(env, userId, now);
  return json({ token, userId, email, createdAt: now });
}

async function login(request, env) {
  const body = await readJson(request);
  const email = normalizeEmail(body.email);
  const password = String(body.password || '');
  if (!EMAIL_RE.test(email) || password.length === 0) return json({ error: 'invalid_credentials' }, 401);

  const user = await env.DB.prepare(
    'SELECT id, email, pass_hash, pass_salt, iterations FROM users WHERE email = ?',
  ).bind(email).first();
  if (!user) return json({ error: 'invalid_credentials' }, 401);

  const hash = await hashPassword(password, user.pass_salt, user.iterations);
  if (!timingSafeEqual(hash, user.pass_hash)) return json({ error: 'invalid_credentials' }, 401);

  const now = new Date().toISOString();
  const token = await issueToken(env, user.id, now);
  return json({ token, userId: user.id, email: user.email });
}

async function logout(request, env) {
  const auth = await authenticate(request, env);
  if (!auth) return json({ error: 'unauthorized' }, 401);
  await env.DB.prepare('DELETE FROM tokens WHERE token_hash = ?').bind(auth.tokenHash).run();
  return json({ ok: true });
}

async function me(request, env) {
  const auth = await authenticate(request, env);
  if (!auth) return json({ error: 'unauthorized' }, 401);
  const user = await env.DB.prepare('SELECT id, email, created_at FROM users WHERE id = ?')
    .bind(auth.userId).first();
  if (!user) return json({ error: 'unauthorized' }, 401);
  return json({ userId: user.id, email: user.email, createdAt: user.created_at });
}

async function getDataset(request, env) {
  const auth = await authenticate(request, env);
  if (!auth) return json({ error: 'unauthorized' }, 401);
  const row = await env.DB.prepare(
    'SELECT rev, updated_at, payload FROM datasets WHERE user_id = ?',
  ).bind(auth.userId).first();
  if (!row) return json({ rev: 0, updatedAt: null, payload: null });
  return json({ rev: row.rev, updatedAt: row.updated_at, payload: row.payload });
}

async function putDataset(request, env) {
  const auth = await authenticate(request, env);
  if (!auth) return json({ error: 'unauthorized' }, 401);

  const body = await readJson(request);
  const payload = typeof body.payload === 'string' ? body.payload : JSON.stringify(body.payload ?? null);
  if (!payload || payload.length > MAX_PAYLOAD_BYTES) return json({ error: 'payload_too_large' }, 413);

  const baseRev = Number(body.baseRev ?? 0);
  const now = new Date().toISOString();
  const current = await env.DB.prepare('SELECT rev, updated_at, payload FROM datasets WHERE user_id = ?')
    .bind(auth.userId).first();

  const currentRev = current ? current.rev : 0;
  if (baseRev !== currentRev) {
    // 别的设备已经写过：把最新快照带回去，让客户端合并后重试
    return json({
      conflict: true,
      rev: currentRev,
      updatedAt: current ? current.updated_at : null,
      payload: current ? current.payload : null,
    }, 409);
  }

  const nextRev = currentRev + 1;
  if (current) {
    await env.DB.prepare(
      'UPDATE datasets SET rev = ?, updated_at = ?, payload = ? WHERE user_id = ?',
    ).bind(nextRev, now, payload, auth.userId).run();
  } else {
    await env.DB.prepare(
      'INSERT INTO datasets (user_id, rev, updated_at, payload) VALUES (?, ?, ?, ?)',
    ).bind(auth.userId, nextRev, now, payload).run();
  }
  return json({ rev: nextRev, updatedAt: now });
}

async function changePassword(request, env) {
  const auth = await authenticate(request, env);
  if (!auth) return json({ error: 'unauthorized' }, 401);
  const body = await readJson(request);
  const oldPassword = String(body.oldPassword || '');
  const newPassword = String(body.newPassword || '');
  if (newPassword.length < 8) return json({ error: 'weak_password' }, 400);

  const user = await env.DB.prepare(
    'SELECT pass_hash, pass_salt, iterations FROM users WHERE id = ?',
  ).bind(auth.userId).first();
  if (!user) return json({ error: 'unauthorized' }, 401);
  const oldHash = await hashPassword(oldPassword, user.pass_salt, user.iterations);
  if (!timingSafeEqual(oldHash, user.pass_hash)) return json({ error: 'invalid_credentials' }, 401);

  const salt = randomHex(16);
  const hash = await hashPassword(newPassword, salt);
  await env.DB.prepare('UPDATE users SET pass_hash = ?, pass_salt = ?, iterations = ? WHERE id = ?')
    .bind(hash, salt, PBKDF2_ITERATIONS, auth.userId).run();
  // 踢掉其它设备的令牌，只留当前这台
  await env.DB.prepare('DELETE FROM tokens WHERE user_id = ? AND token_hash != ?')
    .bind(auth.userId, auth.tokenHash).run();
  return json({ ok: true });
}

async function deleteAccount(request, env) {
  const auth = await authenticate(request, env);
  if (!auth) return json({ error: 'unauthorized' }, 401);
  const body = await readJson(request);
  const password = String(body.password || '');

  const user = await env.DB.prepare('SELECT pass_hash, pass_salt, iterations FROM users WHERE id = ?')
    .bind(auth.userId).first();
  if (!user) return json({ error: 'unauthorized' }, 401);
  const hash = await hashPassword(password, user.pass_salt, user.iterations);
  if (!timingSafeEqual(hash, user.pass_hash)) return json({ error: 'invalid_credentials' }, 401);

  await env.DB.prepare('DELETE FROM datasets WHERE user_id = ?').bind(auth.userId).run();
  await env.DB.prepare('DELETE FROM tokens WHERE user_id = ?').bind(auth.userId).run();
  await env.DB.prepare('DELETE FROM users WHERE id = ?').bind(auth.userId).run();
  return json({ ok: true });
}

// ---------------------------------------------------------------- 更新镜像

/**
 * 国内镜像：把 GitHub Releases 的最新版本信息转发出来，并把 APK 直链改写成
 * 本 Worker 的下载地址。GitHub 在国内经常连不上，走这里更稳。
 * 只代理本仓库的 release 资源，避免变成开放代理。
 */
async function latestRelease(url) {
  const upstream = await fetch(`https://api.github.com/repos/${REPO}/releases/latest`, {
    headers: { 'user-agent': 'budget-meow-mirror', accept: 'application/vnd.github+json' },
    cf: { cacheEverything: true, cacheTtl: 300 },
  });
  if (!upstream.ok) return json({ error: 'github_unavailable', status: upstream.status }, 502);

  const data = await upstream.json();
  const assets = (data.assets || []).map((asset) => ({
    name: asset.name,
    size: asset.size,
    browser_download_url:
      `${url.origin}/v1/release/download?url=${encodeURIComponent(asset.browser_download_url)}`,
  }));

  return json({
    tag_name: data.tag_name,
    html_url: `https://github.com/${REPO}/releases/tag/${data.tag_name}`,
    body: data.body || '',
    assets,
    mirrored: true,
  });
}

async function downloadAsset(url) {
  const target = url.searchParams.get('url') || '';
  if (!new RegExp(`^https://github\\.com/${REPO}/releases/download/`).test(target)) {
    return json({ error: 'bad_url' }, 400);
  }

  const upstream = await fetch(target, {
    headers: { 'user-agent': 'budget-meow-mirror' },
    redirect: 'follow',
    cf: { cacheEverything: true, cacheTtl: 86400 },
  });
  if (!upstream.ok) return json({ error: 'upstream', status: upstream.status }, 502);

  return new Response(upstream.body, {
    status: 200,
    headers: {
      'content-type': 'application/vnd.android.package-archive',
      'content-length': upstream.headers.get('content-length') || '',
      'cache-control': 'public, max-age=86400',
      ...corsHeaders(),
    },
  });
}

// ---------------------------------------------------------------- helpers

async function authenticate(request, env) {
  const header = request.headers.get('authorization') || '';
  const match = header.match(/^Bearer\s+(.+)$/i);
  if (!match) return null;
  const tokenHash = await sha256Hex(match[1].trim());
  const row = await env.DB.prepare('SELECT user_id, expires_at FROM tokens WHERE token_hash = ?')
    .bind(tokenHash).first();
  if (!row) return null;
  if (row.expires_at && new Date(row.expires_at).getTime() < Date.now()) {
    await env.DB.prepare('DELETE FROM tokens WHERE token_hash = ?').bind(tokenHash).run();
    return null;
  }
  await env.DB.prepare('UPDATE tokens SET last_used_at = ? WHERE token_hash = ?')
    .bind(new Date().toISOString(), tokenHash).run();
  return { userId: row.user_id, tokenHash };
}

async function issueToken(env, userId, now) {
  const raw = randomHex(32);
  const tokenHash = await sha256Hex(raw);
  const expires = new Date(Date.now() + TOKEN_TTL_DAYS * 24 * 3600 * 1000).toISOString();
  await env.DB.prepare(
    'INSERT INTO tokens (token_hash, user_id, created_at, expires_at, last_used_at) VALUES (?, ?, ?, ?, ?)',
  ).bind(tokenHash, userId, now, expires, now).run();
  return raw;
}

async function hashPassword(password, saltHex, iterations = PBKDF2_ITERATIONS) {
  const enc = new TextEncoder();
  const key = await crypto.subtle.importKey('raw', enc.encode(password), 'PBKDF2', false, ['deriveBits']);
  const bits = await crypto.subtle.deriveBits(
    { name: 'PBKDF2', salt: hexToBytes(saltHex), iterations, hash: 'SHA-256' },
    key,
    256,
  );
  return bytesToHex(new Uint8Array(bits));
}

async function sha256Hex(text) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text));
  return bytesToHex(new Uint8Array(digest));
}

function timingSafeEqual(a, b) {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

function randomHex(bytes) {
  const buf = new Uint8Array(bytes);
  crypto.getRandomValues(buf);
  return bytesToHex(buf);
}

function bytesToHex(bytes) {
  return Array.from(bytes).map((b) => b.toString(16).padStart(2, '0')).join('');
}

function hexToBytes(hex) {
  const out = new Uint8Array(hex.length / 2);
  for (let i = 0; i < out.length; i++) out[i] = parseInt(hex.substr(i * 2, 2), 16);
  return out;
}

function normalizeEmail(value) {
  return String(value || '').trim().toLowerCase();
}

async function readJson(request) {
  try {
    return await request.json();
  } catch {
    return {};
  }
}

function corsHeaders() {
  return {
    'access-control-allow-origin': '*',
    'access-control-allow-headers': 'authorization,content-type',
    'access-control-allow-methods': 'GET,POST,PUT,DELETE,OPTIONS',
  };
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8', ...corsHeaders() },
  });
}
