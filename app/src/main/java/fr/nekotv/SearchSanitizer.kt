package fr.nekotv

/**
 * Nettoie et valide une requête de recherche.
 * Retourne null si la requête est invalide.
 */
object SearchSanitizer {

    /** Sanitize la requête : trim, limites de longueur, suppression caractères dangereux. */
    fun sanitize(query: String?): String? {
        val trimmed = query?.trim()
        if (trimmed.isNullOrBlank()) return null
        if (trimmed.length > 200) return null  // Limite de longueur
        if (trimmed.length < 2) return null    // Longueur minimale

        // Retirer caractères de contrôle et normaliser espaces
        val cleaned = trimmed
            .replace(Regex("[\\p{Cc}\\p{Cf}]"), "")  // Caractères de contrôle
            .replace(Regex("\\s+"), " ")
            .trim()

        return if (cleaned.length >= 2) cleaned else null
    }
}