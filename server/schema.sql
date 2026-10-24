-- 预算喵同步服务 schema（Cloudflare D1 / SQLite）
CREATE TABLE IF NOT EXISTS users (
  id          TEXT PRIMARY KEY,
  email       TEXT NOT NULL UNIQUE,
  pass_hash   TEXT NOT NULL,
  pass_salt   TEXT NOT NULL,
  iterations  INTEGER NOT NULL DEFAULT 100000,
  created_at  TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS tokens (
  token_hash    TEXT PRIMARY KEY,
  user_id       TEXT NOT NULL,
  created_at    TEXT NOT NULL,
  expires_at    TEXT NOT NULL,
  last_used_at  TEXT
);

CREATE INDEX IF NOT EXISTS idx_tokens_user ON tokens(user_id);

CREATE TABLE IF NOT EXISTS datasets (
  user_id     TEXT PRIMARY KEY,
  rev         INTEGER NOT NULL DEFAULT 1,
  updated_at  TEXT NOT NULL,
  payload     TEXT NOT NULL
);
