# Travail demandé

- [ ] Directs Volkamax : catégorie séparée, actifs, programmes à venir, rappel par cloche 10 minutes avant.
- [ ] Mise à jour au lancement depuis les releases du dépôt GitHub configuré ; accès privé authentifié, installation Android et reprise après mise à jour.
- [ ] Reprendre : un titre par série, dernier épisode/saison/timecode, épisode suivant à 30 secondes de la fin, retrait par appui long.
- [ ] Fiche : saison et défilement vers épisode repris, progression des épisodes vus, dates de première/dernière diffusion et statut.
- [ ] Choix VF/VOSTFR au lancement uniquement, correction des faux libellés de langue.
- [ ] Sources désactivées exclues des alertes ; catégories sur fond transparent.
- [ ] Recherche : focus/clavier immédiat et historique compact sur deux lignes.
- [ ] Lecteur : icône et PiP automatique, précédent/suivant en bas, doubles taps cumulatifs de 15 secondes, accélération télécommande.
- [ ] Scan QR interne avec caméra pour appairage.
- [x] IntroDB : vérifier contrat API et timecodes ; bouton de skip branché au lecteur avec repli secondes et validation d'intervalle.
- [ ] Vérification finale de toutes les sources et corrections ; rapport distinguant catalogue, recherche, fiche, extraction et lecture effective.

Validation matérielle : aucun appareil ADB connecté au début de cette session.

Convention demandée : chaque APK livré incrémente le nombre central, v1.1.69 puis v1.2.69 ; versionCode strictement croissant. Les compilations de validation ne sont pas des livraisons.

## Règle permanente de version

Tout ajout ou correction fonctionnelle doit augmenter immédiatement la version,
sans exception. Le `versionCode` est strictement croissant et le nombre central
de `versionName` augmente selon la convention de livraison. Cette règle s’applique
également au portage Windows : chaque installateur `.exe` doit être rattaché à une
version fonctionnelle identifiée.

## APK v1.8.78 / code 85 — commandes TV, enchaînement et nettoyage

Critères : une seule action par appui OK complet, focus visible sur les commandes,
icône de veille d’origine affichée uniquement avec les commandes du lecteur.
Masquage après **8 secondes d’inactivité**, avec maintien des menus pendant le choix.

- [x] Isoler l’ouverture des commandes du clic sur Pause au relâchement de OK.
- [x] Faire basculer lecture/pause avec OK sur la barre, sans perdre une avance en cours.
- [x] Conserver le clic natif sur le bouton Pause et les autres boutons du lecteur.
- [x] Corriger les doubles cibles de focus ; renforcer Sélectionner, télécommande et Reprendre.
- [x] Restaurer `ic_sleep_timer`, avec libellé accessible et masquage avec les commandes.
- [x] Déclencher le suivant une seule fois, à l’outro IntroDB valide ou au plus tard à 30 secondes de la fin.
- [x] Exclure les films/directs, l’absence de suivant, la pause et la minuterie expirée ; respecter le réglage automatique.
- [x] Garder les clips courts lisibles, marquer l’épisode terminé et arrêter l’ancien flux.
- [x] Préparer une reconstruction/signature reproductible et un nettoyage à liste explicite.
- [x] Valider les cycles OK, Entrée et pavé numérique, les pixels du focus et les clics doigt/souris sur Android.
- [x] Vérifier le masquage automatique de l’icône et l’ouverture du menu de veille.
- [x] Exécuter les régressions de lecture, télécommande et minuterie.
- [x] Compiler, signer, vérifier et installer l’APK universel en mise à jour.
- [x] Retirer sorties de build, caches locaux, dépendances npm régénérables, anciennes exportations et SDK de test redondant.
- [x] Mesurer l’espace libéré et vérifier la présence des sources, wrappers, configurations et clé.

Les essais sur émulateur doivent être distingués des essais sur téléviseur physique.
Preuves du lot : [verification/2026-10-09-apk-controls/ANDROID.md](verification/2026-10-09-apk-controls/ANDROID.md).

## Estimation du portage Windows

L’estimation initiale de 5 à 9 jours était indicative et ne mesurait ni les jetons
ni le temps de calcul Codex. La cible retenue utilise Electron et electron-builder,
sans JDK, Gradle ou WiX pour le desktop. Le suivi actuel, les critères bloquants
et les preuves sont dans [windows/PORTAGE_COMPLET.md](windows/PORTAGE_COMPLET.md)
et [windows/VALIDATION.md](windows/VALIDATION.md). Un Windows x64 est nécessaire
pour les essais finaux ; la signature nécessite le certificat de l’éditeur.

Stabilisation après signalement des bugs : [windows/STABILISATION.md](windows/STABILISATION.md).
Lot v1.6.78 / code 83 : 56 tests Linux et 28 scénarios Electron
réussis sur le code et l’archive embarquée. Les défauts de sources et les essais
Windows réels restent ouverts et empêchent la clôture complète.
