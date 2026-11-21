# Revue finale rc9 — 26 octobre 2026

Skills appliqués : apple-design, frontend-ui-engineering, code-review-and-quality,
performance-optimization, security-and-hardening et Ponytail. Application Android
native Compose/Media3 ; le « backend » concerné est la couche réseau, les adaptateurs,
l’annuaire d’adresses et le stockage local, sans serveur web propre à déployer.

## Problèmes corrigés, par gravité

| Priorité | Constat | Correction et emplacement |
| --- | --- | --- |
| P1 | Une scène post-générique pouvait être sautée en passant automatiquement à l’épisode suivant. | `IntroDb.kt`, `EpisodeAdvance.kt:9`, `Playback.kt:317` : conserve début et fin de la scène ; saut des crédits vers son début, changement après sa fin. |
| P1 | Sans outro connu, la fin du récit pouvait être coupée. | `EpisodeAdvance.kt:23` : avance automatique à la fin réelle ; proposition manuelle deux minutes avant. |
| P1 | FrenchManga ne rejoignait pas les autres catalogues : suffixe « Intégrale (année) » et saison zéro. | `streamflix/.../FrenchMangaProvider.kt:37` : métadonnées nettoyées, saison régulière à partir de 1. Test et deux médias Naruto VF/VOSTFR confirmés. |
| P1 | Voiranime renvoyait surtout les dérivés/OAV avant la série recherchée. | `streamflix/.../TvSamaAnimeProviders.kt:111` : tri par popularité ; Naruto VF et VOSTFR retrouvés, lecteurs natifs confirmés. |
| P1 | Les lignes sportives portant plusieurs indicateurs TV étaient ignorées. | `FrenchLiveTv.kt:46` : teste le bit TV ; vérification des valeurs 1, 257, 513 et exclusion de 256. |
| P2 | Le sélecteur de lecteurs Direct restait étroit sur téléphone. | `LiveScreen.kt:67` : dialogue pleine largeur/hauteur, liste défilante et bouton Fermer accessible. |
| P2 | L’historique réécrit lors d’un nouveau visionnage faisait perdre la liste des titres terminés. | `LibraryStore.kt:59`, `MainActivity.kt:1027` : registre persistant séparé et page « Contenu visionné » ; toutes les saisons régulières connues requises pour une série. |
| P2 | Les segments intro/outro restaient invisibles sur la chronologie. | `SegmentTimeBar.kt` et layouts Media3 : bandes cyan pour intro/récapitulatif, or pour fin ; lecture et recherche conservées. Test d’inflation du vrai layout. |
| P2 | La proposition suivante apparaissait trop tôt. | `EpisodeAdvance.kt:36`, `Playback.kt:751` : deux minutes avant la fin ou trente secondes avant l’outro ; focus TV à chaque apparition, protection post-générique. |
| P2 | Une validation humaine pouvait être résumée à tort en absence de lecteur. | `StreamFlixProviderManager.kt` : conserve l’état « Validation du site requise » lors des échecs d’extraction et du bilan de vérification. |
| P3 | En-tête serré et icône d’appairage non conforme à la demande. | `MainActivity.kt:847`, `CastSupport.kt:55` : écart 20 dp, éclair vert avec libellé accessible. |
| P3 | Titre du lecteur dans un bandeau, trop discret. | `Playback.kt:721` : texte blanc gras en haut à gauche, ombre légère, aucune plaque de fond. |
| P3 | Ancienne source Volkamax toujours utilisée par Direct. | `LiveScreen.kt`, `LiveStreamResolver.kt` : retirée des chemins actifs ; TVradioZap et Streamed utilisent leurs propres lecteurs. |

## Contrôle des sources

`ANIME_READERS_NETWORK.md` couvre les onze adaptateurs français. Sur Naruto S1E1,
seize contrôles de lecteurs/langues sur six sites ont fourni un média confirmé : Anime-Sama, Vostfree, Voiranime, MyFluneo,
FrenchManga et AnimeSite. Plusieurs lecteurs et langues sont contrôlés, pas seulement
la présence d’un iframe. Les playlists HLS trop courtes sont rejetées.

Cela ne valide pas chaque épisode de leurs catalogues. FRAnime et AnimeKO exigent
encore une validation de sécurité ; FrenchAnime a répondu 403 ; Animes-Sama et
Anime-Ultime n’ont pas trouvé la série exacte dans cet échantillon. Ces sources ne
sont pas présentées comme universellement opérationnelles.

Candidats étudiés : DessinAnime, Streaming-integrale, SenAnimes, KiboAnime. Aucune
nouvelle source n’a été activée sur la seule base de sa page d’accueil : lecteurs non
confirmés, restrictions HTTP ou application sans API publique confirmée. La liste
FMHY consultée : https://fmhy.net/non-english#french-francais ; site du candidat :
https://streaming-integrale.com/ .

Les adresses restent gérées par l’annuaire OùStreamer et les secours existants ;
les changements passent la validation du catalogue avant remplacement. Un captcha
n’est pas une preuve de panne et aucun contrôle de sécurité n’a été neutralisé.

## Interface, performance et sécurité

Relecture des pages Accueil, Recherche, Fiche, Lecteur, Ma liste, Reprendre,
Contenu visionné, Sources, TV, Direct, Réglages et Télécommande. Réutilisation des
composants de focus et des grilles/listes paresseuses. Aucune nouvelle dépendance.
Les nouvelles listes n’ajoutent pas de réseau au rendu ; la chronologie utilise le
ticker Media3 existant. Les contrôles réseau sont bornés et les WebView détruites
en sortie. Les en-têtes sensibles restent limités au domaine du média.

Pas d’appareil ADB connecté : rendu physique, fluidité du projecteur, décodage,
audio et captchas humains restent à vérifier sur appareil. Aucun gain de performance
chiffré ni disponibilité permanente de services tiers n’est revendiqué.
