# IronTap — Décisions techniques

Décisions prises avant le démarrage du développement (13 oct. 2026). La spécification fonctionnelle et le design sont dans un dépôt séparé, `irontap-specs` (à côté de ce dépôt) : `design_handoff_irontap/` (`spec-app-muscu.md` fait foi pour le comportement, `README.md` pour le rendu visuel). Les évolutions suivantes arrivent en handoffs incrémentaux, dont la spec remplace la précédente : `handoff_historique_exercice/` (spec v1.3, §4.3.8), puis `handoff_degressive/` (spec v1.5, séries dégressives), puis `handoff_superset/` (spec v1.7, supersets), puis `handoff_a_propos/` (spec v1.8, écran À propos).

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
| Icônes | Phosphor, exportées en vector drawables (29 icônes, aucune dépendance ; flèche des dégressives et lien des supersets en deux épaisseurs comme les maquettes ; haltère en deux variantes, générées par `outils/generer_icones.py` depuis la géométrie des maquettes) |
| Export, import | `ACTION_SEND` + `FileProvider`, `ActivityResultContracts.CreateDocument` / `OpenDocument` ; texte et CSV générés et lus à la main |
| Build | Gradle Kotlin DSL + version catalog |
| Tests | JUnit sur la logique pure (validation, arrondi 0,25, calcul du chrono, export, import, autocomplétion sans accents) + Room en mémoire ; fichiers CSV d'exemple dans `app/src/test/resources/import/` |

## Identité et compatibilité

- `applicationId` : **`fr.irontap`**, tiré du domaine `irontap.fr` (recommandation d'F-Droid). Définitif après la première diffusion : en changer créerait une autre appli, dont l'historique ne se retrouverait que par un export CSV puis un import. Le `namespace` (paquets Kotlin) reste `app.irontap`.
- `minSdk 26` (Android 8), `targetSdk 36`.
- Pas de permission `INTERNET` : garantit qu'aucun appel réseau n'est possible.
- Seule permission demandée : `POST_NOTIFICATIONS` (Android 13+, précédée de l'écran 9).
- R8 (`isMinifyEnabled`) activé en release.
- Sauvegarde : aucune sauvegarde dans le cloud (`allowBackup="false"` et `dataExtractionRules`). Le transfert direct vers un nouveau téléphone (Android 12+) reste permis pour garder l'historique.
- Version de développement : paquet distinct `fr.irontap.debug` (« IronTap debug »), qui coexiste avec la version signée sans partager ses données.
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
- **v2 (séries dégressives, spec v1.4)** : poids et répétitions passent de `Serie` à la table `Palier` (1 = palier principal). Migration manuelle `Migration1Vers2` : chaque série existante reçoit son palier 1, puis `serie` est recréée sans ces colonnes (SQLite d'Android 8 ne sait pas en supprimer). Les clés étrangères restant actives pendant la migration, les valeurs transitent par une table temporaire : supprimer l'ancienne `serie` effacerait sinon en cascade les paliers déjà créés. Test : une base v1 créée depuis `schemas/…/1.json`, ouverte par Room, qui valide le schéma v2.
- **v3 (supersets, spec v1.6)** : `Exercice.superset_id` (UUID partagé) et `superset_role` (A | B), `palier_en_cours.role` (onglet d'un superset), table `superset_en_cours`. Migration `Migration2Vers3` par ajout de colonnes, vides pour l'existant ; test depuis `schemas/…/2.json`.
- **Tours d'un superset** : le numéro d'une série de A ou de B (`Serie.ordre`) est celui de son tour, pas de colonne de plus. Supprimer la série de A d'un tour où B en a une ne renumérote rien ; un tour devenu vide disparaît et les tours suivants sont renumérotés pour A et B. Les numéros d'un exercice peuvent donc sauter (A : 1, 3), ce qui ne se voit qu'à l'export CSV (`ordre_serie`, format inchangé hors périmètre). Un exercice resté seul (l'autre supprimé, ou un premier tour avec A seul) redevient ordinaire, ses séries renumérotées à la suite.
- **`PalierEnCours`** (ajout) : les paliers gardés par « Dégressive » sont en base, pas dans le brouillon en mémoire, pour être retrouvés si l'appli est tuée (critère du handoff). La série elle-même n'est créée qu'à « Nouvelle série » / « Nouvel exercice », avec tous ses paliers : numérotation, notification et accueil ne voient jamais de série incomplète.

## Écrans

- **« Nouvel entraînement » avec un entraînement en cours** (spec §4.1, sans maquette) : dialogue « Terminer l'entraînement en cours ? » au style de 4b. Confirmer le termine (ou le supprime s'il n'a aucune série, comme §4.3.6), puis ouvre l'écran 2.
- **Sélecteur de date (2b)** : calendrier Material dans la fenêtre des dialogues Nocturne (voile neutre-900, rayon 24, élévation lg). Il ne descend pas sous 360 dp de large : la marge de 20 dp se réduit sur les écrans de moins de 400 dp. Il est toujours en français, quelle que soit la langue du téléphone.
- **Suggestions d'exercice** (3b) : la liste s'arrête au-dessus du clavier et défile au-delà.
- **« Enregistrer la série et terminer »** (4a) : la série et la fin de l'entraînement sont écrites dans une même transaction ; si la fin échoue, la série n'est pas enregistrée et un nouvel essai ne la double pas.
- Bandeau « en cours » de l'accueil (1c) : chrono en `MM:SS` comme partout ailleurs.
- **Renommer un exercice** (spec §4.4, sans maquette) : dialogue « Renommer l'exercice » avec le champ et l'autocomplétion de la saisie (liste dans le dialogue, sous le champ), Annuler / Enregistrer.
- **Supprimer une série** (6b) : la fin du texte s'adapte. « Les séries suivantes seront renumérotées » seulement s'il y en a ; pour la seule série d'un exercice, « l'exercice sera retiré de l'entraînement ». Supprimer l'entraînement (menu ⋮) : confirmation au même style, « Cette action est définitive ».
- **Balayage** : la zone « Supprimer » raccourcit la ligne (comme la maquette 6b) au lieu de la pousser hors de l'écran ; la ligne reste ouverte derrière le dialogue et se referme si on annule.
- **Historique de l'exercice** (3f, 3g, spec §4.3.8) :
  - noms comparés avec la normalisation de l'autocomplétion (espaces de bord, casse, accents), calculée en Kotlin : SQLite ne sait pas retirer les accents. Pas de colonne `nom_normalise`, donc pas de migration ;
  - le bouton suit en direct les noms des autres entraînements (une requête observée par entraînement, la comparaison se fait en mémoire : pas de debounce) ;
  - la popup est lue une fois, à l'ouverture : les autres entraînements ne changent pas pendant la saisie. Titre : le nom tel qu'enregistré dans la séance la plus récente ;
  - deux séances le même jour : la plus récemment créée d'abord. Échec de lecture : toast d'erreur, la saisie continue.
- **Séries dégressives** (3·D, spec §4.3.9) :
  - « Dégressive » sur le premier palier d'un nouvel exercice crée l'exercice tout de suite (sans série) : c'est ce qui verrouille le nom, et il survit à un kill. La fin de l'entraînement supprime les paliers gardés et un tel exercice resté sans série ; un entraînement réduit à cela est supprimé comme un entraînement vide.
  - Pas de toast sur « Dégressive » (maquette) : l'en-tête montre déjà les paliers gardés. Le toast de « Nouvelle série » décrit les paliers lus en base.
  - Dialogue 4a : avec des paliers gardés, il propose la série même si les champs sont vides ; le palier courant s'y ajoute s'il est valide.
  - « Terminer l'entraînement en cours ? » depuis l'accueil abandonne une dégressive en attente, comme les champs non enregistrés.
- **Édition d'une dégressive** (7·D, spec §4.5.1) : une fois en tableau (dégressive, ou « + Ajouter un palier » depuis l'écran 7), la feuille y reste jusqu'à sa fermeture, même si tous les paliers dégressifs sont retirés : pas de changement de mise en page sous le doigt. L'écran 7 garde son libellé « Supprimer » ; le tableau dit « Supprimer la série ». Erreur de répétitions : bordure sur chaque champ fautif et message sous le tableau ; une ligne sans aucune valeur est ignorée, et si toutes sont vides c'est la première qui est signalée.
- **Superset — saisie** (S1–S5, spec §4.3.10) :
  - Les exercices A et B ne sont créés qu'au premier tour enregistré, A puis B, pour garantir leur ordre : « Dégressive » sur un onglet garde le palier et mémorise son nom (verrouillé), sans créer l'exercice.
  - **Survie à un kill** : noms, onglet actif et valeurs saisies des deux onglets sont mémorisés en base à chaque bascule d'onglet et quand l'écran passe en arrière-plan (`ON_STOP`), le moment où Android peut tuer l'appli. Hors superset, les champs restent en mémoire comme avant (spec §7).
  - « Exercice N » compte un superset comme un seul exercice (prototype : A et B affichent le même N).
  - Toasts : « Série k enregistrée · A … · B … », « … · A seul · … » (« Nouvel exercice » avec un onglet vide, prototype). Pour sortir d'un superset commencé par erreur : « Nouvel exercice » avec les onglets vides, après confirmation (cf. « Nouvel exercice » sans rien de saisi).
  - Un onglet compte comme saisi pour le dialogue de fin s'il a un nom et des répétitions valides, ou des paliers gardés.
  - Autocomplétion sur l'onglet actif modifiable : la liste s'affiche sous les deux onglets, sur toute leur largeur.
  - Ligne de la notification et du bandeau de l'accueil : « Élévations latérales + Curl marteau · Série 2 ».
  - Pastilles de 36 dp comme la maquette, dans une zone tactile de 48 dp.
- **Ajout rapide du poids** (hors spec, demande de l'utilisateur) : +1, +2, +5, +10, +20 (spec §4.3.1 : sans +2), sur l'écran de saisie et dans la feuille d'édition (écran 7). Cinq ronds de 58 dp répartis sur la largeur : sur 360 dp, 9,5 dp entre deux cibles (vérifié par les captures). Les répétitions gardent +1, +2, +5, +10.
- **« Nouvel exercice » sans rien de saisi** (hors spec, demande de l'utilisateur) : un appui par erreur sur « Nouvelle série » verrouillait le nom avec des champs vides, et « Nouvel exercice » exigeait alors des répétitions.
  - Nom verrouillé, ni poids, ni répétitions, ni palier gardé : dialogue au style de 4b, « Passer à l'exercice suivant ? », « Les champs sont vides : la série 4 ne sera pas enregistrée. Tirage vertical garde ses 3 séries. », « Annuler » / « Nouvel exercice ». Confirmé, l'exercice suivant s'ouvre (nom modifiable) sans rien enregistrer : pas de toast, chrono inchangé (aucune série enregistrée).
  - Un champ rempli, ou un palier gardé : validation normale. Nom encore modifiable : rien à passer, erreurs habituelles.
  - Superset aux deux onglets vides, même confirmation, « Terminer le superset ? » : un appui par erreur ne termine plus un superset, qu'on ne peut pas reprendre. Le toast « Superset terminé » disparaît, la confirmation le remplace. Un seul onglet vide reste ignoré sans confirmation (spec §4.3.10).
- **Annuler la dernière série** (hors spec, demande de l'utilisateur) : action « Annuler » (accent-300, zone tactile de 48 dp) à droite du toast « Série N enregistrée », seul toast qui la porte.
  - Annule le dernier enregistrement : série simple, dégressive avec ses paliers, tour de superset (A et B, ou « A seul »), y compris quand il a été suivi de « Nouvel exercice ».
  - La saisie revient à l'état d'avant l'appui : valeurs remises dans les champs (texte tel que saisi), paliers dégressifs gardés rétablis, superset en cours et onglets rétablis, nom remis s'il venait d'être saisi (l'exercice créé par la série disparaît). Le numéro est libéré : l'en-tête réaffiche « Série N ». Toast « Série N annulée », sans action : un seul niveau d'annulation.
  - Le chrono n'est pas touché : il compte depuis l'enregistrement annulé, donc le repos réel pendant la correction ; le réenregistrement le relance comme d'habitude.
  - Durée du toast avec « Annuler » : 5 s, allongée si le réglage d'accessibilité « Délai de réaction » d'Android (10+) le demande ; les autres toasts restent à 2,6 s. L'annulation disparaît avec le toast, ou dès qu'une autre écriture commence (série, palier dégressif, superset, passage à l'exercice suivant).
  - L'état d'avant est relevé en base dans la transaction de l'enregistrement (`Annulation` : nouvel exercice, exercices et leur superset, paliers gardés, superset en cours), gardé en mémoire seulement (une annulation ne survit pas à un kill). L'annulation, en une transaction, est refusée si autre chose a été enregistré depuis.
  - Sur un petit écran, le toast (juste au-dessus des actions) peut recouvrir la rangée +X des répétitions, « Annuler » au-dessus de +10. Risque faible (5 s juste après la série, pendant le repos) et sans gravité : les valeurs reviennent dans les champs.
- **Superset — détail** (H, 3·S+ T1) :
  - Appui sur le numéro d'un tour : « Modifier le tour N » (la maquette ne dit pas comment l'ouvrir). Le palier principal saisi remplace celui de la série, ses paliers dégressifs sont gardés ; un bloc vidé ne change pas la série (la supprimer passe par « Supprimer le tour »).
  - Indications d'« Ajouter un tour » : la dernière série de chaque exercice (maquette T1 : B reprend le tour 3 quand le tour 4 n'a pas de B).
  - Renommer : appui sur le nom de A ou de B dans l'en-tête de la carte (pas de crayon sur la maquette).
  - « Supprimer le tour N ? » : choix destructifs empilés, rappel des séries de A et B.
- **À propos** (10a, 10b, spec §4.6) :
  - Textes de licence embarqués dans `assets/licences/`, préparés à chaque compilation par la tâche `preparerLicences<Variante>` (`app/build.gradle.kts`) : `LICENSE`, `licences/Inter-OFL.txt`, `licences/Phosphor-MIT.txt`, `licences/Apache-2.0.txt` (texte officiel), `licences/Protobuf-BSD-3-Clause.txt`, et `bibliotheques.txt`, la liste des bibliothèques réellement résolues pour la variante (BOM exclues), avec leur licence et leur titulaire. Pas de plugin `aboutlibraries` / `oss-licenses` : la tâche suffit et reste hors ligne. Liste triée : builds reproductibles.
  - Toutes les bibliothèques sont sous Apache 2.0, sauf `androidx.datastore:datastore-preferences-external-protobuf`, copie de protobuf embarquée par DataStore : BSD 3 clauses, « Copyright 2008 Google Inc. », dont la licence impose de reproduire la mention et le texte (écran « Bibliothèques Android », après Apache 2.0). Garde-fous : une bibliothèque d'un groupe inconnu arrête la compilation ; le test `AProposTest` compare chaque licence listée à celle que déclare le POM du module (cache Gradle, parents compris).
  - Texte d'Inter : celui du dépôt (`licences/`), qui accompagne la version de la police embarquée, même s'il cite encore l'ancienne adresse de l'OFL.
  - Icône de l'appli dessinée comme la maquette (haltère accent-300 sur accent-900 bordé d'accent) : l'icône du lanceur, sur fond `bg`, disparaîtrait sur le fond de l'écran.
  - Découpage des textes pour l'affichage : paragraphes réunis ; titres de section : ligne courte en majuscules (« PREAMBLE »), ligne centrée (« Preamble » de la GPL) ou intitulé numéroté seul (« 0. Definitions. ») ; ligne de copyright en tête (« (c) » écrit « © »).
  - « Code source » : `ACTION_VIEW` vers `github.com/IronTap/irontap` ; sans navigateur, toast système « Aucune application pour ouvrir ce lien ».
- Feuilles du bas sans ombre lg : avec la feuille Material, l'ombre serait dessinée en haut de l'écran ; le voile suffit.

## Export

- **Portée** : depuis l'accueil, les entraînements terminés (l'entraînement en cours n'est pas exporté), dans l'ordre de l'accueil, du plus récent au plus ancien ; depuis le menu ⋮ du détail, cet entraînement seul. Le contenu suit la base tant que la feuille est ouverte.
- **Texte** : format de la spec §5 ; « PDC × 12 » au poids du corps, comme dans l'appli ; une ligne vide entre les exercices, deux entre les entraînements.
- **Supersets** (écart à la spec §5, qui les exportait comme deux exercices séparés ; prérequis de l'import) :
  - texte : un bloc à la place de A, « Superset : Élévations latérales + Curl marteau », puis une ligne par tour comme la carte du détail et le toast, « A 8 kg × 15 · B 12 kg × 12 », « — » pour une série absente (« A 8 kg × 12 · B — ») ;
  - CSV : trois colonnes ajoutées à la fin, les huit de la spec inchangées. `entrainement` : numéro de l'entraînement dans le fichier (1, 2…, dans l'ordre de l'export), qui sépare sans ambiguïté deux séances du même jour au même libellé ; `superset` : numéro du superset dans l'entraînement (1, 2…, dans l'ordre des exercices) ; `role_superset` : A ou B. Les deux dernières sont vides hors superset. `ordre_serie` reste le numéro du tour : il peut sauter (A : 1, 3) ;
  - un exercice resté seul de son superset (cas qui ne devrait pas se produire, l'appli le dissout) s'exporte comme un exercice ordinaire.
- **CSV** : colonnes de la spec (dont `palier`, une ligne par palier), séparateur `;`, point décimal, poids vide au poids du corps, lignes en CRLF (RFC 4180). Un libellé ou un nom contenant `;`, `"` ou un saut de ligne est mis entre guillemets. Un champ commençant par `=`, `+`, `-`, `@`, tabulation ou retour chariot est préfixé d'une apostrophe (injection de formules, recommandation OWASP) ; un champ commençant déjà par une apostrophe en reçoit une aussi, pour que l'import retire sans ambiguïté celle qui a été ajoutée. Le fichier commence par une marque d'ordre d'octets UTF-8, pour qu'Excel affiche les accents.
- **Partager…** : un texte de moins de 50 000 caractères part tel quel (`EXTRA_TEXT`, lisible dans une messagerie ou un bloc-notes) ; au-delà, et toujours pour le CSV, il part en pièce jointe. Le fichier est écrit dans `cache/exports/`, seul dossier exposé par le `FileProvider` (non exporté, lecture accordée à l'appli choisie seulement), et vidé à chaque nouvel export.
- **Enregistrer le fichier** : sélecteur de document système (`CreateDocument`), nom proposé `irontap-2026-10-12.txt` (un entraînement) ou `irontap-historique-2026-10-14.csv` (tout l'historique, date du jour). Le document choisi est écrit en mode « wt » (contenu remplacé).
- Après un partage ou un enregistrement réussi, la feuille se referme (enregistrement : toast système « Fichier enregistré », l'accueil et le détail n'ont pas de toast Nocturne). Un échec s'affiche dans la feuille, qui reste ouverte. La feuille rouverte repart du format Texte.
- Aperçu : les 5 premières lignes en chasse fixe (police monospace du système) ; les lignes longues du CSV défilent horizontalement.
- Carte CSV : « Pour un tableur, réimportable » (seul le CSV s'importe).

## Import

Hors spec (§1 et §5 : « pas d'import en V1 »), demande de l'utilisateur : sans import, toute perte de données (changement de téléphone sous Android 8 à 11, réinstallation, changement de clé) était définitive.

- **CSV seul** : complet et sans ambiguïté ; le texte reste pour la lecture. Un export texte choisi par erreur est refusé avec « Seul le format CSV s'importe : exporte de nouveau en choisissant CSV. »
- **Points d'entrée** : « Importer un export CSV » sous l'invitation de l'état vide de l'accueil (1b, le cas du nouveau téléphone, où Exporter est grisé), et « Importer un fichier CSV… » en bas de la feuille Exporter de l'accueil (pas dans celle du détail) : la feuille se referme, puis le sélecteur s'ouvre. Cas non couvert : historique vide avec un entraînement en cours (ni état vide, ni Exporter) ; il suffit de le terminer.
- **Sélecteur de document système** (`OpenDocument`, types `text/*`, `application/csv`, `application/vnd.ms-excel`, `application/octet-stream` : chaque appli annonce un CSV à sa façon). Aucune permission, toujours pas d'`INTERNET`. Fichier lu en UTF-8, ou en Windows-1252 s'il n'est pas de l'UTF-8 valide (CSV réenregistré par Excel) ; 20 Mo au plus.
- **Lecture** (`lireExportCsv`), tolérante sur la forme : marque d'ordre d'octets facultative, séparateur `;` ou `,` (celui de l'en-tête), guillemets RFC 4180 (`;`, `""`, saut de ligne), CRLF ou LF, colonnes repérées par leur nom, lignes vides ignorées, poids au point ou à la virgule arrondi au 0,25 kg comme à la saisie (vide ou nul : poids du corps). L'apostrophe ajoutée par l'export est retirée quand elle précède une formule (`=`, `+`, `-`, `@`, tabulation, retour chariot) ou une autre apostrophe.
- **Entraînements** : regroupés par la colonne `entrainement`. Sans elle (anciens exports), une ligne commence un nouvel entraînement quand la date ou le libellé change, ou quand elle ne suit pas la précédente (exercice, série, palier croissants, même nom pour un même exercice) : deux séances du même jour au même libellé restent séparées dans l'ordre de l'export.
- **Supersets reconstruits** : les deux exercices d'un même `superset` reçoivent un nouvel identifiant commun et leur rôle ; les numéros de série gardent les tours, sauts compris (tours vides retirés). A doit être immédiatement suivi de B. Un exercice seul de son superset redevient ordinaire, comme dans l'appli. Anciens exports (sans ces colonnes) : A et B reviennent en deux exercices ordinaires, séries renumérotées 1, 2…
- **Exercices, séries, paliers** renumérotés 1, 2… dans leur ordre ; un même palier en double est une erreur.
- **Doublons** : un entraînement identique à un entraînement de l'historique (date, libellé, exercices, séries, paliers, supersets) est ignoré, un pour un ; réimporter le même fichier n'ajoute rien. Un entraînement du même jour au contenu différent est importé.
- **Tout ou rien** : le fichier est vérifié en entier avant toute écriture ; la moindre erreur le refuse, dialogue « Import impossible », « Rien n'a été importé. », les 5 premières erreurs avec leur ligne (« Ligne 14 : répétitions manquantes ») puis « … et 3 autres erreurs. », « OK ». L'ajout se fait en une transaction (doublons de nouveau écartés : l'historique a pu changer).
- **Confirmation** (style 4b) : « Importer 12 entraînements ? », « Du 12 sept. au 26 oct. 2026. 3 déjà présents seront ignorés. », « Annuler » / « Importer » ; rien de nouveau : « Rien à importer », « Les 15 entraînements du fichier sont déjà dans l'historique. », « OK ». Après l'import, toast système « 12 entraînements importés » (l'accueil n'a pas de toast Nocturne).
- **Entraînements ajoutés** : terminés à l'instant de l'import ; `cree_le` décroissant dans l'ordre du fichier, pour que deux séances du même jour gardent leur ordre à l'accueil. L'entraînement en cours n'est pas touché.
- Pas de migration de base.

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

## Prochaines étapes

1. Installer JDK 21 et le SDK Android en ligne de commande dans WSL2.
2. Créer le squelette Gradle (`app.irontap`) et le thème Nocturne (couleurs, typographie Inter, formes).
3. Créer le modèle Room (Entrainement, Exercice, Serie, EtatChrono).
4. Écran Saisie série (écran principal) et chrono, puis Accueil, Nouvel entraînement, Détail, Édition de série, Export.
