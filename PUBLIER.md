# Publier une version

Une seule clé signe tous les APK publiés (versions GitHub et F-Droid). F-Droid recompile chaque version depuis son tag, vérifie que le résultat est identique à l'APK publié sur GitHub (build reproductible), puis distribue cet APK signé. Changer de clé obligerait les utilisateurs à désinstaller, et IronTap n'a pas d'import : leur historique serait perdu.

## Une fois : la clé de signature
```sh
keytool -genkeypair -v -keystore irontap.jks -alias irontap -keyalg RSA -keysize 4096 -validity 10000
```
- Garder `irontap.jks` et ses mots de passe **hors du dépôt**, en deux copies (par exemple un gestionnaire de mots de passe et une clé USB). Sans eux, plus aucune mise à jour possible.
- À la racine du dépôt, créer `keystore.properties` (ignoré par git, comme `*.jks`) :
  ```properties
  storeFile=/chemin/vers/irontap.jks
  storePassword=…
  keyAlias=irontap
  keyPassword=…
  ```
- Empreinte du certificat, demandée par F-Droid (`AllowedAPKSigningKeys`) : la ligne `SHA256` de `keytool -list -v -keystore irontap.jks -alias irontap`, en minuscules et sans les deux-points.

## À chaque version
1. Dans `app/build.gradle.kts`, augmenter `versionCode` (entier, +1) et `versionName` (`0.1.0`, `0.2.0`…).
2. Écrire `fastlane/metadata/android/fr-FR/changelogs/<versionCode>.txt` et sa version `en-US` (500 caractères au plus).
3. Commiter, puis poser le tag `v<versionName>` (par exemple `git tag v0.1.0`) et pousser le commit et le tag.
4. Compiler depuis ce commit, sans modification locale : `./gradlew clean assembleRelease`.
5. Renommer `app/build/outputs/apk/release/app-release.apk` en `irontap-<versionName>.apk` et le joindre à une version GitHub créée sur le tag.

F-Droid détecte le nouveau tag, recompile et publie en quelques jours ; Obtainium voit la version GitHub tout de suite.

## Une fois : la demande d'inclusion dans F-Droid
Conditions à vérifier avant d'envoyer la demande :
- au moins 3 mois de développement actif (premier commit le 13 octobre 2026, donc pas avant mi-janvier 2027) ;
- une première version publiée sur GitHub, avec son tag.

Étapes (https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/) :
1. Compte sur gitlab.com, fork de https://gitlab.com/fdroid/fdroiddata, branche `fr.irontap`.
2. Copier `outils/fdroid/fr.irontap.yml` dans `metadata/fr.irontap.yml` du fork et remplir ses champs `À COMPLÉTER`.
3. Tester avec le conteneur de compilation d'F-Droid (Docker, environ 5 Go), ou laisser la CI de GitLab le faire sur le fork : `fdroid readmeta`, `fdroid rewritemeta fr.irontap`, `fdroid lint fr.irontap`, `fdroid build fr.irontap`.
4. Commit « New App: fr.irontap », push, puis demande de fusion (merge request) vers fdroiddata. Répondre aux questions des relecteurs.
