# TvSama

Application Android TV en Kotlin et Jetpack Compose. TvSama regroupe des catalogues
francophones, affiche les fiches et épisodes, résout des sources vidéo autorisées,
puis lit les médias avec AndroidX Media3/ExoPlayer.

Version en cours : `v1.8.88-rc6` (validation, pas de release finale).
Vavoo est retiré ; les résultats réseau de Wiflix et Coflix restent variables. Papadustream et FRAnime
restent soumis à leurs captchas interactifs. Voir [le contrôle ciblé](verification/PRIORITY_SOURCES.md)
et [les changements de cette version](verification/PRIORITY_PATCHES.md).
Le correctif rc2 stabilise [l’affichage progressif « À découvrir »](verification/DISCOVERY_STABILITY.md).
L’[audit d’interface classé](verification/APPLE_DESIGN_AUDIT.md) recense les problèmes corrigés sur les écrans Compose.
Le [contrôle des extraits vidéo](verification/PROMO_VIDEO_GUARD.md) écarte les faux lecteurs courts et précise quand une source peut recevoir une coche verte.
Le [bilan des correctifs de revue rc4](verification/REVIEW_FIXES.md) détaille la sécurité des sessions, les replis et la réduction du travail de sauvegarde.
Le [correctif des catalogues et de Coflix rc5](verification/SOURCE_DIVERSITY_RC5.md) distingue les corrections testées des lecteurs encore indisponibles.
L’[actualisation autonome des adresses](verification/AUTONOMOUS_ADDRESSES.md) conserve OùStreamer en priorité et ajoute AdressesDirect et le wiki FMHY en secours.
Le [téléchargement mobile hors ligne](verification/OFFLINE_MOBILE_PLAN.md) est préparé pour une prochaine mise à jour ; il n'est pas encore implémenté.
Le [logo premium, minimaliste et moderne](verification/NEXT_RELEASE_BACKLOG.md) est prévu pour la prochaine mise à jour.

Le [correctif animés et écran OLED rc6](verification/RC6_VALIDATION.md) décrit les nouveaux lecteurs, Vostfree et les limites des essais.

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

### Correctifs v1.8.79

Le démarrage affiche un chargement modal annulable par « Fermer », puis attend
la première image pendant 25 secondes maximum. Un échec de démarrage propose la
page vidéo dans le navigateur. Les directs suivent les iframes publiées avec leur
Referer parent ; leur résolution reste annulable. Les WebView invisibles de
résolution n’interceptent plus les touches tactiles.

« À découvrir » est une grille verticale paginée automatiquement. « Reprendre »
occupe plusieurs colonnes (deux sur téléphone) et ses cartes d’accueil sont agrandies.
Les boutons supérieurs respectent les zones réservées aux encoches. Les réglages
étroits placent leurs commandes sous le texte, notamment la minuterie.

MyFluneo utilise son API publique paginée ; Coflix inclut films, séries, animés
et drames ; Wiflix utilise Flemmix et parcourt aussi les films anciens. Les alias
anglais, japonais, VF/VOSTFR et saisons de Saiki sont regroupés en conservant les
références. L’affiche principale est conservée entre les versions.

« À l’affiche » prépare et mémorise pendant six heures une sélection de films
sortis depuis moins de six mois, populaires, dont un média français a répondu.
Cette vérification réseau ne prouve pas le décodage sur chaque appareil.

L’annuaire présente un bilan des adresses appliquées et mémorisées. Il conserve
une adresse précédente lorsqu’une proposition est inaccessible ou incompatible.
Chaque source possède un interrupteur, y compris les catalogues accessibles par
le navigateur intégré. Ces derniers conservent leur session et proposent chargement,
actualisation, navigateur externe et plein écran vidéo ; leur présence ne signifie
pas qu’ils disposent d’un adaptateur natif de recherche et d’extraction.

L’audit des pages de catalogue est dans `verification/REQUESTED_SOURCES.md`.
Les protections et comptes des services tiers restent gérés par ces services.

`TvSamaPlayer` crée un `ExoPlayer` Media3 par source et détecte HLS (`.m3u8`), DASH
(`.mpd`) et MP4 (`.mp4`). Il configure les en-têtes, sous-titres, langue audio et
reprise à la dernière position sauvegardée.

Fonctions de lecture :

- contrôles Media3, barre de progression, écran toujours allumé et plein écran ;
- qualité réellement détectée à partir de la taille vidéo ;
- doubles taps cumulatifs de 15 secondes à gauche ou à droite ;
- accélération de la recherche avec les touches de télécommande ;
- épisodes précédent/suivant ; autoplay au début d’une outro IntroDB valide, au plus tard à 30 secondes de la fin ;
- commandes masquées après 8 secondes d’inactivité, touche OK pour les afficher puis lecture/pause sur la barre ;
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

L’effacement de l’historique dans les réglages demande confirmation et conserve
une copie locale persistante. « Restaurer l’historique » récupère le dernier
effacement sans perdre les nouvelles lectures ni les favoris. Un nouvel effacement
non vide remplace cette copie. Les suppressions anciennes reçues par synchronisation
ne rétablissent pas l’historique effacé et ne masquent pas une récupération explicite.

Le bilan des corrections et des limites de disponibilité du 16 octobre 2026 est
dans `verification/SOURCE_RECOVERY.md`.

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
# Compilation de test debug (sans signature release)
./gradlew assembleDebug
./gradlew testDebugUnitTest --no-daemon
```

### Tutoriel : Compiler un APK Universel Release (non-debug)

Pour générer un **APK universel Release** (optimisé, sans surcoût de debug, compatible avec toutes les architectures CPU Android) :

#### Méthode 1 : Script automatisé (Recommandé)

Le dépôt intègre un script qui compile en mode `:app:assembleRelease`, zipaligne (alignement 16 Ko / 4 octets pour Android 15+) et signe l'APK :

```bash
python3 scripts/build-apk.py
```

- **Sortie générée :** `../releases/TvSama-<version>-universal.apk` ainsi que son empreinte `.apk.sha256`.
- **Signature personnalisée (optionnel) :** Par défaut, la clé `~/.android/debug.keystore` est utilisée pour signer l'APK release. Si vous utilisez un keystore de production :
  ```bash
  TVSAMA_KEYSTORE="/chemin/vers/mon-keystore.jks" \
  TVSAMA_KEY_ALIAS="mon-alias" \
  TVSAMA_STORE_PASSWORD="motdepassestore" \
  TVSAMA_KEY_PASSWORD="motdepassecle" \
  python3 scripts/build-apk.py
  ```

#### Méthode 2 : Directement via Gradle & Android Build-Tools

Si vous préférez exécuter les étapes manuelles :

1. **Compiler l'APK Release non signé :**
   ```bash
   ./gradlew :app:assembleRelease --console=plain
   ```
   L'APK non signé est produit dans `app/build/outputs/apk/release/app-release-unsigned.apk`.

2. **Aligner les binaires (zipalign) :**
   ```bash
   $ANDROID_HOME/build-tools/36.0.0/zipalign -P 16 -f 4 \
     app/build/outputs/apk/release/app-release-unsigned.apk \
     app/build/outputs/apk/release/app-release-aligned.apk
   ```

3. **Signer l'APK (apksigner) :**
   ```bash
   $ANDROID_HOME/build-tools/36.0.0/apksigner sign \
     --ks ~/.android/debug.keystore \
     --ks-key-alias androiddebugkey \
     --ks-pass pass:android \
     --key-pass pass:android \
     --v4-signing-enabled false \
     --out app/build/outputs/apk/release/TvSama-universal.apk \
     app/build/outputs/apk/release/app-release-aligned.apk
   ```

4. **Vérifier l'alignement et la signature :**
   ```bash
   $ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs app/build/outputs/apk/release/TvSama-universal.apk
   $ANDROID_HOME/build-tools/36.0.0/zipalign -c -P 16 4 app/build/outputs/apk/release/TvSama-universal.apk
   ```

L'APK universel inclut toutes les bibliothèques et architectures supportées (arm64-v8a, armeabi-v7a, x86_64), prêt pour l'installation sur Android TV et smartphones.

La version actuelle est `v1.8.87` avec le `versionCode` `94`. Les compilations
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
La détection trie les releases par date `published_at`, indépendamment du tag.
La date de compilation sert de référence initiale, puis la publication de l’APK réellement installé est mémorisée.
Un téléchargement annulé reste proposé ; republier le même APK avec son SHA-256 ne le repropose pas. Publier un APK universel signé avec la même clé que l’app installée.
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
Utiliser ce nom comme tag GitHub (actuellement `v1.8.79`) et joindre le nouvel APK
universel signé avec la même clé que la version installée. Augmenter `versionCode`
est obligatoire à chaque ajout ou correction fonctionnelle.

### Reconstruire après nettoyage

Le SDK de compilation reste dans `/home/leon/Android/Sdk`, indiqué par `local.properties`.
Les wrappers Gradle, configurations, sources Android/Windows, dépendances déclarées,
lockfile npm, fixtures et tests sont conservés. Les caches globaux de dépendances
et la clé existante dans `~/.android/debug.keystore` restent en place.

```bash
python3 scripts/build-apk.py
cd windows
npm ci
npm run build
```

Le script Android compile, aligne, signe et vérifie l’APK, puis le dépose dans
`../releases/`, séparément des sources. Pour une autre clé existante : variables
`TVSAMA_KEYSTORE`, `TVSAMA_KEY_ALIAS`, `TVSAMA_STORE_PASSWORD`, `TVSAMA_KEY_PASSWORD`.
Conserver la même clé pour installer les prochaines versions en mise à jour.

`python3 scripts/clean-generated.py` affiche les sorties régénérables ; `--apply`
les supprime. L’option `--remove-test-sdk` retire uniquement le SDK de test voisin,
après arrêt de son émulateur. Un émulateur ou appareil ADB reste nécessaire pour
exécuter `:app:connectedDebugAndroidTest` ; il n’est pas nécessaire pour construire l’APK.

### Correctifs v1.8.80

Le chargement annulable apparaît après une demande de lecture nécessitant une recherche de source.
Le lecteur conserve ses contrôles et propose un nouvel essai ou le navigateur après un échec.
Budgets réduits : recherche 18 secondes, première image 12 secondes, résolution du direct 20 secondes.
Les requêtes réseau synchrones peuvent attendre leur propre délai avant de rendre la main.
L’annuaire charge ses pages en parallèle et conserve la validation des adresses avant leur mémorisation.
La une utilise la base des sorties cinéma, en cache six heures, sans extraction vidéo préalable ;
la disponibilité réelle du lecteur est vérifiée au lancement.

« À découvrir » utilise un profil local pondéré par récence et engagement, les favoris et les recherches
(y compris les alias et les métadonnées des résultats précédents). Le classement combine thèmes transversaux,
rareté des mots, ordre de popularité des catalogues, pénalité pour les titres déjà vus et diversité de formats.
Une place sur cinq privilégie l’exploration. Aucun profil n’est envoyé à un service de recommandation.
Le réglage « Voir les sources indisponibles au démarrage » contrôle l’affichage du dernier état connu,
sans ajouter de vérification réseau bloquante au lancement.

### Correctifs v1.8.81

Sur téléphone, le logo est séparé du bouton Menu par 12 dp. Le menu sportif se nomme Direct ; la catégorie IPTV reste TV.
Les sorties françaises AlloCiné sont associées par titre/alias et année aux catalogues activés de Coflix, Wiflix et WarFlix.
AlloCiné fournit des métadonnées, jamais une URL vidéo. Le catalogue cinéma WarFlix reste un secours ;
les flux se vérifient au lancement, une fiche trouvée ne garantit pas qu’un hébergeur répondra.
Les pages cinéma sont chargées en parallèle, sans extraction vidéo ni recherche en cascade. Le cache reste affichable
pendant l’actualisation et un échec affiche Réessayer au lieu de supprimer silencieusement la section.

À découvrir analyse désormais des mots séparés, avec une similarité normalisée, la popularité chiffrée disponible,
les notes pondérées par le nombre de votes et la récence de sortie. L’ordre d’un fournisseur n’est plus traité comme
une mesure de popularité. Les genres des cartes WarFlix sont renseignés sans requête supplémentaire.
Les contenus déjà vus passent après les découvertes non vues ; une place sur cinq explore les succès hors du profil.

Le vrai synopsis est choisi avant les métadonnées générales du site. Les slogans de streaming sont ignorés,
y compris pour les anciennes fiches enregistrées. Coflix fournit aussi le synopsis des séries.
Pour Volkamax, les lecteurs web publiés sont essayés en parallèle, chacun avec son budget ; un échec de première
image essaie un autre lecteur avant le navigateur. Le menu Choisir le lecteur permet de sélectionner les lecteurs
et leurs libellés réels. Aucun changement local ne peut rétablir un hébergeur distant en panne.

### Correctifs v1.8.82

Les adaptateurs qui attendent une racine de site ignorent désormais les chemins des anciennes adresses externes
mémorisées (par exemple WarFlix `/film/1423191/` ou MyFluneo `/catalog/`). Leur domaine persistant est conservé.
Les adaptateurs dont le chemin est intentionnel le conservent. Deux tests reproduisaient les anciennes bases
erronées avant ce correctif.

La une affiche les sorties AlloCiné même si tous les catalogues de lecture échouent. Une fiche sans correspondance
porte « disponibilité à vérifier » et propose Rechercher une source ; la recherche dans les sources activées se
fait au clic et remplace la fiche par ses références réelles. Aucun lien AlloCiné n’est présenté comme un flux vidéo.
Les sorties populaires du catalogue cinéma disponible et les sorties françaises sont mélangées ; le cache migre
sans nécessiter de réinstaller ou d’effacer les favoris.

Quand un direct ne peut être extrait nativement, sa vraie page s’ouvre dans un WebView visible, avec les deux
lecteurs du site, les cookies persistants, le plein écran et le bouton Navigateur. Cette session interactive n’a
pas de timeout artificiel : la validation demandée par le site et l’appui sur Lecture restent à l’utilisateur.
Les erreurs HTTP de la page principale sont affichées sans bloquer les sous-ressources ou les pages de validation.
La lecture Android réelle reste à vérifier sur téléphone/TV ; les tests de parseur et le navigateur de bureau
ne constituent pas une preuve de décodage.

### Correctifs v1.8.83

À découvrir affiche les premières cartes avant la fin du classement. Une grille ne contenant que des en-têtes
ne déclenche plus la pagination. Le classement attend la fin du chargement du lot, les expressions de découpage
et les thèmes sont précompilés, et la diversification coûteuse se limite aux 120 premières positions ;
la suite conserve tous les titres triés par pertinence. Un test charge 1500 cartes et vérifie qu’aucune n’est perdue.
Les cartes des anciennes catégories et les clés dupliquées sont éliminées du flux affiché.
Le chargement initial conserve les réponses partielles à l’expiration de son budget plutôt que de laisser
le catalogue attendre chaque source. Les vérifications cinéma sur le réseau sont consignées séparément dans
verification/CINEMA_PLAYBACK.md, sans annoncer qu’un film est lisible sur la seule présence d’une fiche.

La loupe utilise un cercle et un manche vectoriels séparés, sans les arcs superposés de l’ancien tracé.
Lorsqu’un film possède plusieurs références, les autres catalogues sont essayés avant WarFlix, dont une
fiche de métadonnées ne garantit pas la présence d’un flux. Le contrôle réseau a confirmé des médias
VF/VOSTFR pour Heart Of The Beast (Movix/Coflix) et Les Misérables (Frembed), au moment de l’audit.

### Correctifs v1.8.84

- Reprendre : une seule fenêtre de chargement, sans barre supplémentaire sur la fiche.
- Lecture : les adresses contenant des espaces ou caractères non échappés utilisent le même analyseur que le contrôle réseau ; le test reproduisait auparavant une URISyntaxException fatale. Les erreurs d’initialisation sont remontées au parcours de récupération, et le lecteur ne démarre qu’après installation de ses gestionnaires d’erreur.
- Téléphone : bouton Réinitialiser les préférences sur une ligne dédiée, confirmation rouge et annulation avant toute suppression. Les valeurs de l’interface sont remises à jour après confirmation ; historique et favoris sont conservés.
- Vérification : test JVM de régression sur les adresses vidéo ; tests Android ajoutés pour confirmation/annulation sur une largeur de 320 dp et récupération après échec d’initialisation. Ces tests Android sont compilés mais nécessitent un appareil pour être exécutés.

### Correctifs v1.8.85

- Recherche/actualisation des sources : VidsrcRu ne termine plus deux fois la même recherche lorsque plusieurs requêtes de manifeste arrivent avant le traitement sur le thread principal.
- VidLink : les retours JavaScript sont traités sur le thread principal, avec vérification de l’état de la recherche au moment du traitement.
- VidsrcRu, VidLink, StreamWish et Upzone : destruction des WebView dans un bloc finally sur leur thread principal, y compris après annulation, délai dépassé et succès. Les minuteurs des deux premiers sont supprimés en fin de recherche.
- Les extracteurs web et la session de catalogue gèrent la perte du processus de rendu au lieu de laisser Android fermer l’application.
- Régression : les tests reproduisaient une IllegalStateException de double réponse et une destruction depuis un worker ; ajout de contrôles pour annulation, retours JavaScript, perte du moteur web et transition chargement/lecture. Robolectric vérifie ces transitions sans certifier le décodage matériel ni la disponibilité d’un fournisseur externe.
