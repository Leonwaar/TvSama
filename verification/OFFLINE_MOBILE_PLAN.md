# Téléchargement mobile — préparation de la prochaine mise à jour

Demandé le 23 octobre 2026. Priorité actuelle : fiabiliser et diversifier les sources. Ce document prépare la fonction ; aucun téléchargement hors ligne n'est encore implémenté.

## Comportement attendu

- Depuis une fiche de film ou d'épisode, choisir un flux réellement résolu et une qualité, puis télécharger.
- Afficher progression, taille, état et commandes pause, reprise, annulation et suppression dans « Téléchargements ».
- Lire les éléments terminés sans réseau sur téléphone, avec la même progression de lecture que le lecteur actuel.
- Prévenir un manque d'espace avant de démarrer ; conserver les éléments terminés après fermeture et redémarrage de l'application.
- Proposer le Wi-Fi uniquement par défaut et rendre visibles les éléments expirés ou incomplets.

## Intégration prévue

Réutiliser Media3 et son gestionnaire de téléchargements avec un service de premier plan Android et un cache disque dédié. Résoudre le média avant mise en file ; conserver les en-têtes nécessaires sans les exposer dans les journaux. Une URL temporaire expirée doit être résolue à nouveau depuis la référence du programme, puis la reprise validée. Si le média a changé, recommencer avec confirmation plutôt que mélanger deux versions.

Les flux en direct, les pages HTML/captcha et les extraits promotionnels ne sont pas téléchargeables. Un flux protégé nécessitant une licence hors ligne doit être indiqué comme indisponible tant que le fournisseur ne propose pas cette licence. Les sous-titres sélectionnés doivent être conservés avec le média.

## Validation avant livraison

Tester sur téléphone : mode avion, fermeture forcée, redémarrage, perte de réseau, manque de place, annulation, suppression, URL expirée, téléchargement HLS et fichier progressif, sous-titres et absence d'extrait de 30 secondes. Vérifier les exigences Android du service de téléchargement et la notification sur les versions prises en charge avant implémentation.
