# TvSama v1.8.88-rc8 — validation

26 octobre 2026. Version Android 102.

## Changements

- Logo noir et blanc : jeune sorcière au nœud, chat à l’avant du balai, vue de profil. Nouvelle image sur l’en-tête, la télécommande, l’icône et la bannière TV ; mot-symbole TVSAMA conservé.
- Recherche : loupe et texte « Rechercher » dans un seul bouton ; logo compact sur les écrans étroits.
- Lecteur : titre du film ou de la série en haut à gauche lorsque les commandes sont visibles ; saison et épisode en petit pour les séries.
- TV et Direct ont chacun leur page, leur chargement et leur retour depuis la lecture. TV utilise les chaînes françaises TVradioZap ; Direct utilise les événements Volkamax et Streamed. Le raccourci TV de l’accueil ouvre la page TV.
- Normalisation du son déplacée vers Réglages ; amplification plafonnée à +3 dB, sans bouton dans le lecteur.
- Vérification des sources d’animé : Anime-Sama, Vostfree et AnimeSite essaient Naruto, dont un lecteur natif a été vérifié, avant les nouveautés de l’accueil. Une page d’accueil indisponible ne masque plus un échantillon de recherche lisible. Si aucun échantillon n’est accessible, l’état reste en échec.
- Intro/outro : second catalogue public [TheIntroDB](https://github.com/TheIntroDB/theintrodb-npm/blob/main/docs/getting-started.md) en secours d’[IntroDB](https://introdb.app/docs/api). Les plages multiples sont conservées séparément : intro, récapitulatif, crédits et aperçu. Une scène après le générique retarde le passage automatique. Les intros publiées jusqu’à quinze minutes sont acceptées si elles restent dans la première moitié de l’épisode. Les réponses sont mises en cache une heure seulement si elles contiennent des marqueurs ou confirment leur absence.

## Preuves

- [Contrôle anime](ANIME_COVERAGE_NETWORK.md) : les six couples Naruto, Classroom of the Elite et Mushoku Tensei × Anime-Sama/Vostfree ont au moins un média et un segment accessibles. Certains lecteurs isolés restent en erreur.
- [Contrôle des marqueurs](INTRO_OUTRO_NETWORK.md) : Grey’s Anatomy S1E1–E2, Cyberpunk: Edgerunners S1E1–E2 et Hunter x Hunter S1E1 interrogés. Grey’s Anatomy S1E2 commence son intro à 441,389 s, ce qui explique le rejet par l’ancienne limite de cinq minutes. Grey’s Anatomy S1E1 et Cyberpunk S1E1 n’ont pas de marqueur d’intro publié ; leurs outros existent. Des tests unitaires couvrent les bornes, les plages multiples et les scènes après générique.

## Limites

Ces marqueurs sont liés à une numérotation et une durée d’épisode ; une édition différente peut les rendre inexacts et sera rejetée si la fin publiée dépasse la durée réelle de plus de quinze secondes. Aucun marqueur absent n’est inventé. Les médias et segments réseau accessibles ne prouvent pas le décodage ni l’ergonomie sur vos appareils ; Waydroid reste inexploitable dans cet environnement.

## APK

- `/home/leon/TvSama_fixed_v2/releases/TvSama-v1.8.88-rc8-universal.apk`, **55 455 353 octets**.
- Package `fr.nekotv`, version `v1.8.88-rc8`, code **102**, Android minimum **23** ; `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`.
- SHA-256 : `2759788a1e70a06ce0bee3433a47191578ecb012075856d1314198c92d52a268`.
- Certificat SHA-256 : `5b522fba46fef8a6a34e7e09d6ceee1ff9f732dcefdd7e0a48db83d80e27d50f`, inchangé depuis rc7.
- Build propre après défaut de cache de fusion des ressources : tests unitaires app/streamflix, lint debug/release, release et APK de tests instrumentés réussis. **240 tests recensés, 215 exécutés, 25 ignorés, aucun échec**. Lint : **0 erreur**, **83 avertissements** dans chaque variante. Signature, alignement et `git diff --check` vérifiés.
- APK produit localement, sans publication distante.
