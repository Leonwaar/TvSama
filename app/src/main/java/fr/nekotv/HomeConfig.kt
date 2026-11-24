package fr.nekotv

/** Centralised configuration for home page releases, synopsis resolution and date formatting. */
object HomeConfig {
    /** Default region used for release queries (e.g. TMDB region=FR). */
    const val REGION = "FR"
    /** Default language used for release queries (e.g. TMDB language=fr). */
    const val LANGUAGE = "fr"

    /** Maximum number of items fetched per row. */
    const val ROW_LIMIT = 20
    /** TTL (milliseconds) for translated-overview cache entries. */
    const val TRANSLATION_TTL_MS = 6L * 60 * 60 * 1000 // 6 hours

    /** Whether automatic (machine) translation of non-French synopses is enabled. */
    const val AUTO_TRANSLATION_ENABLED = false

    /** Translation provider key used when AUTO_TRANSLATION_ENABLED is true. */
    const val TRANSLATION_PROVIDER = "none"

    /** Whether the optional header banner is enabled. */
    const val BANNER_ENABLED = false

    /** Number of visible cards per row on large screens / tablets / mobile. */
    const val CARDS_WIDE = 6
    const val CARDS_TABLET = 4
    const val CARDS_MOBILE = 2.5f
}
