# Portage complet APK → Windows : contrat de livraison

Objectif : parité des fonctions utilisateur de l’APK, interface fidèle et navigation
souris/tactile/clavier/HID, sans dépendance à Android ou à un émulateur.

## Règles obligatoires

1. Chaque lot fonctionnel augmente immédiatement le nombre central de version
   Android et desktop, et le versionCode Android. Ne jamais réutiliser une version
   publiée. Lot actuel : v1.6.78 / versionCode 83. Tout ajout ou correctif livré
   impose cette augmentation, sans exception.
2. Suivre les étapes dans l’ordre ci-dessous. Ne cocher que les critères démontrés.
3. Pour chaque fonction : code, test automatisé, puis preuve sur Windows réel.
   Une simulation DOM ou Wine ne prouve pas la validation matérielle Windows.
4. Une source inaccessible reste marquée en erreur avec la cause ; aucun catalogue
   factice, aucune vidéo de démonstration substituée à une extraction qui échoue.
5. Conserver les APK, EXE précédents et les données utilisateur. Versionner les
   schémas de stockage et vérifier les migrations avant de changer le format.
6. « 100 % » exige tous les critères, aucune régression connue et les preuves.
   Les services externes peuvent changer ; leur disponibilité n’est pas garantie.

## Ordre et critères

| Étape | Fonctions APK et références | Critères de sortie | Avancement / preuves |
|---|---|---|---|
| 1 | Architecture, versions, IPC, stockage, réseau | Renderer isolé, sauvegarde atomique, migrations, timeouts, concurrence bornée, annulation | Implémenté ; tests services Linux ; Windows ouvert |
| 2 | Accueil/recherche/catégories, `StreamFlixProviderManager`, `CatalogIdentity`, `TitleAliases` | Résultats progressifs, pagination, dédoublonnage multi-source, alias, aucune requête obsolète affichée | Implémenté ; progression/pagination ; alias et Windows ouverts |
| 3 | Tous les fournisseurs FR enregistrés dans `Provider.kt`, annuaire `SourceDirectory` | Pour chaque source : catalogue, recherche, fiche, épisodes, serveurs, vidéo ; rapport distinct à chaque étape ; activation persistante | Partiel ; 21 sources auditées, erreurs à corriger |
| 4 | `LibraryStore`, `AdvancedSettings`, fiches/saisons/épisodes | Favoris stables, un titre par série dans Reprendre, seuil 30 s, progression, retrait, sauvegarde APK import/export | Implémenté ; tests import/export et reprise Linux |
| 5 | `Playback`, `MediaNetwork`, `WrappedTs`, `PlaybackCache` | MP4/HLS/DASH, en-têtes, pistes/langues, sous-titres, serveurs de secours, qualité, seek accéléré/double clic, cache borné | Implémenté ; décodage local Linux ; pistes réelles/cache disque ouverts |
| 6 | `IntroDb`, `SleepTimer`, `PauseDimming` | Intro/outro validées, timer continu entre épisodes, assombrissement après 30 s en pause, réveil sans activation involontaire | Implémenté ; parsing testé ; essais bout en bout ouverts |
| 7 | `LiveScreen`, `VolkaMaxProvider`, `LiveReminders` | Calendrier Paris/DST, programmes/actifs, lecture effective, rappel 10 min avant persistant après relance | Implémenté ; dates testées ; rappels réels ouverts |
| 8 | `RemoteLink`, QR, `RemoteControls`, `CastSupport` | Protocole Android TCP big endian compatible, QR/scanner, synchronisation et commandes, Cast réel/reconnexion | Partiel ; TCP/QR/protobuf testés ; Android/Cast réels ouverts |
| 9 | Réglages, TMDB, licences, voix, PiP | Préférences appliquées, métadonnées facultatives, saisie vocale Windows ou équivalent explicite, PiP testé | Implémenté ; dictée système ; PiP et TMDB réels ouverts |
| 10 | `AppUpdates`, distribution Windows | Installer/portable, version croissante, téléchargement vérifié, signature éditeur si disponible, retour arrière des données | Portable construit ; SHA-256 testé ; publication/signature ouvertes |
| 11 | Validation finale et performances | Installation propre Windows, tests souris/toucher/HID, lecture locale 30 min, reprise, veille/réseau, sources auditées | Ouvert ; E2E Linux et binaire sous Wine réussis ; Windows natif manquant |

## Inventaire fournisseurs à suivre individuellement

FrAnime, FrenchStreaming, WarFlix, Coflix, BlablaStream, AnimeUltime, AnimesSama,
Voiranime, Anime-Sama, AnimeKO, MyFluneo, PapaduStream, Movix, Wiflix,
FrenchAnime, FrenchStream, Frembed, FrenchManga, Pluto TV FR et Vavoo FR ;
ajouter tout fournisseur dont `language == "fr"` détecté dans l’enregistrement APK.
IPTV-Org est une option supplémentaire, son fournisseur APK est enregistré en anglais.
Les sites externes de l’annuaire ne sont pas automatiquement des adaptateurs vidéo.

## Exigences de performance

- Recherche annulée à la navigation ; debounce 300 ms ; huit requêtes maximum,
  quatre résolutions vidéo maximum, trois chargements de fiche maximum.
- Cache TTL borné, aucune croissance non limitée des URL, médias ou événements.
- Enregistrement de progression toutes les 3 s maximum et à l’arrêt ; aucune
  écriture disque à chaque image ou à chaque événement `timeupdate`.
- Liste paginée, images lazy et décodées en différé, pas de rendu de milliers de cartes.
- Mesurer démarrage, mémoire au repos/après lecture et latence de recherche sur
  Windows ; consigner matériel et résultats avant de conclure « efficient ».

## Preuves de livraison

Journal : `VALIDATION.md`. Rapports automatisés et sources : dossier `verification/`.
Artefacts : `../releases/`, note de version avec SHA-256 et limites restantes.
Cette feuille de route remplace les estimations et cases ambiguës de l’aperçu.

## Point de contrôle actuel

Le portable v1.6.78 est un artefact de stabilisation intermédiaire. Aucun jalon de
parité Windows n’est fermé : l’environnement de travail est Linux. Les fonctions
des étapes suivantes sont implémentées pour permettre leur revue, mais leur code
ne vaut pas validation du jalon. Reprendre les critères dans l’ordre, avec les
preuves Windows, avant toute qualification « complète ».

Priorités issues des nouvelles mesures : extractions FRAnime/Animes-Sama/FrenchAnime/
WarFlix/MyFluneo, captcha AnimeKO et sources réseau en échec. Anime-Ultime fournit
maintenant son URL MP4 sans extension ; décodage distant 1280×720 vérifié dans Electron Linux.
Voir l’audit de lecture réelle dans `verification/live-audit-linux.json` ;
l’audit Node avec recherche reste dans `verification/sources-linux.json`. Ne pas fermer
l’étape 3 avec un simple catalogue qui répond. Vérifier ensuite les fonctions de
bibliothèque, lecture, timers, directs, appairage/Cast et distribution sur Windows.
