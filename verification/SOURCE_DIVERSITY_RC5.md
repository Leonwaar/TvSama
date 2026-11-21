# Catalogues et Coflix — v1.8.88-rc5

## Corrections

- L'ouverture d'une fiche recherche les autres catalogues même si deux sites sont déjà connus. Un alias traduit peut retrouver le même programme ; les années et identifiants IMDb contradictoires sont écartés.
- Les recherches d'une même fiche sont mutualisées et mises en cache (2 minutes, 15 secondes si vides, 64 entrées maximum). Budget de découverte : 18 secondes en arrière-plan ; la fiche principale peut apparaître avant sa fin. Le classement de découverte ne demande pas la popularité TMDB.
- Une erreur sur un titre alternatif conserve les résultats du site déjà reçus. Lorsqu’il y a plusieurs sites, un fournisseur est limité à deux extractions simultanées sur les quatre disponibles, afin de laisser une place aux autres sites. Les lecteurs retournés alternent les fournisseurs, en conservant les langues inconnues en dernier. Le nombre affiché dépend des médias réellement accessibles ; aucun objectif numérique ne crée de faux miroir.
- Coflix : nouvelle session lors d'une véritable actualisation des lecteurs ; conservation des lecteurs publiés même marqués inactifs et des liens de téléchargement publiés. Le format Matroska est reconnu, tandis qu'une page HTML ne devient pas un média parce que son URL contient un nom de fichier.
- Un lecteur Coflix conserve son hôte, sa langue et son origine si l'ordre des miroirs change. Une réponse « iframe » extrait l'adresse d'origine `url`, pas un éventuel champ `play` promotionnel. Les pages originales passent par les extracteurs natifs existants.
- Le portail public Coflix indiqué par Oustreamer est suivi uniquement vers une adresse explicitement disponible, puis le vrai catalogue est validé avant remplacement. Les changements passent toujours par la gestion partagée des adresses.
- Le budget Coflix tient compte de ses deux étapes API puis hébergeur (24 secondes, sous les limites de recherche existantes). Les variantes VF régionales sont essayées tôt. Les extraits courts restent rejetés par la validation commune.

## États de santé et Wiflix

Les états distinguent catalogue accessible, vidéo trouvée, lecture confirmée pendant 90 secondes, validation du site, accès refusé, page introuvable et réponse trop lente. Les réponses techniques distantes ne sont plus copiées dans ces états. Les sources web affichent aussi leurs erreurs d'accès. Un test manuel est annulable et son annulation ne laisse plus « Vérification… » affiché indéfiniment.

Le test manuel laisse jusqu'à 45 secondes par fiche dans un budget total de 75 secondes après chargement du catalogue. Le portail Wiflix actuel est reconnu à partir de sa destination publiée ; le fournisseur essaie sa dernière adresse et son portail sans dépendre d'OùStreamer. Le choix de désactiver la mise à jour automatique reste respecté. Le catalogue doit être validé avant mémorisation. La validation HTML s'arrête dès que les fiches sont présentes, sans attendre un mégaoctet de contenu ; son budget permet désormais au DNS partagé de répondre. Les portails Wiflix/Coflix sont revalidés même lorsque l'annuaire propose la même URL.

Le 23 octobre 2026 à 19:21 UTC, un essai Wiflix sans annuaire a chargé 30 fiches et mémorisé l'adresse validée. L'essai suivant à 19:28 UTC a échoué avec un catalogue vide : ce succès n'est donc pas une garantie de disponibilité durable. Le détail du dernier essai est dans [WIFLIX_RECOVERY_NETWORK.md](WIFLIX_RECOVERY_NETWORK.md).

## Contrôles et limites

Les tests locaux couvrent la découverte d'un troisième catalogue à partir de deux références, les titres traduits et remakes, la conservation d'un résultat si un alias échoue, l'alternance des sites, le portail, les miroirs réordonnés, les liens publiés et le choix de l'URL originale.

Les essais réseau sont conservés dans [COFLIX_VIDEO_NETWORK.md](COFLIX_VIDEO_NETWORK.md) et [CATALOGUE_DIVERSITY_NETWORK.md](CATALOGUE_DIVERSITY_NETWORK.md). Sur Spider-Man : Brand New Day, aucun flux complet n'a été confirmé par le gestionnaire natif lors du dernier essai : délais et erreurs réseau des lecteurs. Forcer IPv4 sur la machine de test n'a pas suffi. L'existence d'un lien dans l'API ne prouve donc pas sa lecture.

L'essai de diversité a trouvé trois catalogues pour La La Land, deux pour Simpson et aucun pour Resident Evil. Il ne prouve ni dix miroirs par programme ni la lecture Android. Les captchas de Papadustream et FRAnime et les essais matériels restent non résolus. La durée et la signature média ne prouvent pas, à elles seules, l'identité du programme.

Le téléchargement mobile est seulement [préparé](OFFLINE_MOBILE_PLAN.md) pour une prochaine mise à jour. Priorité conservée aux sources.

Le [contrôle complet des sources](../SOURCE_AUDIT.md) conserve ses résultats datés avant les derniers correctifs Wiflix. Un statut OK de signature réseau ne signifie pas une lecture Android confirmée.

Les derniers correctifs d’actualisation sont décrits dans [AUTONOMOUS_ADDRESSES.md](AUTONOMOUS_ADDRESSES.md), y compris les observations de l’API OùStreamer et les annuaires de secours.
