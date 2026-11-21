# Sources et récupération de l’historique — 16 octobre 2026

Les tests réseau utilisent réellement les adaptateurs Android sous Robolectric.
Ils ne prouvent pas le décodage Media3, l’écoute de la langue ni la disponibilité
de tous les titres et épisodes. Aucun appareil Android ni émulateur n’est disponible
dans cet environnement ; l’utilisateur a confirmé ne pas disposer d’appareil.

## Corrections

- Les résultats média déjà validés restent disponibles lorsqu’un autre extracteur expire.
- La résolution laisse jusqu’à 45 secondes aux autres références et reste annulable.
- Les fiches de films isolées recherchent aussi les références du même titre sur les autres fournisseurs.
- Le premier lancement choisit S1E1 avant les spéciaux de saison 0. Les spéciaux restent sélectionnables.
- WarFlix réutilise les liens français publiés par son lecteur Frembed, avec leurs langues explicites.
- Frembed envoie le référent de la page de lecture, garde les noms publiés des hébergeurs
  lors des redirections et mémorise les changements d’adresse.
- Wiflix migre l’ancienne adresse HTTP 410 vers l’adresse indiquée par son portail.
  L’adaptateur résout les jetons `fx1_` avec la clé de page, la session PHP et le référent canonique.
  Les groupes VF et VOSTFR restent distincts.
- L’historique demande confirmation avant effacement et conserve une copie persistante.
  La récupération conserve les nouvelles lectures, les favoris, les cartes précédemment masquées
  et les historiques de plus de 200 entrées. Elle résiste aux anciennes suppressions synchronisées.

## Contrôles reproductibles

```sh
./gradlew :app:testDebugUnitTest :streamflix:testDebugUnitTest :app:assembleDebug --offline
./gradlew :app:testDebugUnitTest --tests fr.nekotv.PopularTitlesNetworkTest --tests fr.nekotv.SourceNetworkAuditTest.allFrenchSources -PnetworkAudit=true --offline
```

L’audit détaillé est dans `../SOURCE_AUDIT.md` et les observations par adaptateur
dans `source-audit.jsonl`. Les tests réseau sont facultatifs pour la suite locale,
car une défaillance distante doit rester un échec visible lors de l’audit explicite.

Dernière validation : 124 tests locaux réussis, 13 contrôles réseau facultatifs
ignorés dans cette commande, compilation de l’APK réussie. L’exécution réseau
séparée conserve 35 tests bibliothèque réussis, le parcours Naruto/Fight Club réussi
et le contrôle Wiflix Fight Club en échec dans `source-recovery-network.json`.
L’audit global consigne ses erreurs dans le tableau même si sa tâche termine :
13 échantillons média accessibles sur 21 adaptateurs lors du dernier passage.
Ce décompte ne mesure pas la proportion de titres accessibles.

Naruto S1E1 : trois flux étiquetés VF et trois étiquetés VOSTFR accessibles via MyFluneo.
Fight Club : flux étiquetés VF accessibles via WarFlix et Coflix ; aucun VOSTFR
trouvé dans ce contrôle. Les étiquettes proviennent des fournisseurs, sans écoute audio.

APK de validation : `../../releases/TvSama-v1.8.85-sources-history-debug.apk`,
SHA-256 `aae00bb7cacf67d1d2f726d00aad21a2e9a58ec196c834a6a6a41d93574d7103`.
Il s’agit d’une compilation debug, pas d’une release publiée.

## Limites observées

- FRAnime : certains endpoints de lecteurs renvoient une validation Cloudflare ou l’accueil du site.
- AnimeKO et Papadustream : validation interactive demandée par le fournisseur.
- Kidraz : catalogue vide et réponses 404/403 sur les chemins testés.
- BlablaStream : l’adresse testée n’utilise plus le catalogue Dooplay attendu.
  La page `/movie/550` contient un élément `fake-player` et un faux chargement ouvrant une publicité VPN.
- Wiflix : plusieurs lecteurs publiés répondent 404 ou ne fournissent pas de média extractible.
  Un succès ponctuel ne garantit pas leur disponibilité.
- Coflix et Animes-Sama : le dernier échantillon de l’audit global a échoué avec des délais
  ou des erreurs média, malgré des contrôles réussis sur d’autres passages ou titres.
- Les cinq liens externes sans adaptateur restent des liens externes ; leur page disponible
  ne constitue pas une intégration au lecteur natif.

La couverture de 100 % n’est pas acquise. Ces limites sont conservées dans le bilan,
sans déclarer un site fonctionnel au seul motif que sa page répond HTTP 200.
