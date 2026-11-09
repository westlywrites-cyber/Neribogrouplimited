-- NERIBO backend change: rulebook v5 (adds the SOFTWARE_ROLL job). NOT APPLIED. Needs the owner's approval.
-- Safe to run twice. Changes only public.ai_rules. No table, no function, no Edge Function changes.
-- Old jobs (DICE_ROLL, WRITE_CASE, WRITE_PROPOSAL) keep their rules. Claim order needs no change:
-- agent_claim_next_app orders by priority, sort_order, created_at; SOFTWARE_ROLL is published with priority 50
-- (dice and case jobs use 100, the older software jobs 110), so it is always claimed first.
begin;

-- 1. The new rulebook for every user that has an active v4 and no v5 yet.
insert into public.ai_rules (user_id, version, title, active, rules)
select r.user_id, 5, 'Neribo rulebook v5', false,
       jsonb_set(
         jsonb_set(r.rules, '{job_kinds,SOFTWARE_ROLL}',
                   to_jsonb($r$SOFTWARE_ROLL is the ONE job of Software Development Clients for a day. Roll both dice and write the proposals in one answer. The job context has: dice (faces and what each means), inboxRoom, projectTypes, codePurchase, countries, ratesPerUsd, recentClientNames. Submit {contactFace, offerFace, comment (optional), offers:[...]} through submit_result. Write the rules below exactly. Roll honestly: do not choose faces to get more or fewer proposals. 1. Roll both dice honestly (0-5 each), then write exactly `wanted` proposals, where wanted = min(contacts, (contacts*share+50)/100) and never more than inboxRoom and never more than 3. wanted = 0 means an empty offers array. 2. Fit is mandatory. Pick a projectCode from projectTypes. The client must be the kind of organisation or person that would really order that project, and the title, summary and requirements must describe that same project for that same client. A church website comes from a church, ministry or NGO, never a school or a tailor; a school website from a school; a hotel website from a hotel or guesthouse; a clinic booking system from a clinic; a restaurant website from a restaurant or caterer (an individual chef is allowed); a portfolio website from an individual. Follow allowCompany and allowIndividual of the chosen type. 3. Names must be realistic for the country, different from recentClientNames and from each other. Foreign clients use names natural to that country. No celebrities, no real brands, no real people. 4. Do not write any email address, phone number or email-like text anywhere; the app makes contacts itself. Text must not contain the at sign or a web address. 5. fee is a whole number in units of the proposal's currency, a multiple of 50 (NGN: a multiple of 10000), so that units x ratesPerUsd-based naira value lies inside the project type's minNgn-maxNgn (kind CODE_PURCHASE: 1000000-50000000 naira). 6. deliveryWeeks: code purchase 1-2; naira value up to 700000: 2-3; up to 2000000: 3-6; above: 6-12. 7. summary: 1-3 sentences, specific to the client. requirements: 5-7 short, concrete, project-specific lines. Do not write payment, hosting, content or maintenance lines. 8. About 70% Nigerian clients (NGN) and 30% foreign (the currency of that country).$r$::text), true),
         '{software_roll}', to_jsonb($r$1. Roll both dice honestly (0-5 each), then write exactly `wanted` proposals, where wanted = min(contacts, (contacts*share+50)/100) and never more than inboxRoom and never more than 3. wanted = 0 means an empty offers array. 2. Fit is mandatory. Pick a projectCode from projectTypes. The client must be the kind of organisation or person that would really order that project, and the title, summary and requirements must describe that same project for that same client. A church website comes from a church, ministry or NGO, never a school or a tailor; a school website from a school; a hotel website from a hotel or guesthouse; a clinic booking system from a clinic; a restaurant website from a restaurant or caterer (an individual chef is allowed); a portfolio website from an individual. Follow allowCompany and allowIndividual of the chosen type. 3. Names must be realistic for the country, different from recentClientNames and from each other. Foreign clients use names natural to that country. No celebrities, no real brands, no real people. 4. Do not write any email address, phone number or email-like text anywhere; the app makes contacts itself. Text must not contain the at sign or a web address. 5. fee is a whole number in units of the proposal's currency, a multiple of 50 (NGN: a multiple of 10000), so that units x ratesPerUsd-based naira value lies inside the project type's minNgn-maxNgn (kind CODE_PURCHASE: 1000000-50000000 naira). 6. deliveryWeeks: code purchase 1-2; naira value up to 700000: 2-3; up to 2000000: 3-6; above: 6-12. 7. summary: 1-3 sentences, specific to the client. requirements: 5-7 short, concrete, project-specific lines. Do not write payment, hosting, content or maintenance lines. 8. About 70% Nigerian clients (NGN) and 30% foreign (the currency of that country).$r$::text), true)
from public.ai_rules r
where r.version = 4 and r.active
  and not exists (select 1 from public.ai_rules x where x.user_id = r.user_id and x.version = 5);

-- 2. Switch: old active off, new on (in this order, so only one is active at any time).
update public.ai_rules set active = false where version = 4 and active
  and exists (select 1 from public.ai_rules x where x.user_id = ai_rules.user_id and x.version = 5);
update public.ai_rules set active = true where version = 5 and not active
  and not exists (select 1 from public.ai_rules y where y.user_id = ai_rules.user_id and y.active);

commit;

-- Check afterwards:
--   select version, active, rules->'job_kinds'->'SOFTWARE_ROLL' is not null as has_roll from public.ai_rules order by version;
-- Roll back (if ever needed):
--   update public.ai_rules set active = false where version = 5;
--   update public.ai_rules set active = true  where version = 4;
