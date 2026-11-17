# Ascension — programme 90 jours

Appli Android personnelle : objectifs, promesses quotidiennes (socle + bonus), 3 phases de 30 jours, grades, compétences, mesures hebdo, jours minimum, rappels programmés sur le téléphone.

- Code de l'appli : `www/index.html` (HTML/JS sans dépendance de build)
- Emballage Android : Capacitor 8 (`android/`), plugins `local-notifications` et `preferences`
- Compilation : GitHub Actions (`.github/workflows/build-apk.yml`) → APK publié dans **Releases**
- Signature : secrets `ANDROID_KEYSTORE_B64` et `ANDROID_KEYSTORE_PASSWORD` (même clé à chaque version, sinon la mise à jour échoue)

## Installer ou mettre à jour
Sur le téléphone : ouvrir la dernière Release, télécharger `Ascension.apk`, l'ouvrir, autoriser l'installation depuis le navigateur. Les données sont conservées lors des mises à jour.
