# Ascension — programme 90 jours

Appli Android personnelle : objectifs, promesses quotidiennes (socle + bonus), 3 phases de 30 jours, grades, compétences, mesures hebdo, jours minimum, rappels programmés sur le téléphone.

- Code de l'appli : `www/index.html` (HTML/JS sans dépendance de build)
- Emballage Android : Capacitor 8 (`android/`), plugins `local-notifications` et `preferences`
- Compilation : GitHub Actions (`.github/workflows/build-apk.yml`) → APK publié dans **Releases**
- Signature : clé de type debug versionnée (`android/app/ascension-debug.keystore`, mots de passe standards `android`) — même signature à chaque version pour pouvoir mettre à jour sans perdre les données

## Installer ou mettre à jour
Sur le téléphone : ouvrir la dernière Release, télécharger `Ascension.apk`, l'ouvrir, autoriser l'installation depuis le navigateur. Les données sont conservées lors des mises à jour.
