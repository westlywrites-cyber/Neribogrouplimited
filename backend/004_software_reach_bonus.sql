-- NERIBO backend change 004: rulebook gets the "reach bonus" for SOFTWARE_ROLL. NOT APPLIED. Needs the owner's approval.
-- What changes: followers of the company's social media bring extra client contacts (reachBonus 0..3, final, made by the app).
--   contacts = contactsByFace[contactFace] + reach.reachBonus; most proposals in one day = 6 (was 3).
-- Safe to run twice. Changes only public.ai_rules. No table, no function, no Edge Function changes.
-- It copies the rulebook that is ACTIVE now (expected v7, which holds ADVERT_NOTE and SOCIAL_NOTE) into the next free
-- version number of each user (expected v8) and changes only job_kinds.SOFTWARE_ROLL and software_roll.
-- Rules 2 to 8 are word for word the same as before. The text is copied by script from INSTRUCTIONS in
-- app/src/main/java/com/westly/neribogroup/data/SoftwareAi.kt (single source of truth).
begin;

-- 1. The new rulebook for every user whose active rulebook has SOFTWARE_ROLL but not the reach bonus yet.
insert into public.ai_rules (user_id, version, title, active, rules)
select r.user_id,
       (select max(m.version) from public.ai_rules m where m.user_id = r.user_id) + 1,
       'Neribo rulebook v' || ((select max(m.version) from public.ai_rules m where m.user_id = r.user_id) + 1),
       false,
       jsonb_set(
         jsonb_set(r.rules, '{job_kinds,SOFTWARE_ROLL}',
                   to_jsonb($r$SOFTWARE_ROLL is the ONE job of Software Development Clients for a day. Roll both dice and write the proposals in one answer. The job context has: dice (faces and what each means), reach (reachBonus, totalFollowers), inboxRoom, projectTypes, codePurchase, countries, ratesPerUsd, recentClientNames. Submit {contactFace, offerFace, comment (optional), offers:[...]} through submit_result. Write the rules below exactly. Roll honestly: do not choose faces to get more or fewer proposals. 1. Roll both dice honestly (0-5 each), then write exactly `wanted` proposals, where contacts = contactsByFace[contactFace] + reachBonus (reachBonus is in the job context and is already final: the extra contacts the company's social followers bring today; never roll or change it) and wanted = min(contacts, (contacts*share+50)/100) and never more than inboxRoom and never more than 6. wanted = 0 means an empty offers array. 2. Fit is mandatory. Pick a projectCode from projectTypes. The client must be the kind of organisation or person that would really order that project, and the title, summary and requirements must describe that same project for that same client. A church website comes from a church, ministry or NGO, never a school or a tailor; a school website from a school; a hotel website from a hotel or guesthouse; a clinic booking system from a clinic; a restaurant website from a restaurant or caterer (an individual chef is allowed); a portfolio website from an individual. Follow allowCompany and allowIndividual of the chosen type. 3. Names must be realistic for the country, different from recentClientNames and from each other. Foreign clients use names natural to that country. No celebrities, no real brands, no real people. 4. Do not write any email address, phone number or email-like text anywhere; the app makes contacts itself. Text must not contain the at sign or a web address. 5. fee is a whole number in units of the proposal's currency, a multiple of 50 (NGN: a multiple of 10000), so that units x ratesPerUsd-based naira value lies inside the project type's minNgn-maxNgn (kind CODE_PURCHASE: 1000000-50000000 naira). 6. deliveryWeeks: code purchase 1-2; naira value up to 700000: 2-3; up to 2000000: 3-6; above: 6-12. 7. summary: 1-3 sentences, specific to the client. requirements: 5-7 short, concrete, project-specific lines. Do not write payment, hosting, content or maintenance lines. 8. About 70% Nigerian clients (NGN) and 30% foreign (the currency of that country).$r$::text), true),
         '{software_roll}', to_jsonb($r$1. Roll both dice honestly (0-5 each), then write exactly `wanted` proposals, where contacts = contactsByFace[contactFace] + reachBonus (reachBonus is in the job context and is already final: the extra contacts the company's social followers bring today; never roll or change it) and wanted = min(contacts, (contacts*share+50)/100) and never more than inboxRoom and never more than 6. wanted = 0 means an empty offers array. 2. Fit is mandatory. Pick a projectCode from projectTypes. The client must be the kind of organisation or person that would really order that project, and the title, summary and requirements must describe that same project for that same client. A church website comes from a church, ministry or NGO, never a school or a tailor; a school website from a school; a hotel website from a hotel or guesthouse; a clinic booking system from a clinic; a restaurant website from a restaurant or caterer (an individual chef is allowed); a portfolio website from an individual. Follow allowCompany and allowIndividual of the chosen type. 3. Names must be realistic for the country, different from recentClientNames and from each other. Foreign clients use names natural to that country. No celebrities, no real brands, no real people. 4. Do not write any email address, phone number or email-like text anywhere; the app makes contacts itself. Text must not contain the at sign or a web address. 5. fee is a whole number in units of the proposal's currency, a multiple of 50 (NGN: a multiple of 10000), so that units x ratesPerUsd-based naira value lies inside the project type's minNgn-maxNgn (kind CODE_PURCHASE: 1000000-50000000 naira). 6. deliveryWeeks: code purchase 1-2; naira value up to 700000: 2-3; up to 2000000: 3-6; above: 6-12. 7. summary: 1-3 sentences, specific to the client. requirements: 5-7 short, concrete, project-specific lines. Do not write payment, hosting, content or maintenance lines. 8. About 70% Nigerian clients (NGN) and 30% foreign (the currency of that country).$r$::text), true)
from public.ai_rules r
where r.active
  and r.rules->'job_kinds' ? 'SOFTWARE_ROLL'
  and (r.rules->'job_kinds'->>'SOFTWARE_ROLL') not like '%reach (reachBonus, totalFollowers)%'
  and not exists (select 1 from public.ai_rules x
                   where x.user_id = r.user_id
                     and (x.rules->'job_kinds'->>'SOFTWARE_ROLL') like '%reach (reachBonus, totalFollowers)%');

-- 2. Switch: old active off, new on (in this order, so only one is active at any time).
update public.ai_rules set active = false
where active
  and (rules->'job_kinds'->>'SOFTWARE_ROLL') not like '%reach (reachBonus, totalFollowers)%'
  and exists (select 1 from public.ai_rules x
               where x.user_id = ai_rules.user_id and not x.active
                 and (x.rules->'job_kinds'->>'SOFTWARE_ROLL') like '%reach (reachBonus, totalFollowers)%');
update public.ai_rules set active = true
where not active
  and (rules->'job_kinds'->>'SOFTWARE_ROLL') like '%reach (reachBonus, totalFollowers)%'
  and not exists (select 1 from public.ai_rules y where y.user_id = ai_rules.user_id and y.active);

commit;

-- Check afterwards (expect one active row per user, the highest version, has_reach = true, has_advert_note = true):
--   select version, active, title,
--          (rules->'job_kinds'->>'SOFTWARE_ROLL') like '%reach (reachBonus, totalFollowers)%' as has_reach,
--          (rules->>'software_roll') like '%never more than 6%' as says_six,
--          rules->'job_kinds' ? 'ADVERT_NOTE' as has_advert_note
--   from public.ai_rules order by version;
-- Roll back (if ever needed): the new rulebook off, the highest older one on again.
--   update public.ai_rules set active = false
--    where active and (rules->'job_kinds'->>'SOFTWARE_ROLL') like '%reach (reachBonus, totalFollowers)%';
--   update public.ai_rules p set active = true
--    where not exists (select 1 from public.ai_rules y where y.user_id = p.user_id and y.active)
--      and p.version = (select max(b.version) from public.ai_rules b
--                        where b.user_id = p.user_id and (b.rules->'job_kinds'->>'SOFTWARE_ROLL') not like '%reach (reachBonus, totalFollowers)%');
