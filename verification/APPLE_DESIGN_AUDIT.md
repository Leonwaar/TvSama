# Audit de l’interface avec `apple-design` — v1.8.88-rc2

Audit statique des dix fichiers Compose de l’application Android TV. Les principes du
skill appliqués ici sont la réponse immédiate, la stabilité spatiale, le contrôle par
l’utilisateur, la lisibilité et l’adaptation au format de l’écran. Les conseils propres
aux gestes tactiles et au CSS ne s’appliquent pas au lecteur Android TV.

| Gravité | Problème constaté | Emplacement corrigé | Correction |
| --- | --- | --- | --- |
| P1 | « À découvrir » reclassait et remplaçait des affiches pendant le chargement, déplaçant la cible de la télécommande. | `MainActivity.kt:256`, `Recommendations.kt:28` | Premier groupe pertinent après 750 ms ; enrichissement sur place et ajout en fin de grille. |
| P1 | Le menu s’ouvrait dès qu’il recevait le focus et rétrécissait la grille. | `MainActivity.kt:755`, `MainActivity.kt:758`, `MainActivity.kt:986` | Ouverture sur action, panneau superposé ; Retour ferme le panneau et restaure le focus. |
| P1 | Les actions d’une source tenaient sur une seule ligne, hors écran sur les petits formats. | `MainActivity.kt:970` | Libellés et commandes séparés ; commandes sur plusieurs lignes si nécessaire. |
| P2 | Le bouton « Passer l’intro » volait le focus dès son apparition pendant la lecture. | `Playback.kt:633` | Le bouton reste accessible sans changer la sélection courante. |
| P2 | Les options « Densité des affiches » et « Animations de focus » étaient enregistrées sans modifier l’interface. | `Ui.kt:47`, `MainActivity.kt:807`, `Ui.kt:95` | Largeur de grille et transitions de focus suivent les préférences immédiatement. |
| P2 | Une erreur d’actualisation du cinéma devenait invisible lorsqu’une affiche était en cache. | `MainActivity.kt:813` | Erreur et action Réessayer restent visibles dans l’espace réservé au bandeau. |
| P2 | Les commandes de la télécommande dépassaient sur les écrans étroits. | `RemoteControls.kt:68` | Retour à la ligne automatique. |
| P2 | Le dialogue QR pouvait masquer ses commandes en paysage ; « Terminé » et « Fermer » faisaient la même chose. | `CastSupport.kt:84`, `CastSupport.kt:95` | Contenu défilable et une seule sortie explicite. |
| P3 | Les flèches du bandeau devenaient inutilisables durant leur animation. | `MainActivity.kt:1023` | L’action suivante interrompt la précédente ; le réglage d’animation réduite supprime le défilement animé. |
| P3 | Les vingt premières adresses de l’annuaire étaient affichées sans annoncer la limite. | `SourceDirectoryUi.kt:44` | Nombre de correspondances et invitation à affiner la recherche. |
| P3 | Le retour visuel principal des boutons ignorait l’état appuyé ; les libellés longs pouvaient déborder. | `Ui.kt:93`, `Ui.kt:112` | État appuyé immédiat et ellipse du texte long. |

Les boutons de lecture, les fiches, la recherche, les favoris, l’historique, les directs,
les sources, les réglages, les mises à jour et les dialogues ont été relus. L’audit ne
garantit pas qu’un défaut visuel absent du code soit détecté : aucun projecteur ou
émulateur Android n’est connecté à cet environnement.

## Vérification

`testDebugUnitTest` : 124 tests, 0 échec, 18 tests réseau ignorés par défaut.
`assembleDebugAndroidTest` : compilation réussie ; exécution sur appareil à faire.
`git diff --check`, signature Android et alignement ZIP 16 Ko : conformes.

APK de validation universel : `../releases/TvSama-v1.8.88-rc2-validation-universal.apk`
(code 96, API 23+, ARM64, ARMv7, x86, x86_64).
SHA-256 : `ca8e2ef3ce6e855894427a71f9a49c53518cd3b0a42b4f8a44cda2329e75dbe6`.

La publication finale reste liée à la vérification humaine des captchas Papadustream
et FRAnime déjà signalée ; le test local ne valide pas ces lectures.
