-- NERIBO backend change 006: the Storage Room app keeps its two off-site drives (A and B) and its nightly-backup
-- story in the cloud, so a reinstall or an APK update brings them back exactly as they were.
--   1. public.room_state: one small text row per owner and key ('drives' and 'backup'), private to the owner.
--   2. public.get_room_state(p_key): the app reads its saved state (returns found, content, updated_ms).
--   3. public.save_room_state(p_key, p_content): the app saves its state. The table is not writable directly.
-- Both drives start at the last known copy: 10.13 TB (off-site copy of 15 Nov 2026).

create table if not exists public.room_state (
  user_id uuid not null references auth.users(id) on delete cascade,
  key text not null check (key in ('drives', 'backup')),
  content text not null check (char_length(content) between 1 and 20000),
  updated_at timestamptz not null default now(),
  primary key (user_id, key)
);

alter table public.room_state enable row level security;
revoke all on public.room_state from anon, authenticated;

create or replace function public.get_room_state(p_key text)
returns jsonb language plpgsql security definer set search_path = '' as $f$
declare
  v_user uuid; v_content text; v_at timestamptz;
begin
  v_user := auth.uid();
  if v_user is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  if p_key is null or p_key not in ('drives', 'backup') then
    return private.err('bad_request', jsonb_build_object('detail', 'p_key must be drives or backup'));
  end if;
  select content, updated_at into v_content, v_at from public.room_state where user_id = v_user and key = p_key;
  if v_content is null then
    return jsonb_build_object('status', 'ok', 'found', false);
  end if;
  return jsonb_build_object('status', 'ok', 'found', true, 'content', v_content,
                            'updated_ms', (extract(epoch from v_at) * 1000)::bigint);
end $f$;

create or replace function public.save_room_state(p_key text, p_content text)
returns jsonb language plpgsql security definer set search_path = '' as $f$
declare
  v_user uuid;
begin
  v_user := auth.uid();
  if v_user is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  if p_key is null or p_key not in ('drives', 'backup') then
    return private.err('bad_request', jsonb_build_object('detail', 'p_key must be drives or backup'));
  end if;
  if p_content is null or char_length(p_content) not between 1 and 20000 then
    return private.err('bad_request', jsonb_build_object('detail', 'p_content must be 1..20000 chars'));
  end if;
  insert into public.room_state (user_id, key, content, updated_at)
  values (v_user, p_key, p_content, now())
  on conflict (user_id, key) do update set content = excluded.content, updated_at = now();
  return jsonb_build_object('status', 'ok');
end $f$;

revoke all on function public.get_room_state(text) from public, anon;
revoke all on function public.save_room_state(text, text) from public, anon;
grant execute on function public.get_room_state(text) to authenticated;
grant execute on function public.save_room_state(text, text) to authenticated;

-- Start both drives at the last known figure (10.13 TB = 10,877,004,677 KB) for the owner of the ledger.
-- Line format of 'drives': U|saved at, D|drive|on the table|copied KB|last copy at|failed, C|(no copy running), N|next drive.
insert into public.room_state (user_id, key, content)
select o.user_id, 'drives',
  E'U|1\nD|A|0|10877004677|1795090762538|0\nD|B|0|10877004677|1794935728144|0\nC|-1|NONE|0|0|0|0\nN|1\n'
from (select distinct user_id from public.ledger_docs limit 1) o
on conflict (user_id, key) do nothing;

insert into public.room_state (user_id, key, content)
select o.user_id, 'backup', E'U|1\nF|1794720079109\n'
from (select distinct user_id from public.ledger_docs limit 1) o
on conflict (user_id, key) do nothing;
