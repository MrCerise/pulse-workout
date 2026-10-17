# Publier la politique de confidentialité

Les pages sont prêtes à héberger :

- [Français](fr.html)
- [English](index.html)

Elles fonctionnent sans JavaScript, police externe ou dépendance. Elles décrivent le stockage local,
le GPS, le microphone, l’export vers Health Connect, les transferts avec la montre, les sauvegardes,
la conservation et la suppression des données. Le contact est `security@nexalab.fr`.

## GitHub Pages

1. Envoyez ces fichiers dans la branche que vous souhaitez publier, par exemple `main`.
2. Dans le dépôt GitHub, ouvrez **Settings → Pages**.
3. Choisissez **Deploy from a branch**, puis votre branche et le dossier **`/docs`**. Enregistrez.
4. Attendez la fin du déploiement et ouvrez l’adresse indiquée par GitHub Pages.
5. Vérifiez la page dans une fenêtre de navigation privée, sans connexion à GitHub.

Pour le dépôt `MrCerise/pulse-workout`, avec le domaine GitHub Pages par défaut, les adresses seront :

```text
https://mrcerise.github.io/pulse-workout/privacy/fr.html
https://mrcerise.github.io/pulse-workout/privacy/
```

Ces adresses deviennent utilisables après activation et déploiement de GitHub Pages. Si un domaine
personnalisé est configuré, utilisez l’adresse affichée dans **Settings → Pages** suivie de
`/privacy/fr.html` ou `/privacy/`.

Collez l’adresse française publique dans le champ **URL des règles de confidentialité** de la Play
Console. Google demande une page publique active, accessible sans restriction géographique, au format
HTML et non modifiable par les visiteurs :
[Règlement sur les données utilisateur](https://support.google.com/googleplay/android-developer/answer/10144311).

## Autre hébergement

Vous pouvez aussi déposer `fr.html` et `index.html` dans le même dossier sur votre hébergement HTTPS.
Les deux fichiers sont autonomes ; les liens de langue fonctionnent tant qu’ils restent ensemble.
Utilisez alors l’adresse publique de `fr.html` dans la Play Console.

## Modifier les textes

Modifiez [`legal/privacy.json`](../../legal/privacy.json), puis lancez :

```sh
python scripts/generate_privacy_policy.py
python scripts/generate_privacy_policy.py --check
```

Le script met à jour les deux pages HTML et les textes intégrés aux applications téléphone et
montre. Ne modifiez pas directement les fichiers générés. La présentation HTML se trouve dans
[`scripts/generate_privacy_policy.py`](../../scripts/generate_privacy_policy.py).
