# À découvrir : affichage stable — v1.8.88-rc2

## Cause

Chaque fournisseur envoyait une liste cumulative déjà reclassée. Cette liste
était immédiatement affichée, puis reclassée à nouveau par les recommandations
à la fin du chargement. Un changement de fournisseur principal pouvait aussi
changer la clé Compose d’une même affiche. Le bandeau cinéma et sa ligne de
chargement s’inséraient et disparaissaient au-dessus de la grille.

## Correctif

- Après la première réponse non vide, un délai fixe de 750 ms regroupe les premières
  réponses. Il ne repart pas à zéro à chaque arrivée et n’attend pas les sources lentes.
- Le premier groupe est classé selon les recommandations avant d’être affiché.
- Les mises à jour enrichissent les cartes en place et ajoutent les découvertes en fin
  de liste. Elles conservent les clés des cartes existantes, même après fusion des sources.
- Pendant une actualisation ou une pagination, les cartes des sources encore en cours
  ne disparaissent pas. En fin de chargement, les cartes absentes du catalogue sont retirées.
- Un changement de catégorie ou de recherche réinitialise la sélection affichée.
  Le retour depuis une fiche conserve l’ordre et restaure le focus sur la carte affichée.
- La place du bandeau cinéma est réservée dès le départ ; son chargement n’ajoute
  plus de ligne au-dessus des affiches. Les sections de la grille ont des clés explicites.
- Le menu latéral s’ouvre sur action et se superpose à la grille ; son focus ne
  déclenche plus de changement de largeur. Une erreur du cinéma reste visible
  même avec des affiches en cache.
- Le classement et la fusion restent exécutés sur `Dispatchers.Default`.

## Vérification

Les régressions couvrent les arrivées dans un ordre différent, les cartes plus pertinentes
arrivant tardivement, l’enrichissement par une autre source sans changement d’identité,
les anciennes catégories et les réponses partielles pendant une actualisation.

```sh
./gradlew :app:testDebugUnitTest --tests fr.nekotv.RecommendationsTest --tests fr.nekotv.ComfortRegressionTest :app:assembleDebug --offline
```

La vérification sur projecteur et à la télécommande reste à effectuer : aucun appareil
Android n’est connecté à cet environnement. Ce correctif ne lève pas les validations
humaines encore nécessaires pour Papadustream et FRAnime.
