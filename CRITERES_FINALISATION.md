# Critères de finalisation TvSama

Demandés par l’utilisateur : poursuivre les corrections tant que les sources et le lecteur ne sont pas opérationnels. Une source ne devient pas « OK » sur la seule réponse de sa page d’accueil.

## Demandes à valider

- [ ] Chaque source fournie possède une véritable intégration de catalogue, recherche, fiches et lecteurs ; un lien externe ne valide pas ce critère.
- [ ] Anime-Sama et Anime-Ultime : catalogue non vide, recherche et lecture vérifiées sur des appels réels.
- [ ] Classroom of the Elite : saisons complètes, VF/VOSTFR correctes, démarrage d’un épisode réellement décodé.
- [ ] Directs : manifeste et segments valides, lecture avec son, basculement en cas de serveur défaillant.
- [ ] Movix : catalogue sans clé via les pages publiques TMDB, API personnelle facultative, lecture vérifiée ; aucun secret inventé ou pris à un autre compte.
- [ ] Coflix : sessions navigateur conservées et accès réel contrôlé ; aucune protection présentée comme résolue sans essai.
- [ ] Adresses : actualisation automatique depuis l’annuaire, persistance et conservation des favoris après rotation.
- [x] Minuterie automatique : désactivée ou 1 h, 1 h 30, 2 h, 2 h 30, 3 h ; échéance conservée entre lecteurs et épisodes (tests de régression passés).
- [ ] Chrono actif visible dans le lecteur, arrêt effectif à l’échéance, reprise explicite.
- [x] Films : aucune saison ou numéro d’épisode inventé dans la reprise (test de régression passé).
- [x] Découvertes : enrichissement en place, nouvelles affiches ajoutées à la suite ; test sur 2 500 titres passé.
- [x] Alias anglais/français/japonais : une fiche et plusieurs références ; japonais conservé et remakes distincts (tests passés).
- [ ] Assombrissement automatique configurable dans le menu : pause de 30 s, assombrissement fort, restauration au toucher, mouvement ou commande.

## Critères supplémentaires

### Critères ajoutés le 6 octobre 2026

- [ ] Un inventaire généré depuis les adaptateurs et les liens affichés dans l'APK donne, pour chaque nom visible, son type d'intégration et son état réel. Aucun simple lien de site n'est présenté comme une source opérationnelle.
- [ ] Chaque essai de source conserve aussi l'URL de référence ou un identifiant stable, l'heure, la langue demandée, le serveur retenu, l'étape exacte d'échec et le code HTTP ou Media3, sans exposer cookies ni jetons.
- [ ] Pour un épisode ou un film vérifié, le contenu décodé correspond au titre, à la saison et au numéro demandés. Une vidéo publicitaire, une bande-annonce ou un autre épisode ne valide pas la source.
- [ ] Les erreurs réseau temporaires et les fins de fenêtre HLS déclenchent une reprise bornée du direct à sa position courante ; une playlist terminée ou un événement fini ne provoque aucune boucle de relance.
- [ ] Après changement ou désactivation d'une source, les résultats en cache, favoris, reprises et alertes ne pointent pas silencieusement vers une autre œuvre ou vers une source désactivée.
- [ ] La version livrée correspond exactement à l'APK testé : empreinte SHA-256, versionCode, installation neuve et mise à jour, avec un rapport daté. Toute modification ultérieure annule cette validation de livraison.

- [ ] Distinguer les états : catalogue disponible, lecteur extrait, média accessible, vidéo décodée ; conserver les preuves et les limites du contrôle.
- [ ] Aucun gel de l’interface pendant recherche, actualisation ou intégration des résultats.
- [ ] Démarrage dès le premier média validé ; autres serveurs ajoutés en arrière-plan.
- [ ] Délais bornés, annulation au changement d’épisode, pas de boucle entre liens déjà échoués.
- [ ] Respecter VF/VOSTFR et le choix de langue ; ne jamais étiqueter une piste inconnue comme VF.
- [ ] Préserver progression, favoris, minuterie et sessions lors des transitions.
- [ ] Ne pas intégrer de mot de passe, adresse personnelle ou jeton privé dans l’APK.
- [ ] Compilation, tests de régression et essai Android de l’APK livré réussis.

## Barrières strictes avant déclaration « final »

Les cases cochées ci-dessus attestent uniquement les régressions automatisées indiquées ; elles ne remplacent pas les essais Android ci-dessous. Aucun critère global n’est acquis tant que toutes ses sous-vérifications ne sont pas passées.

### Sources : toutes les sources fournies, sans exception silencieuse

- [ ] Inventaire nominatif complet : Anime-Sama, Anime-Ultime, Animes-Sama, FRAnime, AnimeOVF, AnimeKO, AniWatch, VoirAnime, Movix, Coflix, ActVid, BlablaStream, DessinAnime, FrenchStreaming, TFX73 et WarFlix, ainsi que les autres adaptateurs activés.
- [ ] Pour chaque source : catalogue réel, cinq recherches pertinentes, pagination sans doublons, cinq fiches complètes et correspondance exacte titre/année/type.
- [ ] Pour chaque source de films : trois films différents décodés pendant au moins deux minutes chacun ; son présent dans les pistes et décodeur audio actif.
- [ ] Pour chaque source de séries : deux séries différentes, trois épisodes par série dont un changement de saison ; aucun numéro inventé et aucune saison disponible sur le site omise.
- [ ] Tester VF et VOSTFR lorsque le site les fournit ; contrôler les pistes et les sous-titres, pas seulement le texte du bouton.
- [ ] Classroom : S1 E1, un épisode intermédiaire et un épisode d’une autre saison ; contrôler le bon contenu et l’accès au premier épisode après installation neuve.
- [ ] Une source qui nécessite une connexion n’est validée qu’après authentification réelle et relecture après redémarrage ; aucune création de compte annoncée sans preuve.
- [ ] Aucun « OK » pour une page HTML, un captcha, une redirection publicitaire, un fichier vide ou un flux dont les segments échouent.

### Lecteur et performances

- [ ] Première image : médiane ≤ 5 s et 95e percentile ≤ 10 s sur dix lancements par source avec média accessible, réseau de test documenté et cache froid ; extraction et décodage inclus.
- [ ] Interface : réaction visuelle ≤ 150 ms aux commandes courantes ; aucun ANR ni gel > 500 ms pendant chargements et actualisations.
- [ ] Détails : affichage exploitable ≤ 5 s ; les métadonnées et les autres fournisseurs ne retardent pas la lecture déjà disponible.
- [ ] Épisode suivant : première image ≤ 5 s quand préchargé ; progression et langue correctes, aucune remise à zéro de la minuterie.
- [ ] Directs : trois événements sur des lecteurs différents pendant cinq minutes chacun, image et audio décodés, reprise après coupure et absence d’erreur de conteneur.
- [ ] Erreurs : couper le réseau, répondre HTTP 403/404/500, retourner HTML à la place d’un média, rendre un serveur lent ; repli borné, aucune boucle et annulation effective à la navigation.
- [ ] Refaire les essais après fermeture forcée, redémarrage, passage en arrière-plan et retour ; aucun lecteur, WebView ou téléchargement abandonné ne continue inutilement.

### Confort et données

- [ ] Assombrissement : vérifier à 29 s, 30 s et après mouvement ; luminosité et surcouche rétablies en ≤ 250 ms, vidéo toujours en pause, aucun assombrissement causé par un simple buffering.
- [ ] Tester toucher, souris, télécommande, commandes du téléphone, mode plein écran et PiP ; aucune commande de réveil ne lance une lecture involontaire.
- [ ] Minuterie : tester les cinq durées, désactivation, changement de serveur, épisode et film ; arrêt local et Cast à l’échéance, aucune reprise automatique après expiration.
- [ ] Catalogue : cinq actualisations successives et trois pages ajoutées ; les positions existantes et le focus restent identiques, références enrichies, nouveaux titres uniquement à la suite.
- [ ] Alias : recherches anglaises, françaises et japonaises sur dix animes et dix films ; une seule fiche par œuvre et aucun regroupement de remake, suite ou type différent.
- [ ] Progression et favoris : conservation du timecode exact après changement de source et redémarrage ; aucune perte à la rotation d’adresse.

### Autonomie et livraison

- [ ] Adresse changée dans l’annuaire : adaptation au prochain contrôle, ancienne URL sauvegardée encore utilisable, adresse incompatible rejetée ou retour à la dernière adresse fonctionnelle.
- [ ] Vérifier le renouvellement d’un lien expiré et la restauration d’une session navigateur sans redemander inutilement une connexion.
- [ ] Chaque preuve contient date, version APK, source, titre/épisode, étape, résultat et durée ; les échecs sont conservés.
- [ ] APK final compilé après la dernière modification, tests réussis, installation en mise à jour et installation neuve vérifiées ; version et rapport correspondent à cet APK.

### Expérience TV inspirée de SmartTube

Référence d’ergonomie : [projet SmartTube](https://github.com/yuliskov/SmartTube), interface adaptée à la TV, commandes personnalisables et réglages de lecture. Les exigences ci-dessous sont les critères propres à TvSama, et doivent être vérifiées dans son APK.

- [ ] Parcours entièrement réalisable avec une télécommande à cinq boutons : accueil, recherche, fiche, saison, épisode, lecture, réglages et retour ; aucune étape exigeant un écran tactile.
- [ ] Focus toujours visible et cohérent ; retour à la même affiche et à la même position après fermeture d’une fiche ou du lecteur ; aucune perte de focus après actualisation.
- [ ] Cent actions directionnelles consécutives sur le catalogue et les commandes sans blocage, saut imprévisible ou piège de navigation ; maintien d’une flèche utilisable sans déclencher plusieurs lectures.
- [ ] Bouton central : afficher les commandes quand elles sont masquées, sélectionner l’action focalisée quand elles sont visibles ; touches lecture/pause, avance et retour prises en charge sans double exécution.
- [ ] Retour ferme d’abord le menu, puis les commandes, puis le lecteur ; progression sauvegardée avant sortie et aucun abandon involontaire de la vidéo.
- [ ] Réglages audio, sous-titres, qualité, vitesse, format d’image, minuterie et assombrissement accessibles depuis le lecteur ; valeur active visible et conservation des préférences entre épisodes.
- [ ] Barre de progression utilisable à la télécommande, position cible affichée avant validation, recherche bornée à la durée réelle ; un direct sans fenêtre de reprise n’affiche pas une fausse durée navigable.
- [ ] Contrôles lisibles à trois mètres sur TV 1080p, sans éléments tronqués ; vérification également en 720p et 4K, et sur téléphone en portrait et paysage.
- [ ] Commandes masquées automatiquement pendant lecture, maintenues visibles pendant navigation dans un menu ; le premier mouvement réveille la pause assombrie sans reprendre la lecture.
- [ ] Enchaînement automatique avec prochain épisode identifié, possibilité d’annuler et de désactiver ; fin de saison correcte et absence de lancement répété du même épisode.
- [ ] En cas d’erreur, conserver le timecode, la langue et la minuterie pendant le changement de serveur ; expliquer l’échec et proposer une action utilisable à la télécommande.
- [ ] Session continue de deux heures sur TV, avec dix changements d’épisode, cinq recherches, trois reprises et deux coupures réseau : aucun crash, ANR, audio superposé ou lecteur resté actif après sortie.

Aucune comparaison de confort avec SmartTube n’est présentée comme acquise avant ces essais. Une implémentation ou un test unitaire seul ne suffit pas à cocher ces critères.

Les essais de décodage exigent un lecteur Android réel ou émulé. Une réponse HTTP, une extraction ou un test JVM seul ne les valide pas. Les indisponibilités d’un service externe restent signalées et empêchent de présenter cette source comme finalisée. La durée de validité d’un contrôle et le périmètre effectivement testé figurent dans le rapport ; aucun résultat n’est extrapolé à tout un catalogue.

## Point de contrôle du 8 octobre 2026 — livraison suspendue

Le build de livraison a été interrompu : les critères globaux ci-dessus ne sont pas tous satisfaits. Aucun APK de cette session n’est déclaré final.

Corrections réalisées : URL Movix avec crochets, reconnaissance des sagas Animes-Sama, lecture des boutons Wiflix encodés, état de lecture Cast et cible des sauts tactiles après changement de lecteur. Les régressions des parseurs ont passé leurs tests ciblés. La correction Wiflix est confirmée sur un échantillon réseau, sans décodage Android de cette source.

Preuves partielles : lecture Android et reprise de progression après remplacement de l’APK observées sur émulateur ; cela ne valide ni toutes les sources ni les parcours TV/Cast.

Restent notamment : sources externes non intégrées, sources protégées ou indisponibles, matrice de titres/langues, essais de panne, mesures de performance, session TV de deux heures, Cast et vérification finale de l’artefact exact. Les échecs externes ne sont pas reclassés en succès.

### Périmètre Android accepté le 8 octobre

L’utilisateur demande de poursuivre les essais avec un émulateur Android quelconque, sans exiger un modèle TV. Les parcours au pavé directionnel peuvent donc être exercés sur l’émulateur téléphone disponible. Cela ne constitue pas une preuve de réception Cast : les décisions locales de pause/reprise peuvent être contrôlées séparément.

Corrections complémentaires : la minuterie expirée bloque l’autoplay initial et la reprise locale après Cast ; les commandes du téléphone ciblent le lecteur actif ; une fin normale de direct ne déclenche plus la recherche automatique d’un autre serveur. Les validations Android de ces modifications restent à relever ci-dessous.

### Preuves supplémentaires du 8 octobre

Voir `verification/2026-10-08/ANDROID.md` : cinq scénarios instrumentés réussis sur émulateur (MP4/HLS/DASH + audio, erreurs HTTP/HTML, minuterie expirée et expiration en lecture, reprise explicite, commandes et remplacement du lecteur). Le test de minuterie injecte une échéance rapprochée ; il ne remplace pas une session de deux heures. L’accueil reste utilisable après 100 touches directionnelles, avec focus final présent ; l’ensemble du parcours télécommande reste à contrôler.

Les cases globales demeurent ouvertes : ces preuves sont partielles et ne couvrent pas les médias de chaque fournisseur. Le dernier audit conserve notamment des HTTP 403, des captchas, des catalogues sans épisode disponible et cinq sources sans adaptateur.
