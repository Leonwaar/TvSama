# Validation · v1.6.78 · 9 octobre 2026

Le lot livre un portable Windows corrigé. La parité APK complète reste ouverte :
plusieurs hébergeurs échouent ; aucun poste Windows natif ni appareil Android/Cast
physique n’est disponible ici. Historique : `verification/validation-v1.5.78.md`.

## Résultats

- **56 tests passent**, aucun ignoré : 20 services, 9 maintenance/extraction,
  17 fiabilité, 10 régressions du renderer sous JSDOM.
  Preuve : `verification/services-v1.6.78-linux.tap`.
- **28 scénarios Electron passent** sur le code et l’archive ASAR embarquée :
  MP4/HLS/DASH réellement décodés, pause/reprise, plein écran/Échap, favoris,
  flèches, toucher Chromium, menus souris/clavier/toucher, DNS, licences,
  erreur d’association récupérable, réseau et fermeture.
- **28 scénarios sur le portable Windows sous Wine 11**. L’EXE s’extrait,
  lance l’application et décode les vidéos. Cela ne valide pas un Windows natif,
  ses pilotes ou ses périphériques. `verification/wine-e2e-linux.json` contient
  l’empreinte du fichier lancé. Zéro erreur JavaScript non traitée observée.
- Parcours complémentaire : **29 scénarios avec PiP** passent sous Linux et
  sur l’EXE sous Wine avec un profil au chemin accentué. Le bouton ouvre la
  fenêtre vidéo, puis le retour au lecteur fonctionne.
  Preuves : `verification/extra-e2e-linux.json` et `wine-accented-e2e-linux.json`.
- Affichage : 360×480, 640×480, 1280×720, 1920×1080, 3840×2160 ;
  zoom 100/125/150/200 %. Aucun débordement horizontal mesuré du document/contenu,
  menus dans le viewport et tous les onglets visibles. Sur petit écran, les
  onglets occupent plusieurs lignes au lieu de défiler horizontalement.
- Réseau réel local : Referer/Origin requis contrôlés côté serveur, HLS servi en
  `text/plain`, redirection HTTPS entre origines sans transfert d’Authorization,
  HTTP 503 puis lecture reprise par le bouton de renouvellement.
- 25 fichiers runtime de l’ASAR identiques aux sources ; version vérifiée
  séparément car electron-builder retire les métadonnées de développement.
  Preuve : `verification/asar-v1.6.78.json`.

Les fixtures APK durent environ 12 secondes. Les décodages courts sont vérifiés
par dimensions vidéo et progression du temps, pas par la seule présence d’une URL.
Rapports et captures : `verification/e2e-linux.json`, `bundle-e2e-linux.json`
et `wine-e2e-linux.json`.

## Corrections du lot

Le transport natif expose les redirections manuelles et l’URL finale réelle,
transmet les en-têtes vidéo et arrête le téléchargement lorsque le consommateur
ferme son corps. Les tests simulés ne détectaient pas les écarts de net.fetch
Electron ; les nouveaux scénarios HTTPS locaux les reproduisent. La sécurité et
l’isolation du renderer restent activées.

DNS configurable comme dans l’APK : système, Cloudflare par défaut, Google.
Application immédiate avec invalidation des caches. Cela ne garantit pas l’accès
à un site qui refuse les requêtes, impose un captcha ou subit une restriction externe.

Résolution : trois serveurs concurrents, budget de 25 secondes, annulation et
causes conservées. Un serveur bloqué ne masque plus les autres. Un corps vidéo
bloqué après ses en-têtes expire ; quitter le lecteur annule ses requêtes.
La fermeture normale sauvegarde la bibliothèque ; garde de cinq secondes contre
une attente indéfinie du renderer. Le renderer volontairement figé n’a pas encore
de scénario dédié. La fenêtre d’accès au site respecte désormais l’espace écran.

Extracteurs complétés : redirections littérales sans exécution JavaScript,
sources littérales/base64, alternatives Voiranime, API MailRu et Vidara.
FRAnime filtre les films avant pagination ; Papadustream signale le captcha.
Une TV qui ferme sans réponse rejette sa commande ; un ancien socket Cast ne
détruit plus la nouvelle connexion. Tests TCP local et récepteur simulé,
sans preuve matérielle. La restauration applique zoom/DNS en cours ; sa boîte
native reste à couvrir de bout en bout.

## Sources publiques

`verification/live-audit-linux.json` utilise le réseau Electron, le renderer
et le décodeur, avec certificats vérifiés : un échantillon par source, parmi
21 adaptateurs. Un titre sans épisode publié reste non testé en lecture.
L’annuaire a actualisé Papadustream. L’audit Node avec recherche est conservé
séparément, historique v1.5.78 : `verification/sources-linux.json`.

| Source décodée | Image |
|---|---|
| IPTV-Org FR | 1024×576 |
| Anime-Ultime | 1280×720 |
| FrenchStream | 1920×798 |
| Vavoo FR | 1920×1080 |

18 catalogues répondent ; cela ne certifie ni tous leurs titres ni leurs lecteurs.
Les autres échantillons échouent ou restent non testés : HTTP 403, captcha,
ERR_BLOCKED_BY_CLIENT, délai réseau, vidéo 404 ou lecteur non reconnu.
Movix résout désormais une URL via Vidara, mais son média échantillonné répond 404.
Les résultats varient entre passages et ne garantissent pas la disponibilité future.

## Endurance et critères ouverts

Le test Linux de 30 minutes a réussi sur un candidat v1.6.78 chargé avant les
dernières corrections de transport/navigation : 1 800 025 ms, 30 relevés,
21 461 images décodées, aucune pause ni erreur détectée. Il boucle une vidéo MP4
locale et contrôle chaque minute images, état lecture, tas JS et nœuds DOM.
Preuve complète : `verification/endurance-e2e-linux.json` ; relevés :
`verification/endurance-linux.json`. Ce contrôle ne représente ni la lecture
distante prolongée, ni l’EXE final, ni la RAM totale ou Windows.

Un premier essai prolongé du portable sous Wine a été interrompu après trois
relevés. Les journaux Wine signalent « Disk quota exceeded » lors de la création
concurrente d’un autre préfixe de test ; le quota est une cause probable.
L’échec est conservé dans `verification/wine-endurance-failure.json`.
Après suppression du préfixe supplémentaire, un nouvel essai de dix minutes
a réussi, seul sous Wine, sur l’EXE livré identifié par SHA-256 : 600 014 ms,
10 relevés, 7 182 images, aucune pause ni erreur détectée, 344 nœuds DOM.
Tas JS mesuré : 10 983 728 à 11 833 760 octets ; ce n’est pas la RAM totale.
Les 29 scénarios avec PiP et la fermeture/sauvegarde passent dans cette exécution.
Preuve : `verification/wine-endurance-e2e-linux.json` ; relevés :
`verification/endurance-wine.json`. Cela ne valide pas 30 minutes sur Windows natif
ni l’endurance d’un média distant. La vidéo locale de 12 secondes est bouclée.

Restent bloquants : sources en échec, Windows natif propre, veille/reprise,
SmartScreen/pare-feu, chemins accentués Windows, écrans/périphériques physiques,
Android/Cast réels, PiP, pistes réelles, timers/rappels/IntroDB en lecture,
mises à jour publiées et endurance Windows. Le PiP manuel et un chemin accentué
ont été vérifiés sous Wine ; leur comportement Windows natif reste ouvert.
La CI construit avant de tester `dist/win-unpacked/TvSama.exe` et propose
30 minutes d’endurance en lancement manuel ; elle n’est pas exécutée ici.

## Reproduction et export

Dans windows : `npm ci`, `npm test`, `npm run dist`, `npm run test:e2e`.
ASAR : `TVSAMA_E2E_APP=/chemin/app.asar node e2e.cjs`.
Wine : `WINEPREFIX=/profil/test TVSAMA_WINE_EXE=/chemin/portable.exe node e2e.cjs`.
Windows natif : définir TVSAMA_WINDOWS_EXE vers l’exécutable construit.
Audit : `TVSAMA_LIVE_AUDIT=1 node e2e.cjs`.
Endurance : `TVSAMA_ENDURANCE_MS=1800000 node e2e.cjs`.
Les options de certificats/sandbox sont limitées aux fixtures locales du test.

Portable non signé : `../releases/TvSama-1.6.78-Windows-x64.exe`, précédents
conservés. Empreinte/taille : `verification/artifact-v1.6.78.json`.
Android v1.6.78 / code 83, Windows et lockfile alignés ; aucun APK recompilé ici.
