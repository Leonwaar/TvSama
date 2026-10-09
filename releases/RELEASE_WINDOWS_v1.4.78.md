# TvSama Windows · v1.4.78

Artefact de validation du portage APK, **parité complète non atteinte**.

- Fichier : `TvSama-1.4.78-Windows-x64.exe`
- Taille : 151 629 442 octets, environ 144,6 Mio.
- SHA-256 : `80d1fb0690e65255ca27000ae1d2c440f17de715db14b25bc5b5aa4805885afe`
- Portable Windows x64, Electron inclus, non signé.
- Version source Android alignée : v1.4.78, versionCode 81 ; aucun nouvel APK n’est joint à cette livraison desktop.

L’archive autoextractible utilise un lanceur PE32 et contient l’application x64.
La version précédente est conservée. Aucune publication distante n’a été faite.

## Changements

Catalogues progressifs et adaptateurs FR, recherche/catégories, fiches/saisons/
épisodes et choix multi-source ; bibliothèque, reprise, sauvegarde Android/Windows
et suppression des flux personnels. Lecteur MP4/HLS/DASH, qualité/pistes,
sous-titres proxifiés et conversion SRT, serveurs de secours, IntroDB, minuterie,
assombrissement, navigation souris/tactile/clavier/HID. Calendrier/rappels,
télécommande Android TCP, QR par image, Cast, métadonnées TMDB facultatives,
licences et téléchargement SHA-256. Dictée via Windows + H.

Le stockage est atomique ; le réseau et ses caches sont bornés. La fermeture
attend l’enregistrement. Le renderer est isolé et le JavaScript des lecteurs
distants est traité comme des données, sans évaluation dans Node.

## Preuves et limites

20 tests de services réussis. Electron Linux décode les fixtures APK MP4, HLS et
DASH, avec pause/reprise, favoris, rechargement, flèches et toucher injecté par
Chromium. L’ASAR a été inspecté ; les fichiers principaux correspondent par hash
aux sources testées. Aucune vulnérabilité runtime connue dans l’audit npm effectué.

L’audit de 21 sources conserve plusieurs HTTP 403, échecs réseau et résolutions
sans média ; il ne certifie aucune lecture distante complète. Les essais Windows,
Cast/Android réels, PiP, tactile/HID physiques, veille et endurance restent ouverts.
Les rappels nécessitent le processus ouvert. Le scan QR caméra et le cache disque
multimédia restent absents. La CI Windows est préparée, pas exécutée ici.

Suivi : [feuille de route](../windows/PORTAGE_COMPLET.md),
[validation](../windows/VALIDATION.md), [rapports](../windows/verification/).
