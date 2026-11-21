# TvSama v1.8.88-rc7 — version universelle de test

25 octobre 2026. Version Android 101.

## Changements

- Identité TVSAMA et nouveau pictogramme vectoriel sur l’accueil, la télécommande, l’icône et la bannière. Recherche avec loupe sur les grands écrans. TFX73 retiré des sources par défaut.
- Télécommande sur une page dédiée, accessible après association QR. OK, navigation, retour, accueil, volume, lecture, épisodes et déplacement de 15 secondes ; un appui maintenu accélère le déplacement. Sur TV, OK met immédiatement en pause ou reprend, gauche/droite avancent ou reculent, haut/bas affichent les commandes.
- Le panneau de pause d’une série affiche le titre et, en plus petit, la saison et l’épisode en cours.
- Préchargement de l’épisode suivant seulement avec une connexion non limitée et assez de vidéo en mémoire. Bouton « Épisode suivant » dans les 150 dernières secondes ou avant un outro publié ; les segments d’intro et d’outro sont bornés par la durée réelle.
- Amplification audio optionnelle plafonnée à +3 dB ; repli de décodeur Media3 activé. Les données de télécommande anciennes ou malformées sont vérifiées avant d’atteindre l’interface.
- Direct français TVradioZap : 53 chaînes françaises publiées lors du contrôle réseau, avec les adresses de secours propres à la chaîne. Streamed et Volkamax complètent le catalogue. Les adresses d’annuaire sont contrôlées avant enregistrement ; les chaînes françaises apparaissent sans attendre le chargement des autres listes. Les lecteurs annoncés en français sont essayés avant les autres.
- AnimeSite : catalogue et épisodes natifs, recherche, lecteurs publiés par épisode et échange de jeton via l’API publique du site. Une coche verte pour une source indique un échantillon vidéo accessible ; la lecture confirmée reste distinguée après contrôle plus long.

## Vérification réseau

- [Direct français](FRENCH_LIVE_NETWORK.md) : 53 chaînes ; les deux adresses Arte et les deux adresses BFM TV ont fourni média et segment. Deux miroirs Streamed trouvés dans FMHY, mais les liens de l’annuaire seuls ne prouvent aucune lecture.
- [AnimeSite](ANIMESITE_NETWORK.md) : Naruto, 220 épisodes répertoriés, un des cinq lecteurs testés a fourni média et segment. Solo Leveling, 12 épisodes répertoriés, aucun des sept lecteurs de l’épisode testé ne fonctionne actuellement. Les autres catalogues d’animés restent nécessaires comme solutions de repli.
- Un lecteur Streamed a décodé une image 1080p dans Chrome Linux. Cela ne valide pas le décodage Media3 sur Android ni tous les événements Streamed.

## Limites à vérifier sur appareil

Waydroid reste sans écran Android ni ADB exploitable. La stabilité du lien QR sur les deux vrais appareils, le décodeur Android des directs, le niveau sonore, les gestes de télécommande, le logo sur l’écran d’accueil et la découpe caméra du Xiaomi 12T doivent être confirmés par l’utilisateur. Le défaut FrenchManga signalé a été durci côté décodage/transport, mais sa cause exacte n’a pas été reproduite avec des journaux d’appareil. Les captchas demandent toujours une intervention humaine lorsqu’un site l’exige. La disponibilité permanente de toutes les sources et dix lecteurs par titre ne peut pas être attestée.

## Build

- APK signé : `releases/TvSama-v1.8.88-rc7-universal.apk` (répertoire parent du dépôt), **54 988 578 octets**.
- Package `fr.nekotv`, version `v1.8.88-rc7`, code **101**, Android minimum **23** ; architectures `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`.
- SHA-256 : `29f1e7cfa1d8258ab283c87ec9772a691679207c32459b06bebc9601bf1b253a`.
- Certificat SHA-256 : `5b522fba46fef8a6a34e7e09d6ceee1ff9f732dcefdd7e0a48db83d80e27d50f` (même certificat que rc6).
- Gradle : tests unitaires des deux modules, lint debug/release, assemblage release et APK de tests instrumentés réussis. **235 tests recensés, dont 211 exécutés et 24 ignorés**, aucun échec. Une seconde passe des tests app et de l’assemblage a réussi après le changement du panneau de pause.
- Lint : **0 erreur**, **82 avertissements** en debug et en release. Signature et alignement vérifiés par le script de packaging ; `git diff --check` réussi dans les deux dépôts.
- APK produit localement ; aucune publication distante.
