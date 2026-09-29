# Sync readiness

Taski has no network code and no Supabase SDK. It works fully without an
account, forever; an account will only ever be an addition. But every storage
decision is made now so that a later phase can add sync with Supabase
(Postgres + Auth + Realtime) **without a schema rewrite or a data migration**.

This document is the contract. Each rule names the code that enforces it.

## Identity and ownership

| Rule | Where |
| --- | --- |
| Every syncable row has a client-generated **UUIDv7** primary key (time-ordered), stored as text that maps 1:1 onto a Postgres `uuid`. | `core/domain/.../id/IdGenerator.kt` (`UuidV7Generator`) |
| No auto-increment keys anywhere, including join tables and local-only tables. | `SyncRegistryTest.nothingUsesAutoIncrementKeys` |
| Every syncable row has a nullable `user_id`. | `SyncColumns.kt`, `SyncRegistryTest` |
| On first sign-in, all local rows are claimed (their `user_id` set) and queued for push. | `LocalDataClaimer.kt`, `RepositoryTest.signingInClaimsEveryLocalRow` |

## Change tracking

Every synced row carries `created_at`, `updated_at`, `deleted_at` (tombstone),
`rev` and `server_updated_at` (always null locally; set by the server trigger
and used as the pull cursor). Instants are UTC epoch millis.

**`rev` is a hybrid logical clock** (`Hlc.kt`, `HlcClock.kt`): 15-digit wall
millis, a 5-digit counter and a 16-hex device id, e.g.
`001727623456789-00003-9f2c4a7be1d05c38`. The fixed width makes string order
equal to clock order, so ordering is total and never trusts a device's wall
clock alone: a device whose clock goes back keeps issuing increasing revisions
(the last one is persisted across restarts, `PersistentHlcClock`), and
receiving a remote revision moves the local clock past it. The device id is
excluded from backups (`data_extraction_rules.xml`) so two phones never share
one clock identity.

**Per-field clocks.** Rows whose fields are edited independently — `tasks`,
`projects`, `tags`, `saved_views` — also keep `field_revs`, a JSON object of
column → HLC. Repositories never list the changed columns by hand: they write
the row, and `ColumnSet.changed(old, new)` finds what differs, so a column
cannot be written without being stamped.

**The outbox.** `sync_outbox (id, entity, row_id, op [upsert|delete],
changed_fields, rev, created_at)` is written in the **same Room transaction**
as every change, by `ChangeWriter`, which is the only way repositories write
synced rows. The UI never touches it. It is compacted: a unique index on
`(entity, row_id)` folds later changes into the pending entry (newest op and
rev, union of changed fields). Nothing reads it yet except tests.

**Deletes are tombstones.** `deleted_at` is set, never a `DELETE`. Emptying
the trash sets `purged_at` and clears the title and notes (and the task's tag
links), but keeps the row, so a later sync still knows the task was deleted.
Restoring from the trash is an edit that clears `deleted_at` with a newer rev.

## Shapes that merge well

- **Manual order** uses fractional-index strings (`FractionalIndex.kt`, a port
  of rocicorp/fractional-indexing, base 62). Moving an item rewrites one key.
  Equal keys from two devices inserting into the same gap are ordered by id,
  and the next local move re-spreads them (`OrderPlanner.kt`). Keys compare
  bytewise: SQLite's default collation does, and Postgres declares
  `sort_key text COLLATE "C"`.
- **Many-to-many links** (`task_tags`, `task_dependencies`) are rows with their
  own id, rev and tombstone. A pair has one row for life: removing tombstones
  it, adding again revives it with a newer rev. Two devices that each create
  the same pair offline are merged onto the lower id (`mergeDuplicateLinks`).
- **Append-only records** — `task_completions`, `timer_sessions`,
  `activity_log` — have their own ids and are never edited, so syncing them is
  idempotent.
- **Derived data is never stored as syncable data**: subtask counts, progress
  from subtasks, blocked state and search results are computed
  (`DefaultTaskRepository.items`), and the search index is a local-only FTS
  table.
- **Enums** are stable text codes (`in_progress`, `highest`), never ordinals
  (`Coded`). **Dates** are ISO `YYYY-MM-DD` text, **times** `HH:MM`,
  **timestamps** UTC epoch millis (`DateCodes.kt`). **Repeat rules** and
  **saved-view definitions** are versioned JSON (`{"v":1,…}`); a version the app
  does not understand is left untouched, because only an edit to that field
  rewrites it.
- **Column names** are snake_case and identical in Room and Postgres
  (`SyncRegistryTest.columnNamesAreSnakeCase`, `SupabaseSchemaParityTest`).
- **No foreign keys between synced tables**: rows can arrive in any order (a
  subtask before its parent). Dangling references are resolved by the conflict
  rules, not rejected by the database. The only FK is `user_id → auth.users`.

## Local-only data (never synced)

Declared in one place, `SyncRegistry.kt`:

| Table | Holds |
| --- | --- |
| `sync_outbox` | Pending changes. |
| `reminder_state` | Reminder delivery bookkeeping, rebuilt from task data. |
| `timer_state` | The focus timer counting down on this device. |
| `task_fts` (+ shadow tables) | The full-text index. |

Device preferences — language, calendar, theme, density, last tab, board
layout, reminder schedule — live in DataStore, not in the database.

Alarms are **rebuilt from task data after any change** (`ReminderScheduler`
observes the database and the settings and recomputes every alarm from
`ReminderRules`), so a change that later arrives by sync reschedules exactly
like a local edit. Boot, app update, clock and time-zone changes trigger the
same rebuild.

Tests fail if a table in Room's schema is not classified, if a synced table
lacks a required column, or if `field_revs` is on the wrong tables
(`SyncRegistryTest`).

## Conflict rules (defined now, implemented later)

Pure functions in `core:domain`, unit-tested, so the sync engine only has to
feed them (`ConflictRules.kt`, `CycleRules.kt`, `OrphanRules.kt`):

1. **Field-level last-writer-wins by HLC.** Each column goes to the side whose
   field clock is newer. A field without a clock falls back to its row's `rev`.
2. **Delete versus edit: the later rev wins.** The tombstone is merged like a
   field (`deleted_at`). If any edit is newer than the delete, the row comes
   back; a delete newer than every edit keeps it deleted (with the edits merged
   into the tombstone).
3. **Cycles.** If a merge makes a loop in the parent chain or in dependencies,
   the edge with the newest rev in each loop is dropped, repeatedly until none
   remain; the engine records each drop in the activity log
   (`cycle_edge_dropped`). Locally, an edit that would close a loop is refused.
4. **A task whose project was deleted elsewhere moves to the Inbox**
   (`project_id = null`), logged as `moved_to_inbox`. Deleting a project on
   this device applies the same rule.

All merges are commutative: both devices reach the same row whatever order
they see each other's changes in (`ConflictRulesTest`).

## Boundaries for the future

- `SyncEngine` (`core/data/.../sync/SyncEngine.kt`) with `NoOpSyncEngine` bound
  by Hilt today. The real engine will push the outbox, pull rows newer than its
  `server_updated_at` cursor, merge with the rules above, and write through
  Room.
- `Clock`, `IdGenerator` and `HlcClock` are injected everywhere. Nothing calls
  `System.currentTimeMillis()` or `UUID.randomUUID()` directly, composables
  included (`LocalClock`).
- Repositories expose Flows read from Room only. A screen cannot tell whether
  a row was written here or arrived by sync.
- `supabase/migrations/0001_init.sql` is a draft, **not deployed**: the same
  tables and columns, `uuid` keys, `user_id uuid references auth.users`, RLS
  on every table with `user_id = auth.uid()` policies (no DELETE policy —
  tombstones only), a trigger that sets `server_updated_at = now()` on insert
  and update, indexes on `(user_id, server_updated_at)`, and the tables added
  to the Realtime publication. `SupabaseSchemaParityTest` compares it with
  Room's exported schema (`core/data/schemas/…/1.json`) table by table and
  column by column, including type compatibility, so they cannot drift.
- Attachments, if added, get their own table with a local URI and a nullable
  remote path for Supabase Storage (sketched at the end of the migration).

### Type mapping

| Room affinity | Postgres |
| --- | --- |
| `TEXT` id / reference | `uuid` |
| `TEXT` codes, dates, times, HLCs | `text` (with `CHECK`s for codes and formats) |
| `TEXT` versioned JSON, `field_revs` | `jsonb` |
| `INTEGER` instants | `bigint` (epoch millis) |
| `INTEGER` counts | `integer` |
| `INTEGER` flags | `boolean` |
| `server_updated_at` (`INTEGER`, null locally) | `timestamptz`, converted by the engine |
