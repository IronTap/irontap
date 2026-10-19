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
import app.irontap.ui.composants.EtiquetteSuperset
import app.irontap.ui.composants.BadgeLettre
import app.irontap.donnees.RoleSuperset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import app.irontap.donnees.SerieAvecPaliers
import app.irontap.logique.Suggestion
import app.irontap.logique.decrirePalier
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
import app.irontap.ui.composants.PaliersEmpiles
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
    fun modifierSerie(exercice: Exercice, serie: SerieAvecPaliers)
    fun ajouterSerie(exercice: Exercice)
    fun supprimerSerie(exercice: Exercice, serie: SerieAvecPaliers)
    fun renommer(exercice: Exercice)

    /** Superset (H) : appui sur « — », la série manquante de [exercice] pour ce [tour]. */
    fun ajouterSerieAuTour(exercice: Exercice, tour: Int)
    fun ajouterTour(bloc: BlocDetail.Superset)
    fun modifierTour(bloc: BlocDetail.Superset, tour: TourSuperset)
    fun supprimerTour(bloc: BlocDetail.Superset, tour: TourSuperset)
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
        serieRevelee = panneaux.suppression?.serie?.serie?.id
            ?: panneaux.suppressionTour?.tour?.let { (it.a ?: it.b)?.serie?.id }
            ?: panneaux.serieEnSuppression,
        navigation = navigation,
        onExporter = { exporter = true },
        onSupprimerEntrainement = vm::demanderSuppressionEntrainement,
        actions = object : ActionsDetail {
            override fun modifierSerie(exercice: Exercice, serie: SerieAvecPaliers) = vm.ouvrirEdition(exercice, serie)
            override fun ajouterSerie(exercice: Exercice) = vm.ouvrirEdition(exercice, serie = null)
            override fun supprimerSerie(exercice: Exercice, serie: SerieAvecPaliers) = vm.demanderSuppression(exercice, serie)
            override fun renommer(exercice: Exercice) = vm.ouvrirRenommage(exercice)
            override fun ajouterSerieAuTour(exercice: Exercice, tour: Int) = vm.ouvrirEdition(exercice, serie = null, tour = tour)
            override fun ajouterTour(bloc: BlocDetail.Superset) = vm.ouvrirAjoutTour(bloc)
            override fun modifierTour(bloc: BlocDetail.Superset, tour: TourSuperset) = vm.ouvrirModificationTour(bloc, tour)
            override fun supprimerTour(bloc: BlocDetail.Superset, tour: TourSuperset) = vm.demanderSuppressionTour(bloc, tour)
        },
    )
    FeuilleTour(
        edition = panneaux.tour,
        champs = vm.champsTour,
        paliersEnErreur = panneaux.paliersEnErreur.keys,
        erreurEnregistrement = panneaux.erreurEnregistrement,
        enregistrable = vm.tourEnregistrable(),
        onEnregistrer = vm::enregistrerTour,
        onSupprimer = { edition ->
            val bloc = blocsDetail(pret.complet.exercices).filterIsInstance<BlocDetail.Superset>().firstOrNull { it.a.exercice.id == edition.a.id }
            val tour = bloc?.tours?.firstOrNull { it.numero == edition.tour }
            if (bloc != null && tour != null) vm.demanderSuppressionTour(bloc, tour)
        },
        onFermer = vm::fermerTour,
    )
    panneaux.suppressionTour?.let { suppression ->
        DialogueSuppressionTour(
            suppression = suppression,
            erreur = panneaux.erreurEnregistrement,
            onAnnuler = vm::annulerSuppressionTour,
            onSupprimer = vm::confirmerSuppressionTour,
        )
    }
    FeuilleEdition(
        edition = panneaux.edition,
        paliers = vm.paliers,
        paliersEnErreur = panneaux.paliersEnErreur.keys,
        erreurEnregistrement = panneaux.erreurEnregistrement,
        actions = object : ActionsEdition {
            override fun ajouterAuPoids(valeur: Int) {
                vm.ajouterAuPoids(valeur)
            }
            override fun ajouterAuxRepetitions(valeur: Int) {
                vm.ajouterAuxRepetitions(valeur)
            }
            override fun ajouterPalier() = vm.ajouterPalier()
            override fun retirerPalier(cle: Long) = vm.retirerPalier(cle)
            override fun enregistrer() = vm.enregistrerEdition()
            override fun supprimer(exercice: Exercice, serie: SerieAvecPaliers) = vm.demanderSuppression(exercice, serie)
            override fun fermer() = vm.fermerEdition()
        },
    )
    // La feuille d'un tour dont le superset vient d'être défait (série supprimée) se ferme.
    val blocs = blocsDetail(pret.complet.exercices)
    panneaux.tour?.let { tour ->
        if (blocs.none { it is BlocDetail.Superset && it.a.exercice.id == tour.a.id }) LaunchedEffect(tour) { vm.fermerTour() }
    }
    panneaux.suppression?.let { suppression ->
        val series = pret.complet.exercices.firstOrNull { it.exercice.id == suppression.exercice.id }?.series.orEmpty()
        DialogueSuppressionSerie(
            suppression = suppression,
            nombreSeries = series.size,
            renumerotation = renumerotationApresSuppression(blocs, suppression.exercice.id, suppression.serie.serie.ordre),
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
            items(blocsDetail(complet.exercices), key = { it.cle }) { bloc ->
                when (bloc) {
                    is BlocDetail.Simple -> BlocExercice(bloc.exercice, serieRevelee, actions)
                    is BlocDetail.Superset -> BlocSuperset(bloc, serieRevelee, actions)
                }
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
                key(serie.serie.id) {
                    LigneSerie(
                        serie = serie,
                        revelee = serie.serie.id == serieRevelee,
                        onModifier = { actions.modifierSerie(exercice, serie) },
                        onSupprimer = { actions.supprimerSerie(exercice, serie) },
                    )
                }
            }
            LigneAjout { actions.ajouterSerie(exercice) }
        }
    }
}

/**
 * Superset (maquette H) : étiquette « 🔗 Superset », puis une carte au filet accent de 2 dp à gauche ; en-tête A | B
 * (badges, noms ; appui = renommer), une ligne par tour, « + Ajouter un tour ».
 */
@Composable
private fun BlocSuperset(bloc: BlocDetail.Superset, serieRevelee: Long?, actions: ActionsDetail) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EtiquetteSuperset("Superset", Modifier.padding(start = 2.dp), detail = true)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(Formes.Grande)
                .background(Couleurs.Surface)
                .drawBehind { drawRect(Couleurs.Accent, size = Size(2.dp.toPx(), size.height)) }
                .padding(start = 2.dp),
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(18.dp))
                RoleSuperset.entries.forEach { role ->
                    val exercice = bloc.exercice(role).exercice
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(Formes.Moyenne)
                            .clickable(role = Role.Button, onClickLabel = "Renommer l’exercice $role") { actions.renommer(exercice) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        BadgeLettre(role, taille = 18.dp, style = Typo.BadgeOngletPetit)
                        Text(exercice.nom, style = Typo.TitreCarte, color = Couleurs.Texte, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            bloc.tours.forEach { tour ->
                val revelee = (tour.a ?: tour.b)?.serie?.id
                key(revelee) { LigneTour(bloc, tour, revelee != null && revelee == serieRevelee, actions) }
            }
            LigneAjout("Ajouter un tour") { actions.ajouterTour(bloc) }
        }
    }
}

/**
 * Un tour : numéro (appui = « Modifier le tour N »), série A, série B en 18 / 500 (appui = édition, écran 7 ou 7·D),
 * « — » neutre-600 s'il en manque une (appui = l'ajouter à ce tour). Balayé : « Supprimer le tour N ? ».
 */
@Composable
private fun LigneTour(bloc: BlocDetail.Superset, tour: TourSuperset, revelee: Boolean, actions: ActionsDetail) {
    LigneBalayable(revelee, "Supprimer le tour ${tour.numero}", { actions.supprimerTour(bloc, tour) }, hauteurMin = 52.dp, onClick = null) {
        NumeroLigne(
            tour.numero,
            Modifier.clickable(role = Role.Button, onClickLabel = "Modifier le tour ${tour.numero}") { actions.modifierTour(bloc, tour) },
        )
        RoleSuperset.entries.forEach { role ->
            val exercice = bloc.exercice(role).exercice
            val serie = tour.serie(role)
            Box(
                Modifier
                    .weight(1f)
                    .clickable(role = Role.Button, onClickLabel = if (serie == null) "Ajouter la série $role" else "Modifier la série $role") {
                        if (serie == null) actions.ajouterSerieAuTour(exercice, tour.numero) else actions.modifierSerie(exercice, serie)
                    },
            ) {
                if (serie == null) {
                    Text("—", style = Typo.TitreSection, color = Couleurs.Neutre600)
                } else {
                    ValeurSerie(serie, Typo.TitreSection)
                }
            }
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
 * Ligne de série, 54 dp au moins : numéro (calé en haut), valeur 20 / 500, chevron ; les paliers d'une dégressive
 * s'empilent sous la valeur (6a), la ligne grandit. Balayée, elle demande la suppression de la série entière.
 */
@Composable
private fun LigneSerie(serie: SerieAvecPaliers, revelee: Boolean, onModifier: () -> Unit, onSupprimer: () -> Unit) {
    LigneBalayable(revelee, "Supprimer la série", onSupprimer, hauteurMin = 54.dp, onClick = onModifier) { decalee ->
        NumeroLigne(serie.serie.ordre)
        ValeurSerie(serie, Typo.TitreEntete, Modifier.weight(1f))
        if (!decalee) Icone(R.drawable.ic_caret_right, taille = 16.dp, couleur = Couleurs.Neutre600)
    }
}

/** Numéro d'une série ou d'un tour : 15 sp neutre-500, 18 dp, calé en haut (paliers empilés dessous). */
@Composable
private fun RowScope.NumeroLigne(numero: Int, modifier: Modifier = Modifier) {
    Text(
        "$numero",
        style = Typo.Corps,
        color = Couleurs.Neutre500,
        modifier = modifier
            .align(Alignment.Top)
            .padding(top = 4.dp)
            .width(18.dp),
    )
}

/** Valeur d'une série en [style], paliers dégressifs empilés dessous. */
@Composable
private fun ValeurSerie(serie: SerieAvecPaliers, style: TextStyle, modifier: Modifier = Modifier) {
    val textes = serie.paliers.map { decrirePalier(it.poidsKg, it.repetitions) }
    PaliersEmpiles(textes.firstOrNull().orEmpty(), textes.drop(1), style, modifier)
}

/**
 * Ligne de la carte balayable vers la gauche : elle laisse apparaître la zone « Supprimer » (96 dp, rouge sombre)
 * et appelle [onSupprimer] (confirmation) ; elle reste ouverte tant que le dialogue est affiché ([revelee]), puis
 * se referme si la suppression est annulée. [onClick] : appui sur toute la ligne (série) ; `null` si le contenu a
 * ses propres zones d'appui (tour de superset). Le contenu reçoit `true` tant que la ligne est décalée.
 */
@Composable
private fun LigneBalayable(
    revelee: Boolean,
    actionSuppression: String,
    onSupprimer: () -> Unit,
    hauteurMin: Dp,
    onClick: (() -> Unit)?,
    contenu: @Composable RowScope.(decalee: Boolean) -> Unit,
) {
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
            .filetHaut()
            .padding(top = 1.dp)
            .anchoredDraggable(
                state = glisse,
                orientation = Orientation.Horizontal,
                flingBehavior = AnchoredDraggableDefaults.flingBehavior(glisse, positionalThreshold = { it / 2 }),
            )
            .semantics {
                customActions = listOf(CustomAccessibilityAction(actionSuppression) { onSupprimer(); true })
            },
    ) {
        if (decalage < 0f) {
            // Hauteur de la ligne, qui grandit avec les paliers d'une dégressive.
            Box(Modifier.matchParentSize()) {
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
        }
        // La ligne se raccourcit à mesure que la zone « Supprimer » apparaît : sa valeur reste lisible (6b).
        Row(
            modifier = Modifier
                // Hauteur avec le filet du haut.
                .heightIn(min = hauteurMin - 1.dp)
                .layout { mesurable, contraintes ->
                    val largeurLigne = (contraintes.maxWidth + decalage.roundToInt()).coerceAtLeast(0)
                    val ligne = mesurable.measure(contraintes.copy(minWidth = largeurLigne, maxWidth = largeurLigne))
                    layout(contraintes.maxWidth, ligne.height) { ligne.place(0, 0) }
                }
                .background(if (decalage < 0f || appuye) Couleurs.Neutre800 else Couleurs.Surface)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(interactions, indication = null, role = Role.Button, onClick = onClick)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (onClick != null) 14.dp else 12.dp),
        ) { contenu(decalage < 0f) }
    }
}

/** Dernière ligne de la carte, 48 dp : « + Ajouter une série » (ou [texte]) en 15 / 500 accent-300. */
@Composable
private fun LigneAjout(texte: String = "Ajouter une série", onClick: () -> Unit) {
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
        Text(texte, style = Typo.Action, color = Couleurs.Accent300)
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
    /** Les séries suivantes changent de numéro (dans un superset : seulement si le tour devient vide). */
    renumerotation: Boolean = suppression.serie.serie.ordre < nombreSeries,
    onAnnuler: () -> Unit,
    onSupprimer: () -> Unit,
) {
    val serie = suppression.serie.serie
    DialogueNocturne(titre = "Supprimer la série ${serie.ordre} ?", onFermer = onAnnuler) {
        Text(
            text = buildAnnotatedString {
                append("${suppression.exercice.nom} · ")
                withStyle(SpanStyle(color = Couleurs.Texte)) { append(suppression.description) }
                append(".")
                when {
                    nombreSeries <= 1 -> append(" C’est sa seule série : l’exercice sera retiré de l’entraînement.")
                    renumerotation -> append(" Les séries suivantes seront renumérotées.")
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

/**
 * « Supprimer le tour N ? » (H) : séries de A et de B rappelées, puis « Supprimer A », « Supprimer B »,
 * « Supprimer les deux » (le choix sans objet est masqué) et « Annuler ». Un tour devenu vide disparaît.
 */
@Composable
internal fun DialogueSuppressionTour(
    suppression: SuppressionTour,
    erreur: Boolean,
    onAnnuler: () -> Unit,
    onSupprimer: (Set<RoleSuperset>) -> Unit,
) {
    val tour = suppression.tour
    val presents = RoleSuperset.entries.filter { tour.serie(it) != null }
    DialogueNocturne(titre = "Supprimer le tour ${tour.numero} ?", onFermer = onAnnuler) {
        Text(
            text = buildAnnotatedString {
                RoleSuperset.entries.forEachIndexed { index, role ->
                    if (index > 0) append("\n")
                    val exercice = if (role == RoleSuperset.A) suppression.a else suppression.b
                    append("$role · ${exercice.nom} · ")
                    withStyle(SpanStyle(color = Couleurs.Texte)) { append(tour.serie(role)?.let { decrireSerie(it.valeurs) } ?: "—") }
                }
                append("\nUn tour sans série disparaît, les suivants sont renumérotés.")
            },
            style = Typo.CorpsDialogue,
            color = Couleurs.Neutre300,
        )
        if (erreur) MessageErreur(ErreurSuppression)
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (presents.size == 2) {
                RoleSuperset.entries.forEach { role -> BoutonSupprimerLarge("Supprimer $role") { onSupprimer(setOf(role)) } }
                BoutonSupprimerLarge("Supprimer les deux") { onSupprimer(presents.toSet()) }
            } else {
                presents.forEach { role -> BoutonSupprimerLarge("Supprimer $role") { onSupprimer(setOf(role)) } }
            }
            BoutonTexte("Annuler", onClick = onAnnuler, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Choix destructif empilé (« Supprimer A ») : contour erreur, texte rouge clair, 48 dp, pleine largeur. */
@Composable
private fun BoutonSupprimerLarge(texte: String, onClick: () -> Unit) {
    BoutonContour(
        texte = texte,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        hauteur = 48.dp,
        forme = Formes.Moyenne,
        style = Typo.Action,
        couleurTexte = Couleurs.DestructifDialogue,
        bordure = Couleurs.ErreurBordure,
        fondAppuye = Couleurs.FondSuppression,
    )
}

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
