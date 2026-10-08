# TvSama v1.1.74 — versionCode 75

## Changements

- Télécommande locale après scan QR : lecture/pause, déplacement au timecode, ±30 secondes, précédent/suivant, sélection d’un épisode et relance du timer depuis le téléphone.
- Progression et liste Reprendre synchronisées toutes les trois secondes entre les deux applications ouvertes ; les retraits de la liste sont partagés. La dernière progression horodatée gagne, même après un retour en arrière dans la vidéo.
- Une recherche distante conserve le lecteur TV actif et ne lance pas de recherche catalogue sur la TV pendant la lecture.
- Démarrage TV sans chargement automatique des fournisseurs/catalogues ; le catalogue reste accessible sur demande. Les liens sont résolus sur le téléphone lorsqu’il pilote la TV.
- Pause automatique trois heures après l’ouverture du lecteur, sans remise à zéro lors du changement d’épisode. Bouton pour relancer trois heures dans le lecteur et sur le téléphone.
- OK met lecture/pause lorsque la surface vidéo a le focus ; les commandes visibles gardent leur propre action.
- Menu latéral replié, ouvert au focus sur Menu ou au clic, refermé en quittant le groupe.
- Bouton Fermer sur le message d’erreur de lecture.
- Chargement automatique des sources sur la fiche. Préparation des sources voisines et préchargement partiel MP4/HLS dans un cache disque borné à 24 Mo. Les liens préparés sur le téléphone sont envoyés à la TV ; le début de la vidéo est chargé par la TV.
- Lecture automatique conservée à l’ouverture du lecteur.
- Identifiant IMDb des séries récupéré via les external_ids TMDB. Bouton IntroDB focalisé à son apparition ; l’outro propose l’épisode suivant s’il existe. Les segments restent conditionnés aux données disponibles.
- Avance télécommande : pas de 1 seconde pendant les trois premières secondes d’appui, puis 10, 30 et 60 secondes.
- Recherche : correspondance du titre prioritaire ; popularité/tendances TMDB si une clé est configurée. Alternance des catalogues pour éviter une liste monopolisée par le premier fournisseur.
- Direct : prise en charge des iframes ordinaires, bonne base URL après redirection et récupération du manifeste via le lecteur web en cas d’extracteur absent. Les en-têtes de lecture sont conservés ; les lecteurs déjà en échec ne sont pas retentés en boucle.

## Vérification et limites

Compilation APK et tests unitaires des modules app et streamflix. Tests ajoutés pour les commandes à distance, timecodes, synchronisation et retraits de Reprendre, classement des titres, accélération et parsing des lecteurs sportifs.

Aucun appareil Android connecté lors de la validation : lecture sportive réelle, focus télécommande, délai réseau et fluidité du projecteur restent à vérifier sur les appareils. Le préchargement réduit l’attente, sans garantir un lancement instantané. Installer cette version sur les deux appareils.

Les données de popularité dépendent de TMDB : https://developer.themoviedb.org/docs/popularity-and-trending
Format IntroDB vérifié : https://api.introdb.app/openapi.json
