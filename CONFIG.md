# Adding a New Provider Source

## 1. Implement the Adapter
Place the adapter in `streamflix/app/src/main/java/com/streamflixreborn/streamflix/providers/`:
- Extend `TvSamaAnimeProvider` for HTML-based catalogues.
- Or implement `MediaAdapter` directly for non-HTML backends.

```kotlin
object NewProvider : TvSamaAnimeProvider("NewProvider", "https://example.com/") {
    override suspend fun search(query: String, canonicalIds: List<String>, titles: List<String>, page: Int): List<AppAdapter.Item> { ... }
    override suspend fun resolve(item: AppAdapter.Item): List<Video.Server> { ... }
}
```

## 2. Register the Provider
In `Provider.kt`, add to the `providers` map:

```kotlin
NewProvider to ProviderSupport(movies = false, tvShows = true)
```

## 3. Set Base URL
Use `SourceAddresses.current("NewProvider", "https://fallback.com/")` so domain changes persist across restarts.

## 4. Add Fixtures and Tests
- Save HTML fixtures to `streamflix/app/src/test/resources/<provider>/`.
- Write a test extending `AnimesSamaProviderFixtureTest` pattern.

## 5. Reliability
- All readers are validated by `VideoValidator` before being offered.
- `ReaderReliability` tracks success rate, quality, and language for final ranking.

## 6. Limits
- `REQUEST_TIMEOUT_MS = 3000` per search
- `RESOLVE_TIMEOUT_MS = 15000` per reader batch
- Max 12 concurrent searches, 6 concurrent resolutions
- Max 5 redirect hops (`SourceSecurity.redirectAllowed`)