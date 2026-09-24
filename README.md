# TvSama

Application Android TV en Kotlin, avec recherche, catégories, jaquettes et lecteur Media3.

## Démonstration

Le catalogue inclut des fiches de démonstration et une recherche sur l'API publique de l'Internet Archive. Les résultats distants sont limités aux éléments dont la métadonnée indique une licence Creative Commons ou le domaine public. Les jaquettes sont chargées depuis les miniatures de l'Archive. Les URL de démonstration ne fournissent pas d'épisodes d'anime. Le lecteur lit HLS (`.m3u8`), DASH (`.mpd`) et MP4 (`.mp4`) via HTTPS.

Depuis **Mes sources**, on peut ajouter, modifier ou supprimer localement un lien direct HTTPS vers un média dont on détient les droits ou qu'on est autorisé à diffuser. Ces entrées sont sauvegardées sur l'appareil et apparaissent dans le sélecteur du lecteur.

## Extension des catalogues

`CatalogProvider` est le point d'extension pour connecter un catalogue. L'intégration Internet Archive utilise ses API de recherche et de métadonnées et propose les fichiers vidéo directs disponibles sur la fiche. Les autres catalogues doivent utiliser une API documentée ou une intégration autorisée par le site. Les embeds tiers ne sont pas extraits : il faut utiliser le lecteur officiel prévu par le site, avec sa permission et les contraintes de sa plateforme. Les liens ajoutés manuellement sont des URL de média directes, pas des pages de sites.

## Construction

Ouvrir le dossier dans Android Studio avec JDK 17 et Android SDK 36, ou lancer `./gradlew assembleDebug`. Le wrapper Gradle est inclus dans le dépôt. L'APK de démonstration se trouve dans `app/build/outputs/apk/debug/app-debug.apk` après compilation.
