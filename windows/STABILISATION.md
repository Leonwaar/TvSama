# Stabilisation Windows : critères bloquants

Le signalement utilisateur invalide toute conclusion de bon fonctionnement global
de v1.4.78. Les anciennes fixtures prouvent seulement trois décodages courts.
Lot de correction : v1.6.78 / code Android 83. Ne jamais écraser l’EXE précédent.

## Ordre de travail

1. Reproduire les défauts avec le vrai renderer et le vrai processus Electron.
   Couvrir boutons, listes, navigation rapide et erreurs réseau avant correction.
2. Corriger les contrôles : souris, toucher, Entrée/Espace, flèches, Escape,
   focus et menus lisibles. Aucun bouton sans action, attente ou erreur explicite.
3. Corriger l’affichage : 640×480, 1280×720, 1920×1080, 4K et mise à l’échelle
   125/150/200 %. Pas de contrôle inaccessible, débordement horizontal ou vidéo étirée.
4. Corriger les parcours de bibliothèque : favoris sans nouvelle requête inutile,
   langues et saisons cohérentes, reprise correcte, sauvegarde à la fermeture.
5. Corriger le lecteur : film sans épisode, flux sans extension, qualité réellement
   sélectionnée, changements rapides de serveur, secours et erreur récupérable.
6. Automatiser la maintenance des adresses : annuaire avec TTL, adresse antérieure
   conservée en cas de panne, réparation bornée, erreurs par source et repli.
   Une réponse de catalogue ne suffit jamais à qualifier une source opérationnelle.
7. Auditer chaque source : catalogue, recherche, fiche, épisodes, résolution et
   décodage. Conserver succès et échecs ; aucun contenu de test substitué à un échec.
8. Tester redémarrage, perte/reprise réseau, arrêt propre, Android/Cast et Windows.
   Exiger tests sur l’artefact livré et périphériques réels avant clôture globale.

## Conditions de clôture

- Zéro erreur JavaScript non traitée dans les parcours couverts.
- Chaque régression a un scénario reproductible qui échoue avant correction.
- Toutes les actions sont vérifiées, avec succès et échec de la dépendance externe.
- Aucune réponse obsolète ne remplace la page, le film ou le serveur courant.
- Les menus restent lisibles et utilisables au clavier, au doigt et à la souris.
- Les changements d’adresses ne nécessitent pas une nouvelle compilation.
- Une panne de source ne bloque ni les autres sources ni la navigation.
- Lecture prolongée, ressources bornées et essais Windows consignés.
- Les limites non résolues interdisent les mentions « tout fonctionne » ou « 100 % ».

L’autonomie vise la récupération des pannes et changements d’adresses. Un service
tiers supprimé, une API réécrite ou une validation interactive peuvent toujours
nécessiter une intervention ; aucun logiciel local ne peut garantir leur stabilité.

## Journal

- Corrigé : menus, dimensions/zoom, catégorie active, recherche personnelle,
  pagination des favoris, fiches conservées, langue disponible, retour du lecteur,
  Échap vidéo, course entre serveurs et renouvellement borné des lecteurs expirés.
- Ajouté : annuaire automatique, migration des adresses, conservation hors ligne,
  reconnaissance des médias sans extension et données Flight de MyFluneo.
- Tests : 56 tests unitaires/intégration Linux ; 28 scénarios Electron sur ASAR
  et portable Windows sous Wine, sans erreur JavaScript observée.
- Ouvert : décodage distant par source, défauts d’extraction encore constatés,
  validation Windows réelle, endurance et périphériques physiques. La clôture
  globale reste interdite tant que ces critères n’ont pas leurs preuves.
- Résultats et nouvelles preuves : `verification/` et `VALIDATION.md`.

## Lot v1.6.78

- [x] Rétablir le réglage DNS de l’APK ; tester les trois modes et leur persistance.
- [x] Résoudre les serveurs concurrents avec un budget et des erreurs explicites.
- [ ] Compléter les extracteurs utilisés par les sources françaises.
- [x] Auditer 21 sources avec le réseau Electron : quatre échantillons décodés.
- [x] Tester HTTP 503/reprise et fermeture ; 30 min sur candidat Linux,
  10 min sur le portable final sous Wine. Endurance Windows natif ouverte.
- [x] Exécuter le portable Windows sous Wine, puis conserver l’export et son SHA-256.

Compléments livrés : transport HTTP avec URL finale et redirections manuelles,
annulation des téléchargements, délai sur corps vidéo bloqué, MailRu/Vidara,
alternatives Voiranime, redirections littérales et pagination FRAnime corrigée.
Les onglets sur petit écran restent tous visibles sur plusieurs lignes.
Les contrôles tactiles sont injectés via Chromium ; le matériel reste à valider.
L’audit conserve 17 échantillons sans lecture réussie (échecs ou non testés).
L’étape d’extraction reste ouverte ; aucun « 100 % » ne découle des tests locaux.

## Prochaines preuves exigées pour les sources

| Blocage mesuré | Sources concernées | Critère avant clôture |
|---|---|---|
| Catalogue : délai, HTTP 403 ou structure vide | FrenchStreaming, BlablaStream, Wiflix | Catalogue et recherche réels ; pagination vérifiée ; aucune page de validation interprétée comme résultat vide |
| Validation interactive annoncée | AnimeKO, Papadustream | Après validation légitime dans la fenêtre d’accès, nouvelle tentative sans cache négatif ; sinon erreur explicite et navigation disponible |
| Requête bloquée ou HTTP 403 | Anime-Sama, Voiranime, MyFluneo, Pluto TV FR, FRAnime, Coflix, FrenchAnime, FrenchManga | Identifier le blocage réseau ou la réponse de l’hébergeur ; décodage après accès autorisé ; conserver l’erreur si refus persistant |
| Média résolu mais HTTP 404 | Movix | Tester les lecteurs alternatifs bornés, conserver langue/épisode ; vérifier des images et un temps qui avance |
| Lecteur non reconnu | WarFlix, Frembed | Fixture provenant de la structure réelle, extracteur testé sans exécuter le JavaScript distant, puis décodage public réel |
| Premier titre sans épisode | Animes-Sama | Distinguer titre à venir et défaut d’analyse ; vérifier une série publiée et ses saisons/langues |
| Lecture réussie sur un seul échantillon | IPTV-Org, Anime-Ultime, FrenchStream, Vavoo | Autres titres/chaînes, recherche, changement de lecteur, panne/reprise et lecture prolongée ; un seul succès ne clôt pas la source |

Ne pas désactiver la sécurité du renderer pour faire disparaître une erreur.
Ne pas confondre retour d’URL, premier décodage, lecture prolongée et stabilité
de tous les titres. Chaque niveau conserve sa preuve et ses limites propres.
