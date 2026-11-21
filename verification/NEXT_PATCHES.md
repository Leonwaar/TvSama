# Correctifs livrés — v1.8.87

Version universelle signée générée le 21 octobre 2026 : `../releases/TvSama-v1.8.87-universal.apk`, versionCode 94. Signature identique à v1.8.86 ; ABI arm64-v8a, armeabi-v7a, x86 et x86_64 vérifiées. Android 6.0 minimum.

SHA-256 : `6c6fc530bda77ca94aa51d0fb1fb874fc8e98d94f1a56dc226f59b76d7170450`.

## Navigation

- Les mises à jour de la première page remplacent les résultats déjà fusionnés au lieu de les fusionner à nouveau.
- Le retour depuis une fiche réutilise le catalogue chargé pour la même recherche et la même génération.
- Le catalogue ne charge plus de pages supplémentaires sans action sur « Afficher davantage ».
- Les sorties cinéma ne sont plus actualisées à chaque retour sur l’accueil après une actualisation manuelle.
- Le lecteur ne s’arrête plus après douze secondes sans première image. Les erreurs remontées par Media3 restent traitées.

## Sources et validation humaine

| Source | Correctif | Limite de vérification |
|---|---|---|
| FrenchStreaming | Variantes de langue conservées ; redirection Filmoon vers Vidara ; nouveau flux HLS publié par Vidzy reconnu sans imposer l’ancien script compressé ; session et en-têtes transmis | Vérification média dans l’audit réseau ; disponibilité des hébergeurs variable |
| FRAnime | En-tête JSON conforme au client publié ; lecteur interne `/watch2/` accepté ; l’accueil reste rejeté comme faux résultat | Le lecteur interne demande une validation de sécurité dans l’environnement d’audit |
| AnimeKO | Reprise à partir de la page validée par l’utilisateur et de son lecteur publié | Captcha interactif à tester sur Android réel |
| Papadustream | Même reprise à partir du lecteur de la page validée | Captcha interactif à tester sur Android réel |
| Wiflix | Cookie utilisateur conservé ; page validée et clé publiée par le lecteur réutilisables | La recherche réseau sans validation renvoie encore 403 |
| FrenchAnime | Cookies partagés ; en-têtes de session ; liens de validation corrigés avec le chemin et l’extension `.html` ; page validée réutilisable ; nouveau lecteur Vidzy pris en charge | Échantillon média vérifié le 21 octobre ; recherche encore protégée par HTTP 403 |
| BlablaStream | Retiré du registre et des raccourcis actifs | L’adresse contrôlée affiche un élément `fake-player` et conduit à une publicité VPN |
| Voiranime | Nouveau domaine `voembed.net` relié au parseur HLS existant | Échantillon vérifié dans l’audit final v1.8.87 ; des expirations ont été observées lors d’essais précédents |
| MyFluneo | Même prise en charge du lecteur myTV ; contrôle sur un épisode publié de Naruto | Échantillon Naruto vérifié dans le dernier audit ; les autres titres ne sont pas tous validés |

Quand une lecture échoue, la fiche peut être ouverte dans la page intégrée. Après validation manuelle, « J’ai validé · réessayer » conserve les cookies et reprend la lecture native. Les pages rendues du même contexte navigateur sont gardées uniquement en mémoire, pendant deux minutes, avec quatre entrées au maximum. Une page est réutilisable uniquement pour son adresse HTTPS exacte, sans le fragment ; elle ne donne aucun accès aux autres pages ou domaines.

Si le lecteur fonctionne dans la page mais que son extraction directe échoue, « Lire la vidéo affichée dans TvSama » permet son transfert natif. Ce bouton apparaît lorsqu’une requête HLS, DASH ou MP4 est observée. Le transfert exige encore une vérification réelle du manifeste et du média ; observer une URL ne suffit pas à afficher la coche verte. L’utilisateur doit vérifier que la vidéo affichée correspond au contenu voulu avant de la transférer.

## Changements d’adresse via Oustreamer

Les nouvelles adresses publiques sont contrôlées puis mémorisées. Les chemins, paramètres et fragments des favoris sont conservés après plusieurs changements de domaine. L’application vérifie l’annuaire au démarrage et périodiquement ; après un échec de lecture, elle peut aussi actualiser l’annuaire et réessayer une fois si l’adresse concernée a changé. Les vérifications automatiques rapprochées utilisent un délai de cinq minutes ; l’actualisation explicite reste immédiate.

Les contrôles des catalogues FRAnime, FrenchAnime et Frembed acceptent désormais leur format réel. FRAnime est vérifié en lecture progressive pour éviter de charger tous ses épisodes en mémoire. Une adresse candidate est vérifiée directement, sans appliquer les anciennes redirections à cette vérification. Une proposition incompatible ne remplace pas l’adresse conservée.

Les requêtes d’annuaire annoncent un client navigateur et attendent du JSON ; deux pages au maximum sont demandées en parallèle. Le test réseau ciblé d’Oustreamer passe après cette correction et le dernier audit complet analyse 382 entrées françaises.

« Vérifier média » travaille sur une seule source à la fois, avec plusieurs échantillons de son catalogue. Dans cette version, la coche verte indiquait qu’un média avait passé la vérification réseau. Depuis rc3, elle exige une lecture plus longue ; voir [le contrôle des extraits](PROMO_VIDEO_GUARD.md).

## Vérification

Les tests locaux couvrent les variantes de langue, le lecteur FRAnime, les liens FrenchAnime, le cookie Wiflix, les sources HLS publiées et l’isolation des pages validées. Le rapport réseau actualisé est dans `SOURCE_AUDIT.md` ; les preuves par échantillon sont dans `verification/source-audit.jsonl`.

Les tests locaux et `:app:assembleRelease` passent sur v1.8.87. Les nouveaux contrôles couvrent aussi les rotations successives d’adresse et le transfert d’un média observé dans le navigateur : il reste non vérifié jusqu’au contrôle de ses octets. L’APK a passé `apksigner verify` et `zipalign -c -P 16`.

Le test réseau `PopularTitlesNetworkTest.sourceVerificationGreensOnlyAnAccessibleMediaSample` passe pour le nouveau parcours de vérification ciblée sur FrenchStreaming. L’audit final du 21 octobre comporte 20 adaptateurs actifs, dont 15 avec un échantillon média vérifié. FrenchStreaming passe désormais via Vidzy et FrenchAnime a aussi un échantillon accessible. FRAnime, AnimeKO, Papadustream et Wiflix restent bloqués par une validation ; leur parcours interactif de reprise est intégré, mais leur lecture après validation humaine reste à confirmer sur appareil. L’échantillon Vavoo France Live TV échoue dans ce dernier essai, après avoir passé des essais précédents. Ces résultats décrivent les échantillons et la session d’audit, sans garantir tous les titres ni la disponibilité future des hébergeurs.

L’environnement JVM ne réalise ni captcha interactif ni décodage vidéo matériel. La fluidité et la manipulation des captchas à la télécommande restent à vérifier sur le projecteur.
