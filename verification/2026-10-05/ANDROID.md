# Vérification Android — 5 octobre 2026

APK debug v1.2.76, code 77 ; émulateur téléphone Android 35, 1080 × 1920. Ces essais portent sur une construction intermédiaire : les changements ajoutés après cet essai exigent une nouvelle vérification.

- Doing Life, fiche initiale FrenchStreaming, lecteur Coflix / Voe étiqueté VF : image affichée et progression pendant plus de trois minutes. Logcat : initialisation ExoPlayer à 22:55:41, décodeur H.264 à 22:55:42 et décodeur AAC à 22:55:43. L’émulateur a été lancé sans périphérique audio ; le décodage audio est observé, l’écoute sonore et la langue réelle restent à vérifier.
- Défaut découvert : le premier `h1` de FrenchStreaming était le nom du site, au lieu de Doing Life. Sélecteur corrigé et test ajouté ; nouvelle installation à vérifier.
- Pause tactile à 04:09. [Image avant assombrissement](paused-before.png), [image après pause prolongée](paused-after.png), [réveil au toucher](paused-wake.png). L’image revient sur la même scène, sans reprise. L’instant exact 30 s est couvert par le test unitaire ; cet essai visuel confirme le comportement après une pause supérieure à 30 s, sans mesure du seuil ni du délai de restauration.
- Touche multimédia pause initialement sans effet : absence de session multimédia Android détectée. Session ajoutée puis APK installé : `dumpsys media_session` confirme une session TvSama active, intitulée « Doing Life · Film », avec état PLAYING ; touche 127 puis état PAUSED, position 40 226 ms. Commande pause système vérifiée. La version avec conservation du focus a été compilée ensuite ; nouveau contrôle TV nécessaire.

Ne valide pas l’ensemble des sources, les directs, la navigation TV, le Cast, les performances statistiques ou l’endurance de deux heures.
