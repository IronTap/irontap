package app.irontap.ui.saisie

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.irontap.R
import app.irontap.donnees.EntrainementDao.Suite
import app.irontap.donnees.EtatChrono
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
import app.irontap.ui.composants.DialogueNocturne
import app.irontap.ui.composants.Icone
import app.irontap.ui.composants.LibelleChamp
import app.irontap.ui.composants.MessageErreur
import app.irontap.ui.composants.RangeeAjouts
import app.irontap.ui.composants.cadreChamp
import app.irontap.ui.theme.Couleurs
import app.irontap.ui.theme.Elevation
import app.irontap.ui.theme.Formes
import app.irontap.ui.theme.Typo
import app.irontap.ui.theme.elevation
import app.irontap.ui.theme.hauteurLigne
import kotlinx.coroutines.delay

/** Actions de l'écran, implémentées par [SaisieViewModel]. */
interface ActionsSaisie {
    fun basculerChrono()
    fun relancerChrono()
    fun choisir(suggestion: Suggestion)
    fun ajouterAuPoids(valeur: Int)
    fun ajouterAuxRepetitions(valeur: Int)
    fun enregistrer(suite: Suite)
    fun demanderFin()
    fun annulerFin()
    fun terminer(avecSerie: Boolean)
}

private val AjoutsPoids = listOf(1, 5, 10, 20)
private val AjoutsRepetitions = listOf(1, 2, 5, 10)

/** Hauteur du bloc d'actions du bas (72 + 8 + 48) : le toast s'affiche juste au-dessus. */
private val HauteurActions = 128.dp

/** Écran 3 : saisie des séries de l'entraînement en cours. [onQuitter] est appelé quand il n'y en a plus. */
@Composable
fun EcranSaisie(onQuitter: () -> Unit, vm: SaisieViewModel = viewModel(factory = SaisieViewModel.Fabrique)) {
    val etat by vm.etat.collectAsStateWithLifecycle()
    val formulaire by vm.formulaire.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    when (val e = etat) {
        is EtatSaisie.EnCours -> ContenuSaisie(
            etat = e,
            formulaire = formulaire,
            suggestions = suggestions,
            nom = vm.nom,
            poids = vm.poids,
            repetitions = vm.repetitions,
            realtime = vm::realtime,
            actions = vm,
        )
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
    nom: TextFieldState,
    poids: TextFieldState,
    repetitions: TextFieldState,
    realtime: () -> Long,
    actions: ActionsSaisie,
) {
    val focusManager = LocalFocusManager.current
    val densite = LocalDensity.current
    // Position du champ nom dans l'écran : la liste de suggestions se superpose au contenu qui suit.
    var positionRacine by remember { mutableStateOf(Offset.Zero) }
    var ancreNom by remember { mutableStateOf<Pair<Offset, Int>?>(null) }
    var focusNom by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Couleurs.Fond)
            .onGloballyPositioned { positionRacine = it.positionInRoot() },
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
                // Au moins la hauteur de l'écran, pour pousser les actions en bas ; défile si le clavier est ouvert.
                Column(Modifier.heightIn(min = hauteurVisible)) {
                    EnTete(etat)
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                    ) {
                        BandeauChrono(etat.chrono, realtime, actions)
                        Spacer(Modifier.height(14.dp))
                        LibelleChamp("Nom de l’exercice")
                        if (etat.nomVerrouille != null) {
                            NomVerrouille(etat.nomVerrouille)
                        } else {
                            ChampNom(
                                etat = nom,
                                erreur = formulaire.erreurNom,
                                focus = focusNom,
                                modifier = Modifier
                                    .onFocusChanged { focusNom = it.isFocused }
                                    .onGloballyPositioned { ancreNom = it.positionInRoot() to it.size.width },
                            )
                            if (formulaire.erreurNom) {
                                MessageErreur("Indique le nom de l’exercice", Modifier.padding(top = 5.dp))
                            }
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
                        Spacer(Modifier.weight(1f))
                        Actions(actions)
                    }
                }
            }
        }

        val ancre = ancreNom
        if (focusNom && ancre != null && etat.nomVerrouille == null && suggestions.isNotEmpty()) {
            val (position, largeur) = ancre
            ListeSuggestions(
                suggestions = suggestions,
                onChoisir = actions::choisir,
                modifier = Modifier
                    .offset {
                        val decalage = position - positionRacine
                        IntOffset(decalage.x.toInt(), decalage.y.toInt() + (52 + 6).dp.roundToPx())
                    }
                    .width(with(densite) { largeur.toDp() }),
            )
        }

        Toast(
            message = formulaire.toast,
            erreur = formulaire.toastErreur,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = HauteurActions),
        )
    }

    when (val dialogue = formulaire.dialogue) {
        null -> Unit
        is DialogueFin.AvecSerie -> DialogueFinAvecSerie(dialogue.serie.description, actions)
        DialogueFin.Simple -> DialogueFinSimple(actions)
    }
}

/** En-tête de 52 dp : libellé (ou date) au-dessus de « Exercice N · Série M ». */
@Composable
private fun EnTete(etat: EtatSaisie.EnCours) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = etat.entrainement.libelle ?: formaterDate(etat.entrainement.date),
            style = Typo.Petit,
            color = Couleurs.Neutre400,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "Exercice ${etat.numeroExercice} · Série ${etat.numeroSerie}",
            style = Typo.EnteteSaisie,
            color = Couleurs.Neutre200,
        )
    }
}

// --- Chrono ---

/**
 * Temps écoulé du chrono, rafraîchi à chaque changement de seconde tant qu'il tourne et que l'écran
 * est visible. Au retour au premier plan (téléphone rallumé…), la valeur est recalculée aussitôt.
 */
@Composable
private fun ecouleMs(chrono: EtatChrono?, realtime: () -> Long): Long {
    var maintenant by remember { mutableLongStateOf(realtime()) }
    val cycleDeVie = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(chrono, cycleDeVie) {
        cycleDeVie.repeatOnLifecycle(Lifecycle.State.STARTED) {
            maintenant = realtime()
            while (chrono?.etat == EtatChrono.Etat.EnCours) {
                delay(1_000 - chrono.ecouleMs(maintenant) % 1_000)
                maintenant = realtime()
            }
        }
    }
    return chrono?.ecouleMs(maintenant) ?: 0
}

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
                text = formaterChrono(ecouleMs(chrono, realtime)),
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
                // Décalage optique du triangle, comme dans la maquette.
                Icone(R.drawable.ic_play, Modifier.offset(x = 1.5.dp), couleur = Couleurs.Accent300)
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

private val FiltreNom = InputTransformation.maxLength(60)

@Composable
private fun ChampNom(etat: TextFieldState, erreur: Boolean, focus: Boolean, modifier: Modifier) {
    BasicTextField(
        state = etat,
        modifier = modifier.fillMaxWidth(),
        inputTransformation = FiltreNom,
        textStyle = Typo.Champ.copy(color = Couleurs.Texte),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        lineLimits = TextFieldLineLimits.SingleLine,
        cursorBrush = SolidColor(Couleurs.Accent),
        decorator = { champ ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .cadreChamp(erreur, focus)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (etat.text.isEmpty()) Text("ex. Tirage vertical", style = Typo.Champ, color = Couleurs.Neutre600)
                champ()
            }
        },
    )
}

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

/** Menu d'autocomplétion : surface, élévation md, lignes de 52 dp ; la partie correspondante en 600 accent-200. */
@Composable
private fun ListeSuggestions(suggestions: List<Suggestion>, onChoisir: (Suggestion) -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .elevation(Elevation.Md, Formes.Grande)
            .background(Couleurs.Surface, Formes.Grande)
            .clip(Formes.Grande)
            .padding(vertical = 4.dp),
    ) {
        suggestions.forEach { suggestion ->
            val interactions = remember { MutableInteractionSource() }
            val appuye by interactions.collectIsPressedAsState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(if (appuye) Couleurs.Neutre800 else Color.Transparent)
                    .clickable(interactions, indication = null, role = Role.Button) { onChoisir(suggestion) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = buildAnnotatedString {
                        val nom = suggestion.nom
                        append(nom, 0, suggestion.debut)
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Couleurs.Accent200)) {
                            append(nom, suggestion.debut, suggestion.fin)
                        }
                        append(nom, suggestion.fin, nom.length)
                    },
                    style = Typo.Suggestion,
                    color = Couleurs.Texte,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// --- Actions, toast, dialogues ---

@Composable
private fun Actions(actions: ActionsSaisie) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BoutonPrincipal(
            texte = "Nouvelle\nsérie",
            onClick = { actions.enregistrer(Suite.NouvelleSerie) },
            modifier = Modifier.weight(1f),
            hauteur = 72.dp,
        )
        BoutonSecondaire(
            texte = "Nouvel\nexercice",
            onClick = { actions.enregistrer(Suite.NouvelExercice) },
            modifier = Modifier.weight(1f),
            hauteur = 72.dp,
            style = Typo.GrandeAction,
            couleurTexte = Couleurs.Texte,
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

/** Toast : neutre-800, rayon 8, élévation md, ✓ accent-300 et texte 14 neutre-100. */
@Composable
private fun Toast(message: String?, erreur: Boolean, modifier: Modifier) {
    // Garde le dernier message pendant le fondu de sortie.
    var dernier by remember { mutableStateOf("") }
    if (message != null) dernier = message
    AnimatedVisibility(visible = message != null, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .elevation(Elevation.Md, Formes.Moyenne)
                .background(Couleurs.Neutre800, Formes.Moyenne)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (erreur) {
                Icone(R.drawable.ic_warning_circle, taille = 18.dp, couleur = Couleurs.ErreurTexte)
            } else {
                Icone(R.drawable.ic_check, taille = 18.dp, couleur = Couleurs.Accent300)
            }
            Text(dernier, style = Typo.Secondaire, color = Couleurs.Neutre100)
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
