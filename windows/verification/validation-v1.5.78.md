# Validation · v1.5.78 · 8 octobre 2026

## Résultats obtenus

- 39 tests passent : 20 tests de services, 9 tests de maintenance/extraction,
  10 régressions du vrai renderer sous JSDOM.
  Rapport complet : `verification/services-v1.5.78-linux.tap`.
  Les deux rapports `regressions-before.tap` et
  `regressions-library-before.tap` conservent les échecs avant correction.
- Electron Linux sous Xvfb : 21 scénarios passent, zéro erreur JavaScript observée.
  MP4/HLS/DASH décodés, progression, pause/reprise, plein écran et Échap,
  favoris conservés, flèches, toucher injecté via Chromium, menus clavier/souris,
  licences et erreur d’association récupérable.
- Interface : 360×480, 640×480, 1280×720, 1920×1080, 3840×2160 ;
  zoom Electron 100/125/150/200 %. Aucun débordement horizontal du document
  ou du contenu mesuré ; menus à l’intérieur du viewport.
- Le plein écran échouait dans Electron à cause du refus général des permissions :
  autorisation limitée à cette permission pour la seule fenêtre de l’application.
- Bibliothèque : recherche des flux personnels, pagination au-delà de 120 favoris,
  fiche conservée hors ligne, langue réellement disponible et retour sans requête.
- Lecteur : résolution concurrente protégée contre les réponses obsolètes, position
  conservée au changement de serveur, reconnaissance des médias sans extension.
  Un échec des lecteurs déclenche un seul renouvellement automatique, puis expose
  une reprise manuelle. Le cache des anciennes URL est ignoré au renouvellement.
- Maintenance : annuaire quotidien, contrôle périodique, backoff après panne,
  conservation des adresses connues, migration des anciennes URL, annulation
  pendant l’attente et délais globaux de catalogue/résolution bornés.
- Version Android alignée : v1.5.78, code 82. Versions Windows et lockfile alignées.
- Les mêmes 21 scénarios passent sur l’archive ASAR du paquet Windows chargée
  par Electron Linux : `verification/bundle-e2e-linux.json`.
  21 fichiers runtime vérifiés identiques aux sources : `verification/asar-v1.5.78.json`.
  Empreinte et taille du portable : `verification/artifact-v1.5.78.json`.

Le rapport `verification/e2e-linux.json` et les captures contiennent les mesures.
Tas JavaScript du renderer : 14 335 004 octets lors du dernier passage ; ce chiffre
ne représente pas la RAM totale et ne mesure pas les performances Windows.
Les fixtures durent environ 12 secondes, chaque décodage vérifié dépasse 1,2 s.
Ce contrôle n’est pas un test d’endurance.

## Audit des sources

`verification/sources-linux.json` : 21 sources, annuaire actualisé, un catalogue,
une recherche, une fiche et une résolution par source accessible.
13 catalogues répondent ; 7 échantillons fournissent une ou plusieurs URL.
L’annuaire a modifié l’adresse Papadustream, mais son accès reste en échec.
Aucune vidéo distante n’est décodée par cet audit.

Anime-Ultime : URL MP4 signée sans extension maintenant reconnue.
MyFluneo : repli vers son API si le catalogue HTML est vide ; serveurs Flight JSON
extraits sans exécution de scripts, limités à l’épisode demandé. Les serveurs du
premier épisode testé répondent encore par erreurs réseau ou HTTP 404.
AnimeKO : captcha requis signalé avec une action à suivre dans Sources.
FRAnime, Animes-Sama, FrenchAnime et WarFlix restent en échec d’extraction sur
l’échantillon testé. Plusieurs autres sites répondent par HTTP 403 ou erreur réseau.
Voiranime résout une URL, mais sa recherche de l’échantillon n’a pas de résultat.
Le réseau Electron peut se comporter différemment de fetch Node Linux.

## Critères ouverts

Le portable est construit depuis Linux ; son exécution sur Windows réel, veille,
installation propre, SmartScreen/pare-feu, chemins accentués et périphériques
tactiles/HID physiques ne sont pas validés. La CI Windows est préparée, non exécutée.
Cast/Android réels, PiP, rappels, IntroDB en lecture, pistes réelles, mises à jour
publiées et endurance 30 minutes restent ouverts.
Les rappels nécessitent l’application ouverte ; la dictée utilise Windows + H.
La stabilité perpétuelle des sites et une parité à 100 % ne sont pas revendiquées.
La feuille de route `STABILISATION.md` conserve ces conditions bloquantes.

## Reproduction

Dans `windows/` : `npm ci`, `npm test`, `npm run test:e2e`,
`node audit-sources.cjs`, `npm run dist`.
Les tests Electron utilisent un profil temporaire et un serveur HTTPS local.
Le portable est non signé ; sa note de release fournit son SHA-256.
