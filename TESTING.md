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

## Critères de finition des sources et du lecteur

Le produit est prêt lorsque chaque parcours ci-dessous est vérifié et que les
échecs restent compréhensibles et récupérables :

- Chaque source intégrée activée expose son état réel ; recherche, fiche,
  épisodes et serveurs ne doivent ni se bloquer ni afficher de faux résultats.
- Chaque source de lecture proposée pointe vers un média détecté et joignable,
  ou vers un lecteur navigateur explicitement signalé. Un lien de page web ne
  doit pas être présenté comme un flux direct.
- Les cas d’URL mal formée, de délai dépassé, de source hors ligne et de réponse
  HTML sont filtrés ou expliqués ; choisir un autre serveur reste possible.
- Sur appareil, décoder au moins un flux de chaque format pris en charge
  (HLS, DASH et MP4), puis vérifier démarrage, pause, recherche, reprise et fin
  de lecture. Les sources live sont vérifiées séparément.
- Confirmer l’épisode, la langue, les pistes audio/sous-titres, la qualité
  détectée, la reprise, l’autoplay, la rotation serveur et les contrôles TV.
- Les tests locaux passent ; chaque audit réseau exécuté indique clairement
  les sources accessibles, indisponibles ou protégées. Un audit réussi ne vaut
  pas preuve de décodage.

La disponibilité d’un service tiers ne peut pas être garantie par TvSama. Un
captcha, une validation humaine, une connexion requise ou une panne externe
doivent être signalés et ne sont pas contournés. Ces sources ne comptent comme
opérationnelles que si leur parcours autorisé fonctionne après l’action
utilisateur prévue.

## Commandes de vérification

```bash
rtk ./gradlew :app:assembleDebug :app:testDebugUnitTest --offline
rtk ./gradlew :app:testDebugUnitTest --tests fr.nekotv.SourceNetworkAuditTest.allFrenchSources -PnetworkAudit=true
```

Les audits réseau sont ignorés par défaut. Utiliser `-PnetworkAudit=true` : `-Dtvsama.networkAudit=true` n'est pas transmis au processus de test. Vérifier `skipped="0"` dans les résultats XML avant de citer un audit comme réussi. Les rapports HTTP ne prouvent pas le décodage Android.

## Limites de validation dans cet environnement

Un émulateur Android 35 est disponible dans `../android-test-sdk`. Il permet de
vérifier la première image et les décodeurs Media3 ; il ne remplace pas les
essais Android TV, la télécommande physique, le son écouté ni Google Cast.
Un build réussi ne constitue pas une validation de lecture.

Les services tiers peuvent modifier leurs domaines, catalogues et formats.
Une validation doit distinguer erreur de l’application et indisponibilité de
la source. Aucun accès à un fournisseur ne garantit à lui seul la langue ou
la disponibilité d’un programme.
