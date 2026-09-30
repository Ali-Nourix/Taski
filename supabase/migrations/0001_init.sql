-- ─────────────────────────────────────────────────────────────────────────────
-- Taski · draft Supabase schema (NOT deployed).
--
-- Mirrors the app's Room schema table for table and column for column; the
-- test SupabaseSchemaParityTest in core/data fails if the two drift. See
-- docs/SYNC.md for the rules behind every column.
--
-- Conventions
--   • Primary keys are client-generated UUIDv7 (no defaults, no sequences).
--   • created_at / updated_at / deleted_at and other instants: UTC epoch millis (bigint).
--   • rev and field_revs hold hybrid-logical-clock strings (see Hlc.kt).
--   • server_updated_at is set by the trigger below and is the pull cursor.
--   • Dates are ISO 'YYYY-MM-DD' text, times 'HH:MM' text, enums stable text codes.
--   • sort_key is a fractional index compared bytewise, hence COLLATE "C".
--   • Deletes are tombstones (deleted_at). Clients have no DELETE policy.
-- ─────────────────────────────────────────────────────────────────────────────

create or replace function public.set_server_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.server_updated_at := now();
  return new;
end;
$$;

create table public.projects (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name text not null,
  color text not null,
  sort_key text collate "C" not null,
  field_revs jsonb not null default '{}'::jsonb,
  created_at bigint not null,
  updated_at bigint not null,
  deleted_at bigint,
  rev text not null,
  server_updated_at timestamptz
);

create table public.tags (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name text not null,
  color text not null,
  sort_key text collate "C" not null,
  field_revs jsonb not null default '{}'::jsonb,
  created_at bigint not null,
  updated_at bigint not null,
  deleted_at bigint,
  rev text not null,
  server_updated_at timestamptz
);

-- Tasks. project_id null = the Inbox. parent_id makes a subtask. No foreign keys between synced
-- tables: rows arrive in any order, and dangling references are resolved by the conflict rules.
create table public.tasks (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  project_id uuid,
  parent_id uuid,
  title text not null,
  notes text not null default '',
  status text not null check (status in ('not_started', 'in_progress', 'done', 'not_done')),
  priority text check (priority in ('highest', 'high', 'medium', 'low', 'lowest')),
  start_date text check (start_date ~ '^\d{4}-\d{2}-\d{2}$'),
  start_time text check (start_time ~ '^\d{2}:\d{2}$'),
  due_date text check (due_date ~ '^\d{4}-\d{2}-\d{2}$'),
  due_time text check (due_time ~ '^\d{2}:\d{2}$'),
  repeat_rule jsonb,
  progress_done integer,
  progress_total integer check (progress_total >= 1),
  timer_minutes integer,
  reminder_offset_min integer,
  sort_key text collate "C" not null,
  completed_at bigint,
  purged_at bigint,
  field_revs jsonb not null default '{}'::jsonb,
  created_at bigint not null,
  updated_at bigint not null,
  deleted_at bigint,
  rev text not null,
  server_updated_at timestamptz
);

-- Saved board configurations; definition is versioned JSON.
create table public.saved_views (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name text not null,
  definition jsonb not null,
  sort_key text collate "C" not null,
  field_revs jsonb not null default '{}'::jsonb,
  created_at bigint not null,
  updated_at bigint not null,
  deleted_at bigint,
  rev text not null,
  server_updated_at timestamptz
);

-- Task <-> tag links. Removing a tag tombstones the row, adding it again revives it.
create table public.task_tags (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  task_id uuid not null,
  tag_id uuid not null,
  created_at bigint not null,
  updated_at bigint not null,
  deleted_at bigint,
  rev text not null,
  server_updated_at timestamptz,
  unique (task_id, tag_id)
);

-- task_id waits for depends_on_id.
create table public.task_dependencies (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  task_id uuid not null,
  depends_on_id uuid not null,
  created_at bigint not null,
  updated_at bigint not null,
  deleted_at bigint,
  rev text not null,
  server_updated_at timestamptz,
  unique (task_id, depends_on_id),
  check (task_id <> depends_on_id)
);

-- Append-only: one row per completion, never edited.
create table public.task_completions (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  task_id uuid not null,
  occurrence_date text check (occurrence_date ~ '^\d{4}-\d{2}-\d{2}$'),
  completed_at bigint not null,
  created_at bigint not null,
  updated_at bigint not null,
  deleted_at bigint,
  rev text not null,
  server_updated_at timestamptz
);

-- Append-only: one row per focused stretch.
create table public.timer_sessions (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  task_id uuid not null,
  started_at bigint not null,
  ended_at bigint not null,
  planned_seconds integer not null,
  finished boolean not null,
  created_at bigint not null,
  updated_at bigint not null,
  deleted_at bigint,
  rev text not null,
  server_updated_at timestamptz
);

-- Append-only: what happened to which row.
create table public.activity_log (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  entity text not null,
  row_id uuid not null,
  kind text not null,
  payload jsonb not null default '{}'::jsonb,
  occurred_at bigint not null,
  created_at bigint not null,
  updated_at bigint not null,
  deleted_at bigint,
  rev text not null,
  server_updated_at timestamptz
);

-- Pull cursor, server_updated_at trigger, and row-level security on every table.
create index projects_user_cursor_idx on public.projects (user_id, server_updated_at);
create trigger projects_set_server_updated_at
  before insert or update on public.projects
  for each row execute function public.set_server_updated_at();
alter table public.projects enable row level security;
create policy projects_select_own on public.projects for select using (user_id = (select auth.uid()));
create policy projects_insert_own on public.projects for insert with check (user_id = (select auth.uid()));
create policy projects_update_own on public.projects for update using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

create index tags_user_cursor_idx on public.tags (user_id, server_updated_at);
create trigger tags_set_server_updated_at
  before insert or update on public.tags
  for each row execute function public.set_server_updated_at();
alter table public.tags enable row level security;
create policy tags_select_own on public.tags for select using (user_id = (select auth.uid()));
create policy tags_insert_own on public.tags for insert with check (user_id = (select auth.uid()));
create policy tags_update_own on public.tags for update using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

create index tasks_user_cursor_idx on public.tasks (user_id, server_updated_at);
create index tasks_project_id_idx on public.tasks (project_id);
create index tasks_parent_id_idx on public.tasks (parent_id);
create trigger tasks_set_server_updated_at
  before insert or update on public.tasks
  for each row execute function public.set_server_updated_at();
alter table public.tasks enable row level security;
create policy tasks_select_own on public.tasks for select using (user_id = (select auth.uid()));
create policy tasks_insert_own on public.tasks for insert with check (user_id = (select auth.uid()));
create policy tasks_update_own on public.tasks for update using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

create index saved_views_user_cursor_idx on public.saved_views (user_id, server_updated_at);
create trigger saved_views_set_server_updated_at
  before insert or update on public.saved_views
  for each row execute function public.set_server_updated_at();
alter table public.saved_views enable row level security;
create policy saved_views_select_own on public.saved_views for select using (user_id = (select auth.uid()));
create policy saved_views_insert_own on public.saved_views for insert with check (user_id = (select auth.uid()));
create policy saved_views_update_own on public.saved_views for update using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

create index task_tags_user_cursor_idx on public.task_tags (user_id, server_updated_at);
create index task_tags_tag_id_idx on public.task_tags (tag_id);
create trigger task_tags_set_server_updated_at
  before insert or update on public.task_tags
  for each row execute function public.set_server_updated_at();
alter table public.task_tags enable row level security;
create policy task_tags_select_own on public.task_tags for select using (user_id = (select auth.uid()));
create policy task_tags_insert_own on public.task_tags for insert with check (user_id = (select auth.uid()));
create policy task_tags_update_own on public.task_tags for update using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

create index task_dependencies_user_cursor_idx on public.task_dependencies (user_id, server_updated_at);
create index task_dependencies_depends_on_id_idx on public.task_dependencies (depends_on_id);
create trigger task_dependencies_set_server_updated_at
  before insert or update on public.task_dependencies
  for each row execute function public.set_server_updated_at();
alter table public.task_dependencies enable row level security;
create policy task_dependencies_select_own on public.task_dependencies for select using (user_id = (select auth.uid()));
create policy task_dependencies_insert_own on public.task_dependencies for insert with check (user_id = (select auth.uid()));
create policy task_dependencies_update_own on public.task_dependencies for update using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

create index task_completions_user_cursor_idx on public.task_completions (user_id, server_updated_at);
create index task_completions_task_id_idx on public.task_completions (task_id);
create trigger task_completions_set_server_updated_at
  before insert or update on public.task_completions
  for each row execute function public.set_server_updated_at();
alter table public.task_completions enable row level security;
create policy task_completions_select_own on public.task_completions for select using (user_id = (select auth.uid()));
create policy task_completions_insert_own on public.task_completions for insert with check (user_id = (select auth.uid()));
create policy task_completions_update_own on public.task_completions for update using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

create index timer_sessions_user_cursor_idx on public.timer_sessions (user_id, server_updated_at);
create index timer_sessions_task_id_idx on public.timer_sessions (task_id);
create trigger timer_sessions_set_server_updated_at
  before insert or update on public.timer_sessions
  for each row execute function public.set_server_updated_at();
alter table public.timer_sessions enable row level security;
create policy timer_sessions_select_own on public.timer_sessions for select using (user_id = (select auth.uid()));
create policy timer_sessions_insert_own on public.timer_sessions for insert with check (user_id = (select auth.uid()));
create policy timer_sessions_update_own on public.timer_sessions for update using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

create index activity_log_user_cursor_idx on public.activity_log (user_id, server_updated_at);
create index activity_log_row_id_idx on public.activity_log (row_id);
create trigger activity_log_set_server_updated_at
  before insert or update on public.activity_log
  for each row execute function public.set_server_updated_at();
alter table public.activity_log enable row level security;
create policy activity_log_select_own on public.activity_log for select using (user_id = (select auth.uid()));
create policy activity_log_insert_own on public.activity_log for insert with check (user_id = (select auth.uid()));
create policy activity_log_update_own on public.activity_log for update using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

-- Realtime: devices listen for changes to their own rows (RLS applies).
alter publication supabase_realtime add table public.projects, public.tags, public.tasks, public.saved_views, public.task_tags, public.task_dependencies, public.task_completions, public.timer_sessions, public.activity_log;

-- Attachments, when added, get a table of their own:
--   attachments (id uuid, user_id, task_id uuid, local_uri text, remote_path text null /* Supabase Storage */, …sync columns)
-- local_uri is device-specific and would be excluded from the push.
