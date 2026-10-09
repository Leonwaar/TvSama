# TvSama Windows x64 · v1.6.78

Portage Electron autonome : Accueil, Recherche, Ma liste, Reprendre, Sources,
Directs sportifs et Réglages. La version 1.3.78 reste conservée. **La parité APK
à 100 % n’est pas validée** : voir [PORTAGE_COMPLET.md](PORTAGE_COMPLET.md).

Le code comprend 20 adaptateurs APK français et IPTV-Org supplémentaire, catalogues
progressifs, choix de source, fiches/épisodes, favoris/historique, sauvegardes
Android/Windows, lecteur MP4/HLS/DASH, pistes/qualité/sous-titres VTT/SRT, IntroDB,
minuterie, assombrissement, calendrier/rappels, télécommande TCP Android,
association QR depuis une image, Cast et téléchargement vérifié des mises à jour.
L’implémentation d’un adaptateur ne prouve pas que le site fonctionne : les erreurs
figurent dans [verification/live-audit-linux.json](verification/live-audit-linux.json).

## Construire et vérifier

```powershell
npm ci
npm test
npm run dist
```

Sortie : `dist/TvSama-1.6.78-Windows-x64.exe`, portable x64 non signé.
Electron est inclus ; aucun SDK Android ni émulateur n’est nécessaire pour l’utiliser.
Le build vérifie l’alignement des versions Android, Windows et lockfile et inclut
les licences. Les données résident dans le dossier utilisateur Electron, hors EXE.

`npm run test:e2e` lance réellement Electron et décode les fixtures MP4/HLS/DASH de
l’APK via HTTPS local. Linux nécessite Xvfb et OpenSSL ; Windows nécessite OpenSSL
dans PATH. Les options de certificats et de sandbox du test ne sont jamais ajoutées
au lancement normal. Le workflow `.github/workflows/windows-desktop.yml` prépare
ces contrôles sur Windows ; il n’a pas encore été exécuté ici.

`node audit-sources.cjs` actualise l’annuaire puis vérifie catalogue, recherche, fiche et résolution par
source publique. Il ne décode pas les vidéos distantes et ne certifie pas tous les titres.

## Navigation et limites

Clic, toucher, Tab, Entrée/Espace et flèches ; télécommande transmettant des touches
HID. F11 : plein écran ; Échap : quitter le plein écran vidéo, fermer le menu ou revenir ; flèches sur le lecteur : recherche
accélérée. Contrôles d’au moins 48 pixels CSS, 54 sur pointeur tactile.
Pour dicter : sélectionner Recherche puis Windows + H, via la dictée du système.

Réglages → Résolution DNS : Système, Cloudflare (par défaut comme Android), Google.
Réglages → Taille de l’interface : 75 à 200 %. La fenêtre respecte l’espace
disponible ; les menus sombres restent dans l’écran. Favoris et flux personnels
sont paginés. Les adresses des sources sont actualisées quotidiennement avec
conservation des adresses connues si l’annuaire tombe. Une lecture en échec tente
une fois de renouveler les serveurs, puis propose une reprise manuelle.
Feuille de route et critères bloquants : [STABILISATION.md](STABILISATION.md).

Les rappels fonctionnent lorsque TvSama est ouvert et sont conservés à la relance.
Le binaire Windows est testé sous Wine, ce qui ne remplace pas un poste Windows.
Cast, PiP, HID physique, tactile physique, pare-feu et performances Windows restent
à tester. Les sources exigeant des en-têtes privés ne sont pas envoyées au
récepteur Cast par défaut. Les mises à jour sont vérifiées uniquement lorsqu’une
empreinte SHA-256 est fournie par la publication GitHub officielle.
