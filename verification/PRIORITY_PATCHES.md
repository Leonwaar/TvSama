# Sources prioritaires — v1.8.88-rc1

État du 21 octobre 2026 : version de validation, release finale bloquée.
L’utilisateur ne peut pas effectuer les captchas maintenant.

## Version de validation disponible

`../../releases/TvSama-v1.8.88-rc1-validation-universal.apk`, versionCode 95,
64 210 988 octets. Compilation **debug de validation**, non publiée comme release finale.
ARM64, ARMv7, x86 et x86_64 ; Android 6.0 minimum. Signature et alignement 16 Ko vérifiés.
Certificat identique à v1.8.87 : installation en mise à jour possible.

SHA-256 : `2058064c2ec54e2bb4d67663701ac3cd635e1486eec1c15521ecc969d9f82371`.

La commande locale complète réussit : 147 tests exécutés sans échec ;
18 contrôles réseau facultatifs ignorés dans cette commande. Les contrôles réseau
séparés ci-dessous restent nécessaires et ne sont pas remplacés par ce résultat local.

## Modifications

- Vavoo est retiré du registre des sources, dans toutes ses langues. Aucun historique ni favori n’est effacé.
- Wiflix, Papadustream et FRAnime restent des adaptateurs natifs, pas des raccourcis web.
- L’actualisation démarre à chaque lancement, en arrière-plan, et se répète toutes les six heures.
  Le bouton « Actualiser adresses et catalogues » appelle la même actualisation forcée.
- Les adresses Oustreamer sont validées puis enregistrées dans la base locale `source_addresses`
  et les préférences des adaptateurs. Une adresse déjà correcte est aussi enregistrée.
  Les anciennes adresses des favoris restent routées vers les domaines actuels.
- Le portail Wiflix publié dans Oustreamer est suivi jusqu’à son adresse de catalogue.
  Le contenu du catalogue doit encore être validé avant d’adopter l’adresse finale.
  Une simple page de redirection ne devient pas une source native validée.
- Les fiches, serveurs et flux mis en cache sont invalidés. Le catalogue FRAnime est renouvelé,
  sauf s’il vient déjà d’être téléchargé pendant cette actualisation : cela évite une seconde
  récupération et analyse de son catalogue de plusieurs mégaoctets au démarrage.
  Les liens de lecture sont redemandés lors de la lecture, plutôt que conservés comme des liens permanents.
- Trois catalogues au maximum sont vérifiés en parallèle. Wiflix limite aussi à trois ses
  requêtes de résolution des serveurs.
- Wiflix utilise la signature et les paramètres publiés avec ses jetons de lecteur.
  L’en-tête `Accept-Language: fr-FR,fr;q=0.9` corrige le HTTP 403 de sa recherche.
- FRAnime ouvre la page `/watch2/` exacte ayant demandé une validation, pour le bon épisode
  et la bonne langue. La reprise réutilise cette page après la validation, même si la manipulation
  du captcha a duré plus de deux minutes. Les instantanés validés restent limités à deux minutes
  à partir de leur capture ; aucun HTML de session n’est sauvegardé sur disque.

L’application lit l’annuaire public Oustreamer. Elle ne modifie pas la base distante de ce service.

## Contrôles

Les tests locaux couvrent le calcul de signature Wiflix, la validation du portail,
le retrait de Vavoo, la présence native des trois sources, l’isolation du lecteur
FRAnime par épisode et l’invalidation de son cache.

Le test réseau `PopularTitlesNetworkTest.wiflixKnownMovieHasVerifiedMedia` passe :
recherche « Fight Club », fiche, résolution des lecteurs et média/segment accessibles via Voe VF.
Le lecteur Vidara testé sur ce titre répond HTTP 525 ; le relais Voe fonctionne.

Le contrôle des trois sources enregistre aussi leurs adresses réellement mémorisées :
[PRIORITY_SOURCES.md](PRIORITY_SOURCES.md). Wiflix passe sur « Clueless » via Voe VF.
Papadustream demande un jeton Turnstile pour obtenir son iframe de lecture ; FRAnime
demande une validation de sécurité sur `/watch2/`. Le contrôle exige un média accessible
pour chacune des trois sources et reste en échec tant que ces étapes ne passent pas.

Commandes :

```sh
./gradlew :app:testDebugUnitTest :streamflix:testDebugUnitTest :app:assembleDebug --offline
./gradlew :app:testDebugUnitTest --tests fr.nekotv.PopularTitlesNetworkTest.wiflixKnownMovieHasVerifiedMedia -PnetworkAudit=true --offline
./gradlew :app:testDebugUnitTest --tests fr.nekotv.PrioritySourcesNetworkTest -PnetworkAudit=true --offline
```

## À vérifier sur l’appareil avant la release finale

1. Ouvrir un épisode Papadustream et lancer la lecture. Dans la page proposée,
   choisir un hébergeur et effectuer le captcha, puis « J’ai validé · réessayer ».
2. Faire de même pour un épisode FRAnime : la page proposée doit être le lecteur
   `/watch2/` correspondant à cet épisode.
3. Si la vidéo fonctionne seulement dans la page, utiliser « Lire la vidéo affichée
   dans TvSama » après avoir confirmé qu’il s’agit du bon contenu.
4. Vérifier l’image, l’audio, la télécommande, puis un autre épisode après relance.

Les captchas obligatoires peuvent être redemandés par ces sites. Le partage des cookies
et l’actualisation des adresses ne garantissent pas une dispense permanente de captcha.
Le décodage et la fluidité sur le projecteur n’ont pas été vérifiés dans cet environnement.
