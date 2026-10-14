package app.irontap.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.irontap.R
import app.irontap.donnees.Entrainement
import app.irontap.donnees.EntrainementComplet
import app.irontap.donnees.Exercice
import app.irontap.donnees.ExerciceAvecSeries
import app.irontap.donnees.Serie
import app.irontap.logique.Suggestion
import app.irontap.logique.decrireSerie
import app.irontap.logique.formaterDate
import app.irontap.ui.composants.BoutonContour
import app.irontap.ui.composants.BoutonPrincipal
import app.irontap.ui.composants.BoutonRond
import app.irontap.ui.composants.BoutonTexte
import app.irontap.ui.composants.ChampTexte
import app.irontap.ui.composants.DialogueNocturne
import app.irontap.ui.composants.Icone
import app.irontap.ui.composants.ListeSuggestions
import app.irontap.ui.composants.MessageErreur
import app.irontap.ui.export.ExportViewModel
import app.irontap.ui.export.FeuilleExport
import app.irontap.ui.theme.Couleurs
import app.irontap.ui.theme.Formes
import app.irontap.ui.theme.Typo
import kotlin.math.roundToInt

/** Ce que le détail déclenche ailleurs (navigation). */
interface NavigationDetail {
    fun retour()
    fun modifierEntrainement()
}

/** Gestes sur le contenu du détail, implémentés par [DetailViewModel] (via [EcranDetail]). */
interface ActionsDetail {
    fun modifierSerie(exercice: Exercice, serie: Serie)
    fun ajouterSerie(exercice: Exercice)
    fun supprimerSerie(exercice: Exercice, serie: Serie)
    fun renommer(exercice: Exercice)
}

/** Écran 4 : détail d'un entraînement passé, ses séries, et l'édition de série en feuille (écran 5). */
@Composable
fun EcranDetail(vm: DetailViewModel, navigation: NavigationDetail) {
    val etat by vm.etat.collectAsStateWithLifecycle()
    val panneaux by vm.panneaux.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val pret = etat as? EtatDetail.Pret
    if (pret == null) {
        Box(Modifier.fillMaxSize().background(Couleurs.Fond))
        if (etat == EtatDetail.Introuvable) LaunchedEffect(Unit) { navigation.retour() }
        return
    }
    var exporter by rememberSaveable { mutableStateOf(false) }
    ContenuDetail(
        complet = pret.complet,
        serieRevelee = panneaux.suppression?.serie?.id ?: panneaux.serieEnSuppression,
        navigation = navigation,
        onExporter = { exporter = true },
        onSupprimerEntrainement = vm::demanderSuppressionEntrainement,
        actions = object : ActionsDetail {
            override fun modifierSerie(exercice: Exercice, serie: Serie) = vm.ouvrirEdition(exercice, serie)
            override fun ajouterSerie(exercice: Exercice) = vm.ouvrirEdition(exercice, serie = null)
            override fun supprimerSerie(exercice: Exercice, serie: Serie) = vm.demanderSuppression(exercice, serie)
            override fun renommer(exercice: Exercice) = vm.ouvrirRenommage(exercice)
        },
    )
    FeuilleEdition(
        edition = panneaux.edition,
        poids = vm.poids,
        repetitions = vm.repetitions,
        erreurRepetitions = panneaux.erreurRepetitions,
        erreurEnregistrement = panneaux.erreurEnregistrement,
        actions = object : ActionsEdition {
            override fun ajouterAuPoids(valeur: Int) = vm.ajouterAuPoids(valeur)
            override fun ajouterAuxRepetitions(valeur: Int) = vm.ajouterAuxRepetitions(valeur)
            override fun enregistrer() = vm.enregistrerEdition()
            override fun supprimer(exercice: Exercice, serie: Serie) = vm.demanderSuppression(exercice, serie)
            override fun fermer() = vm.fermerEdition()
        },
    )
    panneaux.suppression?.let { suppression ->
        val series = pret.complet.exercices.firstOrNull { it.exercice.id == suppression.exercice.id }?.series.orEmpty()
        DialogueSuppressionSerie(
            suppression = suppression,
            nombreSeries = series.size,
            erreur = panneaux.erreurEnregistrement,
            onAnnuler = vm::annulerSuppression,
            onSupprimer = vm::confirmerSuppression,
        )
    }
    panneaux.renommage?.let {
        DialogueRenommage(
            nom = vm.nom,
            erreur = panneaux.erreurNom,
            erreurEnregistrement = panneaux.erreurEnregistrement,
            suggestions = suggestions,
            onChoisir = vm::choisir,
            onAnnuler = vm::fermerRenommage,
            onEnregistrer = vm::renommer,
        )
    }
    if (panneaux.suppressionEntrainement) {
        DialogueSuppressionEntrainement(
            entrainement = pret.complet.entrainement,
            erreur = panneaux.erreurEnregistrement,
            onAnnuler = vm::annulerSuppressionEntrainement,
            onSupprimer = vm::supprimerEntrainement,
        )
    }
    // Cet entraînement seul (spec §5).
    if (exporter) {
        FeuilleExport(vm = viewModel(factory = ExportViewModel.fabrique(pret.complet.entrainement.id)), onFermer = { exporter = false })
    }
}

/**
 * Contenu du détail, sans état propre hors du menu ⋮. [serieRevelee] : série dont la suppression attend
 * confirmation, laissée balayée derrière le dialogue (6b).
 */
@Composable
fun ContenuDetail(
    complet: EntrainementComplet,
    serieRevelee: Long?,
    navigation: NavigationDetail,
    onExporter: () -> Unit,
    onSupprimerEntrainement: () -> Unit,
    actions: ActionsDetail,
    menuOuvert: Boolean = false,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Couleurs.Fond)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        EnTete(complet, navigation, onExporter, onSupprimerEntrainement, menuOuvert)
        val basEcran = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp + basEcran),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (complet.exercices.isEmpty()) {
                item {
                    Text(
                        "Cet entraînement n’a plus aucune série.",
                        style = Typo.Corps,
                        color = Couleurs.Neutre400,
                        modifier = Modifier.padding(top = 8.dp, start = 2.dp),
                    )
                }
            }
            items(complet.exercices, key = { it.exercice.id }) { exercice ->
                BlocExercice(exercice, serieRevelee, actions)
            }
        }
    }
}

/** En-tête 68 dp : ←, date 19 / 500 au-dessus de « libellé · N exercices », menu ⋮. */
@Composable
private fun EnTete(
    complet: EntrainementComplet,
    navigation: NavigationDetail,
    onExporter: () -> Unit,
    onSupprimer: () -> Unit,
    menuOuvert: Boolean,
) {
    val entrainement = complet.entrainement
    var menu by rememberSaveable { mutableStateOf(menuOuvert) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BoutonIcone(R.drawable.ic_arrow_left, "Retour", navigation::retour)
        Column(Modifier.weight(1f)) {
            Text(formaterDate(entrainement.date), style = Typo.TitreDetail, color = Couleurs.Texte)
            val nombre = complet.exercices.size
            Text(
                text = listOfNotNull(entrainement.libelle, "$nombre exercice${if (nombre > 1) "s" else ""}").joinToString(" · "),
                style = Typo.Libelle,
                color = Couleurs.Neutre400,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            BoutonIcone(R.drawable.ic_dots_three_vertical, "Plus d’options") { menu = true }
            MenuEntrainement(
                ouvert = menu,
                onFermer = { menu = false },
                onModifier = navigation::modifierEntrainement,
                onExporter = onExporter,
                onSupprimer = onSupprimer,
            )
        }
    }
}

@Composable
private fun BoutonIcone(icone: Int, description: String, onClick: () -> Unit) {
    BoutonRond(
        onClick = onClick,
        taille = 48.dp,
        fond = Color.Transparent,
        fondAppuye = Couleurs.Neutre800,
        bordure = Color.Transparent,
        description = description,
    ) { Icone(icone, couleur = Couleurs.Neutre300) }
}

/** Menu ⋮ (6c) : neutre-800, rayon 14, élévation lg, 232 dp ; « Supprimer l'entraînement » en rouge après un filet. */
@Composable
private fun MenuEntrainement(
    ouvert: Boolean,
    onFermer: () -> Unit,
    onModifier: () -> Unit,
    onExporter: () -> Unit,
    onSupprimer: () -> Unit,
) {
    DropdownMenu(
        expanded = ouvert,
        onDismissRequest = onFermer,
        modifier = Modifier.width(232.dp),
        offset = DpOffset(x = (-4).dp, y = 0.dp),
        shape = Formes.Grande,
        containerColor = Couleurs.Neutre800,
        tonalElevation = 0.dp,
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, Couleurs.Neutre500),
    ) {
        ElementMenu(R.drawable.ic_pencil_simple, "Modifier date et libellé", Couleurs.Texte, Couleurs.Neutre300) {
            onFermer()
            onModifier()
        }
        ElementMenu(R.drawable.ic_export, "Exporter", Couleurs.Texte, Couleurs.Neutre300) {
            onFermer()
            onExporter()
        }
        Box(
            Modifier
                .padding(vertical = 4.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(Couleurs.Separateur),
        )
        ElementMenu(R.drawable.ic_trash, "Supprimer l’entraînement", Couleurs.Destructif, Couleurs.Destructif) {
            onFermer()
            onSupprimer()
        }
    }
}

@Composable
private fun ElementMenu(icone: Int, texte: String, couleur: Color, couleurIcone: Color, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(texte, style = Typo.Corps) },
        onClick = onClick,
        leadingIcon = { Icone(icone, taille = 18.dp, couleur = couleurIcone) },
        colors = MenuDefaults.itemColors(textColor = couleur),
        contentPadding = PaddingValues(horizontal = 16.dp),
    )
}

/** Un exercice : nom 18 / 500 + crayon (renommer), puis la carte de ses séries et « Ajouter une série ». */
@Composable
private fun BlocExercice(bloc: ExerciceAvecSeries, serieRevelee: Long?, actions: ActionsDetail) {
    val exercice = bloc.exercice
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .clip(Formes.Moyenne)
                .clickable(role = Role.Button, onClickLabel = "Renommer l’exercice") { actions.renommer(exercice) }
                .padding(start = 2.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(exercice.nom, style = Typo.TitreSection, color = Couleurs.Texte, modifier = Modifier.weight(1f, fill = false))
            Icone(R.drawable.ic_pencil_simple, taille = 15.dp, couleur = Couleurs.Neutre500)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(Formes.Grande)
                .background(Couleurs.Surface),
        ) {
            bloc.series.forEach { serie ->
                key(serie.id) {
                    LigneSerie(
                        serie = serie,
                        revelee = serie.id == serieRevelee,
                        onModifier = { actions.modifierSerie(exercice, serie) },
                        onSupprimer = { actions.supprimerSerie(exercice, serie) },
                    )
                }
            }
            LigneAjout { actions.ajouterSerie(exercice) }
        }
    }
}

/** Filet couleur `bg` en haut de chaque ligne de la carte. */
private fun Modifier.filetHaut() = drawBehind {
    drawLine(Couleurs.Fond, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx() * 2)
}

private enum class Glisse { Fermee, Ouverte }

/** Largeur de la zone « Supprimer » révélée par le balayage. */
private val LargeurSuppression = 96.dp

/**
 * Ligne de série de 54 dp : numéro, valeur 20 / 500, chevron. Balayée vers la gauche, elle laisse apparaître la zone
 * « Supprimer » (96 dp, rouge sombre) et demande confirmation ; elle reste ouverte tant que le dialogue
 * est affiché ([revelee]), puis se referme si la suppression est annulée.
 */
@Composable
private fun LigneSerie(serie: Serie, revelee: Boolean, onModifier: () -> Unit, onSupprimer: () -> Unit) {
    val largeur = with(LocalDensity.current) { LargeurSuppression.toPx() }
    val glisse = remember {
        AnchoredDraggableState(
            initialValue = if (revelee) Glisse.Ouverte else Glisse.Fermee,
            anchors = DraggableAnchors {
                Glisse.Fermee at 0f
                Glisse.Ouverte at -largeur
            },
        )
    }
    val demander by rememberUpdatedState(onSupprimer)
    LaunchedEffect(glisse) {
        snapshotFlow { glisse.settledValue }.collect { if (it == Glisse.Ouverte) demander() }
    }
    LaunchedEffect(revelee) {
        if (!revelee && glisse.settledValue == Glisse.Ouverte) glisse.animateTo(Glisse.Fermee)
    }
    val decalage = glisse.offset.takeUnless { it.isNaN() } ?: 0f
    val interactions = remember { MutableInteractionSource() }
    val appuye by interactions.collectIsPressedAsState()
    Box(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .filetHaut()
            .padding(top = 1.dp)
            .anchoredDraggable(
                state = glisse,
                orientation = Orientation.Horizontal,
                flingBehavior = AnchoredDraggableDefaults.flingBehavior(glisse, positionalThreshold = { it / 2 }),
            )
            .semantics {
                customActions = listOf(CustomAccessibilityAction("Supprimer la série") { onSupprimer(); true })
            },
    ) {
        if (decalage < 0f) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(LargeurSuppression)
                    .background(Couleurs.FondSuppression)
                    .clickable(role = Role.Button, onClick = onSupprimer),
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icone(R.drawable.ic_trash, taille = 18.dp, couleur = Couleurs.TexteSuppression)
                Text("Supprimer", style = Typo.Petit, color = Couleurs.TexteSuppression)
            }
        }
        // La ligne se raccourcit à mesure que la zone « Supprimer » apparaît : sa valeur reste lisible (6b).
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .layout { mesurable, contraintes ->
                    val largeurLigne = (contraintes.maxWidth + decalage.roundToInt()).coerceAtLeast(0)
                    val ligne = mesurable.measure(contraintes.copy(minWidth = largeurLigne, maxWidth = largeurLigne))
                    layout(contraintes.maxWidth, ligne.height) { ligne.place(0, 0) }
                }
                .background(if (decalage < 0f || appuye) Couleurs.Neutre800 else Couleurs.Surface)
                .clickable(interactions, indication = null, role = Role.Button, onClick = onModifier)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("${serie.ordre}", style = Typo.Corps, color = Couleurs.Neutre500, modifier = Modifier.width(18.dp))
            Text(decrireSerie(serie.poidsKg, serie.repetitions), style = Typo.TitreEntete, color = Couleurs.Texte, modifier = Modifier.weight(1f))
            if (decalage == 0f) Icone(R.drawable.ic_caret_right, taille = 16.dp, couleur = Couleurs.Neutre600)
        }
    }
}

/** Dernière ligne de la carte, 48 dp : « + Ajouter une série » en 15 / 500 accent-300. */
@Composable
private fun LigneAjout(onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val appuye by interactions.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .filetHaut()
            .background(if (appuye) Couleurs.Neutre800 else Color.Transparent)
            .clickable(interactions, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icone(R.drawable.ic_plus, taille = 16.dp, couleur = Couleurs.Accent300)
        Text("Ajouter une série", style = Typo.Action, color = Couleurs.Accent300)
    }
}

// --- Dialogues ---

/** Bouton de confirmation destructif des dialogues : contour erreur, texte rouge clair, 48 dp. */
@Composable
private fun BoutonSupprimer(onClick: () -> Unit) {
    BoutonContour(
        texte = "Supprimer",
        onClick = onClick,
        hauteur = 48.dp,
        forme = Formes.Moyenne,
        style = Typo.Action,
        couleurTexte = Couleurs.DestructifDialogue,
        margeHorizontale = 20.dp,
        bordure = Couleurs.ErreurBordure,
        fondAppuye = Couleurs.FondSuppression,
    )
}

@Composable
private fun BoutonsDialogue(contenu: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) { contenu() }
}

/** 6b : « Supprimer la série N ? », exercice et valeur, conséquence sur la numérotation. */
@Composable
internal fun DialogueSuppressionSerie(
    suppression: SuppressionSerie,
    nombreSeries: Int,
    erreur: Boolean,
    onAnnuler: () -> Unit,
    onSupprimer: () -> Unit,
) {
    val serie = suppression.serie
    DialogueNocturne(titre = "Supprimer la série ${serie.ordre} ?", onFermer = onAnnuler) {
        Text(
            text = buildAnnotatedString {
                append("${suppression.exercice.nom} · ")
                withStyle(SpanStyle(color = Couleurs.Texte)) { append(suppression.description) }
                append(".")
                when {
                    nombreSeries <= 1 -> append(" C’est sa seule série : l’exercice sera retiré de l’entraînement.")
                    serie.ordre < nombreSeries -> append(" Les séries suivantes seront renumérotées.")
                }
            },
            style = Typo.CorpsDialogue,
            color = Couleurs.Neutre300,
        )
        if (erreur) MessageErreur(ErreurSuppression)
        BoutonsDialogue {
            BoutonTexte("Annuler", onClick = onAnnuler)
            BoutonSupprimer(onSupprimer)
        }
    }
}

private const val ErreurSuppression = "Suppression impossible : stockage indisponible"

/** Menu ⋮ > Supprimer l'entraînement : confirmation, définitive. */
@Composable
internal fun DialogueSuppressionEntrainement(entrainement: Entrainement, erreur: Boolean, onAnnuler: () -> Unit, onSupprimer: () -> Unit) {
    DialogueNocturne(titre = "Supprimer l’entraînement ?", onFermer = onAnnuler) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Couleurs.Texte)) {
                    append(listOfNotNull(formaterDate(entrainement.date), entrainement.libelle).joinToString(" · "))
                }
                append(" et toutes ses séries seront supprimés. Cette action est définitive.")
            },
            style = Typo.CorpsDialogue,
            color = Couleurs.Neutre300,
        )
        if (erreur) MessageErreur(ErreurSuppression)
        BoutonsDialogue {
            BoutonTexte("Annuler", onClick = onAnnuler)
            BoutonSupprimer(onSupprimer)
        }
    }
}

/** Renommer un exercice : champ avec autocomplétion, mêmes règles qu'en saisie (spec §4.3.2). */
@Composable
internal fun DialogueRenommage(
    nom: TextFieldState,
    erreur: Boolean,
    erreurEnregistrement: Boolean,
    suggestions: List<Suggestion>,
    onChoisir: (Suggestion) -> Unit,
    onAnnuler: () -> Unit,
    onEnregistrer: () -> Unit,
) {
    DialogueNocturne(titre = "Renommer l’exercice", onFermer = onAnnuler) {
        ChampTexte(etat = nom, placeholder = "ex. Tirage vertical", hauteur = 52.dp, longueurMax = 60, erreur = erreur)
        if (erreur) MessageErreur("Indique le nom de l’exercice")
        if (suggestions.isNotEmpty()) ListeSuggestions(suggestions, onChoisir, Modifier.fillMaxWidth())
        if (erreurEnregistrement) MessageErreur("Enregistrement impossible : stockage indisponible")
        BoutonsDialogue {
            BoutonTexte("Annuler", onClick = onAnnuler)
            BoutonPrincipal(
                texte = "Enregistrer",
                onClick = onEnregistrer,
                hauteur = 48.dp,
                forme = Formes.Moyenne,
                style = Typo.Action,
                margeHorizontale = 22.dp,
            )
        }
    }
}
