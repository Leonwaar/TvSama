# Vidrift — intégration actuelle

Documentation du lecteur officiel : https://vidrift.net/. La page publique décrit une iframe et un contrat `postMessage`; elle ne fournit pas d’URL vidéo native à l’application.

## Langues et catalogue

- L’interface propose uniquement VF et VOSTFR. La valeur VO reste un marqueur interne pour rejeter les pistes qui ne correspondent à aucun des deux choix.
- Films et séries utilisent un identifiant TMDB déjà lié au titre.
- Les anime reconnus par leur fournisseur ou leur genre sont recherchés dynamiquement dans AniList. Le résultat doit correspondre exactement au titre ou à un alias; une année connue départage les adaptations. Aucun identifiant d’anime n’est codé en dur.
- Les numéros d’épisode AniList sont absolus et comptés depuis les épisodes disponibles dans le catalogue. Un résultat qui dépasse le nombre d’épisodes déclaré est écarté.
- VF exige une piste audio française déclarée. VOSTFR exige une piste étrangère reconnue et un sous-titre français déclaré; si la langue d’origine est connue, elle doit correspondre.
- Une page de serveur Vidrift reste non vérifiée avant la lecture. La coche verte n’est attribuée qu’après 90 secondes de progression réelle.
- Une source n’est affichée que si sa version demandée est explicitement confirmée. Une page vidzy non étiquetée, un simple « VO » sans sous-titre français, ou un doublage étranger reste masqué : aucune source inconnue n’est réétiquetée VF ou VOSTFR.
- Seule la version choisie (VF ou VOSTFR) génère une source Vidrift, jamais les deux. VOSTFR n’est pas proposé si la langue d’origine est inconnue ou déjà française.

## Lecteur intégré

- Vidrift reste une iframe inter-origine et ne publie pas d’URL vidéo directement. TvSama charge donc cet embed dans un WebView masqué, déclenche sa lecture via le contrat `postMessage` documenté (`vidrift:play`, `vidrift:set`), puis intercepte le manifeste HLS réellement demandé.
- Le flux capturé est validé (manifeste + segment) puis lu par ExoPlayer, avec les commandes natives TvSama : reprise, pause, déplacement, plein écran, épisode précédent/suivant et minuteur. Aucune page web n’est affichée.
- La résolution API officielle (`/api/boot`, `/api/source`) est tentée en parallèle de la capture masquée. Le premier flux valide gagne; l’API est vérifiée par sondage avant d’être retenue.
- Les sources « lecteur intégré » génériques suivent le même chemin : ouverture masquée de la page publiée, capture du média, lecture native. Si aucun flux n’est révélé, la source est signalée indisponible plutôt que d’ouvrir une page.
- Le cache AniList garde une correspondance positive cinq jours et un échec une heure. La recherche commence en parallèle de la résolution des autres serveurs; l’attente finale est bornée à cinq secondes.
- La capture masquée applique le contrat de langue de Vidrift avant d’approuver la lecture : elle met en pause et parcourt les serveurs, pistes audio et sous-titres jusqu’à confirmer exactement la piste demandée. VF exige l’audio français; VOSTFR exige l’audio original déclaré et le sous-titre français actif. L’état est publié dans `window.__tvsamaVidriftStatus` (`pending`, `ready`, `failed`); la capture n’est validée qu’après `ready`, et `failed` arrête immédiatement au lieu d’attendre le délai complet. Un serveur bloqué est abandonné après six secondes, et l’essai complet est borné à vingt-et-une secondes.
- La VOSTFR capturée doit exposer le sous-titre français soit comme piste du manifeste HLS, soit comme fichier `.vtt`/`.srt` français capturé pendant la lecture. Sinon ExoPlayer lirait la VO sans sous-titres et la source est refusée.
- Les manifestes HLS qui déclarent leurs pistes audio sont vérifiés avant d’afficher la source : une source VF dont l’audio déclaré n’est pas français, ou une source VOSTFR dont l’audio déclaré est uniquement français, est écartée. Un manifeste qui ne déclare aucune langue (audio unique, sous-titres incrustés) reste accepté, faute de preuve contraire.
- Pendant la lecture, ExoPlayer force la piste audio confirmée : français pour VF, langue d’origine (ou toute piste non française) pour VOSTFR. Si les pistes chargées contredisent la version choisie, la lecture est arrêtée, la source retirée de la liste et un autre lecteur est proposé. Plus de VF qui démarre en VO ni de VOSTFR qui démarre en VF.
- Les pages « lecteur intégré » génériques ne sont tentées que si l’étiquette du serveur donne une langue explicite; la langue de l’épisode ne sert plus de preuve.
- Les résolutions réussies sont mises en cache trois minutes (échec : trente secondes). Une erreur de lecture invalide l’entrée pour que « Actualiser » retente réellement.
- Le direct et les chaînes TV disposent chacun de leur propre champ de recherche local, indépendant de la recherche du catalogue; il ignore casse et accents.
- Les précédents délais ont été réduits : API Vidrift dix secondes, capture web vingt-quatre secondes au plus, embed générique vingt-et-une secondes.

## Vérifications

- Réponse réelle de l’API publique AniList vérifiée pour « Classroom of the Elite » : l’API renvoie l’entrée de 2017 (`id=98659`, 12 épisodes, origine JP) ainsi que les saisons ultérieures distinctes. Le sélecteur retient l’entrée dont le titre exact et l’année correspondent.
- Tests unitaires Vidrift et de capture média mis à jour; la suite Gradle `:app:testDebugUnitTest` passe. `SourceLanguageGuardTest` couvre les manifestes multilingues : une VF dont l’audio déclaré est catalan ou anglais est refusée, une VOSTFR dont l’audio déclaré est uniquement français aussi, et une VOSTFR sans sous-titre déclaré reste acceptée (sous-titres incrustés possibles).
- La compilation `:app:compileDebugKotlin` et l’assemblage `:app:assembleDebug` passent. Aucun appareil Android/TV n’a encore validé la capture Vidrift en conditions réelles ni le focus de la télécommande.

Sources : [documentation Vidrift](https://vidrift.net/), [documentation de l’API AniList](https://anilist.gitbook.io/anilist-apiv2-docs/docs/guide/migration/version-1).
