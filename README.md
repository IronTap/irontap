# IronTap

Carnet de musculation pour Android : exercices, poids, répétitions et chronomètre de repos. Sans publicité, sans compte, sans accès à internet. Les séances restent sur le téléphone.

Site : https://irontap.fr/

## Fonctionnalités
- Chronomètre de repos qui repart après chaque série et reste visible dans une notification.
- Ajout rapide : +1, +2, +5, +10, +20 sur le poids, +1, +2, +5, +10 sur les répétitions, décimales acceptées.
- Autocomplétion des exercices, avec ou sans accents.
- Historique de l'exercice en un appui, depuis la saisie.
- Séries dégressives et supersets.
- Modification et suppression des séries et des entraînements.
- Export en texte ou en CSV, import d'un export CSV pour restaurer l'historique.

## Vie privée
- L'application ne déclare pas la permission `INTERNET` : elle ne peut rien envoyer ni recevoir.
- Aucune bibliothèque de publicité, de statistiques ou de suivi.
- Une seule autorisation, facultative : `POST_NOTIFICATIONS`, pour le chrono.
- Pas de sauvegarde dans le cloud ; le transfert direct vers un nouveau téléphone reste possible (Android 12 et plus).

## Installation
- **Versions publiées** : APK signé dans les [versions GitHub](https://github.com/IronTap/irontap/releases). Pour être prévenu des mises à jour, [Obtainium](https://github.com/ImranR98/Obtainium) peut suivre ce dépôt.
- **F-Droid** : demande d'inclusion prévue.

Android 8 ou plus récent. L'application est en français.

## Compilation
JDK 17 ou plus récent, puis :

```sh
./gradlew assembleRelease        # app/build/outputs/apk/release/
./gradlew testDebugUnitTest      # tests JVM (Robolectric)
./gradlew recordRoborazziDebug   # captures d'écran dans app/build/outputs/roborazzi
```

Sans fichier `keystore.properties`, l'APK de la version release n'est pas signé. La publication d'une version est décrite dans [PUBLIER.md](PUBLIER.md), les choix techniques dans [DECISIONS.md](DECISIONS.md).

## Développement assisté par IA
Le code d'IronTap est écrit avec l'aide d'un assistant de programmation IA (Claude, d'Anthropic). Les commits concernés portent la mention `Co-Authored-By: Claude`.

## Licence
Copyright © 2026 IronTap Dev

IronTap est un logiciel libre, distribué sous la licence GNU GPL version 3 ou toute version ultérieure (`GPL-3.0-or-later`). Le texte complet est dans [LICENSE](LICENSE).

Composants tiers, avec leur propre licence (textes dans [`licences/`](licences/)) :
- police [Inter](https://rsms.me/inter/), SIL Open Font License 1.1 ;
- icônes [Phosphor](https://phosphoricons.com/), licence MIT ;
- bibliothèques Android (Kotlin, AndroidX : Compose, Room…), licence Apache 2.0, sauf la copie de protobuf embarquée par DataStore (BSD 3 clauses, Google Inc.).

Ces textes, et la liste des bibliothèques réellement embarquées, sont lisibles dans l'application : ⓘ de l'accueil, « À propos ».

Le nom « IronTap » et son logo ne sont pas couverts par la licence : une version modifiée et redistribuée doit porter un autre nom et un autre logo.
