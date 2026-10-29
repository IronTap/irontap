package app.irontap.ui.saisie

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Constraints
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.irontap.donnees.RoleSuperset
import app.irontap.ui.composants.BadgeLettre
import app.irontap.ui.composants.EtiquetteSuperset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.irontap.R
import app.irontap.donnees.EntrainementDao.Suite
import app.irontap.donnees.EtatChrono
import app.irontap.logique.HistoriqueExercice
import app.irontap.logique.SeanceHistorique
import app.irontap.logique.Suggestion
import app.irontap.logique.formaterChrono
import app.irontap.logique.formaterDate
import app.irontap.ui.composants.BoutonContour
import app.irontap.ui.composants.BoutonPrincipal
import app.irontap.ui.composants.BoutonRond
import app.irontap.ui.composants.BoutonSecondaire
import app.irontap.ui.composants.BoutonTexte
import app.irontap.ui.composants.ChampPoids
import app.irontap.ui.composants.ChampRepetitions
import app.irontap.ui.composants.ChampTexte
import app.irontap.ui.composants.DialogueNocturne
import app.irontap.ui.composants.FenetreDialogue
import app.irontap.ui.composants.Icone
import app.irontap.ui.composants.LibelleChamp
import app.irontap.ui.composants.ListeSuggestions
import app.irontap.ui.composants.MessageErreur
import app.irontap.ui.composants.PaliersEmpiles
import app.irontap.ui.composants.RangeeAjouts
import app.irontap.ui.composants.cadreChamp
import app.irontap.ui.composants.tempsEcouleMs
import app.irontap.ui.theme.Couleurs
import app.irontap.ui.theme.Elevation
import app.irontap.ui.theme.Formes
import app.irontap.ui.theme.Typo
import app.irontap.ui.theme.elevation
import app.irontap.ui.theme.hauteurLigne

/** Actions de l'écran, implémentées par [SaisieViewModel]. */
interface ActionsSaisie {
    fun basculerChrono()
    fun relancerChrono()
    fun choisir(suggestion: Suggestion)
    fun ajouterAuPoids(valeur: Int)
    fun ajouterAuxRepetitions(valeur: Int)
    fun enregistrer(suite: Suite)
    fun degressive()
    fun demanderFin()
    fun annulerFin()
    fun terminer(avecSerie: Boolean)
    fun ouvrirHistorique()
    fun fermerHistorique()
    fun commencerSuperset()
    fun basculer(role: RoleSuperset)

    /** « Passer à l'exercice suivant ? » / « Terminer le superset ? » : « Nouvel exercice ». */
    fun confirmerPassage()
    fun annulerPassage()

    /** « Annuler » du toast « Série N enregistrée ». */
    fun annulerSerie()

    /** Écran en arrière-plan : ce qui doit survivre à un kill est mémorisé (superset). */
    fun memoriserSaisie()
}

/** Onglets d'un superset à l'écran : l'onglet actif et le nom saisi dans chaque onglet (libellé de l'inactif). */
data class OngletsSuperset(val actif: RoleSuperset, val nomsSaisis: Map<RoleSuperset, String>)

private val AjoutsPoids = listOf(1, 2, 5, 10, 20)
private val AjoutsRepetitions = listOf(1, 2, 5, 10)

/** Hauteur du bloc d'actions du bas (72 + 8 + 48) : le toast s'affiche juste au-dessus. */
private val HauteurActions = 128.dp

/** Écran 3 : saisie des séries de l'entraînement en cours. [onQuitter] est appelé quand il n'y en a plus. */
@Composable
fun EcranSaisie(onQuitter: () -> Unit, vm: SaisieViewModel = viewModel(factory = SaisieViewModel.Fabrique)) {
    val etat by vm.etat.collectAsStateWithLifecycle()
    val formulaire by vm.formulaire.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val historiqueDisponible by vm.historiqueDisponible.collectAsStateWithLifecycle()
    // Appli en arrière-plan : elle peut être tuée, un superset en cours est mémorisé.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.memoriserSaisie() }
    when (val e = etat) {
        is EtatSaisie.EnCours -> {
            val champs = vm.champs(vm.ongletActif)
            ContenuSaisie(
                etat = e,
                formulaire = formulaire,
                suggestions = suggestions,
                historiqueDisponible = historiqueDisponible,
                nom = champs.nom,
                poids = champs.poids,
                repetitions = champs.repetitions,
                realtime = vm::realtime,
                actions = vm,
                onglets = e.superset?.let {
                    OngletsSuperset(vm.ongletActif, RoleSuperset.entries.associateWith { role -> vm.champs(role).nom.text.toString() })
                },
            )
            PrePermissionNotifications(chronoEnCours = e.chrono?.etat == EtatChrono.Etat.EnCours)
        }
        EtatSaisie.Chargement, EtatSaisie.Aucun -> {
            Box(Modifier.fillMaxSize().background(Couleurs.Fond))
            if (e == EtatSaisie.Aucun) LaunchedEffect(Unit) { onQuitter() }
        }
    }
}

@Composable
fun ContenuSaisie(
    etat: EtatSaisie.EnCours,
    formulaire: Formulaire,
    suggestions: List<Suggestion>,
    historiqueDisponible: Boolean,
    nom: TextFieldState,
    poids: TextFieldState,
    repetitions: TextFieldState,
    realtime: () -> Long,
    actions: ActionsSaisie,
    /** Superset en cours : onglets A / B à la place du champ du nom (S3) ; [nom], [poids], [repetitions] sont ceux de l'onglet actif. */
    onglets: OngletsSuperset? = null,
) {
    val focusManager = LocalFocusManager.current
    val superset = etat.etatSuperset?.takeIf { onglets != null }
    val actif = onglets?.actif ?: RoleSuperset.A
    val nomModifiable = if (superset != null) superset.nomVerrouille(actif) == null else etat.nomVerrouille == null
    val densite = LocalDensity.current
    // Position du champ nom dans l'écran : la liste de suggestions se superpose au contenu qui suit.
    var positionRacine by remember { mutableStateOf(Offset.Zero) }
    var hauteurRacine by remember { mutableIntStateOf(0) }
    var ancreNom by remember { mutableStateOf<Pair<Offset, Int>?>(null) }
    var focusNom by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Couleurs.Fond)
            .onGloballyPositioned {
                positionRacine = it.positionInRoot()
                hauteurRacine = it.size.height
            },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val hauteurVisible = maxHeight
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    // Un appui dans le vide ferme le clavier.
                    .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } },
            ) {
                // Au moins la hauteur visible, pour pousser les actions en bas. Seul l'espace vide est extensible :
                // quand le clavier réduit la hauteur visible, le contenu garde sa taille et défile.
                Column(Modifier.heightIn(min = hauteurVisible)) {
                    EnTete(etat, superset, actif)
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        BandeauChrono(etat.chrono, realtime, actions)
                        Spacer(Modifier.height(14.dp))
                        LigneLibelleNom(if (superset != null) "Exercices du superset" else "Nom de l’exercice") {
                            when {
                                superset != null && historiqueDisponible -> PastilleHistorique(actif, actions::ouvrirHistorique)
                                superset == null && etat.supersetPossible -> PastilleSuperset(actions::commencerSuperset)
                            }
                        }
                        if (superset != null && onglets != null) {
                            Onglets(
                                superset = superset,
                                onglets = onglets,
                                nom = nom,
                                onBasculer = actions::basculer,
                                modifier = Modifier.onGloballyPositioned { ancreNom = it.positionInRoot() to it.size.width },
                                modifierChamp = Modifier.onFocusChanged { focusNom = it.isFocused },
                            )
                            if (formulaire.erreurNom) {
                                MessageErreur("Indique le nom de l’exercice", Modifier.padding(top = 5.dp))
                            }
                        } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                if (etat.nomVerrouille != null) {
                                    NomVerrouille(etat.nomVerrouille)
                                } else {
                                    ChampTexte(
                                        etat = nom,
                                        placeholder = "ex. Tirage vertical",
                                        hauteur = 52.dp,
                                        longueurMax = 60,
                                        erreur = formulaire.erreurNom,
                                        imeAction = ImeAction.Next,
                                        modifier = Modifier
                                            .onFocusChanged { focusNom = it.isFocused }
                                            .onGloballyPositioned { ancreNom = it.positionInRoot() to it.size.width },
                                    )
                                    if (formulaire.erreurNom) {
                                        MessageErreur("Indique le nom de l’exercice", Modifier.padding(top = 5.dp))
                                    }
                                }
                            }
                            if (historiqueDisponible) BoutonHistorique(actions::ouvrirHistorique)
                        }
                        Spacer(Modifier.height(14.dp))
                        LibelleChamp("Poids")
                        ChampPoids(poids)
                        RangeeAjouts(AjoutsPoids, actions::ajouterAuPoids)
                        Spacer(Modifier.height(12.dp))
                        LibelleChamp("Nombre de répétitions")
                        ChampRepetitions(repetitions, erreur = formulaire.erreurRepetitions)
                        RangeeAjouts(AjoutsRepetitions, actions::ajouterAuxRepetitions)
                        if (formulaire.erreurRepetitions) {
                            MessageErreur("Indique le nombre de répétitions", Modifier.padding(top = 6.dp))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Spacer(Modifier.weight(1f))
                    Actions(actions, Modifier.padding(horizontal = 16.dp))
                }
            }
        }

        val ancre = ancreNom
        if (focusNom && ancre != null && nomModifiable && suggestions.isNotEmpty()) {
            val (position, largeur) = ancre
            val decalage = position - positionRacine
            val haut = decalage.y.toInt() + with(densite) { (52 + 6).dp.roundToPx() }
            // La liste s'arrête au-dessus du clavier (et de la barre de navigation) et défile au-delà.
            val bas = hauteurRacine - WindowInsets.safeDrawing.getBottom(densite) - with(densite) { 8.dp.roundToPx() }
            ListeSuggestions(
                suggestions = suggestions,
                onChoisir = actions::choisir,
                modifier = Modifier
                    .offset { IntOffset(decalage.x.toInt(), haut) }
                    .width(with(densite) { largeur.toDp() }),
                hauteurMax = with(densite) { (bas - haut).coerceAtLeast(60.dp.roundToPx()).toDp() },
            )
        }

        Toast(
            message = formulaire.toast,
            erreur = formulaire.toastErreur,
            onAnnuler = if (formulaire.toastAnnulable) actions::annulerSerie else null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = HauteurActions),
        )
    }

    when (val dialogue = formulaire.dialogue) {
        null -> Unit
        is DialogueFin.AvecSerie -> DialogueFinAvecSerie(dialogue.serie.description, actions)
        is DialogueFin.SupersetComplet -> DialogueFinAvecSerie(dialogue.description, actions)
        is DialogueFin.SupersetIncomplet -> DialogueFinSupersetIncomplet(dialogue, actions)
        DialogueFin.Simple -> DialogueFinSimple(actions)
    }
    formulaire.passage?.let { DialoguePassage(it, actions) }
    formulaire.historique?.let { DialogueHistorique(it, actions::fermerHistorique) }
}

/**
 * En-tête de 52 dp : libellé (ou date) au-dessus de « Exercice N · Série M ». En dégressive (3·D A2, A3) :
 * paliers déjà gardés en accent-300 (« 50 kg × 10 ↓ … »), puis badge « ↓ dégressive » après le numéro.
 * En superset (S3) : « 🔗 Superset · saisie de A » en accent-300, sauf dégressive en cours sur l'onglet actif.
 */
@Composable
private fun EnTete(etat: EtatSaisie.EnCours, superset: EtatSuperset?, actif: RoleSuperset) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
    ) {
        val chaine = if (superset != null) superset.chaineDegressive(actif) else etat.chaineDegressive
        if (superset != null && chaine == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icone(R.drawable.ic_link_simple_small, taille = 12.dp, couleur = Couleurs.Accent300)
                Text("Superset · saisie de $actif", style = Typo.Petit, color = Couleurs.Accent300, maxLines = 1)
            }
        } else Text(
            text = chaine ?: etat.entrainement.libelle ?: formaterDate(etat.entrainement.date),
            style = Typo.Petit,
            color = if (chaine != null) Couleurs.Accent300 else Couleurs.Neutre400,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Exercice ${etat.numeroExercice} · Série ${etat.numeroSerie}",
                style = Typo.EnteteSaisie,
                color = Couleurs.Neutre200,
            )
            if (chaine != null) BadgeDegressive()
        }
    }
}

/** Badge « ↓ dégressive » : fond accent-800, texte accent-100 11 sp, rayon 4, padding 1/7/1/5. */
@Composable
private fun BadgeDegressive() {
    Row(
        modifier = Modifier
            .background(Couleurs.Accent800, Formes.Petite)
            .padding(start = 5.dp, top = 1.dp, end = 7.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icone(R.drawable.ic_arrow_down_small, taille = 11.dp, couleur = Couleurs.Accent100)
        Text("dégressive", style = Typo.Badge, color = Couleurs.Accent100)
    }
}

// --- Chrono ---

@Composable
private fun BandeauChrono(chrono: EtatChrono?, realtime: () -> Long, actions: ActionsSaisie) {
    val etat = chrono?.etat ?: EtatChrono.Etat.Arrete
    val couleurTemps = when (etat) {
        EtatChrono.Etat.EnCours -> Couleurs.Accent100
        EtatChrono.Etat.Pause -> Couleurs.Neutre400
        EtatChrono.Etat.Arrete -> Couleurs.Neutre200
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(Couleurs.Surface, Formes.Grande)
            .padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when (etat) {
                    EtatChrono.Etat.EnCours -> {
                        Box(Modifier.size(7.dp).background(Couleurs.Accent400, CircleShape))
                        Text("REPOS · EN COURS", style = Typo.SurTitre, color = Couleurs.Accent300)
                    }
                    EtatChrono.Etat.Pause -> {
                        Icone(R.drawable.ic_pause, taille = 10.dp, couleur = Couleurs.Neutre400)
                        Text("EN PAUSE", style = Typo.SurTitre, color = Couleurs.Neutre400)
                    }
                    EtatChrono.Etat.Arrete -> Text("REPOS", style = Typo.SurTitre, color = Couleurs.Neutre500)
                }
            }
            Text(
                text = formaterChrono(tempsEcouleMs(chrono, realtime)),
                style = Typo.Chrono,
                color = couleurTemps,
                maxLines = 1,
                modifier = Modifier.hauteurLigne(40.dp),
            )
        }
        val enCours = etat == EtatChrono.Etat.EnCours
        BoutonRond(
            onClick = actions::basculerChrono,
            taille = 56.dp,
            fond = Couleurs.Accent900,
            fondAppuye = Couleurs.Accent800,
            bordure = Couleurs.Accent,
            description = if (enCours) "Pause" else "Démarrer",
        ) {
            if (enCours) {
                Icone(R.drawable.ic_pause, couleur = Couleurs.Accent300)
            } else {
                // Pas de décalage (contrairement à la maquette) : le tracé Phosphor intègre déjà le centrage optique.
                Icone(R.drawable.ic_play, couleur = Couleurs.Accent300)
            }
        }
        BoutonRond(
            onClick = actions::relancerChrono,
            taille = 56.dp,
            fond = Couleurs.Fond,
            fondAppuye = Couleurs.Neutre900,
            bordure = Couleurs.Separateur,
            description = "Relancer",
        ) { Icone(R.drawable.ic_arrow_counter_clockwise, couleur = Couleurs.Neutre200) }
    }
}

// --- Nom de l'exercice ---

/** Nom verrouillé après la première série : sans fond, contour pointillé, cadenas. */
@Composable
private fun NomVerrouille(nom: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .contourPointille(Couleurs.Neutre700)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = nom,
            style = Typo.Champ,
            color = Couleurs.Neutre300,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icone(R.drawable.ic_lock, taille = 18.dp, couleur = Couleurs.Neutre500, description = "Verrouillé")
    }
}

// --- Superset (S1–S4) ---

/**
 * Ligne du libellé du nom : libellé à gauche, pastille éventuelle à droite, alignés en bas. La pastille (36 dp)
 * déborde de 8 dp au-dessus et 2 dp au-dessous, comme la maquette : la ligne garde la hauteur du libellé.
 */
@Composable
private fun LigneLibelleNom(texte: String, pastille: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(texte, style = Typo.Libelle, color = Couleurs.Neutre300, maxLines = 1, modifier = Modifier.weight(1f))
        pastille()
    }
}

/**
 * Pastille de 36 dp (rayon 20, filet séparateur, 13 / 500, icône 14 dp, padding 12), dans une zone tactile de
 * 48 dp. Elle occupe 26 dp dans la ligne (débord de 8 dp en haut et 2 dp en bas).
 */
@Composable
private fun Pastille(texte: String, icone: Int, couleur: Color, fondAppuye: Color, description: String, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val appuye by interactions.collectIsPressedAsState()
    val forme = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .layout { mesurable, contraintes ->
                val zone = mesurable.measure(contraintes.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                val hauteur = 26.dp.roundToPx()
                // Pastille visible de -8 à 28 dp : zone tactile de 48 dp centrée sur elle.
                layout(zone.width, hauteur) { zone.place(0, -(8 + 6).dp.roundToPx()) }
            }
            .height(48.dp)
            .clickable(interactions, indication = null, role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(36.dp)
                .clip(forme)
                .background(if (appuye) fondAppuye else Color.Transparent)
                .border(1.dp, Couleurs.Separateur, forme)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icone(icone, taille = 14.dp, couleur = couleur)
            Text(texte, style = Typo.Pastille, color = couleur, maxLines = 1)
        }
    }
}

/** S1 : « 🔗 Superset », tant que l'exercice courant n'a ni série ni palier gardé. */
@Composable
private fun PastilleSuperset(onClick: () -> Unit) =
    Pastille("Superset", R.drawable.ic_link_simple, Couleurs.Neutre300, Couleurs.Texte.copy(alpha = 0.07f), "Faire un superset", onClick)

/** S3 : « 🕘 Historique A » (ou B), à la place de la pastille Superset, pour l'exercice de l'onglet actif. */
@Composable
private fun PastilleHistorique(actif: RoleSuperset, onClick: () -> Unit) =
    Pastille("Historique $actif", R.drawable.ic_clock_counter_clockwise, Couleurs.Accent300, Couleurs.Accent800, "Historique de l’exercice $actif", onClick)

/**
 * Onglets A / B (S3), grille de 2 colonnes (gap 6, 52 dp). Actif sans série encore faite : champ du nom
 * ([nom], [modifierChamp]) ; actif verrouillé : fond accent-900 ; inactif : contour pointillé, appui = bascule.
 */
@Composable
private fun Onglets(
    superset: EtatSuperset,
    onglets: OngletsSuperset,
    nom: TextFieldState,
    onBasculer: (RoleSuperset) -> Unit,
    modifier: Modifier,
    modifierChamp: Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RoleSuperset.entries.forEach { role ->
            val cellule = Modifier.weight(1f)
            val verrouille = superset.nomVerrouille(role)
            when {
                role != onglets.actif -> OngletInactif(
                    role = role,
                    nom = verrouille ?: onglets.nomsSaisis[role].orEmpty().trim(),
                    onClick = { onBasculer(role) },
                    modifier = cellule,
                )
                verrouille != null -> OngletVerrouille(role, verrouille, cellule)
                else -> OngletModifiable(role, nom, cellule.then(modifierChamp))
            }
        }
    }
}

private fun nomParDefaut(role: RoleSuperset) = "Exercice $role"

@Composable
private fun OngletModifiable(role: RoleSuperset, nom: TextFieldState, modifier: Modifier) {
    val filtre = remember { InputTransformation.maxLength(60) }
    BasicTextField(
        state = nom,
        modifier = modifier.semantics { contentDescription = "Nom de l’exercice $role" },
        inputTransformation = filtre,
        textStyle = Typo.Onglet.copy(color = Couleurs.Texte),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        lineLimits = TextFieldLineLimits.SingleLine,
        cursorBrush = SolidColor(Couleurs.Accent),
        decorator = { champ ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(Couleurs.Surface, Formes.Grande)
                    .border(2.dp, Couleurs.Accent, Formes.Grande)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BadgeLettre(role)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (nom.text.isEmpty()) Text(nomParDefaut(role), style = Typo.Onglet, color = Couleurs.Neutre600, maxLines = 1)
                    champ()
                }
            }
        },
    )
}

@Composable
private fun OngletVerrouille(role: RoleSuperset, nom: String, modifier: Modifier) {
    Row(
        modifier = modifier
            .height(52.dp)
            .background(Couleurs.Accent900, Formes.Grande)
            .border(2.dp, Couleurs.Accent, Formes.Grande)
            .padding(horizontal = 10.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Exercice $role : $nom, verrouillé" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BadgeLettre(role)
        Text(nom, style = Typo.Onglet, color = Couleurs.Accent100, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun OngletInactif(role: RoleSuperset, nom: String, onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(Formes.Grande)
            .contourPointille(Couleurs.Neutre700)
            .clickable(role = Role.Tab, onClickLabel = "Passer à l’exercice $role", onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BadgeLettre(role, fond = Couleurs.Neutre800, texte = Couleurs.Neutre300)
        Text(nom.ifEmpty { nomParDefaut(role) }, style = Typo.Onglet, color = Couleurs.Neutre400, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 3f : carré de 52 dp accolé au nom, horloge accent-300 ; appuyé : fond accent-800 et contour accent. */
@Composable
private fun BoutonHistorique(onClick: () -> Unit) {
    BoutonRond(
        onClick = onClick,
        taille = 52.dp,
        fond = Couleurs.Surface,
        fondAppuye = Couleurs.Accent800,
        bordure = Couleurs.Separateur,
        bordureAppuyee = Couleurs.Accent,
        forme = Formes.Grande,
        description = "Historique de l’exercice",
    ) { Icone(R.drawable.ic_clock_counter_clockwise, couleur = Couleurs.Accent300) }
}

/** Contour pointillé de 1 dp, rayon 14 (équivalent de `border: 1px dashed`). */
private fun Modifier.contourPointille(couleur: Color) = drawBehind {
    val trait = 1.dp.toPx()
    val tiret = 3.dp.toPx()
    drawRoundRect(
        color = couleur,
        topLeft = Offset(trait / 2, trait / 2),
        size = Size(size.width - trait, size.height - trait),
        cornerRadius = CornerRadius(14.dp.toPx() - trait / 2),
        style = Stroke(width = trait, pathEffect = PathEffect.dashPathEffect(floatArrayOf(tiret, tiret))),
    )
}

// --- Actions, toast, dialogues ---

/** Grandes actions sur 3 colonnes (3·D A1) : « Nouvelle série », « Dégressive » au centre, « Nouvel exercice ». */
@Composable
private fun Actions(actions: ActionsSaisie, modifier: Modifier) {
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BoutonPrincipal(
                texte = "Nouvelle\nsérie",
                onClick = { actions.enregistrer(Suite.NouvelleSerie) },
                modifier = Modifier.weight(1f),
                hauteur = 72.dp,
                style = Typo.GrandeActionCompacte,
                margeHorizontale = 6.dp,
            )
            BoutonSecondaire(
                texte = "Dégressive",
                onClick = actions::degressive,
                modifier = Modifier.weight(1f),
                icone = R.drawable.ic_arrow_down,
                hauteur = 72.dp,
                style = Typo.GrandeActionCompacte,
                couleurTexte = Couleurs.Texte,
                margeHorizontale = 6.dp,
                iconeAuDessus = true,
                couleurIcone = Couleurs.Accent300,
            )
            BoutonSecondaire(
                texte = "Nouvel\nexercice",
                onClick = { actions.enregistrer(Suite.NouvelExercice) },
                modifier = Modifier.weight(1f),
                hauteur = 72.dp,
                style = Typo.GrandeActionCompacte,
                couleurTexte = Couleurs.Texte,
                margeHorizontale = 6.dp,
            )
        }
        BoutonTexte(
            texte = "Terminer l’entraînement",
            onClick = actions::demanderFin,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            icone = R.drawable.ic_flag,
            couleur = Couleurs.Neutre400,
            style = Typo.Discret,
        )
    }
}

/**
 * Toast : neutre-800, rayon 8, élévation md, ✓ accent-300 et texte 14 neutre-100. [onAnnuler] : action « Annuler »
 * en accent-300 à droite (zone tactile de 48 dp), le toast garde alors la hauteur de l'action.
 */
@Composable
private fun Toast(message: String?, erreur: Boolean, onAnnuler: (() -> Unit)?, modifier: Modifier) {
    // Garde le dernier message, et son action, pendant le fondu de sortie.
    var dernier by remember { mutableStateOf("") }
    var action by remember { mutableStateOf<(() -> Unit)?>(null) }
    if (message != null) {
        dernier = message
        action = onAnnuler
    }
    AnimatedVisibility(visible = message != null, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        val annuler = action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .elevation(Elevation.Md, Formes.Moyenne)
                .background(Couleurs.Neutre800, Formes.Moyenne)
                .padding(start = 14.dp, end = if (annuler != null) 2.dp else 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (erreur) {
                Icone(R.drawable.ic_warning_circle, taille = 18.dp, couleur = Couleurs.ErreurTexte)
            } else {
                Icone(R.drawable.ic_check, taille = 18.dp, couleur = Couleurs.Accent300)
            }
            Text(
                text = dernier,
                style = Typo.Secondaire,
                color = Couleurs.Neutre100,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
            )
            if (annuler != null) BoutonTexte("Annuler", onClick = annuler, margeHorizontale = 12.dp)
        }
    }
}

/** 4a : la série en cours est remplie. */
@Composable
private fun DialogueFinAvecSerie(description: String, actions: ActionsSaisie) {
    DialogueNocturne(titre = "Terminer l’entraînement ?", onFermer = actions::annulerFin) {
        Text(
            text = buildAnnotatedString {
                append("La série en cours ")
                withStyle(SpanStyle(color = Couleurs.Texte)) { append("($description)") }
                append(" n’est pas encore enregistrée.")
            },
            style = Typo.CorpsDialogue,
            color = Couleurs.Neutre300,
        )
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BoutonPrincipal(
                texte = "Enregistrer la série et terminer",
                onClick = { actions.terminer(avecSerie = true) },
                modifier = Modifier.fillMaxWidth(),
                hauteur = 52.dp,
                forme = Formes.Moyenne,
                style = Typo.Action,
                margeHorizontale = 6.dp,
            )
            BoutonContour(
                texte = "Terminer sans enregistrer cette série",
                onClick = { actions.terminer(avecSerie = false) },
                modifier = Modifier.fillMaxWidth(),
                forme = Formes.Moyenne,
                style = Typo.Action,
                couleurTexte = Couleurs.Texte,
                margeHorizontale = 6.dp,
            )
            BoutonTexte("Annuler", onClick = actions::annulerFin, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** 4b : rien à enregistrer. */
@Composable
private fun DialogueFinSimple(actions: ActionsSaisie) {
    DialogueNocturne(titre = "Terminer l’entraînement ?", onFermer = actions::annulerFin) {
        Text(
            text = "Le chrono sera arrêté. Tu retrouveras la séance dans l’historique.",
            style = Typo.CorpsDialogue,
            color = Couleurs.Neutre300,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            BoutonTexte("Annuler", onClick = actions::annulerFin)
            BoutonPrincipal(
                texte = "Terminer",
                onClick = { actions.terminer(avecSerie = false) },
                hauteur = 48.dp,
                forme = Formes.Moyenne,
                style = Typo.Action,
                margeHorizontale = 22.dp,
            )
        }
    }
}

/**
 * « Nouvel exercice » sans rien de saisi, au style de 4b : « Passer à l'exercice suivant ? » (exercice verrouillé)
 * ou « Terminer le superset ? » (deux onglets vides), « Annuler » / « Nouvel exercice ».
 */
@Composable
private fun DialoguePassage(passage: PassageExercice, actions: ActionsSaisie) {
    fun garde(series: Int) = if (series == 1) "sa série" else "ses $series séries"
    val (titre, texte) = when (passage) {
        is PassageExercice.Simple -> "Passer à l’exercice suivant ?" to buildAnnotatedString {
            append("Les champs sont vides : la série ${passage.series + 1} ne sera pas enregistrée. ")
            withStyle(SpanStyle(color = Couleurs.Texte)) { append(passage.nom) }
            append(" garde ${garde(passage.series)}.")
        }
        is PassageExercice.Superset -> "Terminer le superset ?" to buildAnnotatedString {
            if (passage.tours == 0) {
                append("Les deux onglets sont vides : le superset se termine sans aucune série.")
            } else {
                append("Les deux onglets sont vides : la série ${passage.tours + 1} ne sera pas enregistrée. Le superset garde ${garde(passage.tours)}.")
            }
        }
    }
    DialogueNocturne(titre = titre, onFermer = actions::annulerPassage) {
        Text(text = texte, style = Typo.CorpsDialogue, color = Couleurs.Neutre300)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            BoutonTexte("Annuler", onClick = actions::annulerPassage)
            BoutonPrincipal(
                texte = "Nouvel exercice",
                onClick = actions::confirmerPassage,
                hauteur = 48.dp,
                forme = Formes.Moyenne,
                style = Typo.Action,
                margeHorizontale = 22.dp,
            )
        }
    }
}

/**
 * S5 : superset dont un seul onglet est saisi. « Enregistrer A seul et terminer » (principal), « Terminer sans
 * enregistrer cette série » (contour), « Annuler » : mêmes styles que 4a.
 */
@Composable
private fun DialogueFinSupersetIncomplet(dialogue: DialogueFin.SupersetIncomplet, actions: ActionsSaisie) {
    val saisi = dialogue.role
    DialogueNocturne(titre = "Terminer l’entraînement ?", onFermer = actions::annulerFin) {
        Text(
            text = buildAnnotatedString {
                append("La série de superset en cours est incomplète : ")
                withStyle(SpanStyle(color = Couleurs.Texte)) { append("$saisi (${dialogue.description})") }
                append(" est saisi, ")
                withStyle(SpanStyle(color = Couleurs.Texte)) { append(saisi.autre.name) }
                append(" ne l’est pas.")
            },
            style = Typo.CorpsDialogue,
            color = Couleurs.Neutre300,
        )
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BoutonPrincipal(
                texte = "Enregistrer $saisi seul et terminer",
                onClick = { actions.terminer(avecSerie = true) },
                modifier = Modifier.fillMaxWidth(),
                hauteur = 52.dp,
                forme = Formes.Moyenne,
                style = Typo.Action,
                margeHorizontale = 6.dp,
            )
            BoutonContour(
                texte = "Terminer sans enregistrer cette série",
                onClick = { actions.terminer(avecSerie = false) },
                modifier = Modifier.fillMaxWidth(),
                forme = Formes.Moyenne,
                style = Typo.Action,
                couleurTexte = Couleurs.Texte,
                margeHorizontale = 6.dp,
            )
            BoutonTexte("Annuler", onClick = actions::annulerFin, modifier = Modifier.fillMaxWidth())
        }
    }
}

// --- Historique de l'exercice ---

/**
 * 3g : séances précédentes de l'exercice, la plus récente en premier. En-tête fixe (nom et ✕), corps défilant,
 * 640 dp au plus. Fermeture par ✕, appui à l'extérieur ou Retour.
 */
@Composable
private fun DialogueHistorique(historique: HistoriqueExercice, onFermer: () -> Unit) {
    FenetreDialogue(onFermer = onFermer, forme = Formes.Grande) {
        Column(Modifier.heightIn(max = 640.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = historique.nom,
                    style = Typo.TitreDialogue,
                    color = Couleurs.Texte,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                BoutonRond(
                    onClick = onFermer,
                    taille = 48.dp,
                    fond = Color.Transparent,
                    fondAppuye = Couleurs.Neutre800,
                    bordure = Color.Transparent,
                    description = "Fermer",
                ) { Icone(R.drawable.ic_x, couleur = Couleurs.Neutre300) }
            }
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 8.dp),
            ) {
                items(historique.seances) { SectionSeance(it) }
            }
        }
    }
}

/**
 * Une séance : filet en haut, « Libellé — JJ/MM/AAAA » en 13 / 500 neutre-400, puis une ligne 19 / 500 par série,
 * paliers dégressifs empilés dessous.
 */
@Composable
private fun SectionSeance(seance: SeanceHistorique) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val trait = 1.dp.toPx()
                drawLine(Couleurs.Separateur, Offset(0f, trait / 2), Offset(size.width, trait / 2), trait)
            }
            .padding(top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(seance.entete, style = Typo.EnteteSeance, color = Couleurs.Neutre400)
        seance.superset?.let { EtiquetteSuperset("En superset avec $it") }
        seance.series.forEach { PaliersEmpiles(it.principal, it.degressifs, Typo.TitreDetail, retrait = 2.dp) }
    }
}
