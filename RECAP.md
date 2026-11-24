# TvSama/Streamflix Adapter Layer Recap

## Architecture Overview
The adapter layer provides a robust, failure-isolated interface for fetching media across multiple providers.

### Core Components
- `MediaAdapter`: Contract for `search`, `resolve`, and `health`.
- `BaseMediaAdapter`: Legacy provider wrapper with robust matching and metrics.
- `MediaAdapterManager`: Coordinator handling concurrency, stale-while-revalidate caching, and reliability probes.
- `ReaderReliability`: Per-reader scoring (reliability, quality, language preference).
- `VideoValidator`: Native media reachability validation.

### Provider Configuration
- Add new providers in `Provider.kt`'s `providers` map.
- Implement `MediaAdapter` or extend `TvSamaAnimeProvider`.
- Use `SourceAddresses` for domain rotation support.

### Metrics and Monitoring
- Admin dashboard consumes `MediaAdapterManager.getAdminMetrics()`.
- Reliability and latency are tracked per-reader and per-provider.
