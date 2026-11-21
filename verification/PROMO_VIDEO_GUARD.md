# Extraits Uqload et faux lecteurs — v1.8.88-rc3

## Cause constatée

La recherche de sources acceptait un conteneur MP4 ou HLS dès que ses premiers
octets étaient lisibles. Cette vérification réseau ne mesure pas la durée ni le
contenu de la vidéo. Un extrait promotionnel Uqload de 30 secondes pouvait donc
figurer parmi les serveurs et recevoir une coche verte.

## Correctif

- L’adaptateur Uqload lit les configurations `sources`, `source` et `file`, dans
  les scripts ordinaires ou compactés. Il privilégie la configuration du lecteur
  plutôt qu’un fichier publicitaire présent ailleurs dans la page. La requête
  média conserve l’adresse exacte de la page intégrée comme `Referer`, le même
  agent utilisateur et les cookies partagés du client réseau.
- Les films et épisodes restent en chargement jusqu’à ce que Media3 annonce leur
  durée. Une durée inconnue ou inférieure à 90 secondes provoque le repli vers
  un autre serveur avant la première image. Les directs et flux personnels gardent
  leur comportement adapté à des durées inconnues ou courtes.
- Si un flux se termine malgré tout avant 90 secondes, il n’est pas enregistré comme
  programme terminé. Son URL est écartée des recherches suivantes jusqu’à la
  prochaine actualisation des sources ou au redémarrage ; sa durée sera alors
  vérifiée à nouveau avant toute lecture.
- Les playlists HLS VOD complètes indiquant moins de 90 secondes sont rejetées
  dès la sonde réseau. Les requêtes MP4 ne téléchargent que leurs premiers octets ;
  leur durée est jugée par Media3 lors de l’ouverture du lecteur.
- Uqload passe après les autres serveurs disponibles, quelle que soit la qualité
  annoncée. Les autres serveurs restent proposés et le repli est automatique.
- Une sonde réseau seule affiche un état neutre « durée non vérifiée ». La coche
  verte indique désormais une image décodée et plus de 90 secondes de lecture
  réelle pour un échantillon. Elle précise que les autres titres du même site ne
  sont pas vérifiés. Le Cast attend la vérification de durée du programme.

## Vérification et limite

Les tests locaux couvrent un extrait de 30 secondes, un programme de 90 secondes,
une durée inconnue, les directs, les flux personnels, une playlist HLS courte et
le refus de redonner une coche verte à l’URL rejetée. Quatre tests de l’adaptateur
Uqload couvrent les scripts ordinaires/compactés, le référent exact, les publicités
et une adresse non vidéo. Un test Android avec une vidéo synthétique de 12 secondes
vérifie son rejet en tant que film ; il est compilé mais n’a pas été exécuté ici.

Le GET de la fiche Coflix `.ac` avec les en-têtes du lecteur renvoie HTTP 200 au
22 octobre 2026. Le contrôle initial HEAD renvoyait 403. Les trois liens Uqload
directs publiés par cette fiche donnent chacun une playlist VOD complète de 30 s,
ce qui reproduit le problème signalé. Le [contrôle natif ciblé](COFLIX_VIDEO_NETWORK.md)
a trouvé un repli Uqload VFQ de 8 700 secondes (2 h 25), dont un segment est
accessible et une image a été décodée à 120 secondes avec FFmpeg sur la machine
de test. Ce contrôle ne constitue pas une validation visuelle du titre.

La recherche laisse davantage de temps aux serveurs de repli ; son délai global
reste borné. La sonde suit les variantes HLS avant de vérifier la durée, afin
d’écarter aussi les extraits cachés derrière une playlist maître.

Aucun appareil Android n’est connecté. Une durée longue et un segment accessible
ne prouvent pas à eux seuls que les images correspondent au titre ni que le
décodage sur le projecteur fonctionne.

Le seuil de 90 secondes est conservateur : un court métrage ou un épisode légitime
plus court sera aussi écarté. Une durée de catalogue par titre permettrait de traiter
ces cas. Un flux dont la durée reste inconnue nécessite un autre serveur ou le
lecteur du site. Ce correctif ne donne pas accès à un média réservé par l’hébergeur.

## Artefact de validation

APK universel `TvSama-v1.8.88-rc3-validation-universal.apk`, code 97, Android 6+ ;
ARMv7, ARM64, x86 et x86_64. Signature et alignement vérifiés.

Suite application : 109 tests réussis, 19 tests conditionnels ignorés.
Adaptateur Uqload : 4 tests réussis. Test réseau Coflix ciblé réussi séparément.
Les tests Android sont compilés, sans exécution sur appareil.

SHA-256 : `69523f9df2e3266f511a172cf234e0cf670be970666b92fb05d23298c9320672`.
