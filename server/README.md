# 同步服务（Cloudflare Worker + D1）

预算喵的账号与跨设备同步后端。只有一个 Worker、一张表、七个接口，免费额度足够几十人用。

## 为什么是 Cloudflare

- Workers 免费 10 万请求/天，D1 免费 5GB 存储 + 每天 500 万行读 / 10 万行写
- 不会像某些免费数据库那样「N 天无活动就暂停」
- 一个文件就能部署，没有容器、没有冷启动焦虑

## 部署

```bash
npm i -g wrangler
wrangler login

cd server
wrangler d1 create budget-meow
# 把输出的 database_id 填进 wrangler.toml

wrangler d1 execute budget-meow --file schema.sql --remote
wrangler deploy
```

部署完会得到 `https://budget-meow.<你的子域>.workers.dev`，把它填到客户端的
`app/src/main/kotlin/io/github/world303/budgetmeow/core/sync/MeowApi.kt` 的 `DEFAULT_API_BASE`。

如果 workers.dev 打不开（部分地区），可以在自己的域名上加一条路由：
`wrangler.toml` 里加

```toml
routes = [{ pattern = "meow.example.com", custom_domain = true }]
```

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/health` | - | 存活检查 |
| POST | `/v1/register` | - | `{email, password}` → `{token, userId, email}` |
| POST | `/v1/login` | - | `{email, password}` → `{token, userId, email}` |
| POST | `/v1/logout` | Bearer | 删除当前令牌 |
| GET | `/v1/me` | Bearer | 账号信息 |
| GET | `/v1/dataset` | Bearer | `{rev, updatedAt, payload}` |
| PUT | `/v1/dataset` | Bearer | `{baseRev, payload}`；rev 不匹配返回 **409 + 最新快照** |
| POST | `/v1/password` | Bearer | `{oldPassword, newPassword}`，踢掉其它设备令牌 |
| DELETE | `/v1/account` | Bearer | `{password}`，删账号与云端账目 |

### 数据模型

```sql
users(id, email, pass_hash, pass_salt, iterations, created_at)
tokens(token_hash, user_id, created_at, expires_at, last_used_at)
datasets(user_id, rev, updated_at, payload)
```

`payload` 是客户端整份账目的 JSON 字符串（含墓碑），服务端不解析内容，只做版本仲裁。

### 安全

- 密码：PBKDF2-SHA256，16 字节随机盐，10 万次迭代（Workers WebCrypto 的上限）
- 令牌：32 字节随机值，库里只存 SHA-256，180 天过期
- 登录失败不区分「邮箱不存在」与「密码错误」，避免枚举账号
- 改密码会清掉除当前设备外的所有令牌
- 全站 HTTPS；服务端不记录请求体，也没有第三方分析

## 本地开发

```bash
wrangler dev --local     # 本地 D1，不碰线上数据
```

## 数据导出 / 删除

- 客户端「设置 → 备份 → 备份数据」导出 JSON
- 客户端「账号 → 注销云端账号」会删除账号、令牌与云端账目
- 也可以直接 `wrangler d1 execute budget-meow --command "DELETE FROM users WHERE email='...'" --remote`
