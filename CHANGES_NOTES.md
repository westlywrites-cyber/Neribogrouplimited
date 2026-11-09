# NeriboGroup update: Staff copy buttons, Adverts, Claude-only Software roll

Backend: NOT applied. backend/001_rulebook_v5_software_roll.sql waits for the owner's approval.

## Part A: Staff detail
Copy buttons for Email, Phone, Bank, Account number, plus "Copy bank details". (StaffDetailScreen.kt)

## Part B: Adverts (Google Play apps)
New: data/Advert.kt, feature/appdetail/AdvertUi.kt. Changed: Models.kt, LedgerStorage.kt, PlayStore.kt,
DiceEngine.kt, AppUsersScreen.kt, AppDashboardScreen.kt, DiceUi.kt, SelfCheck.kt, AppViewModel.kt, MainActivity.kt.
Row ceiling: MAX_STORED_USER_ROWS_PER_APP = 20,000 (worst-case row 258 bytes -> 5.16 MB + 1.5 MB reserve).
No schema version bump: old files simply have no adverts.

## Part C: Software roll only by Claude (one SOFTWARE_ROLL job a day)
Changed: SoftwareAi.kt (extended, it already existed), SoftwareGenerator.kt, Software.kt, SoftwareJson.kt,
SoftwareEngine.kt (pruneOffers internal), AiJobs.kt, SoftwareAiJobs.kt, SyncStore.kt, SoftwareDiceUi.kt,
SoftwareClientsScreen.kt, AppViewModel.kt, MainActivity.kt.

## Cross-file notes (code differed from the brief)
- The code already had Phase 6 (two dice jobs + WRITE_PROPOSAL jobs, built-in fallback after ~48 h, rulebook v4).
  Part C replaces it: the old publishing and fallback are no longer reachable (old private functions remain, unused).
  Old submitted jobs of the older kinds are rejected; pending ones are left to expire.
- data/SoftwareAi.kt already existed (slots/presets), so SoftwareAi was appended to it, not created.
- Claims go through agent_claim_next_app (priority, sort_order, created_at; no day filter), not agent_claim_next_job.
  SOFTWARE_ROLL priority 50 is always first. No SQL or Edge Function change is needed except rulebook v5.
- Not compiled here (no Kotlin compiler in this environment): the GitHub Actions build is the first compile.
