# Suivi Windows

Référence : [PORTAGE_COMPLET.md](PORTAGE_COMPLET.md). Elle remplace l’ancienne piste
Compose Desktop / jpackage : le portage utilise Electron et electron-builder.
Aucun critère de parité ne se ferme sans preuve.

- [x] Augmenter et aligner les versions : Android v1.6.78 / code 83, Windows 1.6.78.
- [x] Reproduire et corriger dix régressions du renderer, avec tests dédiés.
- [x] Remplacer les menus natifs par des menus sombres utilisables souris/toucher/clavier.
- [x] Vérifier le dimensionnement 360/640/1280/1920/3840 pixels et le zoom 125/150/200 % dans Electron Linux.
- [x] Automatiser annuaire, migration d’adresses, renouvellement borné et annulation.
- [x] Implémenter stockage, réseau, catalogues et services décrits dans README.
- [x] Faire passer les tests des services et la lecture des fixtures dans Electron Linux.
- [x] Faire passer 28 scénarios sur le portable Windows sous Wine, avec empreinte du fichier testé.
- [x] Tester en réseau réel les en-têtes vidéo, redirections et reprise après HTTP 503.
- [x] Vérifier tous les onglets visibles sur petit écran, les menus tactiles et le DNS.
- [x] Tester PiP et profil au chemin accentué sous Wine ; 29 scénarios réussis.
- [x] Lecture prolongée locale : candidat Linux 30 min, EXE final sous Wine 10 min.
- [x] Auditer individuellement les 21 sources et conserver leurs erreurs.
- [x] Préparer une CI Windows qui construit puis teste l’EXE sans publier.
- [ ] Corriger les extractions en échec ; valider recherche/pagination de chaque source.
- [ ] Vérifier Cast réel, reconnexion, commandes et transfert de position avec Android.
- [ ] Valider PiP, pistes réelles, timer et pause longue de bout en bout.
- [ ] Valider migration, chemins accentués, fermeture, réseau et veille sur Windows.
- [ ] Exécuter les essais matériels souris, tactile et télécommande HID.
- [ ] Mesurer démarrage, mémoire et endurance 30 min sur Windows.
- [ ] Signer avec le certificat de l’éditeur s’il est fourni.
- [ ] Fermer tous les critères avant de qualifier le portage de complet.

L’augmentation de version est permanente et inconditionnelle : chaque ajout ou
lot de correction livré
augmente immédiatement le nombre central et le versionCode. Aucun numéro publié
n’est réutilisé. Le build refuse un désalignement.
