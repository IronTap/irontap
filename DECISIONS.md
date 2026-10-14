# IronTap — Décisions techniques

Décisions prises avant le démarrage du développement (13 oct. 2026). La spécification fonctionnelle et le design restent dans `design_handoff_irontap/` (`spec-app-muscu.md` fait foi pour le comportement, `README.md` pour le rendu visuel).

## Principes

- On fait simple : stockage local uniquement, aucun appel réseau.
- L'application doit être rapide et fiable à la salle.
- Développement et tests d'abord par APK, mais tout choix doit rester compatible avec une future publication sur le Play Store.

## Pile technique

| Besoin | Choix |
|---|---|
| Langage / UI | Kotlin + Jetpack Compose, Material 3, thème personnalisé « Nocturne » (tokens du README du handoff) |
| Navigation | Navigation Compose (routes typées) |
| Stockage | Room (SQLite) avec KSP |
| Petites préférences | DataStore (ex. « pré-permission notifications déjà montrée ») |
| Architecture | MVVM : `ViewModel` + `StateFlow`, un seul module Gradle |
| Asynchrone | Coroutines + Flow |
| Injection de dépendances | Aucune bibliothèque : conteneur manuel dans `Application` |
| Dates | `java.time`, locale `fr` (« Lun. 12 oct. 2026 ») |
| Police | Inter, fichiers TTF embarqués dans `res/font` (pas de polices téléchargeables, elles passent par le réseau) |
| Icônes | Phosphor, exportées en vector drawables (19 icônes, aucune dépendance ; haltère en deux variantes, générées par `outils/generer_icones.py` depuis la géométrie des maquettes) |
| Export | `ACTION_SEND` + `FileProvider`, `ActivityResultContracts.CreateDocument` ; texte et CSV générés à la main |
| Build | Gradle Kotlin DSL + version catalog |
| Tests | JUnit sur la logique pure (validation, arrondi 0,25, calcul du chrono, export, autocomplétion sans accents) + Room en mémoire |

## Identité et compatibilité

- `applicationId` : **`app.irontap`** (définitif : ne peut plus changer après publication sur le Play Store).
- `minSdk 26` (Android 8), `targetSdk 36`.
- Pas de permission `INTERNET` : garantit qu'aucun appel réseau n'est possible.
- Seule permission demandée : `POST_NOTIFICATIONS` (Android 13+, précédée de l'écran 9).
- R8 (`isMinifyEnabled`) activé en release.
- Sauvegarde : aucune sauvegarde dans le cloud (`allowBackup="false"` et `dataExtractionRules`). Le transfert direct vers un nouveau téléphone (Android 12+) reste permis pour garder l'historique.
- Version de développement : paquet distinct `app.irontap.debug` (« IronTap debug »), qui coexiste avec la version signée sans partager ses données.
- Dépôts Gradle : `androidx.*` et `com.android.*` viennent exclusivement du dépôt Google, le reste de Maven Central.
- Aucune bibliothèque de publicité, d'analytique ou de traçage.

## Chronomètre et notification

**Pas de service au premier plan**, contrairement à la recommandation indicative de la spec §7.

- Un service pour un chrono de repos devrait utiliser le type `specialUse` sur Android 14+. Il faudrait le justifier dans la Play Console, avec un risque de refus à l'examen. De plus, depuis Android 14, l'utilisateur peut balayer la notification d'un service pour la faire disparaître : le service n'apporte rien.
- Le chrono est calculé à partir d'horodatages, donc aucun code n'a besoin de tourner en arrière-plan.
- On affiche une **notification persistante avec le chronomètre système** (`setUsesChronometer` / `setWhen`). En pause : texte figé, sans chronomètre.
- Les actions Pause / Reprendre / Relancer passent par un `BroadcastReceiver`. Il met à jour l'état en base, puis réaffiche la notification.
- Si l'utilisateur balaie la notification, elle revient à la prochaine action sur le chrono ou au prochain enregistrement d'une série.
- Mise à jour de la notification : tant que l'activité vit, elle suit l'entraînement en cours en base (`NotificationChrono.suivre`) ; appli fermée, c'est le `BroadcastReceiver` (non exporté, intentions explicites et immuables) qui la réaffiche. Pas de notification tant que le chrono n'a jamais été lancé.
- **Contenu** (maquettes 5a, 5b) : le titre d'une notification standard ne peut pas contenir un chronomètre qui tourne (il faudrait une mise en page personnalisée). En marche : en-tête « IronTap • Chrono de repos • 01:12 » (chronomètre système), titre « Repos en cours ». En pause : titre figé « 01:12 · En pause ». Texte : « Tirage vertical · Série 3 », la série en cours de saisie, comme l'en-tête de l'écran 3.
- **Pré-permission (écran 9)** : montrée une seule fois, au premier démarrage du chrono, sur Android 13+ si la permission manque. « Plus tard » ne la reproposera pas : l'utilisateur peut activer les notifications dans les réglages Android.
- Base de temps : `SystemClock.elapsedRealtime()` + temps cumulé, persistés (`EtatChrono`, spec §6).
- **Affichage `MM:SS`** au lieu de `HH:MM:SS` (spec §4.3.4) : en chiffres tabulaires, le format long déborde du bandeau, et un repos ne dépasse pas l'heure. Au-delà, les minutes continuent (`75:12`). Le chronomètre système de la notification affiche lui aussi `MM:SS` sous l'heure.
- **Le redémarrage du téléphone n'est pas géré** : le chrono ne sert qu'à suivre le repos entre deux séries. Le temps calculé est seulement borné à 0, pour ne pas planter si `elapsedRealtime` a été remis à zéro.

## Modèle de données

Spec §6, avec ces précisions :

- **`Entrainement.nouvel_exercice`** (ajout) : vrai après « Nouvel exercice ». Sans lui, à la reprise (spec §4.3.7), impossible de savoir si la prochaine série continue l'exercice courant (nom verrouillé) ou en ouvre un nouveau (nom éditable).
- `EtatChrono` est supprimé à la fin de l'entraînement ; suppressions en cascade (entraînement → exercices → séries, chrono).
- Dates en texte ISO (`2026-10-12`), horodatages en millisecondes ; poids en `REAL` (les multiples de 0,25 sont exacts en binaire).
- Schéma exporté dans `app/schemas/` et versionné : toute modification après la première installation demande une migration.

## Écrans

- **« Nouvel entraînement » avec un entraînement en cours** (spec §4.1, sans maquette) : dialogue « Terminer l'entraînement en cours ? » au style de 4b. Confirmer le termine (ou le supprime s'il n'a aucune série, comme §4.3.6), puis ouvre l'écran 2.
- **Sélecteur de date (2b)** : calendrier Material dans la fenêtre des dialogues Nocturne (voile neutre-900, rayon 24, élévation lg). Il ne descend pas sous 360 dp de large : la marge de 20 dp se réduit sur les écrans de moins de 400 dp.
- Bandeau « en cours » de l'accueil (1c) : chrono en `MM:SS` comme partout ailleurs.
- **Renommer un exercice** (spec §4.4, sans maquette) : dialogue « Renommer l'exercice » avec le champ et l'autocomplétion de la saisie (liste dans le dialogue, sous le champ), Annuler / Enregistrer.
- **Supprimer une série** (6b) : la fin du texte s'adapte. « Les séries suivantes seront renumérotées » seulement s'il y en a ; pour la seule série d'un exercice, « l'exercice sera retiré de l'entraînement ». Supprimer l'entraînement (menu ⋮) : confirmation au même style, « Cette action est définitive ».
- **Balayage** : la zone « Supprimer » raccourcit la ligne (comme la maquette 6b) au lieu de la pousser hors de l'écran ; la ligne reste ouverte derrière le dialogue et se referme si on annule.
- Feuilles du bas sans ombre lg : avec la feuille Material, l'ombre serait dessinée en haut de l'écran ; le voile suffit.

## Environnement de développement

- Développement dans **WSL2** (projet personnel, séparé de l'infrastructure de l'entreprise).
- SDK Android en ligne de commande (`cmdline-tools`) + Gradle, sans Android Studio.
- Installation sur le téléphone, par ordre de préférence :
  1. **Débogage sans fil** (`adb pair` / `adb connect`, Android 11+) ; le mode réseau `mirrored` de WSL le simplifie ;
  2. copie du fichier `.apk` sur le téléphone et installation à la main ;
  3. câble USB via `usbipd-win` (plus contraignant).
- Pas d'émulateur dans WSL. Si besoin de prévisualiser les écrans dans un IDE : Android Studio côté Windows.

## À faire avant une publication Play Store (plus tard)

- Générer une clé de signature release et la conserver en lieu sûr (activer Play App Signing).
- Produire un AAB (`bundleRelease`) au lieu d'un APK.
- Rédiger une politique de confidentialité courte et remplir la section « sécurité des données » (aucune donnée collectée).
- Compte développeur personnel récent : test fermé obligatoire avec 12 testeurs pendant 14 jours avant la mise en production.
- Vérifier le `targetSdk` exigé par Google au moment de la publication.
- Activer la vérification des dépendances (`./gradlew --write-verification-metadata sha256 …`, fichier `gradle/verification-metadata.xml` versionné).
- Embarquer les mentions de licence tierces dans l'application (Inter OFL, Phosphor MIT : `licences/*.txt`), par exemple sur une ligne « Licences ».

## Prochaines étapes

1. Installer JDK 21 et le SDK Android en ligne de commande dans WSL2.
2. Créer le squelette Gradle (`app.irontap`) et le thème Nocturne (couleurs, typographie Inter, formes).
3. Créer le modèle Room (Entrainement, Exercice, Serie, EtatChrono).
4. Écran Saisie série (écran principal) et chrono, puis Accueil, Nouvel entraînement, Détail, Édition de série, Export.
