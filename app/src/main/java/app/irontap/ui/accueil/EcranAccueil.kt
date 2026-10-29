package app.irontap.ui.accueil

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.irontap.R
import app.irontap.donnees.Entrainement
import app.irontap.logique.formaterChrono
import app.irontap.logique.formaterDate
import app.irontap.logique.formaterDateCourte
import app.irontap.ui.composants.BoutonContour
import app.irontap.ui.composants.BoutonPrincipal
import app.irontap.ui.composants.BoutonRond
import app.irontap.ui.composants.BoutonSecondaire
import app.irontap.ui.composants.BoutonTexte
import app.irontap.ui.composants.DialogueNocturne
import app.irontap.ui.composants.EtiquetteLibelle
import app.irontap.ui.composants.Icone
import app.irontap.ui.composants.tempsEcouleMs
import app.irontap.ui.export.ExportViewModel
import app.irontap.ui.export.FeuilleExport
import app.irontap.ui.importation.DialoguesImport
import app.irontap.ui.importation.ImportViewModel
import app.irontap.ui.saisie.EtatSaisie
import app.irontap.ui.theme.Couleurs
import app.irontap.ui.theme.Formes
import app.irontap.ui.theme.Typo
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Ce que l'accueil déclenche ailleurs (navigation). */
interface ActionsAccueil {
    fun nouvelEntrainement()
    fun reprendre()
    fun ouvrir(entrainement: Entrainement)

    /** ⓘ de l'en-tête : écran À propos (spec §4.6). */
    fun aPropos()
}

/** Hauteur du dégradé qui sépare la liste du bouton ancré en bas. */
private val HauteurDegrade = 28.dp

/** Écran 1 : historique, bandeau de l'entraînement en cours, « Nouvel entraînement » en bas. */
@Composable
fun EcranAccueil(actions: ActionsAccueil, vm: AccueilViewModel = viewModel(factory = AccueilViewModel.Fabrique)) {
    val etat by vm.etat.collectAsStateWithLifecycle()
    val pret = etat as? EtatAccueil.Pret
    if (pret == null) {
        Box(Modifier.fillMaxSize().background(Couleurs.Fond))
        return
    }
    // Entraînement en cours dont la fin est proposée : le dialogue ne revient pas pour un autre entraînement
    // (celui-ci terminé depuis la saisie, puis un nouveau lancé).
    var confirmer by rememberSaveable { mutableStateOf<Long?>(null) }
    var exporter by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Import d'un export CSV (DECISIONS.md) : sélecteur de document système, aucune permission.
    val importVm: ImportViewModel = viewModel(factory = ImportViewModel.Fabrique)
    val etatImport by importVm.etat.collectAsStateWithLifecycle()
    val choisirFichier = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(importVm::analyser) }
    val importer = {
        try {
            choisirFichier.launch(ImportViewModel.TypesFichier)
        } catch (e: ActivityNotFoundException) {
            importVm.signaler("Aucune appli ne permet de choisir un fichier.")
        }
    }
    ContenuAccueil(
        etat = pret,
        realtime = vm::realtime,
        onExporter = { exporter = true },
        onImporter = importer,
        actions = object : ActionsAccueil by actions {
            // Un seul entraînement en cours à la fois (spec §4.1) : on propose d'abord de terminer l'autre.
            override fun nouvelEntrainement() {
                val enCours = pret.enCours
                if (enCours != null) confirmer = enCours.entrainement.id else actions.nouvelEntrainement()
            }
        },
    )
    val enCours = pret.enCours
    if (enCours != null && confirmer == enCours.entrainement.id) {
        DialogueTerminerEnCours(
            avecSeries = enCours.exercices.isNotEmpty(),
            onAnnuler = { confirmer = null },
            onTerminer = {
                confirmer = null
                scope.launch {
                    vm.terminerEnCours().join()
                    actions.nouvelEntrainement()
                }
            },
        )
    }
    // Tout l'historique (spec §5).
    if (exporter) {
        FeuilleExport(vm = viewModel(factory = ExportViewModel.fabrique(id = null)), onFermer = { exporter = false }, onImporter = importer)
    }
    DialoguesImport(etatImport, onConfirmer = importVm::confirmer, onFermer = importVm::fermer)
}

@Composable
fun ContenuAccueil(
    etat: EtatAccueil.Pret,
    realtime: () -> Long,
    onExporter: () -> Unit,
    actions: ActionsAccueil,
    onImporter: () -> Unit = {},
) {
    val vide = etat.historique.isEmpty() && etat.enCours == null
    Column(
        Modifier
            .fillMaxSize()
            .background(Couleurs.Fond)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        EnTete(exportActif = etat.historique.isNotEmpty(), onAPropos = actions::aPropos, onExporter = onExporter)
        Box(Modifier.weight(1f)) {
            // Place réservée sous le contenu pour le bouton ancré en bas (et son dégradé).
            val basBouton = HauteurDegrade + (if (etat.enCours != null) 56.dp else 64.dp) +
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            if (vide) {
                EtatVide(onImporter, Modifier.align(Alignment.BottomStart).padding(bottom = basBouton - HauteurDegrade + 40.dp))
            } else {
                Liste(etat, realtime, actions, PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = basBouton))
            }
            BoutonNouvel(
                secondaire = etat.enCours != null,
                onClick = actions::nouvelEntrainement,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** En-tête 64 dp : titre à gauche, ⓘ (À propos) puis Exporter (ronds 48) à droite, Exporter à 35 % sans historique. */
@Composable
private fun EnTete(exportActif: Boolean, onAPropos: () -> Unit, onExporter: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Entraînements", style = Typo.TitreAccueil, color = Couleurs.Texte, modifier = Modifier.weight(1f))
        BoutonRond(
            onClick = onAPropos,
            taille = 48.dp,
            fond = Color.Transparent,
            fondAppuye = Couleurs.Neutre800,
            bordure = Color.Transparent,
            description = "À propos",
        ) { Icone(R.drawable.ic_info, couleur = Couleurs.Neutre300) }
        BoutonRond(
            onClick = onExporter,
            taille = 48.dp,
            fond = Color.Transparent,
            fondAppuye = Couleurs.Neutre800,
            bordure = Color.Transparent,
            description = "Exporter",
            actif = exportActif,
        ) { Icone(R.drawable.ic_export, couleur = Couleurs.Neutre300) }
    }
}

@Composable
private fun Liste(etat: EtatAccueil.Pret, realtime: () -> Long, actions: ActionsAccueil, marges: PaddingValues) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = marges) {
        etat.enCours?.let { enCours ->
            item(key = "en-cours") {
                BandeauEnCours(enCours, realtime, actions::reprendre, Modifier.padding(bottom = 12.dp))
            }
        }
        if (etat.historique.isNotEmpty()) {
            item(key = "historique") {
                Text(
                    text = "HISTORIQUE · ${etat.historique.size}",
                    style = Typo.SurTitre,
                    color = Couleurs.Neutre500,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                )
            }
        }
        items(etat.historique, key = { it.id }) { entrainement ->
            CarteEntrainement(entrainement, { actions.ouvrir(entrainement) }, Modifier.padding(bottom = 8.dp))
        }
    }
}

/** Carte d'historique : date 17 / 500, étiquette du libellé dessous s'il existe, chevron. */
@Composable
private fun CarteEntrainement(entrainement: Entrainement, onClick: () -> Unit, modifier: Modifier) {
    val interactions = remember { MutableInteractionSource() }
    val appuye by interactions.collectIsPressedAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Formes.Grande)
            .background(if (appuye) Couleurs.Neutre800 else Couleurs.Surface)
            .clickable(interactions, indication = null, role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, top = 13.dp, end = 12.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(formaterDate(entrainement.date), style = Typo.DateCarte, color = Couleurs.Texte)
            entrainement.libelle?.let { EtiquetteLibelle(it) }
        }
        Icone(R.drawable.ic_caret_right, taille = 18.dp, couleur = Couleurs.Neutre500)
    }
}

/** Bandeau 1c : chrono, date et libellé, exercice et série courants, « Reprendre l'entraînement ». */
@Composable
private fun BandeauEnCours(enCours: EtatSaisie.EnCours, realtime: () -> Long, onReprendre: () -> Unit, modifier: Modifier) {
    val entrainement = enCours.entrainement
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Couleurs.Accent, Formes.Grande)
            .background(DegradeBandeau, Formes.Grande)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(7.dp).background(Couleurs.Accent400, CircleShape))
            Text(
                text = "EN COURS · CHRONO ${formaterChrono(tempsEcouleMs(enCours.chrono, realtime))}",
                style = Typo.SurTitre,
                color = Couleurs.Accent300,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = listOfNotNull(formaterDateCourte(entrainement.date), entrainement.libelle).joinToString(" · "),
                style = Typo.TitreSection,
                color = Couleurs.Texte,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(enCours.ligne, style = Typo.Secondaire, color = Couleurs.Neutre300, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        BoutonContour(
            texte = "Reprendre l’entraînement",
            onClick = onReprendre,
            modifier = Modifier.fillMaxWidth(),
            icone = R.drawable.ic_play,
            forme = Formes.Moyenne,
            couleurTexte = Couleurs.Accent200,
            tailleIcone = 16.dp,
            bordure = Couleurs.Accent,
            fondAppuye = Couleurs.Accent900,
        )
    }
}

/** Dégradé du bandeau : `linear-gradient(160deg, accent-900, bg)` des maquettes. */
private val DegradeBandeau = degradeCss(160f, Couleurs.Accent900, Couleurs.Fond)

/** Dégradé linéaire orienté comme en CSS : [angle] en degrés, 0 vers le haut, sens horaire, ligne couvrant les coins. */
private fun degradeCss(angle: Float, debut: Color, fin: Color) = object : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val radians = Math.toRadians(angle.toDouble())
        val direction = Offset(sin(radians).toFloat(), -cos(radians).toFloat())
        val demiLongueur = (abs(size.width * sin(radians)) + abs(size.height * cos(radians))).toFloat() / 2
        val centre = Offset(size.width / 2, size.height / 2)
        return LinearGradientShader(centre - direction * demiLongueur, centre + direction * demiLongueur, listOf(debut, fin))
    }
}

/**
 * 1b : haltère accent 40 dp, « Aucun entraînement » 22 / 500, invitation en 15 neutre-400, calés en bas. Puis
 * « Importer un export CSV » (DECISIONS.md), pour retrouver son historique sur un nouveau téléphone.
 */
@Composable
private fun EtatVide(onImporter: () -> Unit, modifier: Modifier) {
    Column(modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icone(R.drawable.ic_barbell, taille = 40.dp, couleur = Couleurs.Accent)
        Text("Aucun entraînement", style = Typo.GrandTitre, color = Couleurs.Texte, modifier = Modifier.padding(top = 8.dp))
        Text(
            text = "Lance ta première séance : tes exercices, poids et répétitions apparaîtront ici.",
            style = Typo.CorpsDialogue,
            color = Couleurs.Neutre400,
        )
        // Texte aligné sur celui du dessus, zone d'appui de 48 dp.
        BoutonTexte(
            texte = "Importer un export CSV",
            onClick = onImporter,
            modifier = Modifier.offset(x = (-12).dp),
            icone = R.drawable.ic_file_text,
            tailleIcone = 18.dp,
            margeHorizontale = 12.dp,
        )
    }
}

/**
 * « Nouvel entraînement » ancré en bas, dans la zone du pouce, au-dessus d'un dégradé transparent → fond de 28 dp.
 * Principal (64 dp), ou secondaire (56 dp) quand le bandeau « Reprendre » est l'action principale.
 */
@Composable
private fun BoutonNouvel(secondaire: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth()) {
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(HauteurDegrade)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Couleurs.Fond))),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .background(Couleurs.Fond)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            if (secondaire) {
                BoutonSecondaire("Nouvel entraînement", onClick, Modifier.fillMaxWidth(), icone = R.drawable.ic_plus)
            } else {
                BoutonPrincipal("Nouvel entraînement", onClick, Modifier.fillMaxWidth(), icone = R.drawable.ic_plus)
            }
        }
    }
}

/** Un seul entraînement en cours à la fois : terminer l'actuel avant d'en commencer un nouveau. */
@Composable
internal fun DialogueTerminerEnCours(avecSeries: Boolean, onAnnuler: () -> Unit, onTerminer: () -> Unit) {
    DialogueNocturne(titre = "Terminer l’entraînement en cours ?", onFermer = onAnnuler) {
        Text(
            text = if (avecSeries) {
                "Il sera rangé dans l’historique avant de commencer le nouveau."
            } else {
                "Il ne contient aucune série : il sera supprimé avant de commencer le nouveau."
            },
            style = Typo.CorpsDialogue,
            color = Couleurs.Neutre300,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            BoutonTexte("Annuler", onClick = onAnnuler)
            BoutonPrincipal(
                texte = "Terminer",
                onClick = onTerminer,
                hauteur = 48.dp,
                forme = Formes.Moyenne,
                style = Typo.Action,
                margeHorizontale = 22.dp,
            )
        }
    }
}
