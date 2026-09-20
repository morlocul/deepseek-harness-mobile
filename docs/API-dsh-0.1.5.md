# API-ul dsh 0.1.5-rc.2 — hartă pentru aplicația Android

Descoperit pe 20 septembrie 2026, prin proxy de logare între browser și server
(`127.0.0.1:3082 → 127.0.0.1:3080`), capturând cererile reale ale interfeței web.

Aplicația (APK din 31 august) fusese scrisă pe dsh `0.1.2-alpha.3`. Între timp
s-au schimbat **trei lucruri**, toate mecanice:

| | vechi | nou |
|---|---|---|
| separator metode | `session.list` | `session/list` |
| payload | `"payload":{…}` | `"payload":{"args":{…}}` |
| autentificare | niciuna | cookie, obținut cu `GET /?token=…` |

Plicul răspunsului e **neschimbat**:
`{"type":"server-response","rpcId":…,"result":{"ok":true,"value":…}}`
sau `{"ok":false,"error":{"code":…,"message":…}}`.

**Gardul de încredere verifică și antetul `Origin`**, nu doar cookie-ul. Un client
non-browser (OkHttp) nu trimite `Origin`, deci nu e afectat; un proxy trebuie să-l
rescrie.

Erorile sunt auto-descriptive și numesc câmpul lipsă — folosește-le ca introspecție:
`typert gateway: session/list: args fields do not match the descriptor: missing "_request"`.

---

## 1. Autentificare

```
GET /?token=<TOKEN>          → 303 + Set-Cookie: dsh-auth-<hash>=v1.<payload>.<sig>
                               Max-Age 2592000 (30 zile), HttpOnly, SameSite=Strict
```

Payload-ul cookie-ului conține `{"version":1,"authority":"<host:port>","issuedAt","expiresAt"}`.
Cookie-ul e **legat de autoritate** — unul emis pentru `127.0.0.1:3080` nu e valabil
pentru `100.x.y.z:3080`. Autoritățile suplimentare se declară la pornire cu
`--trusted-host`.

Tokenul se schimbă la **fiecare pornire** a serverului.

## 2. REST — `POST /api/<endpoint>`

Corp: `{"type":"client-request","rpcId":"<uuid>","method":"<endpoint>","payload":{"args":{…}}}`

| endpoint | args | înlocuiește |
|---|---|---|
| `settings/describe` | `{}` | `host.describe` |
| `session/list` | `{"_request":{}}` | `session.list` |
| `session/create` | `{"request":{"workspaceId":"<uuid>"}}` | `session.create` |
| `session/modelCatalog` | `{}` | `session.models` |
| `session/prompt` | vezi mai jos | `session.prompt` |
| `session/selectModel` | `{"request":{…}}` *(formă neconfirmată)* | `session.selectModel` |
| `skills/list` | `{"request":{"sessionId":"<id>"}}` | — |
| `commands/list` | `{"agentId":"<sessionId>"}` | — |
| `subagents/list` | `{"parentSessionId":"<id>"}` | — |
| `agentPresets/list` | `{}` | — |
| `credentials/describe` | `{"refs":["DEEPSEEK_API_KEY"]}` | — |
| `llm/listProviders` | `{}` | — |
| `llm/listConfigurableProviders` | `{}` | — |

### `session/prompt` — forma exactă, capturată

```json
{"args":{"request":{
  "requestId":       "<uuid>",
  "sessionId":       "session-<uuid>",
  "mode":            "queue",
  "content":         [{"type":"text","text":"salut"}],
  "clientTimeZone":  "<IANA timezone>"
}}}
```

Răspunsul NU vine pe HTTP — vine pe fluxul `session/follow` (vezi mai jos).

## 3. WebSocket multiplexat — `/api/remote.mux`

O singură conexiune, fluxuri separate prin `streamId`.

**Deschidere flux (client → server):**
```json
{"type":"open","streamId":"<uuid>","endpoint":"<nume>","payload":{"args":{…}}}
```

**Cadru de date (server → client):**
```json
{"type":"item","streamId":"<același uuid>","value":{…}}
```

### Fluxuri

| endpoint | args | ce livrează |
|---|---|---|
| `$events` | `{}` | `{"type":"ready","clientId","host":{"home"}}`, apoi `{"type":"emit","event":"api-session/added","args":[…]}` |
| `workspace/follow` | `{}` | `{"type":"baseline","value":{"items":[{"workspaceId","path","title","sessionIds"}]}}`, apoi `{"type":"upsert","workspace":{…}}` — înlocuiește `workspace.list` |
| `session/control` | `{}` | `{"type":"projection","sessionId","key":"permissions","value":{…}}` |
| `session/follow` | `{"request":{"address":{"kind":"session","sessionId":"<id>"},"maxMessages":50,"assistantStream":true}}` | istoricul **și** fluxul live — înlocuiește `session.history` + `events.mux` |

### Ce vine pe `session/follow`

Două forme de `value`:

- `{"type":"event","event":{"type":"user/message"|"session/title"|"agent/inbox/spliced"|…,"seq","time","data":{…},"surfaceOp":"append"}}`
- `{"type":"projection","sessionId","key":"inbox"|"title"|"turnOutline"|"permissions","value":…,"seq"}`

`turnOutline` e cel mai util pentru un client simplu: `[{"turn":1,"seq":4,"prompt":"salut","response":"Salut! 👋 …"}]`
— se actualizează incremental pe măsură ce modelul scrie.

## 4. Rămase nemapate

- `host.listDirectory` — niciun echivalent găsit (`files/list`, `fs/list`, `host/fs`, `directory/list` → 404)
- `session.attachment` — necăutat
- `session/selectModel` — endpoint-ul există, forma lui `request` neconfirmată

## 5. Alte rute observate

`GET /api/present.host`, `POST /api/present.open`, `POST /api/session.export`,
`POST /api/file`, `POST /api/dynamicCordisRunner/{inventory,syncInspectManifest}`.

Sesiunile se scriu acum ca `session.v3.jsonl.zstd` — format nou; o versiune veche
de dsh nu le poate citi în siguranță.
