package fr.nekotv

/** Common translated titles supplement the alternate titles supplied by catalogues. */
object TitleAliases {
    private val groups = listOf(
        listOf("Mushoku Tensei", "Moshoku Tensei", "Mushoku Tensei: Jobless Reincarnation", "Mushoku Tensei: Isekai Ittara Honki Dasu"),
        listOf("The Simpsons", "Les Simpson", "Les Simpsons", "Simpson"),
        listOf("Hunter x Hunter", "Hunter X Hunter", "Hunter × Hunter", "HxH", "Hunter x Hunter Kai", "ハンター×ハンター"),
        listOf("The Disastrous Life of Saiki K.", "Saiki Kusuo no Psi-nan", "Saiki Kusuo", "Saiki Kusuo no Sainan", "Saiki Kusuo no Ψ Nan (TV)", "Saiki Kusuo no Ψ Nan", "斉木楠雄のΨ難", "Saiki Kusuo no Ψ-nan: Kanketsu-hen", "Saiki Kusuo no Ψ-nan: Ψ-shidou-hen", "The Disastrous Life of Saiki K.: Reawakened"),
        listOf("Classroom of the Elite", "Classroom of Elite", "Youkoso Jitsuryoku Shijou Shugi no Kyoushitsu e", "ようこそ実力至上主義の教室へ"),
        listOf("Attack on Titan", "L’Attaque des Titans", "Shingeki no Kyojin", "進撃の巨人"),
        listOf("Demon Slayer", "Kimetsu no Yaiba", "鬼滅の刃"),
        listOf("Assassination Classroom", "Ansatsu Kyoushitsu", "暗殺教室"),
        listOf("My Hero Academia", "Boku no Hero Academia", "僕のヒーローアカデミア"),
        listOf("Spirited Away", "Le Voyage de Chihiro", "Sen to Chihiro no Kamikakushi", "千と千尋の神隠し"),
        listOf("Howl’s Moving Castle", "Le Château ambulant", "Howl no Ugoku Shiro", "ハウルの動く城"),
        listOf("Your Name", "Kimi no Na wa", "君の名は"),
        listOf("Weathering with You", "Les Enfants du temps", "Tenki no Ko", "天気の子"),
        listOf("Princess Mononoke", "Princesse Mononoké", "Mononoke Hime", "もののけ姫"),
        listOf("The Lord of the Rings: The Fellowship of the Ring", "Le Seigneur des anneaux : La Communauté de l’anneau"),
        listOf("The Lord of the Rings: The Two Towers", "Le Seigneur des anneaux : Les Deux Tours"),
        listOf("The Lord of the Rings: The Return of the King", "Le Seigneur des anneaux : Le Retour du roi")
    )
    private val combiningMarks = Regex("\\p{M}")
    private fun key(value: String) = java.text.Normalizer.normalize(value.lowercase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD)
        .replace(combiningMarks, "").filter { it.isLetterOrDigit() }
    private val index = groups.flatMap { group -> group.map { key(it) to group } }.toMap()
    fun variants(value: String): List<String> = index[key(CatalogIdentity.numberedBase(value))].orEmpty()
    fun canonical(value: String): String = index[key(CatalogIdentity.seriesTitle(value))]?.first() ?: value
    fun seasonNumber(value: String): Int? = if (!value.contains("Saiki", true)) null else when {
        value.contains("Kanketsu-hen", true) -> 3
        value.contains("shidou-hen", true) || value.contains("Saiki K.: Reawakened", true) -> 4
        else -> null
    }
}
