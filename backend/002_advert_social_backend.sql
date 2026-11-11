-- NERIBO backend change 002: adverts and Socials are rolled by the BACKEND (real random faces).
-- Claude only writes a short comment (job kinds ADVERT_NOTE and SOCIAL_NOTE, rulebook v7).
-- The conversion tables live ONLY in public.dice_tables (no access for the app). The app calls
-- advert_roll_days / social_roll_day / roll_notes and receives final numbers only: never a face,
-- a rate, the table, the Blog divisor or the first-day bonus.

-- 1. Secret rules (RLS on, no policy, no grants: only the functions below can read them).
create table if not exists public.dice_tables (
  name text primary key,
  rules jsonb not null,
  updated_at timestamptz not null default now()
);
alter table public.dice_tables enable row level security;
revoke all on public.dice_tables from anon, authenticated;

insert into public.dice_tables (name, rules) values
  ('advert', '{
     "conversion_ppm": [100, 300, 700, 1200, 2000, 3000],
     "premium_bp":     [0, 100, 200, 300, 500, 800],
     "pro_bp":         [0, 50, 100, 150, 250, 400],
     "blog_divisor": 3,
     "first_day_multiplier": 2
   }'::jsonb),
  ('social', '{ "ppm_ranges": [[0,0],[20,60],[60,140],[140,260],[260,400],[400,600]] }'::jsonb)
on conflict (name) do nothing;

-- 2. Adverts: what was registered (never changes after the first roll) and what was rolled.
create table if not exists public.advert_registry (
  user_id uuid not null references auth.users(id) on delete cascade,
  advert_id text not null check (advert_id ~ '^[A-Za-z0-9_-]{1,100}$'),
  app_id text not null,
  kind text not null check (kind in ('CELEBRITY', 'BLOG')),
  followers bigint not null check (followers between 100 and 2000000000),
  total_days int not null check (total_days between 1 and 30),
  start_day date not null,
  celebrity text not null,
  created_at timestamptz not null default now(),
  primary key (user_id, advert_id)
);
create table if not exists public.advert_rolls (
  user_id uuid not null,
  advert_id text not null,
  day_key date not null,
  day_no int not null,
  face int not null check (face between 0 and 5),
  users bigint not null,
  premium bigint not null,
  pro bigint not null,
  comment text,
  created_at timestamptz not null default now(),
  primary key (user_id, advert_id, day_key),
  foreign key (user_id, advert_id) references public.advert_registry (user_id, advert_id) on delete cascade
);
create table if not exists public.social_rolls (
  user_id uuid not null references auth.users(id) on delete cascade,
  day_key date not null,
  platform_key text not null,
  base bigint not null,
  face int not null check (face between 0 and 5),
  ppm int not null,
  gain bigint not null,
  created_at timestamptz not null default now(),
  primary key (user_id, day_key, platform_key)
);
create table if not exists public.social_day_notes (
  user_id uuid not null references auth.users(id) on delete cascade,
  day_key date not null,
  comment text,
  primary key (user_id, day_key)
);
alter table public.advert_registry enable row level security;
alter table public.advert_rolls enable row level security;
alter table public.social_rolls enable row level security;
alter table public.social_day_notes enable row level security;
revoke all on public.advert_registry, public.advert_rolls, public.social_rolls, public.social_day_notes from anon, authenticated;

-- 3. Claude's comments come back through the normal job mailbox; copy them into the tables.
create or replace function private.pull_notes(p_user uuid) returns void
language plpgsql security definer set search_path = '' as $f$
begin
  update public.advert_rolls a
     set comment = left(j.result->>'comment', 200)
    from public.roll_jobs j
   where a.user_id = p_user and a.comment is null
     and j.user_id = p_user and j.kind = 'ADVERT_NOTE' and j.status in ('submitted', 'applied')
     and j.dedupe_key = 'advertnote:' || a.advert_id || ':' || a.day_key::text
     and j.result ? 'comment' and (j.result->>'comment') !~* '(@|https?:|www\.)';
  update public.social_day_notes n
     set comment = left(j.result->>'comment', 200)
    from public.roll_jobs j
   where n.user_id = p_user and n.comment is null
     and j.user_id = p_user and j.kind = 'SOCIAL_NOTE' and j.status in ('submitted', 'applied')
     and j.dedupe_key = 'socialnote:' || n.day_key::text
     and j.result ? 'comment' and (j.result->>'comment') !~* '(@|https?:|www\.)';
  update public.roll_jobs
     set status = 'applied', applied_at = now(), applied_by_device = 'backend'
   where user_id = p_user and kind in ('ADVERT_NOTE', 'SOCIAL_NOTE') and status = 'submitted';
end $f$;
revoke all on function private.pull_notes(uuid) from public, anon, authenticated;

-- 4. advert_roll_days: rolls (once, for good) every requested advert day with a real random face.
create or replace function public.advert_roll_days(p_device_id text, p_requests jsonb)
returns jsonb language plpgsql security definer set search_path = '' as $f$
declare
  v_user uuid; r jsonb; v_rules jsonb;
  reg public.advert_registry%rowtype; rl public.advert_rolls%rowtype;
  v_adv text; v_app text; v_appname text; v_kind text; v_name text;
  v_foll bigint; v_days int; v_start date; v_day date; v_day_no int;
  v_face int; v_ppm int; v_eff numeric; v_amount numeric; v_users bigint; v_prem bigint; v_pro bigint;
  v_ins int; v_results jsonb := '[]'::jsonb; v_rejected jsonb := '[]'::jsonb;
begin
  v_user := auth.uid();
  if v_user is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  if p_device_id is null or char_length(p_device_id) not between 1 and 100 then
    return private.err('bad_request', jsonb_build_object('detail', 'p_device_id must be 1..100 chars'));
  end if;
  if p_requests is null or jsonb_typeof(p_requests) <> 'array' or jsonb_array_length(p_requests) > 60 then
    return private.err('bad_request', jsonb_build_object('detail', 'p_requests must be an array of at most 60 items'));
  end if;
  select rules into v_rules from public.dice_tables where name = 'advert';
  perform private.pull_notes(v_user);

  for r in select value from jsonb_array_elements(p_requests) loop
    begin
      v_adv := r->>'advert_id';
      v_app := left(coalesce(r->>'app_id', ''), 100);
      v_appname := left(coalesce(nullif(r->>'app_name', ''), v_app), 60);
      v_kind := upper(coalesce(r->>'kind', ''));
      v_name := left(btrim(coalesce(r->>'celebrity', '')), 60);
      v_foll := (r->>'followers')::bigint;
      v_days := (r->>'total_days')::int;
      v_start := (r->>'start_day')::date;
      v_day := (r->>'day_key')::date;
      if v_adv is null or v_adv !~ '^[A-Za-z0-9_-]{1,100}$' or v_app = '' or v_kind not in ('CELEBRITY', 'BLOG')
         or char_length(v_name) < 2 or v_foll not between 100 and 2000000000 or v_days not between 1 and 30 then
        v_rejected := v_rejected || jsonb_build_array(jsonb_build_object('advert_id', v_adv, 'day_key', r->>'day_key', 'reason', 'bad_request'));
        continue;
      end if;
      if v_day > (now() at time zone 'utc')::date + 1 then
        v_rejected := v_rejected || jsonb_build_array(jsonb_build_object('advert_id', v_adv, 'day_key', r->>'day_key', 'reason', 'future_day'));
        continue;
      end if;
      -- the first registration wins: later requests can never change followers, kind or length
      insert into public.advert_registry (user_id, advert_id, app_id, kind, followers, total_days, start_day, celebrity)
      values (v_user, v_adv, v_app, v_kind, v_foll, v_days, v_start, v_name)
      on conflict (user_id, advert_id) do nothing;
      select * into reg from public.advert_registry where user_id = v_user and advert_id = v_adv;
      v_day_no := (v_day - reg.start_day) + 1;
      if v_day_no < 1 or v_day_no > reg.total_days then
        v_rejected := v_rejected || jsonb_build_array(jsonb_build_object('advert_id', v_adv, 'day_key', r->>'day_key', 'reason', 'day_outside_advert'));
        continue;
      end if;

      select * into rl from public.advert_rolls where user_id = v_user and advert_id = v_adv and day_key = v_day;
      if not found then
        v_face := floor(random() * 6)::int;
        v_ppm := (v_rules->'conversion_ppm'->>v_face)::int;
        v_eff := case when reg.kind = 'BLOG'
                      then floor(reg.followers / (v_rules->>'blog_divisor')::numeric)
                      else reg.followers::numeric end;
        v_amount := v_eff * v_ppm / 1000000.0 / reg.total_days
                    * case when v_day_no = 1 then (v_rules->>'first_day_multiplier')::numeric else 1 end;
        v_users := greatest(1, floor(v_amount + 0.5))::bigint;
        v_prem := floor(v_users * (v_rules->'premium_bp'->>v_face)::numeric / 10000.0 + 0.5)::bigint;
        v_pro := floor(v_users * (v_rules->'pro_bp'->>v_face)::numeric / 10000.0 + 0.5)::bigint;
        if v_prem + v_pro > v_users then v_pro := greatest(0, v_users - v_prem); end if;
        insert into public.advert_rolls (user_id, advert_id, day_key, day_no, face, users, premium, pro)
        values (v_user, v_adv, v_day, v_day_no, v_face, v_users, v_prem, v_pro)
        on conflict (user_id, advert_id, day_key) do nothing;
        get diagnostics v_ins = row_count;
        select * into rl from public.advert_rolls where user_id = v_user and advert_id = v_adv and day_key = v_day;
        if v_ins > 0 then
          -- Claude only writes the comment. The context holds final numbers only (no face, no rate).
          insert into public.roll_jobs (user_id, dedupe_key, kind, app_id, day_key, priority, sort_order, context, result_schema, created_by_device)
          values (v_user, 'advertnote:' || v_adv || ':' || v_day::text, 'ADVERT_NOTE', v_app, v_day::text, 200, 0,
                  jsonb_build_object('app', v_appname, 'name', reg.celebrity, 'type', lower(reg.kind),
                                     'day', v_day_no, 'of', reg.total_days,
                                     'users', rl.users, 'premium', rl.premium, 'pro', rl.pro),
                  '{"type":"object","properties":{"comment":{"type":"string","minLength":1,"maxLength":200}},"required":["comment"]}'::jsonb,
                  left(p_device_id, 100))
          on conflict (user_id, dedupe_key) do nothing;
        end if;
      end if;
      v_results := v_results || jsonb_build_array(jsonb_build_object(
        'advert_id', v_adv, 'day_key', v_day::text, 'users', rl.users, 'premium', rl.premium, 'pro', rl.pro,
        'comment', rl.comment));
    exception when others then
      v_rejected := v_rejected || jsonb_build_array(jsonb_build_object('advert_id', r->>'advert_id', 'day_key', r->>'day_key', 'reason', 'bad_item'));
    end;
  end loop;
  return jsonb_build_object('status', 'ok', 'results', v_results, 'rejected', v_rejected);
end $f$;

-- 5. social_roll_day: one real random face per platform for one day, stored for good.
create or replace function public.social_roll_day(p_device_id text, p_day text, p_platforms jsonb)
returns jsonb language plpgsql security definer set search_path = '' as $f$
declare
  v_user uuid; v_rules jsonb; v_day date; p jsonb; v_key text; v_base bigint;
  v_face int; v_lo int; v_hi int; v_ppm int; v_gain bigint; sr public.social_rolls%rowtype;
  v_out jsonb := '[]'::jsonb; v_ctx jsonb := '[]'::jsonb; v_ins int; v_comment text;
begin
  v_user := auth.uid();
  if v_user is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  if p_device_id is null or char_length(p_device_id) not between 1 and 100 then
    return private.err('bad_request', jsonb_build_object('detail', 'p_device_id must be 1..100 chars'));
  end if;
  if p_day is null or p_day !~ '^\d{4}-\d{2}-\d{2}$' then
    return private.err('bad_request', jsonb_build_object('detail', 'p_day must be yyyy-MM-dd'));
  end if;
  v_day := p_day::date;
  if v_day > (now() at time zone 'utc')::date + 1 then return private.err('bad_request', jsonb_build_object('detail', 'future day')); end if;
  if p_platforms is null or jsonb_typeof(p_platforms) <> 'array' or jsonb_array_length(p_platforms) > 50 then
    return private.err('bad_request', jsonb_build_object('detail', 'p_platforms must be an array of at most 50 items'));
  end if;
  select rules into v_rules from public.dice_tables where name = 'social';
  perform private.pull_notes(v_user);

  for p in select value from jsonb_array_elements(p_platforms) loop
    v_key := left(btrim(coalesce(p->>'key', '')), 100);
    v_base := (p->>'base')::bigint;
    if v_key = '' or v_base is null or v_base <= 0 or v_base > 100000000000 then continue; end if;
    select * into sr from public.social_rolls where user_id = v_user and day_key = v_day and platform_key = v_key;
    if not found then
      v_face := floor(random() * 6)::int;
      v_lo := (v_rules->'ppm_ranges'->v_face->>0)::int;
      v_hi := (v_rules->'ppm_ranges'->v_face->>1)::int;
      v_ppm := v_lo + floor(random() * (v_hi - v_lo + 1))::int;
      v_gain := case when v_face = 0 then 0 else greatest(1, floor(v_base::numeric * v_ppm / 1000000.0 + 0.5))::bigint end;
      insert into public.social_rolls (user_id, day_key, platform_key, base, face, ppm, gain)
      values (v_user, v_day, v_key, v_base, v_face, v_ppm, v_gain)
      on conflict (user_id, day_key, platform_key) do nothing;
      select * into sr from public.social_rolls where user_id = v_user and day_key = v_day and platform_key = v_key;
    end if;
    v_out := v_out || jsonb_build_array(jsonb_build_object('key', v_key, 'gain', sr.gain));
    v_ctx := v_ctx || jsonb_build_array(jsonb_build_object('name', left(coalesce(nullif(p->>'name', ''), v_key), 60), 'gain', sr.gain));
  end loop;

  if jsonb_array_length(v_out) > 0 then
    insert into public.social_day_notes (user_id, day_key) values (v_user, v_day) on conflict do nothing;
    get diagnostics v_ins = row_count;
    if v_ins > 0 then
      insert into public.roll_jobs (user_id, dedupe_key, kind, app_id, day_key, priority, sort_order, context, result_schema, created_by_device)
      values (v_user, 'socialnote:' || v_day::text, 'SOCIAL_NOTE', 'socials', v_day::text, 200, 0,
              jsonb_build_object('day', v_day::text, 'platforms', v_ctx),
              '{"type":"object","properties":{"comment":{"type":"string","minLength":1,"maxLength":200}},"required":["comment"]}'::jsonb,
              left(p_device_id, 100))
      on conflict (user_id, dedupe_key) do nothing;
    end if;
    select comment into v_comment from public.social_day_notes where user_id = v_user and day_key = v_day;
  end if;
  return jsonb_build_object('status', 'ok', 'day', v_day::text, 'platforms', v_out, 'comment', v_comment);
end $f$;

-- 6. roll_notes: the comments Claude has written lately (the app asks for them now and then).
create or replace function public.roll_notes(p_since text default null)
returns jsonb language plpgsql security definer set search_path = '' as $f$
declare v_user uuid; v_since date; v_adv jsonb; v_soc jsonb;
begin
  v_user := auth.uid();
  if v_user is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  v_since := case when p_since ~ '^\d{4}-\d{2}-\d{2}$' then p_since::date else (now() at time zone 'utc')::date - 45 end;
  perform private.pull_notes(v_user);
  select coalesce(jsonb_agg(jsonb_build_object('advert_id', advert_id, 'day_key', day_key::text, 'comment', comment) order by day_key), '[]'::jsonb)
    into v_adv from public.advert_rolls where user_id = v_user and comment is not null and day_key >= v_since;
  select coalesce(jsonb_agg(jsonb_build_object('day_key', day_key::text, 'comment', comment) order by day_key), '[]'::jsonb)
    into v_soc from public.social_day_notes where user_id = v_user and comment is not null and day_key >= v_since;
  return jsonb_build_object('status', 'ok', 'adverts', v_adv, 'socials', v_soc);
end $f$;

revoke all on function public.advert_roll_days(text, jsonb) from public, anon;
revoke all on function public.social_roll_day(text, text, jsonb) from public, anon;
revoke all on function public.roll_notes(text) from public, anon;
grant execute on function public.advert_roll_days(text, jsonb) to authenticated;
grant execute on function public.social_roll_day(text, text, jsonb) to authenticated;
grant execute on function public.roll_notes(text) to authenticated;

-- 7. The comment jobs wait for no cooldown (like SOCIAL_ROLL): patch the two agent functions in place.
do $do$
declare d text;
begin
  d := pg_get_functiondef('public.agent_claim_next_app(uuid,text)'::regprocedure);
  d := replace(d, 'v_next_kind is distinct from ''SOCIAL_ROLL''',
                  '(v_next_kind is null or v_next_kind not in (''SOCIAL_ROLL'', ''ADVERT_NOTE'', ''SOCIAL_NOTE''))');
  execute d;
  d := pg_get_functiondef('public.agent_submit_job_result(uuid,uuid,jsonb)'::regprocedure);
  d := replace(d, 'if j.kind = ''SOCIAL_ROLL'' then',
                  'if j.kind in (''SOCIAL_ROLL'', ''ADVERT_NOTE'', ''SOCIAL_NOTE'') then');
  execute d;
end $do$;

-- 8. Rulebook v7: adds the two comment jobs. The old Claude-face SOCIAL_ROLL is no longer published.
insert into public.ai_rules (user_id, version, title, active, rules)
select r.user_id, 7, 'Neribo rulebook v7', false,
       jsonb_set(jsonb_set(r.rules, '{job_kinds,ADVERT_NOTE}', to_jsonb($t$ADVERT_NOTE asks for ONE short comment about one day of an advert for an app. The advert is made by a celebrity or by a blog. The job context has: app, name (the celebrity or the blog), type ("celebrity" or "blog"), day and of (day number and total days), users (how many new users arrived that day), premium and pro (how many of them started on a paid plan the same day). Every number is already final. Submit {comment} through submit_result. Rules: 1. comment is one or two natural sentences, at most 200 characters, plain text. 2. Say what happened that day in everyday words and use only the numbers given; if day is 1 you may call it the launch day. 3. A celebrity is a celebrity and a blog is a blog: use the right word. 4. Never mention dice, faces, rolls, rates, percentages, conversion, dividing followers or any rule of the system. 5. Do not invent other numbers, names or events. No at sign and no web address. 6. Do not add any other field.$t$::text)),
                 '{job_kinds,SOCIAL_NOTE}', to_jsonb($t$SOCIAL_NOTE asks for ONE short comment about one day of follower growth of the company's social media accounts. The job context has: day and platforms (a list; each item has name and gain, the followers gained that day). Every number is already final. Submit {comment} through submit_result. Rules: 1. comment is one or two natural sentences, at most 200 characters, plain text. 2. Use only the names and gains given; gain 0 means that platform did not grow. 3. Never mention dice, faces, rolls, rates, percentages or any rule of the system. 4. Do not invent other numbers, names or events. No at sign and no web address. 5. Do not add any other field.$t$::text))
from public.ai_rules r
where r.version = 6 and r.active
  and not exists (select 1 from public.ai_rules x where x.user_id = r.user_id and x.version = 7);

update public.ai_rules set active = false where version = 6 and active
  and exists (select 1 from public.ai_rules x where x.user_id = ai_rules.user_id and x.version = 7);
update public.ai_rules set active = true where version = 7 and not active
  and not exists (select 1 from public.ai_rules y where y.user_id = ai_rules.user_id and y.active);

-- 9. Old Claude-face Socials jobs that never got an answer are closed (the backend rolls Socials now).
update public.roll_jobs set status = 'cancelled' where kind = 'SOCIAL_ROLL' and status in ('pending', 'claimed');
