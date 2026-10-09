# TvSama Windows x64 · v1.5.78 · stabilisation

Portable : `TvSama-1.5.78-Windows-x64.exe`, 151 634 578 octets, non signé.
Android aligné : v1.5.78 / versionCode 82. Les EXE précédents sont conservés.

SHA-256 :
`62bb27e6139b8af859dc9aa9a890ee7908bf4a0797c2a9a1fb39067c21031c71`

## Corrections

- Interface adaptée à l’espace disponible, zoom réglable 75–200 %, menus sombres
  bornés à l’écran et utilisables souris, toucher et flèches/Entrée/Échap.
- Plein écran autorisé pour la fenêtre locale et sortie vidéo sans perte du lecteur.
- Recherche des flux personnels, favoris paginés au-delà de 120, fiche conservée
  lors d’un ajout de favori ou d’un retour du lecteur, langues disponibles respectées.
- Changements rapides de serveur protégés contre les réponses obsolètes ; position
  conservée ; un renouvellement automatique des lecteurs expirés puis reprise manuelle.
- Annuaire automatique avec conservation hors ligne, migration des anciennes
  adresses, annulation et délais bornés.
- Médias signés sans extension reconnus, dont Anime-Ultime ; données Flight
  MyFluneo extraites sans exécuter de scripts ; captcha AnimeKO explicite.

## Preuves et limites

39 tests Linux passent. Les 21 scénarios Electron passent sur les sources et sur
l’archive ASAR embarquée dans le paquet Windows, exécutée avec Electron Linux.
21 fichiers runtime de cette archive sont identiques aux fichiers vérifiés.
MP4/HLS/DASH décodés localement ; affichage de 360 à 3840 pixels, zoom jusqu’à 200 %.
Ce contrôle ne valide pas l’exécutable dans Windows.

Audit public : 21 sources, 13 catalogues répondent, 7 échantillons fournissent des
URL ; décodage distant non testé. Des erreurs réseau/403/404, captchas et défauts
d’extraction restent ouverts. La parité complète et l’autonomie perpétuelle des
services tiers ne sont pas acquises. Windows réel, endurance et périphériques
physiques restent à valider.

Rapports : `windows/verification/`, feuille de route : `windows/STABILISATION.md`,
détail : `windows/VALIDATION.md`.
