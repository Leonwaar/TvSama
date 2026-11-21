# Validation de v1.8.88-rc5

APK de validation, pas une certification de disponibilité de toutes les sources.

- Version : `v1.8.88-rc5`, code `99`, paquet `fr.nekotv`, Android minimum 23.
- Compilation Release terminée ; APK non débogable, ARMv7, ARM64, x86 et x86_64.
- 189 tests locaux exécutés avec succès : application 132, adaptateurs 57. Les 21 tests réseau optionnels sont exclus de cette exécution et ne sont pas comptés comme réussis.
- Lint Debug : 0 erreur, 78 avertissements ; lint Release : 0 erreur, 77 avertissements. Compilation Kotlin des tests Android terminée, sans exécution sur appareil.
- `git diff --check` sans erreur dans les deux dépôts.
- Signature et alignement vérifiés par le script de construction. Certificat inchangé : `5b522fba46fef8a6a34e7e09d6ceee1ff9f732dcefdd7e0a48db83d80e27d50f`.

[APK universel](../../releases/TvSama-v1.8.88-rc5-universal.apk), 54 913 894 octets.

SHA-256 : `577df1adb95e7afbc1008feb08b7e88ac36593c20a02eb2a73b79f37e1b7ec92`.

Les tests ont été relancés avec un seul worker et un budget Gradle de 3 Go après un épuisement du tas sur la première validation globale. Les contrôles n'ont pas été affaiblis ni désactivés pour produire l'APK.

## Limites de lecture et de disponibilité

Les annuaires de secours AdressesDirect et FMHY ont reconnu des adresses Wiflix lors du dernier contrôle réseau. Le catalogue Wiflix a réussi un premier essai avec 30 fiches, puis échoué lors d'essais suivants. Les derniers contrôles Wiflix et Coflix n'ont confirmé aucun média complet. Les captchas de Papadustream/FRAnime restent interactifs et n'ont pas été résolus sur appareil. La présence au catalogue ou la signature média ne prouvent ni l'identité du programme ni son décodage Android.

L'objectif de 5 à 10 lecteurs indépendants reste conditionné aux médias réellement publiés et accessibles. L'APK ne garantit pas cette couverture pour chaque titre, ni un service externe en ligne 24 h/24. Aucun essai sur téléphone, projecteur, Android TV ou Chromecast n'a été effectué pendant ce correctif.

Voir [les corrections](SOURCE_DIVERSITY_RC5.md), [l'actualisation autonome et les sources consultées](AUTONOMOUS_ADDRESSES.md), [les essais Coflix](COFLIX_VIDEO_NETWORK.md), [Wiflix](WIFLIX_RECOVERY_NETWORK.md) et [les annuaires](DIRECTORY_BACKUPS_NETWORK.md).

Le logo premium, minimaliste et moderne, ainsi que le téléchargement mobile hors ligne, restent [en attente pour la prochaine mise à jour](NEXT_RELEASE_BACKLOG.md).
