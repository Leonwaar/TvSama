# Vérification TvSama v1.1.76 — 5 octobre 2026

## Modifications

- Suppression du contrôle `versionCode` de l’APK téléchargée depuis GitHub. La proposition de mise à jour reste basée sur un tag de release plus récent. Le nom du paquet, la signature et l’intégrité du téléchargement sont vérifiés.
- Minuteur avec icône vectorielle compatible Android 9, durées de 15 minutes à 3 heures et désactivation. À expiration : pause et luminosité minimale de la fenêtre.
- Synchronisation périodique des domaines OùStreamer pour les sources déjà connues ; conservation des chemins propres aux adaptateurs et des anciennes adresses des contenus enregistrés.
- Résolution des embeds des directs en parallèle, avec contexte iframe, cookies et cycle de vie WebView ; tampon de démarrage des directs réduit.

## Vérifications effectuées

- Compilation `:app:assembleDebug` et tests `:app:testDebugUnitTest` : 31 tests réussis ; les 5 audits réseau optionnels sont ignorés.
- Installation et lancement sur un téléphone Android 15 x86_64, accéléré par KVM.
- Lecture réelle du MP4 Sintel de media.w3.org : image décodée, position en progression, état `playing=true`.
- Ouverture et sélection du menu du minuteur : durées affichées de 15 minutes à 3 heures. L’expiration après 15 minutes n’a pas été attendue sur l’émulateur.
- Préférences de l’application : domaines synchronisés, notamment FrenchStream, Papadustream, Frembed, AnimeKO, Wiflix et VolkaMax.
- Calendrier VolkaMax : événements actuels chargés.
- Manifeste et segments du direct Canal+ sport téléchargés par le lecteur. La lecture reste bloquée. Un segment analysé avec ffprobe contient du H264, mais aucun paquet audio malgré la piste AAC annoncée. Les embeds zonalive testés ne fournissent pas de manifeste.
- Le saut IntroDB est couvert par les tests de parsing et d’identité ; il n’a pas été validé sur un épisode réel dans l’émulateur.

## Limites constatées

Les domaines synchronisés ne constituent pas une validation de lecture de tous les sites. Les directs ne sont pas entièrement réparés. Aucune installation complète d’une future release GitHub n’a été simulée.

## APK

`app/build/outputs/apk/debug/TvSama-v1.1.76-universal.apk` : version 76 / v1.1.76, Android 6 minimum, architectures arm64-v8a, armeabi-v7a, x86 et x86_64. Build debug signée avec la clé Android Debug du workspace.

## Émulateur installé

SDK : `/home/leon/TvSama_fixed_v2/android-test-sdk`.

Relance avec interface graphique :

```bash
rtk proxy /home/leon/TvSama_fixed_v2/android-test-sdk/start-tvsama-phone.sh
```

Le lanceur configure les chemins du SDK, KVM et les DNS de l’émulateur. Arrêter l’instance existante avant de relancer le même AVD.
