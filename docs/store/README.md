# Fiche Google Play — Interval Coach

Fichiers prêts à copier ou importer dans la Play Console :

- [Icône Google Play](icon.png) : PNG RGBA, 512 × 512 px, fond uni identique au lanceur Android.
- [Logo transparent](../brand/interval-coach-logo.png) : PNG RGBA, 1 024 × 1 024 px, pour les autres supports.
- [Nom français](fr-FR/title.txt) : 14 caractères sur 30.
- [Brève description française](fr-FR/short-description.txt) : 77 caractères sur 80.
- [Description complète française](fr-FR/full-description.txt) : moins de 4 000 caractères.
- [Six captures marketing en anglais](screenshots/en/) : PNG opaques, 1 080 × 1 920 px.
- [Télécharger les six captures en ZIP](screenshots/interval-coach-google-play-en.zip).
- [Aperçu de la série](screenshots/contact-sheet.png).
- [Image de présentation Google Play](feature-graphic.png) : PNG 24 bits, 1 024 × 500 px, sans transparence.

Utilisez `icon.png` pour le champ **Icône de l’application**. Le dessin provient du
[logo vectoriel existant](../brand/pulse.svg), sans modification de sa géométrie.
Le fichier ne comporte ni coins arrondis ni ombre extérieure ; Google Play les applique à l’affichage.

Les descriptions présentent les fonctionnalités actuelles, y compris Wear OS et l’export optionnel
des séances vers Health Connect. Elles précisent les conditions des annonces vocales hors ligne et
le caractère non médical de l’application.

Les captures marketing utilisent le style « Neon Athletic Night » : anthracite, accent citron vert,
titres anglais en caractères condensés et cadres Android inclinés. Les quatre captures originales
sont intégrées sans retouche de l’interface. Deux vues sont réutilisées dans la série de six images.
L’éditeur et les fichiers sources se trouvent dans [`tools/store-screenshots`](../../tools/store-screenshots).

L’image de présentation reprend cette direction visuelle avec le logo dans un cadran stylisé et le
slogan anglais « Train at your pace. ». Texte alternatif proposé :
« Interval Coach, minuteur d’entraînement : logo vert citron dans un cadran sur fond sombre. »

Pour réexporter les PNG dans un environnement Python contenant `Pillow` et `resvg-py` :

```sh
python scripts/export_store_icon.py
```

Références Google Play :

- [Icônes et assets de prévisualisation](https://support.google.com/googleplay/android-developer/answer/9866151?hl=fr)
- [Spécifications de conception des icônes](https://developer.android.com/distribute/google-play/resources/icon-design-specifications)
- [Limites des champs de la fiche](https://support.google.com/googleplay/android-developer/answer/9859152?hl=fr)
- [Règles sur les métadonnées](https://support.google.com/googleplay/android-developer/answer/9898842)
