# Implementation Plan: Anime search relevance + anti-bot windows + full provider integration

## Overview
The user reports two live regressions on top of the standing objective (every anime
provider searchable and playable natively in TvSamaPlayer):
1. Anti-bot windows keep opening ("pleins de fenêtres antibot").
2. Search always shows the same popular cards first (Cool Attitude, Mother Witch,
   The Desinvited) instead of query-relevant results.
Standing objective: all anime providers (FRAnime, Anime-Sama/Animes-Sama, AnimeKO,
AnimeSite, AnimeUltime, Vostfree, VoirAnime, Papadustream, FrenchAnime, FrenchManga…)
searchable and natively playable; "overflow" (meaning to confirm) still has no provider.

## Evidence gathered (2026-11-18)
- APK `app-debug.apk` built 10:21, newer than every source edit (latest source 10:16),
  so the current code is what produced the reported symptoms — not a stale build.
- Search gate exists: `MainActivity.kt:740-746` waits for the provider sweep then filters
  via `isCatalogueMatch` (`TmdbMetadata.kt:179`). Blank-query browse mode is NOT filtered
  (`MainActivity.kt:736,743`), so the SEARCH page opens on providers' popular home pages.
- Paging path `loadMore()` (`MainActivity.kt:354-368`) appends page 2+ **without**
  `isCatalogueMatch` — a provider that ignores the query on later pages would inject
  popular cards unfiltered.
- Anti-bot surfaces: visible `SourceAccess` dialog (`MainActivity.kt:383`, `LiveScreen.kt:87`),
  `BypassWebViewActivity` auto-launched from `MainMobileActivity.kt:528` and
  `PlayerMobileFragment.kt:306`, and the still-present hidden `SourceWebSession.render()`
  (`SourceWebSession.kt:51`, currently uncalled — dead but dangerous).
- `refreshSources()` (`StreamFlixProviderManager.kt:541`) and `verifySourceMedia()`
  (`:583`) sweep all enabled providers; any of these that triggers a visible Access
  dialog per host would explain "many windows".

## Architecture decisions
- Keep the visible Access screen as the only anti-bot path; one window per host,
  45 s cooldown already in `SourceWebSession`.
- Search relevance is enforced at the commit point (first page) and must also cover
  paged results; blank-query browse on the SEARCH page must not show popular cards.
- Site-side failures (404, captcha, DNS, short HLS) are documented, not "fixed" in code.

## Task List

### Phase 1: Reproduce and localize
- [ ] Task 1: Prove how popular cards reach the grid (blank-query browse vs paging vs gate bypass) — focused test first.
- [ ] Task 2: Trace every automatic WebView/Access trigger; identify which one fires repeatedly.

### Phase 2: Fixes
- [ ] Task 3: Filter paged results with `isCatalogueMatch`; blank-query SEARCH page shows empty state, not popular home.
- [ ] Task 4: One visible Access dialog per host (cooldown + single instance); remove/disable dead `render()`; make `BypassWebViewActivity` user-confirmed.

### Phase 3: Provider integration (standing objective)
- [ ] Task 5: FRAnime visible-validation end-to-end (validationUrl → Access → retry) with network audit.
- [ ] Task 6: Triage remaining providers (Anime-Sama 404, AnimeKO captcha, Animes-Sama/Anime-Ultime strict match, DNS sites, FrenchManga HLS, MyFluneo 403/404): fix code-fixable, document site-side.

### Phase 4: Verify
- [ ] Task 7: Offline tests + `assembleDebug` + `-PnetworkAudit=true`; update `verification/ANIME_READERS_NETWORK.md`; rebuild APK.

### Checkpoint: After Phase 1
- [ ] Root causes named with file:line evidence; no code changed yet

### Checkpoint: After Phase 2
- [ ] Regression tests fail without fix, pass with it; no automatic anti-bot window in any path

### Checkpoint: Complete
- [ ] All anime providers searchable and playable natively; reports updated

## Risks and Mitigations
| Risk | Impact | Mitigation |
| --- | --- | --- |
| Symptom is UI-only and not reproducible in Robolectric | High | Add instrumentation logs at commit points; ask user to test rebuilt APK |
| "overflow" meaning unknown | Med | Ask user; do not guess |
| Site-side blockers stall providers | Med | Document and move on; do not bypass challenges |

## Open Questions
- What is "overflow" — device name, provider, or remote-link target?
- Is the device running the 10:21 APK, and is it TV or phone (chooses SourceAccess vs BypassWebViewActivity)?
