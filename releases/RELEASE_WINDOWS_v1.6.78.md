# TvSama Windows v1.6.78

Portable Windows x64 : [TvSama-1.6.78-Windows-x64.exe](TvSama-1.6.78-Windows-x64.exe).
Taille : 151 638 891 octets. Non signé. Précédentes versions conservées.

SHA-256 :

```text
a459b94c3fa7a4d639c63195680aafdc4a2f533ee219b45d920f148a3fed7f94
```

Corrections : onglets visibles sur petits écrans, menus souris/tactile/clavier,
DNS comme Android, en-têtes et redirections vidéo, résolution concurrente bornée,
requêtes média annulées à l’arrêt, erreurs réseau récupérables et fermeture bornée.
Extracteurs MailRu/Vidara, alternatives Voiranime et sources littérales/base64 ;
pagination FRAnime, captcha explicite, déconnexion TV et reconnexion Cast corrigées.

Validation : 56 tests passent ; 28 scénarios Electron sur ASAR et sur le portable
sous Wine 11. Le rapport Wine identifie l’EXE par son SHA-256. Certificats validés
pendant l’audit public ; quatre échantillons distants décodés sur 21 adaptateurs.
Les échecs des autres sources sont conservés. L’EXE ne contient aucun catalogue
de démonstration substitué aux sites qui échouent.

Parcours complémentaire : 29 scénarios avec PiP, profil au chemin accentué sous Wine
et flèches sur petits écrans. Endurance locale : 30 min sur candidat Linux chargé
avant les derniers correctifs ; 10 min sur le portable final sous Wine, sans pause
ni erreur détectée. Le premier essai Wine interrompu par un probable quota temporaire
reste conservé, avec le passage réussi après nettoyage. Ces essais ne certifient
ni l’endurance Windows natif ni une lecture distante prolongée.

La parité complète reste ouverte : Windows natif, périphériques et Android/Cast
physiques, sources bloquées ou non reconnues, PiP et plusieurs parcours avancés.
Les sites externes peuvent imposer une validation ou changer leurs API.
Endurance et portée exacte des preuves : [VALIDATION.md](../windows/VALIDATION.md).
Critères bloquants : [STABILISATION.md](../windows/STABILISATION.md).

Version Android alignée v1.6.78 / code 83, aucun APK reconstruit dans ce lot.
Chaque lot fonctionnel livré augmente la version ; aucun numéro publié réutilisé.
