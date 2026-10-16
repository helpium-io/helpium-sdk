# Helpium customer chat API: contract for the mobile SDKs

Every SDK (iOS, Android, and through them React Native and Flutter) implements this contract. It is taken from the Helpium code, not from its docs. Where the two disagree, the code wins.

## Transport

- Base URL: `{host}/api/customer`. Production host: `https://helpium.io`.
- Auth: header `X-API-Key: <widget_key>`. Never `?api_key=` (rejected with 400).
- JSON in, JSON out. Images go as bare base64 inside JSON (no `data:` prefix, ≤ 5 MB decoded, `image/jpeg|jpg|png|gif|webp`).
- Unknown top-level body/query keys → `400 bad_request`. Send only the fields listed here.
- IDs (`conversation.id`, `message.id`, ...) are **integers**. The only UUID is `widget_token`, which the client generates (v4).
- Do not send `source_url` with a custom scheme (it is validated as a URL); omit it.

### Errors

```json
{ "error": { "message": "...", "status": 404, "code": "not_found" } }
```
Validation (422) also has `errors: { field: [msg] }`.

| Status | code | When |
|---|---|---|
| 400 | `bad_request` | Unknown parameter |
| 400 | `conversation_closed` | Close an already closed conversation |
| 401 | `api_key_required` / `invalid_api_key` | |
| 402 | `subscription_inactive` | Workspace has no active plan: hide the chat entry point |
| 404 | `not_found` | Unknown token or message |
| 422 | `invalid_input` | Validation |
| 422 | `conversation_closed` | Send to a closed conversation |
| 422 | `ratings_disabled` / `already_rated` | Rate |
| 422 | `invalid_message` | Feedback/retry on an ineligible message |
| 429 | `demo_*` | Demo workspaces only; show `error.message` verbatim |

### Enums

- `conversation.status`: `open | pending | snoozed | closed` (only `closed` is terminal; resume `snoozed`).
- `conversation.closed_reason`: `agent | customer | inactivity | imported | unrelated | rule | automated | null`.
- `message.role` (REST): `user` (customer) `| assistant` (bot) `| agent` (human) `| system`.
- `sender_type` (WebSocket): `customer | user | bot | system`, mapping to roles user/agent/assistant/system respectively (`user` → agent!).
- `human_handoff_mode`: `disabled | on_request | automatic`. `ai_reply_mode`: `auto | draft`.

## Endpoints

### `GET /widget/settings?lang=<locale>`
`200 { "data": { ... } }`:

| Field | Type | Notes |
|---|---|---|
| `enable_chat`, `enable_bot`, `enable_search` | bool | |
| `require_customer_details` | bool | Ask name + email before the first message when the customer is not identified |
| `collect_marketing_opt_in` | bool | Optional checkbox on that form |
| `human_handoff_mode` | enum | |
| `ai_reply_mode` | enum | In `draft` never show the bot typing indicator |
| `bot_name` | string | Use this, never the WebSocket `sender_name` of a bot |
| `bot_avatar_url` | string? | |
| `primary_color` | `#rrggbb` | |
| `is_online` | bool | |
| `offline_message` | string? | |
| `conversation_ratings_enabled` | bool | |
| `ai_available` | bool | false once the AI message limit is reached |
| `help_center_url` | string? | |
| `websocket` | `{host, port:int, scheme:"http"\|"https", app_key, use_api_host:bool}` | `use_api_host` is local-dev only: connect to the API host instead |
| `instant_answers` | `[{id:int, label, is_shortcut:bool}]` | Non-shortcut: send `label` with `instant_answer_id`. Shortcut: send `label` as a plain message |

Greeting, welcome title/subtitle and every other UI text are client side (SDK strings, en/es, overridable by the host).

### `POST /conversations` → `201 { "data": Conversation }`
Creates the conversation with the first message. Body:

| Field | Required | Notes |
|---|---|---|
| `message` | unless `image` | ≤ 10000 |
| `image` | | `{data, name, type}` |
| `widget_token` | yes (SDK) | The UUID the SDK generated and stores |
| `language` | | Only when the host set a locale explicitly |
| `instant_answer_id` | | |
| `customer` | | `{email?, name?, first_name?, last_name?, marketing_opt_in?, metadata?, userJwt?}` (camelCase `userJwt`) |

`data.messages` holds only the customer message; replies arrive over the socket.

### `GET /conversations/{token}` → `200 { "data": Conversation | null }`
Never 404: `data: null` means the token is gone (forget it).

### `POST /conversations/{token}/messages` → `201 { "data": [Message] }`
Body: `content` (unless `image`), `image`, `language`, `metadata`, `instant_answer_id`. A closed conversation → 422 `conversation_closed`.

### `GET /conversations/{token}/messages?since=<ISO>` → `200 { "data": [Message] }`
`since` is strict (`created_at > since`). Without `since` returns all.

### `POST /conversations/{token}/request-human` → `200 { "data": Conversation }` (no `messages`)
No body. Idempotent. No-op when handoff is disabled. A system message follows over the socket.

### `POST /conversations/{token}/close` → `200 { "data": Conversation }`
No body. Already closed → 400.

### `POST /conversations/{token}/rate` → `204`
`{rating: 1...5, comment?: string ≤ 1000}`. UI: 5 faces (Poor, Okay, Good, Great, Amazing); ≤ 2 asks for a comment first.

### `POST /conversations/{token}/messages/{id}/feedback` → `204`
`{feedback: "up" | "down"}`. Bot messages only.

### `POST /conversations/{token}/messages/{id}/retry` → `204`
For a bot message with `interactive.type == "retry"`. Clear the button locally and show typing.

### `POST /conversations/{token}/messages/status` → `200 {"updated": n}`
`{status: "delivered" | "seen", message_ids: [int] (1...100)}`. Batch with a short debounce. Delivered as soon as the SDK learns of an agent/bot message; seen when it is on screen.

### `POST /tracking/identify` → `200 { "data": {success, verified, id, active_conversation} }`
Body: `email` (**required**), `name?`, `first_name?`, `last_name?`, `marketing_opt_in?`, `metadata?`, `widget_token?`, `user_jwt?` (snake_case here). When verified, `active_conversation` is the customer's open/pending conversation (Conversation + `widget_token`) or null: adopt its token, so the conversation follows the user across devices.

### `POST /broadcasting/auth` → `200 {"auth": "..."}`
`{socket_id, channel_name}`; only `private-conversation.{id}.typing`.

### `POST /devices` → `201 {"id"}` / `DELETE /devices` → `204`
POST `{widget_token, push_token, platform: ios|android, app_id?, sdk_version?}` once a conversation exists (and again whenever the token or conversation changes). DELETE `{push_token}` on logout.

## Shapes

### Conversation
`{id:int, subject?, status, assigned_agent: {id, name, avatar_url?}?, created_at, updated_at, closed_reason?, rated:bool, customer_identified:bool, messages?: [Message]}` (`messages` key absent when not loaded).

### Message (REST)
`{id:int, content:string ("" for image only), role, created_at, delivered_at?, seen_at?, ai_generated:bool, ai_confidence?, should_handoff:bool, customer_feedback?: "up"|"down", ai_sources:[{id, title, url, type}], sender_name?, sender_avatar_url?, attachments: [{name, url, type, transcript?}] | null, interactive: object | null}`

### `interactive`
| type | Shape | Customer action |
|---|---|---|
| `retry` | `{type}` | `POST .../retry` |
| `confirm` | `{type, description?}` | Send the localized "Accept" / "Cancel" as a normal message. Latest message only, not while typing |
| `suggestions` | `{type, options:[string]}` | Send the option as a message. Latest message only |
| others | | Ignore |

## Realtime (Pusher protocol, Laravel Reverb)

- URL: `ws(s)://{host}:{port}/app/{app_key}?protocol=7&client=...&version=...`, TLS when `scheme == "https"`.
- Answer `pusher:ping` with `pusher:pong`. Resubscribe after reconnect, then reconcile with `GET .../messages` (no `since`) and merge by id.
- Channels: public `conversation.{id}`; private `private-conversation.{id}.typing` (auth via `/broadcasting/auth`).
- Event names are the PHP class names: `App\Events\NewMessage`, `App\Events\ConversationUpdated`, `App\Events\MessageStatusUpdated`, `App\Events\BotTyping`, `App\Events\MessageInteractiveUpdated`. Whisper: `client-typing`.

Payloads:
- **NewMessage**: `{id, conversation_id, sender_type, sender_name, sender_avatar_url?, system_event?, content, content_truncated:bool, is_internal:bool, attachments?, created_at, delivered_at?, seen_at?, ai_generated, ai_confidence?, should_handoff, ai_sources, interactive?}`. Drop `is_internal`. When `content_truncated`, refetch the messages. A bot or agent message clears the typing indicator. The customer's own message comes back too: replace the optimistic one.
- **ConversationUpdated**: `{id, update_type, status, assigned_user: {id, name, avatar_url?}?, reason?, updated_at}`. `status == "pending"` → handoff requested; `"closed"` → closed (`reason == "inactivity"` for auto-close).
- **MessageStatusUpdated**: `{id, conversation_id, delivered_at?, seen_at?}`.
- **BotTyping**: `{conversation_id, is_typing, status?}`. Never sent with `false`: clear on the next bot/agent message. Ignore while an agent is assigned.
- **MessageInteractiveUpdated**: `{id, conversation_id, interactive?}`.
- **client-typing**: `{sender_type: "customer"|"agent", sender_name?, is_typing}`. Show agent typing only for `agent`, and only while the bot is not typing. Send `true` at most every 2 s and `false` after 3 s idle.

## Behavior the SDKs replicate

1. Load settings; on 402 do not offer the chat.
2. Stored token → `GET /conversations/{token}`: null → forget; closed and waiting for a rating (`!rated && conversation_ratings_enabled`) → rating screen; closed otherwise → forget; open/pending/snoozed → resume.
3. No conversation exists until the first message (`POST /conversations` with a fresh `widget_token`).
4. Optimistic customer message, replaced by the server copy.
5. Bot typing shown optimistically after sending only when no agent is assigned, no handoff was requested, `enable_bot`, `ai_available` and `ai_reply_mode != draft`.
6. "Talk to a human" under the last bot message when it has `should_handoff`, handoff not yet requested and mode is not `disabled`.
7. Closing: `POST close`, then the rating screen when ratings are on. After rating or skipping, forget the token.
8. Unread count: agent/bot messages without `seen_at`.
9. Push: register the device after the conversation exists. A push payload carries `helpium: "1"`, `conversation_token`, `message_id`.
