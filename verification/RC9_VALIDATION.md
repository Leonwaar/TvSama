# Validation v1.8.88-rc9 — 26 octobre 2026

APK : `../releases/TvSama-v1.8.88-rc9-universal.apk`
VersionCode 103 ; Android API 23 minimum, cible 36 ; 55 451 425 octets.
Architectures : ARM64, ARMv7, x86, x86_64.
SHA-256 : `775688d3c23c33acadba4b3cf600d43504dc32fa980cdc2824775cd26a20afef`.
Certificat SHA-256 : `5b522fba46fef8a6a34e7e09d6ceee1ff9f732dcefdd7e0a48db83d80e27d50f`.
Même signature que rc8, alignement ZIP 16 Ko et vérification apksigner réussis.

## Vérifications terminées

- `:app:testDebugUnitTest :streamflix:testDebugUnitTest` : 246 tests déclarés,
  220 exécutés avec succès, 26 tests réseau opt-in ignorés dans cette suite standard.
- `:app:lintDebug :app:lintRelease` : zéro erreur, zéro fatal ; 110 avertissements
  dans chaque variante, sans suppression ajoutée. Détail machine dans `RC9_CHECKS.json`.
  Ils concernent notamment les suggestions KTX/dépendances, ressources inutilisées,
  conventions Compose et accès à des ressources privées Media3. Les layouts sont
  testés avec Media3 1.8.0 ; leur intégration devra être revue lors de sa mise à niveau.
- `:app:assembleRelease :app:assembleDebugAndroidTest` : succès. L’APK de tests
  instrumentés est compilé ; aucune exécution sur appareil n’est revendiquée.
- Mutation volontaire de la protection post-générique : le test
  `postCreditsAreReachedOnceAndPromptWaitsForSceneEnd` échoue comme attendu.
  Code restauré puis `EpisodeAdvanceTest` relancé avec succès.
- `AnimeCoverageNetworkTest`, activé explicitement : six couples source/titre
  confirmés pour Anime-Sama et Vostfree × Naruto, Classroom of the Elite,
  Mushoku Tensei. Vérifie le média et les segments accessibles, pas un décodage Android.
- `AnimeReadersNetworkTest`, activé explicitement : onze adaptateurs français
  examinés, seize contrôles lecteurs/langues positifs sur six sites pour Naruto.
  Voir `ANIME_READERS_NETWORK.md` pour chaque échec et chaque confirmation.
- `FrenchLiveNetworkTest` : Arte, BFM TV, L’Équipe, France TV Sport, Sport en France
  et Equidia disposent d’au moins un média contrôlé ; sauvegardes incluses quand publiées.
- Test réel d’inflation du layout du lecteur et de sa barre de segments ; tests du
  registre des contenus terminés, des indicateurs TV combinés et des métadonnées FrenchManga.
- `git diff --check` dans les deux dépôts : succès.

## Livraison et limites

Le rapport `RC9_REVIEW.md` classe les problèmes corrigés et couvre interface,
réseau/adaptateurs, stockage, sécurité et performance. Cette version ajoute la page
Contenu visionné, le logo blanc, l’éclair vert, l’espacement d’en-tête, le titre du
lecteur sans bandeau, les segments graphiques et le comportement suivant/post-générique.
Volkamax est retiré des chemins actifs ; les chaînes et sports français sont séparés.

Aucune nouvelle source candidate non vérifiée n’a été activée. Les relais anime
existants ont été renforcés, notamment Voiranime et FrenchManga. FRAnime et AnimeKO
restent soumis à validation humaine ; FrenchAnime a répondu HTTP 403 dans l’audit,
Animes-Sama et Anime-Ultime n’ont pas fourni de résultat exact pour l’échantillon.
La disponibilité de tous les lecteurs et de tous les titres, ou de cinq à dix sites
par titre, n’est donc pas garantie. Aucun faux statut vert n’a été ajouté pour masquer ces cas.

Aucun appareil ADB connecté : captchas, rendu réel sur projecteur/téléphone, décodage
et qualité audio restent à tester. Les adresses continuent à utiliser le mécanisme
automatique existant OùStreamer et secours, avec validation du catalogue avant remplacement.
