# TvSama v1.8.78 — versionCode 85

APK final : `../../releases/TvSama-v1.8.78-universal.apk`, séparé des sources nettoyées.

- OK ouvre les commandes ; OK sur la barre ou Pause bascule lecture/pause.
- Focus renforcé sur Sélectionner, télécommande, Reprendre et commandes du lecteur.
- Ancienne icône de veille rétablie ; commandes masquées après 8 secondes d’inactivité.
- Auto-suivant à l’outro IntroDB valide, au plus tard 30 secondes avant la fin, avec repli autonome et protection contre les doubles transitions.
- Nettoyage global : environ 12,7 Go libérés ; sources, outils de reconstruction et clé conservés.

SHA-256 : `3bf9fbc4e532841b3dffe1834510957b4e3f6b5d7c66fa1705ee377469800d66`.
APK universel arm64-v8a, armeabi-v7a, x86 et x86_64 ; Android 6/API 23 minimum.
Signature identique à v1.2.78 ; mise à jour installée et démarrage vérifiés.

Validation : 74 tests JVM réussis, 9 scénarios Android réussis sur émulateur ;
pas de téléviseur physique testé. [Rapport détaillé](../verification/2026-10-09-apk-controls/ANDROID.md).
Les métadonnées Windows sont alignées, sans nouveau build Windows dans ce lot APK.
