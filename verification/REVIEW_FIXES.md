# Correctifs de revue — v1.8.88-rc4, code 98

## Corrections

1. **Sessions réseau :** les redirections ne transmettent plus le cookie explicite
   ni l’autorisation de l’origine. Le cookie propre au domaine destinataire reste
   utilisable. L’origine est enregistrée après la réécriture des anciennes adresses.
2. **Langues :** un lecteur sans indication fiable reste un repli « Langue à
   vérifier ». Il n’est pas étiqueté VF par supposition et passe après les lecteurs
   dont la langue est connue. Une langue explicitement différente reste filtrée.
   Les chaînes du fournisseur IPTV français conservent leur langue française.
3. **IPTV :** le caractère direct est transmis au lecteur et conservé dans la
   bibliothèque. Une fenêtre HLS courte ou de durée inconnue ne subit plus le
   contrôle réservé aux films et épisodes. Les anciennes cartes Pluto TV FR sont
   reconnues ; les chaînes ne sont pas mélangées aux séries trouvées ailleurs.
4. **Navigateur intégré :** la perte du moteur web libère la vue détruite, affiche
   une erreur récupérable et permet de créer une nouvelle vue avec Réessayer.
5. **Mises à jour :** les publications sans APK compatible et les métadonnées
   invalides sont ignorées individuellement, sans masquer les publications valides.
6. **Rotation d’adresse :** une nouvelle adresse protégée est mémorisée séparément
   et proposée par Accès. L’ancienne adresse fonctionnelle reste utilisable. Après
   validation humaine, le catalogue compatible est contrôlé avant application
   persistante de l’adresse et invalidation des caches. L’actualisation automatique
   et le bouton d’annuaire continuent de traiter les rotations sans captcha.
7. **Replis :** les extracteurs reçoivent leur fournisseur dans le contexte de leur
   coroutine. Ils ne changent plus le fournisseur global ni sa base locale à chaque
   miroir. Quatre extractions au maximum peuvent progresser ensemble ; les budgets
   fournisseur et global restent bornés et les résultats partiels restent immédiats.
   Les créneaux réseau ne sont plus conservés pendant l’attente des requêtes de
   saisons : un test avec neuf fournisseurs à cache froid reproduisait auparavant
   ce blocage et vérifie maintenant le retour de leurs neuf lecteurs.
   Quand le premier catalogue ne donne aucun flux, les catalogues découverts
   pendant le chargement de la fiche sont essayés dans le même budget global.
8. **Exports :** les clés d’appairage et l’identité de la TV restent sur l’appareil.
   L’import d’une ancienne sauvegarde contenant ces champs ne remplace pas les
   identifiants locaux.
9. **Réinitialisation :** l’ancien serveur et sa connexion sont fermés, les
   commandes en attente sont vidées et un nouveau secret remplace l’ancien QR.
   La cible associée est retirée de l’état de l’interface.
10. **Sous-titres :** les préférences sont observées pendant la session et appliquées
    au lecteur local ainsi qu’au choix des pistes Cast. La réinitialisation écrit
    explicitement les valeurs par défaut pour notifier aussi les observateurs des
    versions Android antérieures à Android 11.
11. **Démarrage :** Accueil, Reprendre et Ma liste sont effectivement utilisés au
    lancement ; les rappels de direct conservent leur priorité.
12. **Historique :** les entrées de progression restent légères et leur lecture est
    mise en cache tant que les données n’ont pas changé. Le catalogue d’épisodes
    n’est chargé que pour les cartes de reprise. Les sauvegardes périodiques sont
    sérialisées sur un worker ; une écriture en attente ne rétablit pas une carte
    masquée ou un historique effacé après sa demande.

Le format de bibliothèque reste compatible avec les données existantes. Le test
de charge structurelle utilise une série de 600 épisodes : l’historique ne charge
aucun de ces épisodes par entrée ; la carte Reprendre conserve le catalogue complet.
Ce contrôle ne mesure pas le temps d’image sur le projecteur.

## Validation

Contrôles finaux réussis :

- Application : 124 tests réussis, 19 tests conditionnels ignorés.
- Adaptateurs StreamFlix : 49 tests réussis, soit 173 tests exécutés réussis au total.
- Lint Debug : 0 erreur, 78 avertissements ; Release : 0 erreur, 77 avertissements.
- Tests instrumentés compilés ; compilation Release réussie.
- Signature existante et alignement de l’APK vérifiés ; diff sans erreurs d’espacement.

Le contrôle réseau Coflix a été exécuté séparément et a échoué ; voir sa limite
ci-dessous et le [rapport réseau](COFLIX_VIDEO_NETWORK.md). Il n’est pas compté
parmi les 173 tests locaux réussis.

Les erreurs de lint sont corrigées sans baseline ni désactivation d’analyse :
indentation, configuration réseau de test explicite, type de collection compatible
et interception des touches par le callback public de la fenêtre.

## Limites

Aucun appareil Android n’est connecté. Les tests instrumentés sont compilés, mais
le décodage matériel, Cast et la fluidité sur le projecteur ne sont pas certifiés.
Papadustream et FRAnime peuvent toujours demander une validation humaine. Les
changements de structure des sites et les hébergeurs hors service ne sont pas
réparables par une simple rotation de domaine.

Le seuil anti-extrait de 90 secondes reste conservateur pour les programmes VOD
réellement plus courts ; un flux long ne prouve pas l’identité de son contenu.

Le contrôle externe Coflix du 23 octobre 2026 échoue pour Spider-Man : Brand New Day :
le serveur VFQ de 8 700 secondes trouvé antérieurement n’est plus publié. Une
requête indépendante vers le lecteur REST public confirme neuf entrées, avec un
seul lecteur déclaré actif (Uqload VF) dont la playlist dure 30 secondes. Aucun
flux conforme n’a été confirmé sur cette fiche Coflix lors de ce nouveau contrôle.
L’application écarte cet extrait et essaie les autres références disponibles ;
ce bilan ne certifie pas la disponibilité du titre sur un autre site.

## APK universel de validation

`TvSama-v1.8.88-rc4-universal.apk`, versionCode 98, build Release non débogable,
Android 6 minimum ; ARMv7, ARM64, x86 et x86_64. Taille : 54 864 742 octets.

SHA-256 : `671ec15e54055da6f8ae87c35e87ea409c578a8e3392cc012522b4c9ad22f634`.

Certificat SHA-256 inchangé :
`5b522fba46fef8a6a34e7e09d6ceee1ff9f732dcefdd7e0a48db83d80e27d50f`.
