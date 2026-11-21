# TvSama v1.8.88-rc6 — version universelle de test

25 octobre 2026. Version Android 100. Le logo et le téléchargement hors ligne restent au [prochain lot](NEXT_RELEASE_BACKLOG.md).

## Corrections

- **Anime-Sama / VF** : découverte des liens VF/VOSTFR publiés sur chaque page de saison ; reprise de l’URL versionnée `episodes.js` avec le référent de la page. Cache des listes de lecteurs limité à 24 saisons, 2 minutes, invalidé avec le catalogue. Le choix d’un serveur réutilise cette liste au lieu de retélécharger le fichier pour chaque lecteur.
- **Mushoku Tensei** : interprétation de `creerListe`, `newSP` et `finirListe` après le dernier `resetListe`. La saison 1 commence à 1, pas à 24. Les emplacements spéciaux conservent leur position dans la liste des médias, mais ne sont pas attribués à un épisode régulier. Les spéciaux sans correspondance de catalogue ne sont pas fusionnés automatiquement.
- **Naruto** : les versions Kai et sans fillers ne sont pas assimilées à la numérotation du montage original.
- **Vostfree** : nouveau catalogue natif, recherche, pagination, films/séries, saisons, langues, lecteurs propres à chaque épisode. Intégration à la validation des adresses et aux annuaires OùStreamer/FMHy existants ; aucune coche verte sur la seule présence d’une fiche.
- **Ansembed** : domaine reconnu par l’extracteur Vidmoly, flux HLS du lecteur publié.
- **VOE** : requête sur l’adresse publiée, redirections JavaScript explicites conservant la route de la vidéo, maximum trois pages ; plus de choix arbitraire de la première URL de la page. Les nouveaux domaines avec route `/e/<identifiant>` sont traités seulement si les données du lecteur donnent le même `file_code`. Réutilisation du client réseau commun, de ses délais et de ses cookies ; sous-titres conservés.
- **Uqload** : les anciens liens `.io` reprennent le même identifiant sur `.vc`. Les garde-fous contre les extraits courts restent actifs. Cette migration d’un domaine retiré ne garantit pas les changements de domaine futurs de l’hébergeur.
- **Recherche** : alias Mushoku/Moshoku Tensei et titre japonais romanisé.
- **Captchas** : le navigateur utilise l’identification du WebView installé au lieu d’un Chrome Linux figé ; cohérence avec les requêtes des adaptateurs TvSama. Les cadres `about:blank`/`about:srcdoc` sont autorisés, les schémas locaux et `javascript:` restent interdits. FRAnime ouvre le point d’accès API qui a réellement demandé la validation. Le bouton de navigateur externe précise que sa session ne se transfère pas au WebView.
- **Téléphone OLED** : fond de lecture et fenêtre noirs, barres système noires, contraste automatique désactivé en plein écran, découpe caméra autorisée sur les bords courts et navigation système temporaire par glissement. Les commandes gardent leurs marges de sécurité.

## Contrôles réseau

Le [test des adaptateurs natifs](ANIME_COVERAGE_NETWORK.md) passe pour les six couples : Naruto, Classroom of the Elite, Mushoku Tensei × Anime-Sama, Vostfree. Pour chaque couple, au moins un média et son échantillon sont accessibles. Anime-Sama fournit une alternative VF et VOSTFR pour les trois premiers épisodes testés. Vostfree fournit une alternative VF pour chacun.

Le [contrôle indépendant FFprobe/FFmpeg](ANIME_DECODE_RC6.md) confirme sur Ansembed VF, saison 1 épisode 1 : Naruto **1 374 s**, Classroom **1 442 s**, Mushoku **1 422 s** ; une image est décodée à **120 s** pour chacun. Les images inspectées correspondent aux programmes, pas à une annonce Uqload. Ce test Linux ne prouve pas le décodage Media3 sur téléphone.

Restent en échec dans ces échantillons : Sibnet (HTTP 403), certains anciens Uqload, Mytv, StreamSB et Embed4me. Les alternatives fonctionnelles ne rendent pas tous ces hébergeurs disponibles. La couverture de 5 à 10 lecteurs distincts par titre n’est pas garantie.

## Validation sur appareil

Waydroid a été détecté mais reste sur écran noir : service `waydroidplatform` indisponible, IP inconnue, aucun appareil ADB. Un redémarrage normal de la session n’a pas suffi. Aucune donnée Android supprimée, aucune réinitialisation ou modification système privilégiée effectuée. L’utilisateur a demandé de recevoir le build pour tester lui-même.

Captchas FRAnime/Papadustream, aspect sur Xiaomi 12T, commandes tactiles, audio, Cast et performances du projecteur restent à vérifier sur appareil. Aucun captcha résolu artificiellement et aucune source déclarée vérifiée sans lecture réelle de 90 secondes.

## Sources techniques

- [Exigences Cloudflare Turnstile pour WebView](https://developers.cloudflare.com/turnstile/get-started/mobile-implementation/) : environnement stable, JavaScript, stockage DOM et cadres internes.
- [Gestion Android des découpes d’écran](https://developer.android.com/develop/ui/views/layout/display-cutout).
- [Wiki des sources françaises](https://fmhy.net/non-english).

Les informations de catalogue et de lecteur proviennent des pages publiques effectivement interrogées par les tests. Aucun identifiant de session ni URL média signée n’est enregistré dans ce rapport.
## Build livré

- APK signé : `releases/TvSama-v1.8.88-rc6-universal.apk` (répertoire parent du dépôt), **54 913 894 octets**.
- Package `fr.nekotv`, version `v1.8.88-rc6`, code **100**, Android minimum **23**, non débogable.
- Architectures : `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`.
- SHA-256 : `8475410e233f00a25ef9bfeae1c40fec98906fbd919e50b8fcf04d174ae1658d`.
- Certificat SHA-256 : `5b522fba46fef8a6a34e7e09d6ceee1ff9f732dcefdd7e0a48db83d80e27d50f` (identité de signature conservée).
- Validation Gradle finale réussie : tests unitaires des deux modules, lint debug/release, assemblage release et APK de tests instrumentés. **198 tests exécutés réussis**, **22 ignorés** ; aucun échec ni erreur. Les tests réseau explicites sont consignés séparément ci-dessus.
- Lint : **0 erreur**, **79 avertissements debug**, **78 release**. Les avertissements ne sont pas tous résolus.
- Signature et alignement vérifiés par le script de packaging ; `git diff --check` réussi dans les deux dépôts.
- APK de test livré localement ; aucune publication de release distante.
