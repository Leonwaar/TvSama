package fr.nekotv.downloads

/**
 * Centralized configuration for the download manager.
 *
 * All paths, quotas, concurrency, retry, and alert thresholds are defined here.
 * Values can be overridden by SharedPreferences or environment variables in the future.
 */
object DownloadConfig {
    // --- Concurrency ---
    const val MAX_CONCURRENT_DOWNLOADS = 2

    // --- Retry policy ---
    const val MAX_RETRY_ATTEMPTS = 3
    const val RETRY_BASE_DELAY_MS = 2000L // backoff exponential base
    const val RETRY_MAX_DELAY_MS = 30000L // cap on backoff

    // --- Source failover ---
    const val ENABLE_SOURCE_FAILOVER = true

    // --- Disk quotas ---
    /** Total disk quota in bytes (default 10 GB). */
    const val TOTAL_QUOTA_BYTES: Long = 10L * 1024 * 1024 * 1024
    /** Alert threshold: warn user when free space drops below this (default 1 GB). */
    const val ALERT_THRESHOLD_BYTES: Long = 1L * 1024 * 1024 * 1024
    /** Minimum free space to reserve before starting any download (default 500 MB). */
    const val MIN_FREE_SPACE_BYTES: Long = 500L * 1024 * 1024

    // --- Timeout limits ---
    /** Max file size per download in bytes (default 50 GB). */
    const val MAX_FILE_SIZE_BYTES: Long = 50L * 1024 * 1024 * 1024
    /** Max redirects per download (default 10). */
    const val MAX_REDIRECTS = 10
    /** Max duration per download in ms (default 24 hours). */
    const val MAX_DURATION_MS: Long = 24L * 60 * 60 * 1000

    // --- Rate limiting ---
    /** Max download creation requests per minute per user. */
    const val MAX_DOWNLOAD_REQUESTS_PER_MINUTE = 10

    // --- Retention policy ---
    /** Optional retention: delete watched content older than N days (disabled by default = -1). */
    const val RETENTION_DAYS_DISABLED: Long = -1
    const val RETENTION_DAYS: Long = RETENTION_DAYS_DISABLED

    // --- Paths ---
    const val FILMS_DIR = "Films"
    const val SERIES_DIR = "Séries"
    const val CACHE_DIR = "downloads_cache"
    const val TEMP_DIR = "downloads_temp"

    // --- Allowed protocols and domains (SSRF protection) ---
    val ALLOWED_PROTOCOLS = listOf("https", "http")
    val ALLOWED_DOMAINS = emptyList<String>() // Populate from provider allowlist if needed

    // --- Logging ---
    const val LOG_SENSITIVE_URLS = false // Never log signed URLs in clear text
}
