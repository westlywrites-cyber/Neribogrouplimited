-- NERIBO backend change 005: the Storage room's live event feed (Supabase Realtime).
-- Every phone that has the Storage room open can see, in real time, what the owner changes on another phone:
-- limit and plan edits, refused changes, new users, sync status and storage warnings.
-- This is only a feed of short text lines. The storage numbers themselves still travel in the normal sync
-- documents, which this change does not touch.
--   1. public.storage_events: one row per line, private to its owner (row level security).
--   2. public.log_storage_event(...): the app's only way to add a line (the table is not writable directly).
--   3. the table is added to the supabase_realtime publication, so signed-in phones receive new lines at once.
-- The newest 500 lines of each owner are kept; older lines are deleted by the function.

create table if not exists public.storage_events (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  device_id text not null check (char_length(device_id) between 1 and 100),
  level text not null check (level in ('INFO', 'SYNC', 'WARN', 'ERROR', 'CHANGE')),
  source text not null check (char_length(source) between 1 and 60),
  message text not null check (char_length(message) between 1 and 500),
  app_id text check (app_id is null or char_length(app_id) between 1 and 100),
  created_at timestamptz not null default now()
);
create index if not exists storage_events_user_id_idx on public.storage_events (user_id, id desc);

alter table public.storage_events enable row level security;
revoke all on public.storage_events from anon, authenticated;
-- Realtime needs to read the rows as the signed-in owner, and only the owner's own rows.
grant select on public.storage_events to authenticated;
drop policy if exists storage_events_read_own on public.storage_events;
create policy storage_events_read_own on public.storage_events
  for select to authenticated using (user_id = (select auth.uid()));

create or replace function public.log_storage_event(
  p_device_id text, p_level text, p_source text, p_message text, p_app_id text default null)
returns jsonb language plpgsql security definer set search_path = '' as $f$
declare
  v_user uuid; v_id bigint;
begin
  v_user := auth.uid();
  if v_user is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  if p_device_id is null or char_length(p_device_id) not between 1 and 100 then
    return private.err('bad_request', jsonb_build_object('detail', 'p_device_id must be 1..100 chars'));
  end if;
  if p_level is null or p_level not in ('INFO', 'SYNC', 'WARN', 'ERROR', 'CHANGE') then
    return private.err('bad_request', jsonb_build_object('detail', 'p_level must be INFO, SYNC, WARN, ERROR or CHANGE'));
  end if;
  if p_source is null or char_length(p_source) not between 1 and 60 then
    return private.err('bad_request', jsonb_build_object('detail', 'p_source must be 1..60 chars'));
  end if;
  if p_message is null or char_length(p_message) not between 1 and 500 then
    return private.err('bad_request', jsonb_build_object('detail', 'p_message must be 1..500 chars'));
  end if;
  if p_app_id is not null and char_length(p_app_id) not between 1 and 100 then
    return private.err('bad_request', jsonb_build_object('detail', 'p_app_id must be 1..100 chars'));
  end if;

  insert into public.storage_events (user_id, device_id, level, source, message, app_id)
  values (v_user, p_device_id, p_level, p_source, p_message, p_app_id)
  returning id into v_id;

  -- keep only the newest 500 lines of this owner
  delete from public.storage_events
  where user_id = v_user
    and id not in (select e.id from public.storage_events e where e.user_id = v_user order by e.id desc limit 500);

  return jsonb_build_object('status', 'ok', 'id', v_id);
end $f$;

revoke all on function public.log_storage_event(text, text, text, text, text) from public, anon;
grant execute on function public.log_storage_event(text, text, text, text, text) to authenticated;

-- Send new lines to the phones through Supabase Realtime (safe to run twice).
do $r$
begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime')
     and not exists (select 1 from pg_publication_tables
                     where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'storage_events') then
    alter publication supabase_realtime add table public.storage_events;
  end if;
end $r$;
