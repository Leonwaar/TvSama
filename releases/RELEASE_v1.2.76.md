# TvSama v1.2.76 — versionCode 77

## Artefact

- Fichier : `TvSama-v1.2.76-universal.apk`
- SHA-256 : `905b8bb0afc4c48fc4b06789e1f442d1916ffe6ed53505bbd1bd11bcd9e1e5f4`
- Package : `fr.nekotv`
- Version : `v1.2.76` / `versionCode 77`
- APK universel : bibliothèques `arm64-v8a`, `armeabi-v7a`, `x86` et `x86_64` réunies dans un seul fichier
- Signature : APK v1, v2 et v3 valides ; certificat SHA-256 `5b522fba46fef8a6a34e7e09d6ceee1ff9f732dcefdd7e0a48db83d80e27d50f`, identique à v1.1.76 publiée

## Contrôles de cet artefact

- Compilation `:app:assembleRelease` et tests JVM réussis après les dernières modifications.
- Tests JVM app : 54 tests, 0 échec, 8 audits réseau ignorés lorsque le réseau n’est pas demandé.
- Tests JVM streamflix : 23 tests, 0 échec.
- Tests Android instrumentés : 5 tests, 0 échec sur émulateur Android 15 / API 35 : MP4, HLS, DASH avec audio décodé, erreurs HTTP/HTML, minuterie expirée, reprise explicite, commandes et remplacement de lecteur.
- Installation `adb install -r` réussie sur une installation existante ; version 77 affichée ; activité principale résolue et démarrée.

## Corrections incluses dans cette itération

- Recherche progressive conservée pour les requêtes saisies, avec délai réduit à 250 ms, huit requêtes concurrentes bornées, timeout de recherche à 12 s et trois variantes de requête utiles.
- Les services protégés ou lents sont distingués des erreurs réseau franches dans l’état des sources (`Accès protégé` / `Réponse lente`). Les captchas et HTTP 403 externes restent soumis à leur protection.
- Lecteur : suppression de l’icône lune et de l’horloge persistante ; l’assombrissement reste actif par défaut en pause et la minuterie reste accessible par le bouton texte `Minuterie`. Le décompte texte suit la visibilité des contrôles et disparaît avec eux.
- Chaque nouvelle lecture redémarre la minuterie selon la durée enregistrée dans les paramètres, même si la lecture précédente l’avait laissée expirer ou désactivée.

## État de finition

Cet APK est compilé, signé, universel et apte à être joint à une mise à jour. Le produit n’est pas déclaré « 100 % final » : l’audit réel conserve des services protégés ou indisponibles (captcha, HTTP 403), cinq liens externes sans adaptateur, des essais de contenu VF/VOSTFR incomplets, l’absence de récepteur Cast, et la session TV de deux heures non exécutée. Les résultats détaillés sont dans `SOURCE_AUDIT.md` et `verification/2026-10-08/ANDROID.md`.
