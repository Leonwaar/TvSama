# TvSama

Application Android TV en Kotlin et Jetpack Compose. TvSama regroupe des catalogues
francophones, affiche les fiches et épisodes, résout des sources vidéo autorisées,
puis lit les médias avec AndroidX Media3/ExoPlayer.

## État fonctionnel

Le dépôt contient les fonctions suivantes :

- `MainActivity` initialise l'application, traite `tvsama://pair` et active le mode
	Picture-in-Picture pendant une lecture.
- `TvSamaApp` orchestre Accueil, Recherche, Ma liste, Reprendre, Sources, Directs,
	Réglages, Fiche et Lecture. Les recherches sont annulables et paginées.
- `StreamFlixProviderManager` interroge les fournisseurs activés en parallèle,
	applique les catégories et langues, fusionne les doublons, charge les fiches,
	résout les épisodes et extrait les serveurs vidéo.
- `LibraryStore` sauvegarde favoris, historique, épisode repris, position, langue,
	autoplay et recherches récentes.
- `CatalogIdentity` normalise les titres et fusionne les fiches multi-fournisseurs.
- `SourceDirectory` et `SourceDirectoryUi` gèrent l'annuaire local et l'état de santé
	des sources ; chaque fournisseur peut être désactivé.
- `LiveScreen` affiche les directs sportifs Volkamax. `LiveReminders` programme les
	rappels Android associés aux événements.
- `AppUpdates` cherche une release GitHub configurée, vérifie son téléchargement et
	propose l'installation via `FileProvider`.
- `IntroDb` récupère et valide les timecodes d'introduction à partir d'un identifiant IMDb.
- `CastSupport` fournit Google Cast et le scan/appairage QR.
- `Ui` contient le thème, cartes, rails, réglages et états vides.

## Catalogue et sources

La recherche combine les fournisseurs activés et essaie plusieurs formes de la
requête : accents retirés, espaces supprimés et forme avec tirets. Les catégories
supportées sont films, séries, animation et directs. Les marqueurs `VF` et `VOSTFR`
sont normalisés avant affichage.

Une fiche peut contenir plusieurs références. Le chargement des saisons est parallèle
avec des délais indépendants. La résolution des sources sélectionne l'épisode et la
langue, utilise un cache court, limite la concurrence, détecte langue/qualité/
sous-titres/en-têtes, vérifie la joignabilité et classe les résultats par qualité,
latence puis fournisseur.

Les liens ajoutés dans **Sources > Flux personnels** doivent être des URL HTTPS
directes vers un média que l'utilisateur est autorisé à lire. Les pages web, embeds
tiers et protections antibot ne sont pas extraits.

## Lecteur

`TvSamaPlayer` crée un `ExoPlayer` Media3 par source et détecte HLS (`.m3u8`), DASH
(`.mpd`) et MP4 (`.mp4`). Il configure les en-têtes, sous-titres, langue audio et
reprise à la dernière position sauvegardée.

Fonctions de lecture :

- contrôles Media3, barre de progression, écran toujours allumé et plein écran ;
- qualité réellement détectée à partir de la taille vidéo ;
- doubles taps cumulatifs de 15 secondes à gauche ou à droite ;
- accélération de la recherche avec les touches de télécommande ;
- épisodes précédent/suivant et autoplay de l'épisode suivant ;
- bouton pour ignorer l'introduction via IntroDB ;
- sous-titres activables et choix VF/VOSTFR au démarrage ;
- Picture-in-Picture automatique et bouton manuel si compatible ;
- Google Cast, suivi de position distant et reprise locale après déconnexion ;
- rotation entre serveurs si un serveur échoue ;
- sauvegarde périodique de la position, y compris lors de l'arrêt de l'activité.

Les serveurs nécessitant des en-têtes privés ne sont pas envoyés au récepteur Cast
par défaut, car celui-ci ne peut pas transmettre des en-têtes arbitraires.

## Reprendre, favoris et fiches

**Reprendre** garde un titre par série, le dernier épisode, la saison, la position et
la durée. À moins de 30 secondes de la fin, l'épisode est considéré comme terminé et
l'épisode suivant est proposé. Une entrée peut être retirée sans supprimer les autres
données locales.

`EpisodePicker` regroupe les épisodes par saison, fait défiler automatiquement
jusqu'à l'épisode repris et affiche une progression par épisode. Les fiches peuvent
afficher première diffusion, dernière diffusion et statut selon les métadonnées.

## TMDB

TMDB est intégré comme enrichissement optionnel des fiches, pas comme fournisseur de
flux vidéo. Quand un token Read Access est configuré, `TmdbMetadata` complète les
posters, bannières, synopsis, genres, dates, statut, identifiant IMDb et lien de
métadonnées. Une erreur TMDB ne bloque pas le catalogue principal.

Le token doit être fourni au build sans être commité :

```bash
TMDB_READ_TOKEN="<token>" ./gradlew assembleDebug
```

La propriété Gradle `-PtmdbReadToken=<token>` est également acceptée. Le token est
injecté dans l'APK et reste donc récupérable par une personne qui possède l'APK ; un
dépôt GitHub privé ne change pas cette limite. Pour une protection réelle, utiliser
un relais serveur. La clé partagée pendant la session devrait être révoquée et
remplacée si elle a été utilisée ailleurs.

## Construction et validation

Prérequis : JDK 17 et Android SDK 36. Depuis la racine du dépôt :

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest --no-daemon
```

L'APK de validation est généré dans `app/build/outputs/apk/debug/app-debug.apk`.
Les tests unitaires couvrent notamment la reprise de lecture, le classement des
sources et l'audit réseau.

La version compilée actuelle est `v1.1.76` avec le `versionCode` `76`. Les compilations
de validation ne constituent pas une livraison et ne doivent pas incrémenter la
version par elles-mêmes.

### Correctifs v1.1.70

Le choix VF/VOSTFR est directement dans la fiche de lancement. La préférence est
mémorisée et utilisée pour les épisodes suivants, sans page ou dialogue de langue.
Les fiches affichent aussi leur couverture sur téléphone. Les URL relatives des
images sont résolues avec le fournisseur et son Referer accompagne le chargement.

« Actualiser l’annuaire » applique les domaines OùStreamer aux adaptateurs connus.
« Actualiser les sources » effectue cette même mise à jour avant les contrôles.
Les adresses sont persistées, utilisées par les clients HTTP déjà construits et
réappliquées aux anciens liens des favoris. Les domaines des hébergeurs vidéo tiers
restent indépendants. Un changement de domaine ne corrige pas un captcha ou un
changement de structure du site. L’APK utilise cette configuration persistante ;
il ne peut pas réécrire ses constantes compilées ni modifier les serveurs distants.

IntroDB : validation IMDb/saison/épisode, précision des secondes fractionnaires,
cache d’une heure pour les segments trouvés, bouton local et Cast seulement pendant
l’introduction et dans les limites de la durée du média. Aucun segment inventé en
cas d’absence de données. API publique : https://api.introdb.app/openapi.json.

Les mises à jour utilisent les releases publiques de `Leonwaar/TvSama`, sans jeton.
Une release dont le tag est plus récent que la version installée est proposée sans
comparer les `versionCode` des APK. Publier un APK universel signé avec la même clé que l’app installée.
Le téléchargement utilise `browser_download_url` ;
la taille, l’empreinte SHA-256 lorsqu’elle est fournie, le package et la signature
sont vérifiés avant de proposer l’installation Android.

### Correctifs v1.1.72

PiP automatique uniquement pendant la lecture locale active sur les appareils
compatibles ; contrôles et bandeau de sources masqués en PiP. Précédent/suivant
sont intégrés à la ligne du timecode. Les boutons intro/outro apparaissent en bas
à gauche pendant les segments identifiés par IntroDB (outro des films incluse).

L’adaptateur Animes-Sama lit le catalogue intégré à Flixnet/animefr, ses saisons,
ses boutons VF/VO et ses iframes de lecteurs. La langue suit le bouton du serveur,
pas le badge générique SUB. Sibnet utilise HTTPS et conserve ses en-têtes de
lecture. Le contrôle média utilise GET partiel, car certains serveurs refusent HEAD.
Les alias de Classroom of the Elite et les suffixes explicites de saison sont
regroupés ; leurs références et leurs épisodes restent accessibles.

L’APK universel inclut les ABI des dépendances, sans séparation par architecture.
Minimum : Android 6 (API 23). PiP : Android 8+ et prise en charge par l’appareil.
La compilation universelle ne constitue pas un test matériel de tous les modèles.

### Vérification avant publication d’une mise à jour

Pour chaque release, changer `versionName` dans `app/build.gradle.kts`, puis recompiler.
Utiliser ce nom comme tag GitHub (actuellement `v1.1.76`) et joindre le nouvel APK
universel signé avec la même clé que la version installée. Augmenter `versionCode`
reste recommandé pour que l’installateur Android accepte la mise à jour dans tous
les cas ; l’application ne l’exige plus avant de proposer l’installation.
