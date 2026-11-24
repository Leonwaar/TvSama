# Search Implementation Recap

## Architecture
- **Endpoint**: `/search?q=` (implemented as `SearchService.search` internal suspend function).
- **Parallelism**: `StreamFlixProviderManager.atomicSearch` with parallel provider execution.
- **Debounce**: 250ms enforced in `MainActivity` search LaunchedEffect.
- **Cancellation**: `AbortController`-like cancellation using `searchJob` cancellation in `SearchService`.

## Configuration
- `MAX_RESULTS`: 20
- `SEARCH_TIMEOUT_MS`: 2500ms
- `SEARCH_DEBOUNCE_MS`: 250ms
- `SERVER_CACHE_TTL_MS`: 5 minutes
- `RATE_LIMIT_MAX_REQUESTS`: 30 per minute

## UX Features
- **Highlighting**: Implemented via `SearchHighlighting.kt` using `AnnotatedString` in `PosterCard`.
- **Keyboard**: 
    - `/` or `Ctrl+K` to focus search.
    - `Escape` to close/clear.
    - Deletable recent history via `LibraryStore.removeSearch`.
    - Badge system for Content Type and Availability in `PosterCard`.

## Known Limits & Robustness
- **Robustness**: Sanitization enforced via `SearchSanitizer`, rate limiting implemented in `SearchService`.
- **Limits**: Cache size capped at 1000 entries.

## Adding a New Provider
1. Add to `Provider.providers` in `StreamFlixProviderManager`.
2. Implement `provider.search(query, ...)` with native HLS/DASH/MP4 media resolution.
3. Add to `atomicSearch` provider list if needed.
