-- NERIBO backend change 003: the BACKEND rolls three more dice, with real random faces and secret rules:
--   1. the daily channel split of an app (Google Play / Apple Store / Website),
--   2. the sites per user of a Neribo Deploy style app (only for users the dice add),
--   3. the daily storage growth of an app.
-- The app calls app_day_rolls once per app and day and receives final numbers only:
-- the share of new users per channel (basis points), the sites per user (tenths) and the storage growth (basis
-- points of capacity). It never receives a face, a table or the base weights.

insert into public.dice_tables (name, rules) values
  ('app_day', '{
     "channel_base": {"PLAY": 50, "APPLE": 20, "WEBSITE": 30},
     "channel_mult_pct": [60, 80, 100, 120, 140, 160],
     "sites_tenths": [10, 12, 15, 19, 25, 32],
     "storage_bp": [0, 10, 20, 35, 50, 80]
   }'::jsonb)
on conflict (name) do nothing;

-- One row per app and day. The faces are stored for ALL three channels, so the split can be worked out again
-- for whichever channels are switched on (a repeated call always gives the same answer).
create table if not exists public.app_day_rolls (
  user_id uuid not null references auth.users(id) on delete cascade,
  app_id text not null,
  day_key date not null,
  face_play int not null check (face_play between 0 and 5),
  face_apple int not null check (face_apple between 0 and 5),
  face_website int not null check (face_website between 0 and 5),
  face_sites int not null check (face_sites between 0 and 5),
  face_storage int not null check (face_storage between 0 and 5),
  created_at timestamptz not null default now(),
  primary key (user_id, app_id, day_key)
);
alter table public.app_day_rolls enable row level security;
revoke all on public.app_day_rolls from anon, authenticated;

create or replace function public.app_day_rolls(
  p_device_id text, p_app_id text, p_day text, p_channels jsonb,
  p_sites boolean default false, p_storage boolean default false)
returns jsonb language plpgsql security definer set search_path = '' as $f$
declare
  v_user uuid; v_rules jsonb; v_day date; rl public.app_day_rolls%rowtype;
  v_ch text; v_on text[] := '{}'; v_w numeric; v_total numeric := 0; v_sum int := 0;
  v_play int := 0; v_apple int := 0; v_web int := 0; v_big text; v_bigbp int := -1; v_bp int;
  v_weights jsonb := '{}'::jsonb; v_out jsonb;
begin
  v_user := auth.uid();
  if v_user is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  if p_device_id is null or char_length(p_device_id) not between 1 and 100 then
    return private.err('bad_request', jsonb_build_object('detail', 'p_device_id must be 1..100 chars'));
  end if;
  if p_app_id is null or char_length(p_app_id) not between 1 and 100 then
    return private.err('bad_request', jsonb_build_object('detail', 'p_app_id must be 1..100 chars'));
  end if;
  if p_day is null or p_day !~ '^\d{4}-\d{2}-\d{2}$' then
    return private.err('bad_request', jsonb_build_object('detail', 'p_day must be yyyy-MM-dd'));
  end if;
  v_day := p_day::date;
  if v_day > (now() at time zone 'utc')::date + 1 then
    return private.err('bad_request', jsonb_build_object('detail', 'future day'));
  end if;
  if p_channels is null or jsonb_typeof(p_channels) <> 'array' or jsonb_array_length(p_channels) not between 1 and 3 then
    return private.err('bad_request', jsonb_build_object('detail', 'p_channels must list 1 to 3 channels'));
  end if;
  for v_ch in select value from jsonb_array_elements_text(p_channels) loop
    if v_ch not in ('PLAY', 'APPLE', 'WEBSITE') then
      return private.err('bad_request', jsonb_build_object('detail', 'unknown channel'));
    end if;
    if not (v_ch = any (v_on)) then v_on := v_on || v_ch; end if;
  end loop;

  select rules into v_rules from public.dice_tables where name = 'app_day';
  insert into public.app_day_rolls (user_id, app_id, day_key, face_play, face_apple, face_website, face_sites, face_storage)
  values (v_user, p_app_id, v_day, floor(random() * 6)::int, floor(random() * 6)::int, floor(random() * 6)::int,
          floor(random() * 6)::int, floor(random() * 6)::int)
  on conflict (user_id, app_id, day_key) do nothing;
  select * into rl from public.app_day_rolls where user_id = v_user and app_id = p_app_id and day_key = v_day;

  -- the share of every switched-on channel: its base weight times the multiplier of its face of the day
  foreach v_ch in array v_on loop
    v_w := (v_rules->'channel_base'->>v_ch)::numeric
           * (v_rules->'channel_mult_pct'->>(case v_ch when 'PLAY' then rl.face_play when 'APPLE' then rl.face_apple else rl.face_website end))::numeric
           / 100.0;
    v_weights := v_weights || jsonb_build_object(v_ch, v_w);
    v_total := v_total + v_w;
  end loop;
  foreach v_ch in array v_on loop
    v_bp := floor(((v_weights->>v_ch)::numeric) * 10000 / v_total)::int;
    v_sum := v_sum + v_bp;
    if v_bp > v_bigbp then v_bigbp := v_bp; v_big := v_ch; end if;
    if v_ch = 'PLAY' then v_play := v_bp; elsif v_ch = 'APPLE' then v_apple := v_bp; else v_web := v_bp; end if;
  end loop;
  -- what is left from rounding goes to the biggest share, so the three always add up to 10000
  if v_big = 'PLAY' then v_play := v_play + (10000 - v_sum);
  elsif v_big = 'APPLE' then v_apple := v_apple + (10000 - v_sum);
  else v_web := v_web + (10000 - v_sum); end if;

  v_out := jsonb_build_object('status', 'ok', 'day', v_day::text,
                              'play_bp', v_play, 'apple_bp', v_apple, 'website_bp', v_web);
  if p_sites then v_out := v_out || jsonb_build_object('sites_tenths', (v_rules->'sites_tenths'->>rl.face_sites)::int); end if;
  if p_storage then v_out := v_out || jsonb_build_object('storage_bp', (v_rules->'storage_bp'->>rl.face_storage)::int); end if;
  return v_out;
end $f$;

revoke all on function public.app_day_rolls(text, text, text, jsonb, boolean, boolean) from public, anon;
grant execute on function public.app_day_rolls(text, text, text, jsonb, boolean, boolean) to authenticated;
