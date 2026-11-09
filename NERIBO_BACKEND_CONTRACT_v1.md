# NERIBO BACKEND CONTRACT v1

Project: **Neribo Group Limited** (eu-west-2, Postgres 17). Project URL: `https://bpyoruauewlzmutqhkan.supabase.co`
Anon key: dashboard → Project Settings → API. Never use the service_role key in the app.

## How to call (app, as the logged-in owner)
`POST {URL}/rest/v1/rpc/<name>` with headers `apikey: <anon key>`, `Authorization: Bearer <user JWT>`, `Content-Type: application/json`. Body = JSON object of **named** parameters. Every function returns one JSON object with `status`.
No JWT / anon key only → HTTP error (permission denied). A JWT without a user → raises `not_authenticated` (SQLSTATE 28000).

## Limits
- ≤ 200 changes per push; each payload ≤ 12,000,000 bytes (UTF-8); total per push ≤ 40,000,000 bytes.
- doc_key: `^[a-z0-9:_.-]{1,100}$` (lowercase only). device_id: 1..100 chars.
- publish_roll_jobs: ≤ 100 jobs per call; context and result_schema ≤ 200,000 bytes each.
- Agent result ≤ 60,000 bytes. Payload must be valid JSON and must not contain `\u0000`.

## Common error shape
`{"status":"error","code":"<code>", ...extra}`
Error codes: `bad_request, bad_mode, bad_doc_key, duplicate_doc_key, too_many_changes, payload_too_large, push_too_large, invalid_json, checksum_mismatch, not_initialized, already_initialized, cloud_has_data, replace_all_incomplete, cursor_ahead, version_not_found, doc_not_found, confirm_required, backup_not_found, backup_corrupt, bad_reason, too_many_jobs, bad_job, bad_status_filter, job_not_found, bad_status, invalid_value, user_not_found, not_claimed, lease_expired, already_submitted`. Conflict is `{"status":"conflict",...}`.
Only `status:"ok"` results are stored for replay. Errors and conflicts change nothing, so the same `p_op_id` may be retried after fixing the cause.

## App-facing functions (role authenticated)
| Function | Parameters (default) |
|---|---|
| `ensure_user_setup()` | none → `{status:'ok', initialized, last_seq}` |
| `server_now()` | none → `{status:'ok', now}` |
| `sync_status()` | none → `{status:'ok', initialized, initialized_at, last_seq, doc_count, deleted_count, total_bytes, server_time}` |
| `pull_docs` | `p_since_seq bigint (0), p_after_key text (''), p_limit int (40, clamped 1..200), p_max_bytes int (6000000, clamped 1..20000000)` |
| `push_docs` | `p_op_id uuid, p_device_id text, p_label text, p_app_version text, p_changes jsonb, p_mode text ('normal')` |
| `verify_docs` | `p_expect jsonb` |
| `list_doc_history` | `p_doc_key text, p_limit int (50, max 500)` |
| `get_doc_version` | `p_doc_key text, p_version bigint` |
| `restore_doc_version` | `p_op_id uuid, p_device_id text, p_doc_key text, p_version bigint` |
| `create_backup` | `p_reason text ('manual'), p_note text (null)` |
| `list_backups` | `p_limit int (30, max 200)` |
| `restore_backup` | `p_op_id uuid, p_device_id text, p_backup_id bigint, p_confirm text` (must be exactly `RESTORE`) |
| `publish_roll_jobs` | `p_op_id uuid, p_device_id text, p_jobs jsonb` |
| `list_jobs` | `p_statuses text[] (['submitted']), p_limit int (20, max 100)` |
| `mark_job_applied` | `p_op_id uuid, p_device_id text, p_job_id uuid` |
| `reject_job` | `p_op_id uuid, p_device_id text, p_job_id uuid, p_reason text` |
| `get_ai_settings()` | none |
| `update_ai_settings` | `p_paused bool, p_min_gap_minutes int (1..1440), p_max_jobs_per_day int (1..500), p_lease_minutes int (1..240), p_allowed_email_domains text[]` (all null = unchanged) |

### push_docs
`p_changes` = array of `{doc_key, base_version (integer >= 0), payload (string = exact JSON text), payload_sha256 (lowercase hex SHA-256 of the UTF-8 bytes of payload), deleted (bool, default false)}`.
`p_mode`: `normal` (only after initialized), `initial` (only before initialized and only if the cloud has no docs), `replace_all` (after initialized; the change list must contain every live doc key, use `deleted:true` for ones to drop).
Success: `{"status":"ok","seq":2,"results":[{"doc_key":"company","version":2,"payload_sha256":"…","outcome":"created|updated|unchanged"}],"replayed":false}`
Replay of same op_id: identical result with `"replayed":true`.
Conflict (nothing written): `{"status":"conflict","conflicts":[{"doc_key":"cases","base_version":99,"server_version":1,"server_sha256":"…"}],"server_last_seq":2}`. New doc requires `base_version:0` (`server_version:0` in a conflict).
Errors: `checksum_mismatch{doc_key}, invalid_json{doc_key}, payload_too_large{doc_key,size_bytes,max}, too_many_changes{max,got}, bad_doc_key{doc_key}, duplicate_doc_key, not_initialized, already_initialized, cloud_has_data, replace_all_incomplete{missing_doc_keys}, bad_mode, bad_request{detail}`.
`seq` rises by 1 per push that has at least one real change; unchanged-only pushes keep the old seq. `initial` with zero changes still marks initialized.

### pull_docs and the paging rule (word for word)
Result: `{"status":"ok","docs":[{doc_key,version,seq,payload,payload_sha256,deleted,updated_at}],"next_cursor":{"seq":n,"doc_key":"k"},"has_more":bool,"server_last_seq":n}`.
Paging rule: the app starts with `(p_since_seq = its saved last_seq, p_after_key = '')`. While `has_more = true` it calls again with `p_since_seq = next_cursor.seq` and `p_after_key = next_cursor.doc_key`. When `has_more = false` it saves `next_cursor.seq` as its new `last_seq` (this equals `server_last_seq` when fully caught up) and starts the next pull with `p_after_key = ''`. No document is skipped or returned twice. No offset is used.
Server semantics: with `p_after_key = ''` the server returns docs with `seq > p_since_seq` (a whole seq is treated as finished); with a non-empty key it returns docs with `(seq, doc_key) > (p_since_seq, p_after_key)`, ordered by `seq, doc_key`. A page stops at `p_limit` docs or before exceeding `p_max_bytes` (always at least one doc). When `has_more=false`, `next_cursor = {seq: server_last_seq, doc_key: ''}`. The pull is capped at the `last_seq` read at the start of the call, so a concurrent push can never be skipped.
Extra error: `cursor_ahead{server_last_seq}` when `p_since_seq` is larger than the server's `last_seq` (cloud was reset or restored; the app must not treat itself as caught up).
Tombstones come back with `deleted:true`.

### verify_docs
Input items `{doc_key, version, payload_sha256}`. Output `{"status":"ok","all_match":bool,"mismatches":[{doc_key,reason:'missing|version|hash',server_version,server_sha256}],"extra_on_server":[doc_key…]}`. `all_match` is true when there are no mismatches; `extra_on_server` lists live docs not in the input and does not affect `all_match`.

### History and backups
- `list_doc_history` → `{status,doc_key,versions:[{version,seq,payload_sha256,size_bytes,deleted,device_id,created_at}]}` newest first.
- `get_doc_version` → `{status,doc_key,version,seq,payload,payload_sha256,size_bytes,deleted,created_at}`.
- `restore_doc_version` → `{status:'ok',doc_key,version,seq,outcome:'restored|unchanged',restored_from,payload_sha256,replayed}`. Creates a new version; makes a `pre_restore` backup first.
- `create_backup` → `{status:'ok',backup_id,doc_count,total_bytes}` (doc_count and total_bytes count live docs only; the stored backup also holds tombstones). Allowed `p_reason`: `manual, daily, pre_initial_upload, pre_restore, pre_replace_all`.
- `list_backups` → `{status,backups:[{id,created_at,reason,seq_at,doc_count,total_bytes,note}]}`.
- `restore_backup` → `{status:'ok',seq,restored,tombstoned,unchanged,pre_restore_backup_id,replayed}`. Docs not in the backup are tombstoned, never removed. Checks each stored payload hash first (`backup_corrupt`).

### Jobs (mailbox)
Job in `p_jobs`: `{dedupe_key (1..200), kind (^[A-Z][A-Z0-9_]{1,40}$), app_id?, day_key? (yyyy-MM-dd), priority? (100), sort_order? (0), context? (object), result_schema (object)}`.
`publish_roll_jobs` → `{status:'ok',inserted,existing,replayed}`; existing dedupe keys are never touched.
`list_jobs` → `{status:'ok',jobs:[{id,dedupe_key,kind,app_id,day_key,priority,sort_order,status,context,result_schema,result,attempts,last_errors,reject_reason,created_at,submitted_at,applied_at}]}` oldest first.
`mark_job_applied` → `{status:'ok',job_id,job_status:'applied',replayed}`; other than submitted/applied → `bad_status{job_status}`.
`reject_job` → `{status:'ok',job_id,job_status:'rejected',replayed}` (only from submitted). A rejected job is not republished (its dedupe_key stays used).
Job status values: `pending, claimed, submitted, applied, rejected, expired, cancelled`.
`get_ai_settings` / `update_ai_settings` → `{status:'ok',settings:{paused,min_gap_minutes,max_jobs_per_day,lease_minutes,max_attempts,next_allowed_at,last_job_at,jobs_today,allowed_email_domains}}`.

## Agent-facing functions (role service_role only; for Phase 4)
All take `p_user_id uuid` (the owner). Call with the service key from the Edge Function only.
- `agent_get_status(p_user_id)` → `{status:'ok',paused,next_allowed_at,jobs_today,pending_count,claimed_count,submitted_count}`.
- `agent_claim_next_job(p_user_id, p_agent text)` → `{status:'paused'}` | `{status:'wait',retry_after_seconds}` | `{status:'wait',reason:'daily_limit',retry_after_seconds}` | `{status:'empty'}` | `{status:'ok',job:{id,kind,app_id,day_key,context,result_schema,attempts},rulebook:{version,title,rules},limits:{min_gap_minutes,allowed_email_domains},lease_expires_at}`. A retry while the lease is valid returns the same job.
- `agent_submit_job_result(p_user_id, p_job_id, p_result jsonb)` → `{status:'ok',next_allowed_at}` | `{status:'ok',replayed:true,next_allowed_at}` | `{status:'invalid',errors:[…],attempts_left,job_status:'claimed|rejected'}` | errors `not_claimed, lease_expired, already_submitted, job_not_found`. After `max_attempts` (3) invalid answers the job is `rejected` (`too_many_invalid_results`).
- `agent_release_job(p_user_id, p_job_id, p_reason)` → `{status:'ok',replayed}`.
Global checks on results: every email-like string anywhere in the result must use a domain in `allowed_email_domains`; size ≤ 60,000 bytes.

## JSON-Schema subset (result_schema)
Supported: `type` (string or array of: object, array, string, integer, number, boolean, null), `enum`, `const`, `properties`, `required`, `additionalProperties` (boolean; default **false** when `properties` is present), `items` (single schema), `minItems`, `maxItems`, `minimum`, `maximum`, `multipleOf`, `minLength`, `maxLength`, `pattern` (Postgres regex), boolean schemas. Depth ≤ 12, errors ≤ 30. Error text looks like `$.fee: must be <= 5000000`.
Warnings: unknown keywords (title, format, description, oneOf, anyOf, allOf, $ref …) are ignored silently; `additionalProperties` as a schema object is ignored. A broken schema returns an error text and never raises.


## Addendum (update): SOFTWARE_ROLL, one software job a day

- Software Development Clients are now rolled ONLY by Claude, with ONE job a day:
  kind `SOFTWARE_ROLL`, app_id `software-clients`, dedupe_key `<day>:software-clients:SOFTWARE_ROLL:<attempt>` (attempt 1..3),
  priority 50, sort_order 0. The app publishes it with `publish_roll_jobs`. No server function changed.
- Claim order (unchanged): `agent_claim_next_app` orders pending jobs by priority, then sort_order, then created_at, with no day filter.
  Priority 50 therefore comes before dice and case jobs (100) and the older software jobs (110). SOFTWARE_ROLL is not a
  DICE_ROLL, so it is claimed alone, one job at a time (the single-job branch of `roll_next`), and answered with `submit_result`.
- Answer shape (result_schema is built by the app): `{ contactFace 0-5, offerFace 0-5, comment?, offers: [ { projectCode, kind,
  clientType, clientName, contactName, country, title, summary, requirements[5..7], deliveryWeeks 1-12, fee } ] }`.
  The app checks every field again; a rejected answer is asked again (up to 3 attempts a day). Contacts, ids and the payment,
  hosting, content and maintenance lines are made by the app.
- Rulebook v5 adds `job_kinds.SOFTWARE_ROLL` and `software_roll` (the writing rules). The older software job kinds
  (DICE_ROLL with SOFTWARE_CONTACTS / SOFTWARE_PROPOSALS, WRITE_PROPOSAL) are no longer published by the app.
- Adverts (Google Play apps) are local to each app document: no backend change.
