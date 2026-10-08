# Essais Android du 6 octobre 2026

Émulateur Android 35, téléphone, réseau local avec DNS publics. APK intermédiaires v1.2.76 (77), installation en mise à jour. Ces essais ne valident pas toutes les sources ni l’ensemble des critères TV.

## Classroom of the Elite, S1 E1

APK SHA-256 : `58d4dd52bbea7e2b78700e0163838045f99ecc63114c5bad85aa70c9104a15bd`.

- Parcours réel : recherche « Classroom », série 2017, fiche, saison 1, épisode 1, VF sélectionnée.
- Coflix : image réellement affichée ; décodeurs `c2.goldfish.h264.decoder` et `c2.android.aac.decoder` actifs à 09:11:40–41, vidéo 1280 × 720.
- Lecture encore active à 196 707 ms, sans erreur de session ; progression des images observée. Voir `classroom-playing.png` et `classroom-state.txt`.
- Le site annonce VF ; l’audio est décodé, mais son contenu linguistique n’a pas été écouté dans cet émulateur sans sortie audio.
- L’essai a révélé une qualité « 4K » erronée provenant de l’analyse d’une URL opaque. Le code suivant supprime cette déduction et affiche la résolution effectivement décodée pour le serveur actif. Cette correction est couverte par un test ; l’ancien APK testé ci-dessus conserve l’erreur dans sa capture.

## Transport du direct

- Lecteur actuellement publié : iframe dlive.sx puis dembed.top, manifeste obtenu depuis la constante SRC de sa page, sans dépendre des scripts publicitaires.
- Le segment réel téléchargé est un PNG RGB 512 × 1738. Sa charge TIKTIKPX contient du MPEG-TS compressé en gzip.
- Déballage Kotlin : 2 631 548 octets → 2 677 120 octets MPEG-TS, identiques octet par octet à une extraction indépendante. Environ 206 ms dans la JVM de contrôle.
- ffprobe : H.264 1920 × 1080 et AAC. Le manifeste et le premier segment sont accessibles dans le contrôle réseau natif (6 404 ms). Voir `LIVE_NATIVE_AUDIT.md` à la racine.
- Un premier contrôle réseau a échoué ; le contrôle suivant a passé. Ce résultat ponctuel ne garantit pas une disponibilité continue.

APK contenant le transport corrigé et l’affichage de qualité, SHA-256 : `a9f0bdbad9bbc97fb9a0b1290fae6c75ca49e3f83eea804d6b8742f8c3109b7c`. Essai Android du direct encore en cours à la rédaction de cette entrée.

## Régressions automatisées

63 tests passés : confort 10, progression/bibliothèque 29, transport de segments 3, adaptateurs 21. Les filtres PNG 0–4 et RGB/RGBA, WebP EXIF, enveloppes gzip/raw, troncature, formats ordinaires et faux catalogues sont couverts. Aucun de ces tests ne remplace les essais Android de toutes les sources.

## Reprise après sortie de la fenêtre du direct

L'APK intermédiaire précédent a démarré Canal+ sport à 14:53:15 sur l'émulateur Android 35 : première image en 1 711 ms, H.264 1024 × 576 et piste AAC sélectionnées. À 15:12:31, Media3 a signalé `ERROR_CODE_BEHIND_LIVE_WINDOW` (`BehindLiveWindowException`). Le repli vers un autre serveur a échoué car l'événement ne publiait alors plus de lecteur. Cette observation prouve un décodage initial, mais pas une lecture continue de cinq minutes : aucune mesure de progression intermédiaire n'a été enregistrée.

Le lecteur repositionne désormais un direct à sa position par défaut et le prépare de nouveau après cette erreur. La reprise est limitée à deux tentatives par fenêtre de 60 secondes avant le repli normal. Une fin de playlist déclenche aussi la recherche d'un lecteur actualisé. Compilation `:app:assembleDebug` et tests `:app:testDebugUnitTest` réussis après la dernière modification. APK corrigé installé en mise à jour et lancé : `app/build/outputs/apk/debug/app-debug.apk`, v1.2.76 (77), SHA-256 `906a5380e9db09dae90d28cbd4a48555bff5b5ddd7cab920ceb511abad3fb1db`.

Après l'installation de l'APK qui contenait la reprise de fenêtre, un nouvel essai Canal+ sport a expiré pendant la validation native du média ; le lecteur web a chargé sa page mais n'a fourni aucun manifeste validable. Le deuxième événement testé publiait deux lecteurs zonalive.click : leurs extractions ont échoué et leurs pages web n'ont fourni aucun manifeste validable. L'application a affiché « Aucun lecteur compatible disponible pour ce direct ». L'APK reconstruit après la gestion de fin de playlist a été installé et lancé, sans nouvel essai de média disponible. La reprise après `BehindLiveWindowException` reste donc à vérifier sur un direct disponible ; ces deux événements ne sont pas validés comme lisibles avec l'APK corrigé.

## APK intermédiaire après bornage des chargements

APK installé en mise à jour sur le même émulateur Android 35 : v1.2.76 (77), SHA-256 `4c8e99fd551920f1a6bb059103b6060701de4e4682b665618c52efc36f142ea2`. Compilation et tests unitaires locaux réussis après les changements du lecteur de fiches et de la résolution des sources. Cette compilation reste une validation intermédiaire, pas une livraison finale.

- L'écran Sources affiche les fournisseurs intégrés avec leur état « non vérifié » et leurs commandes d'activation ; DessinAnime et TFX73 apparaissent comme liens externes non intégrés, sans commande d'activation.
- Recherche exacte « Classroom of the Elite » : la fiche présente S1 E1 et S1 E2 avec leurs résumés ; le lecteur Coflix « Voe VF » a été choisi pour S1 E1.
- À 16:16:07, première image après 2 776 ms, H.264 1280 × 720 et AAC sélectionnés. Session Media3 `PLAYING`, position 247 167 ms puis 283 354 ms, sans erreur de lecture dans les journaux de cet intervalle. La position initiale provenait d'une reprise existante ; cet essai ne contrôle donc pas un premier lancement neuf. Le contenu linguistique de l'audio n'a pas été écouté.

APK intermédiaire suivant, après ouverture progressive des fiches et correction du clavier de recherche : v1.2.76 (77), SHA-256 `6a2cf904755ef5265bfb6fcbbd79fc800813532995c4332e99e640bc27ac2eaf`. Compilation et tests unitaires locaux réussis, installation en mise à jour et lancement réussis. Après sélection de l'historique « Classroom of the Elite », `dumpsys input_method` indique `mInputShown=false` ; toucher la première affiche ouvre sa fiche et affiche immédiatement son titre et sa description. Le décodage de l'épisode n'a pas été répété sur cette dernière empreinte ; la preuve de lecture ci-dessus appartient à l'APK précédent.

## APK intermédiaire après réparation de Frembed

APK v1.2.76 (77), SHA-256 `ea2cd4d5884d3b63feeeddb54460098cb5c84fa55b824d639ab5887fce2a4473` : compilation et tests unitaires locaux réussis ; installation en mise à jour et lancement sur l'émulateur Android 35 réussis. L'audit réseau ciblé, exécuté sur le même code, vérifie le catalogue, la recherche et les épisodes Frembed, puis un manifeste HLS et ses segments pour un lecteur de Fight Club. Les quatre autres lecteurs de cet échantillon échouent à l'extraction ; aucune lecture Frembed décodée sur Android n'est attestée par cet essai. Voir `FREMBED_AUDIT.md`. Cet APK reste une compilation de validation, pas une livraison finale.

Après ajout de l'iframe publiée comme repli prioritaire, nouvel APK intermédiaire v1.2.76 (77), SHA-256 `287e8f249c87a4a1eb39857392267671e7add82b8d1975f76e4f660a2ed49b2e` : compilation, tests unitaires et audit réseau ciblé réussis ; installation en mise à jour et lancement sur l'émulateur réussis. Le repli navigateur et le décodage de Frembed dans cet APK restent à vérifier sur Android.
