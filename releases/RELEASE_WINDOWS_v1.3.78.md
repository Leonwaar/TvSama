# TvSama Windows v1.3.78 — aperçu

Artefact : `TvSama-1.3.78-Windows-x64.exe`, portable non signé, environ 124 Mio.
Windows x64 ; l’enveloppe auto-extractible NSIS est PE32, l’application embarquée
est PE32+ x86-64. Aucun environnement Java ou Android n’est nécessaire.

SHA-256 : `73dccdec32cda6cd1f5586b4aaa52955b93801f38aae5709de36f2144df573e3`

Disponible : interface sombre, navigation latérale, recherche dans les flux
personnels, favoris, reprise locale, lecteur MP4/HLS HTTPS et navigation aux
flèches, souris ou toucher. Télécommande via touches clavier HID.

Limites : fournisseurs et catalogues Android, DASH, Cast, appairage TV, IntroDB,
rappels sportifs et mises à jour automatiques encore absents. Cet aperçu ne remplace
pas encore l’application Android complète. Il n’a pas été testé sur un PC Windows.

Vérification : build portable terminé avec code de sortie 0, quatre tests DOM
réussis, syntaxe JavaScript vérifiée, contenu app.asar inspecté et aucune
vulnérabilité runtime connue signalée par npm audit. Lecture effective, démarrage
Windows et périphériques à valider. Voir `windows/VALIDATION.md`.

Reconstruction : `cd windows`, `npm ci`, `node --test smoke.test.cjs`, `npm run dist`.
Versions alignées : package desktop 1.3.78, Android v1.3.78 / versionCode 80.
