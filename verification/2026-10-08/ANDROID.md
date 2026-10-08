# Contrôles Android — 8 octobre 2026

## Périmètre

Émulateur téléphone Android 15 / API 35, `tvsama_phone35`, accepté par l’utilisateur. APK de validation v1.2.76 (77), SHA-256 `420f32916cf43d99aef3c620ae518a2cf64b93649418e44d897f0237fb4d5835`. Aucun APK de livraison produit.

## Tests instrumentés du lecteur

Commande : `rtk proxy ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=fr.nekotv.PlayerDeviceTest --offline --console=plain`. La première exécution a nécessité un téléchargement des dépendances UTP sans `--offline`.

Cinq scénarios réussis, zéro échec, 48,261 s d’exécution des tests. Résultat conservé dans `player-device-tests.xml`. Les médias sont des mires de 12 s avec une sinusoïde, générées avec ffmpeg ; serveur HTTP limité au bouclage 127.0.0.1. Le serveur, ses assets et l’activité de test ne font pas partie de la variante release. L’autorisation HTTP de bouclage est propre à debug.

- MP4, HLS et DASH : première image reçue, progression > 1,5 s, vidéo 320×180 et compteur de buffers audio décodés positif. Cela ne valide aucune source externe ni le contenu linguistique d’une piste.
- HTTP 403, 404, 500 et HTML à la place d’un MP4 : erreur remontée en moins de 30 s pour chaque cas, action alternative indiquée. Le repli entre fournisseurs relève d’autres essais.
- Minuterie déjà expirée : MP4 puis HLS préparés et identifiés, `playWhenReady=false` pendant le changement de lecteur.
- Expiration pendant la lecture : arrêt effectif à une échéance rapprochée injectée dans les préférences de test, aucune reprise spontanée, reprise explicite par commande puis nouveau délai. Les durées réelles d’une à trois heures ne sont pas attendues dans ce scénario.
- Commandes distantes locales : pause puis position 4 000 ms ; remplacement MP4 → HLS avec nouveau lecteur actif et ancien lecteur à l’état IDLE. Aucun récepteur Cast utilisé.

La suite instrumentée a désinstallé les APK de l’émulateur en terminant. Réinstallation de l’APK de validation puis démarrage sans données effectués pour le contrôle manuel.

## Régressions JVM

Avant les tests instrumentés : 46 tests app exécutés + 8 audits réseau ignorés, 23 tests streamflix exécutés, zéro échec. Test ajouté sur les cinq durées de minuterie, la limite exacte d’expiration, la conservation entre lecteurs et la désactivation.

## Sources et limites

Le nouvel audit conserve désormais les traces `verification/source-audit.jsonl` : version du code, titre, référence SHA-256, saison/épisode, serveur retenu, résultat et durée de chaque étape. Le champ `decoded=false` évite toute assimilation avec un essai Android. Langues non filtrées dans cet audit.

Restent non acquis : matrice de titres par source, VF/VOSTFR effectivement écoutées, tous les fournisseurs intégrés, disponibilité des services protégés, mesures de performance à cache froid par source, session de deux heures, scénarios TV complets et réception Cast. Le statut global reste non final.

## Parcours directionnel sur accueil

Après chargement du catalogue et fermeture de l’alerte des sources indisponibles : 100 événements directionnels Android (droite, bas, gauche, haut, répétés 25 fois), injectés en 2,38 s. Processus toujours actif, arborescence UI lisible, focus final présent sur Recherche. Capture et XML conservés dans ce dossier. Cela ne mesure pas la latence visuelle de chaque touche et ne valide ni tous les menus ni le lecteur à la télécommande.

## Correction d’extraction postérieure à l’APK de test

FrenchStream transmet désormais le nom du serveur à l’extracteur. Dans l’audit réseau suivant, VOE passe de « aucun extracteur » à une extraction suivie d’un HTTP 403 du média. Le lecteur n’est donc pas déclaré opérationnel pour cet échantillon. Cette correction est postérieure à l’empreinte APK de test ci-dessus ; la livraison finale exigera un nouveau contrôle de l’artefact final.
