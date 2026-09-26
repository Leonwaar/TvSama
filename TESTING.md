# Vérification TvSama

L’application TvSama (`fr.nekotv`) utilise les fournisseurs et extracteurs du
module Streamflix Reborn. Ses écrans sont indépendants : la présence du code
Streamflix dans l’APK ne signifie pas que tous ses écrans et fonctions sont
accessibles dans TvSama.

## Vérifications sur appareil avant diffusion

- Installer l’APK sur Android TV et sur téléphone ; vérifier l’entrée TvSama
  dans le lanceur, le démarrage à froid et le retour après mise en veille.
- Parcourir les menus, les affiches et les dialogues uniquement avec la
  télécommande : chaque cible doit afficher son focus et le bouton Retour doit
  fermer d’abord le dialogue ou l’écran ouvert.
- Ouvrir un film puis une série ; sélectionner saison, épisode et serveur.
  Vérifier que le titre effectivement lu correspond à la sélection.
- Tester VF et VOSTFR séparément. Vérifier les pistes audio et sous-titres
  disponibles dans le flux, y compris les sources sans indication de langue.
- Tester un fournisseur disponible et un fournisseur indisponible : une erreur
  doit rester explicite, sans faux catalogue de démonstration ni blocage.
- Contrôler reprise de lecture, favoris et historique après redémarrage.
- Exporter une sauvegarde, changer un favori, importer la sauvegarde puis
  relancer l’application. Vérifier également le refus d’un fichier étranger ou
  mal formé. Les identifiants cloud ne font pas partie de la sauvegarde locale.
- Sur le même réseau local, connecter un récepteur Google Cast, lancer un
  programme depuis le téléphone puis tester pause, reprise et déconnexion.
  Vérifier le comportement d’un flux nécessitant cookies ou en-têtes HTTP.
- Vérifier affiches, taille des textes et contraste sur un écran TV à distance
  normale et sur un téléphone en portrait.

## Limites de validation dans cet environnement

ADB est installé. Aucun appareil n’était connecté lors de l’audit initial et
aucun émulateur ni image système n’était installé dans le SDK. Un build réussi
ne constitue donc pas une validation de lecture réseau, de télécommande ou de
Cast sur matériel réel.

Les services tiers peuvent modifier leurs domaines, catalogues et formats.
Une validation doit distinguer erreur de l’application et indisponibilité de
la source. Aucun accès à un fournisseur ne garantit à lui seul la langue ou
la disponibilité d’un programme.
