# Tasks — Anime search relevance + anti-bot windows + full provider integration

## Phase 1: Reproduce and localize
- [ ] Task 1: Prove how popular cards reach the grid (blank-query browse vs paging vs gate bypass) — focused test first.
- [ ] Task 2: Trace every automatic WebView/Access trigger; identify which one fires repeatedly.

## Checkpoint: After Phase 1
- [ ] Root causes named with file:line evidence; no code changed yet

## Phase 2: Fixes
- [ ] Task 3: Filter paged results with `isCatalogueMatch`; blank-query SEARCH page shows empty state, not popular home.
- [ ] Task 4: One visible Access dialog per host (cooldown + single instance); remove dead `render()`; `BypassWebViewActivity` user-confirmed.

## Checkpoint: After Phase 2
- [ ] Regression tests fail without fix, pass with it; no automatic anti-bot window in any path

## Phase 3: Provider integration
- [ ] Task 5: FRAnime visible-validation end-to-end (validationUrl → Access → retry) with network audit.
- [ ] Task 6: Triage remaining providers (Anime-Sama 404, AnimeKO captcha, Animes-Sama/Anime-Ultime strict match, DNS sites, FrenchManga HLS, MyFluneo 403/404).

## Phase 4: Verify
- [ ] Task 7: Offline tests + `assembleDebug` + `-PnetworkAudit=true`; update `verification/ANIME_READERS_NETWORK.md`; rebuild APK.

## Checkpoint: Complete
- [ ] All anime providers searchable and playable natively; reports updated
