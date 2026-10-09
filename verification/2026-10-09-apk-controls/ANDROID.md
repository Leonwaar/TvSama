# APK v1.8.78 / code 85 — validation du 9 octobre 2026

## Résultat

APK universel compilé, alignement vérifié, signé avec la même clé que v1.2.78,
installé en mise à jour par `adb install -r` après installation de v1.2.78.
Le package installé affiche v1.8.78 / code 85. Démarrage à froid réussi en 1 152 ms.
Artefact séparé des sources : `../../../releases/TvSama-v1.8.78-universal.apk`.
Empreinte et manifeste : [artifact.json](artifact.json).

## Commandes et interface

- OK / Entrée / Entrée du pavé numérique ouvrent les commandes sans pause au relâchement.
- Sur la barre, OK valide une avance directionnelle puis bascule lecture/pause une seule fois, y compris avec répétition de touche.
- Le bouton Pause garde son clic natif ; ses contours et ceux des commandes Media3 sont visibles au focus.
- Sélectionner, télécommande QR et Reprendre : contour blanc vérifié dans les pixels rendus ; activation télécommande, toucher et souris vérifiée.
- Icône `ic_sleep_timer` d’origine rétablie ; menu de veille accessible et icône masquée avec les commandes.
- Délai de 8 secondes d’inactivité, sans réinitialisation par les mises à jour de position ; menus ouverts maintenus disponibles.

## Épisode suivant

Le réglage automatique existant, activé par défaut, est respecté. Une outro IntroDB
valide peut avancer le déclenchement ; sinon le repli est la durée moins 30 secondes.
Un générique placé plus tard n’ajourne pas ce repli. Les médias de 30 secondes ou
moins ne sont pas ignorés dès leur démarrage. Les films, directs, l’absence de
suivant, une pause et une minuterie expirée empêchent le déclenchement anticipé.
La transition est réclamée une seule fois par épisode, arrête l’ancien flux et
enregistre sa fin. Les marqueurs invalides sont ignorés.

## Essais

- `android-nine-scenarios.xml` : **9 tests Android réussis**, aucun ignoré. Émulateur Android 15 / API 35, affichage 960 × 540 à 160 dpi pour le lot final.
- `android-auto-next-final.xml` : transition réelle revalidée après le dernier ajustement de sauvegarde de progression.
- Tests JVM : **74 exécutés et réussis** (51 app + 23 streamflix). Huit audits réseau optionnels ignorés sans activation explicite.
- Lecture réelle MP4/HLS/DASH avec première image et audio décodés ; erreurs HTTP 403/404/500 et média HTML ; télécommande pause/seek et remplacement de flux.
- Minuterie : durée/expiration testées au JVM avec horloge contrôlée ; réinitialisation déjà existante à nouvelle lecture et reprise distante testées sur Android. Les anciens tests supposant une conservation de deadline entre sources ont été alignés sur le comportement déjà présent, sans modifier cette logique de production.
- Auto-suivant réel : vidéo locale de 45 secondes, aucune transition à 31 secondes restantes, remplacement du lecteur à 30 secondes restantes, ancien lecteur arrêté et un seul déclenchement.
- Régression du focus vérifiée sur les composants Compose réels ; masquage vérifié avec l’horloge réelle et l’arbre d’accessibilité Android.
- `:app:assembleRelease`, lint vital de release, signature et alignement réussis.

Commandes principales : `:app:connectedDebugAndroidTest`, `:app:testDebugUnitTest`,
`:streamflix:testDebugUnitTest`, `:app:assembleRelease` ; signature via
`python3 scripts/build-apk.py --no-build`.

## Limites et nettoyage

Pas de téléviseur physique testé. Les réponses IntroDB sont validées par fixtures et
tests de parsing ; cela ne garantit pas des marqueurs pour chaque titre. Leur
absence conserve le repli à 30 secondes. Ce lot ne certifie pas les fournisseurs externes.

Après validation, l’émulateur et le daemon Gradle ont été arrêtés. Les sorties de
build, anciens exports et captures, dépendances npm locales, SDK/AVD de test
redondants et sauvegarde source obsolète ont été supprimés. Les sources, fixtures,
wrappers, déclarations de dépendances, SDK principal et clé restent disponibles.
Les historiques Git ont été compactés avec `gc --no-prune`.
Mesure d’espace alloué et contrôle des fichiers nécessaires : [cleanup.json](cleanup.json).
La reconstruction est décrite dans le [README](../../README.md#reconstruire-après-nettoyage).
