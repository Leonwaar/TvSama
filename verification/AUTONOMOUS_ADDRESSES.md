# Actualisation des adresses : sources et garanties

## Ordre des vérifications

1. OùStreamer reste prioritaire : `https://app.oustreamer.com/api/sites?per_page=100&page=…`.
2. Si son adresse manque, est signalée indisponible ou ne donne pas de catalogue compatible : AdressesDirect pour Wiflix, Coflix, FrenchStream et Papadustream.
3. En dernier secours : section française « Streaming » du wiki FMHY, limitée aux noms des adaptateurs connus (11 fournisseurs). Les liens proposés ne sont pas considérés en ligne avant vérification.
4. Les portails propres aux fournisseurs restent pris en charge, notamment Wiflix et Coflix. Frembed utilise déjà son endpoint officiel `/api/domain`.

Une nouvelle adresse doit répondre et présenter les données du catalogue attendu avant d'être mémorisée par `SourceAddresses`. Les références enregistrées continuent à être réécrites par ce mécanisme partagé. Les pages de validation humaine restent en attente ; elles ne remplacent pas un adaptateur fonctionnel. Aucun lien de publicité, Telegram ou réseau privé ne constitue un catalogue valide. Le contrôle réseau refuse également les résolutions DNS vers les réseaux privés et réservés avant connexion. L’intégration utilise [l’interface DNS d’OkHttp 4.12](https://raw.githubusercontent.com/square/okhttp/parent-4.12.0/okhttp/src/main/kotlin/okhttp3/Dns.kt), version du projet.

Une panne d'annuaire ne supprime pas les dernières adresses mémorisées. Les pages OùStreamer déjà reçues restent exploitables si une autre page de pagination échoue. Les propositions de secours sont mises en cache 10 minutes ; un échec du wiki n'entraîne pas une nouvelle requête par fournisseur pendant une minute. Une seule récupération du wiki peut être en cours à la fois. La recherche d'adresse est bornée à 45 secondes par source avec la concurrence limitée existante.

L'application actualise au lancement et propose le bouton existant. Elle revérifie aussi les adresses toutes les 30 minutes lorsqu'elle est visible, hors lecture, recherche de lecteurs et validation interactive. Ce contrôle périodique ne recharge pas l'accueil ni ses lignes. Le choix de désactiver l'actualisation automatique d'un fournisseur est respecté lors de ce contrôle périodique.

## États distincts

- Adresse remplacée : catalogue du nouveau domaine validé, remplacement mémorisé.
- Hors ligne selon l'annuaire : déclaration attribuée à sa source, conservant la dernière adresse ; ce n'est pas une mesure de lecture locale.
- Accès bloqué / captcha / refus 403 : accès protégé, pas une preuve que le site est arrêté.
- Page introuvable 404 : adresse ou page à vérifier, pas une panne générale démontrée.
- Annuaire indisponible / vérification trop lente : réseau ou contrôle incomplet ; aucune panne du site déduite.
- Vidéo trouvée : signature média accessible. La coche verte exige encore une lecture réelle de 90 secondes.

## Observation des services le 23 octobre 2026

Le JSON public OùStreamer a répondu avec `success`, `data`, `meta.last_page` et `meta.checked_at` : 525 entrées sur 6 pages. Ses fiches exposent notamment `current_url`, `status`, `last_checked_at`, `ping_ms`, `uptime_30d` et `trust_score`. Le script public `app-CVDs7jUS.js` utilise la même pagination de 100, normalise `danger` vers `blocked`, et consulte `/api/stats` et `/api/activity` toutes les 30 secondes. L'application reprend l'annuaire public et sa normalisation ; elle ne copie pas cette fréquence de sondage sur les petits appareils. Le ping du serveur OùStreamer ne remplace pas la latence mesurée depuis l'appareil.

Ces observations ne constituent pas une documentation officielle de stabilité de l'API, ni une preuve du fonctionnement du scanner interne. Aucun accès aux alertes utilisateur, aux notifications push ou à l'administration n'est nécessaire.

Sources consultées : [OùStreamer](https://app.oustreamer.com/), [script public](https://app.oustreamer.com/build/assets/app-CVDs7jUS.js), [AdressesDirect](https://adressesdirect.com/websites), [fiche Wiflix](https://adressesdirect.com/websites/wiflix), [wiki FMHY](https://fmhy.net/non-english). Les dates d'indexation d'un moteur de recherche ne prouvent pas la fraîcheur d'une adresse : le catalogue réel doit répondre.

## Comparaison du dépôt Streamflix fourni

Consultation de [streamflix-reborn2/streamflix](https://git.gay/streamflix-reborn2/streamflix), branche `main`, via son API publique après expiration du clonage. La liste reçue contient 76 fichiers de fournisseurs et 92 fichiers d'extracteurs ; aucun `CoflixProvider.kt` n'y figure. Wiflix conserve l'ancien défaut `flemmix.team` ; la récupération actuelle du portail est donc conservée dans TvSama.

Les changements utiles intégrés sont le formulaire `/dl` Uqload pour les pages non embed et le décodage Vidzy lié au domaine et au paramètre de largeur publié dans la page. Le contexte de fournisseur reste propre à chaque coroutine, et les validations communes de média et de durée s'appliquent. FrenchStream, FrenchAnime, FrenchManga et Kidraz sont identiques au socle Git local comparé ; les correctifs TvSama existants restent en place. Le mécanisme `/api/domain` Frembed était déjà présent dans TvSama. Vavoo reste supprimé.

Le réseau a reconnu les deux secours Wiflix dans [le contrôle des annuaires](DIRECTORY_BACKUPS_NETWORK.md). Cela ne vérifie pas la lecture. Les derniers essais Wiflix et Coflix n'ont pas confirmé de média complet : voir leurs rapports séparés. Aucun engagement de 5 à 10 lecteurs par programme ou de disponibilité 24 h/24 n'est déduit de la présence dans un annuaire.
