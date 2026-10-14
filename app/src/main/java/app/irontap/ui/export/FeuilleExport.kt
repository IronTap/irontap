package app.irontap.ui.export

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.irontap.R
import app.irontap.logique.FormatExport
import app.irontap.ui.composants.BoutonContour
import app.irontap.ui.composants.BoutonPrincipal
import app.irontap.ui.composants.FeuilleNocturne
import app.irontap.ui.composants.MessageErreur
import app.irontap.ui.theme.Couleurs
import app.irontap.ui.theme.Formes
import app.irontap.ui.theme.Typo
import kotlinx.coroutines.launch

/** Gestes de la feuille d'export. */
interface ActionsExport {
    fun choisir(format: FormatExport)
    fun partager()
    fun enregistrer()
    fun fermer()
}

/**
 * Écran 8 (spec §5) : feuille « Exporter », de l'accueil (tout l'historique) ou du menu ⋮ du détail
 * (cet entraînement). Partager ou enregistrer referme la feuille une fois l'opération faite ; un échec
 * s'affiche dans la feuille, qui reste ouverte.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleExport(vm: ExportViewModel, onFermer: () -> Unit) {
    val etat by vm.etat.collectAsStateWithLifecycle()
    val format by vm.format.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val feuille = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun fermer() {
        scope.launch { feuille.hide() }.invokeOnCompletion {
            vm.reinitialiser()
            onFermer()
        }
    }

    val surDocument: (Uri?) -> Unit = { uri ->
        if (uri == null) {
            vm.abandonner()
        } else {
            scope.launch {
                if (vm.enregistrer(uri)) {
                    Toast.makeText(context, "Fichier enregistré", Toast.LENGTH_SHORT).show()
                    fermer()
                }
            }
        }
    }
    // Un sélecteur par type : le contrat fixe le type MIME du document créé.
    val creerTexte = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FormatExport.Texte.mime), surDocument)
    val creerCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FormatExport.Csv.mime), surDocument)

    VueExport(
        etat = etat,
        format = format,
        operation = operation,
        feuille = feuille,
        actions = object : ActionsExport {
            override fun choisir(format: FormatExport) = vm.choisir(format)

            override fun partager() {
                scope.launch {
                    val intention = vm.partager() ?: return@launch
                    try {
                        context.startActivity(Intent.createChooser(intention, "Partager l’export"))
                        fermer()
                    } catch (e: ActivityNotFoundException) {
                        vm.abandonner("Aucune appli ne permet de partager")
                    }
                }
            }

            override fun enregistrer() {
                val pret = etat as? EtatExport.Pret ?: return
                if (!vm.reserver()) return
                try {
                    (if (format == FormatExport.Texte) creerTexte else creerCsv).launch(vm.nomFichier(pret))
                } catch (e: ActivityNotFoundException) {
                    vm.abandonner("Aucune appli ne permet d’enregistrer un fichier")
                }
            }

            override fun fermer() = fermer()
        },
    )
}

/** Feuille d'export sans état propre (captures). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VueExport(
    etat: EtatExport,
    format: FormatExport,
    operation: OperationExport,
    actions: ActionsExport,
    feuille: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val pret = etat as? EtatExport.Pret
    FeuilleNocturne(onFermer = actions::fermer, etat = feuille, margeHorizontale = 16.dp, espacePoignee = 16.dp) {
        Text("Exporter", style = Typo.TitreEntete, color = Couleurs.Texte)
        Text(
            text = when (etat) {
                is EtatExport.Pret -> etat.portee
                EtatExport.Vide -> "Aucun entraînement à exporter"
                EtatExport.Chargement -> ""
            },
            style = Typo.Secondaire,
            color = Couleurs.Neutre400,
            modifier = Modifier.padding(top = 2.dp),
        )

        Titre("Format")
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CarteFormat("Texte", "Lisible, type bloc-notes", format == FormatExport.Texte, { actions.choisir(FormatExport.Texte) }, Modifier.weight(1f))
            CarteFormat("CSV", "Pour un tableur", format == FormatExport.Csv, { actions.choisir(FormatExport.Csv) }, Modifier.weight(1f))
        }
        // Aperçu : premières lignes, les lignes longues du CSV défilent horizontalement.
        Text(
            text = pret?.apercus?.getValue(format).orEmpty(),
            style = Typo.Apercu,
            color = Couleurs.Neutre300,
            softWrap = false,
            modifier = Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .background(Couleurs.Fond, Formes.Moyenne)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )

        Titre("Destination")
        val actif = pret != null && !operation.occupe
        BoutonPrincipal(
            texte = "Partager…",
            onClick = actions::partager,
            modifier = Modifier.fillMaxWidth(),
            icone = R.drawable.ic_export,
            hauteur = 56.dp,
            style = Typo.ActionFeuille,
            tailleIcone = 18.dp,
            actif = actif,
        )
        BoutonContour(
            texte = "Enregistrer le fichier",
            onClick = actions::enregistrer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            icone = R.drawable.ic_download_simple,
            hauteur = 56.dp,
            style = Typo.ActionFeuille,
            couleurTexte = Couleurs.Texte,
            actif = actif,
        )
        operation.erreur?.let { MessageErreur(it, Modifier.padding(top = 10.dp)) }
        Spacer(Modifier.height(12.dp))
    }
}

/** Titre de section (« Format », « Destination ») : 13 neutre-300, 18 dp au-dessus, 8 dessous. */
@Composable
private fun Titre(texte: String) {
    Text(texte, style = Typo.Libelle, color = Couleurs.Neutre300, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
}

/** Carte radio : choisie, fond accent-900 et contour accent ; sinon contour séparateur. */
@Composable
private fun CarteFormat(titre: String, description: String, choisie: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val interactions = remember { MutableInteractionSource() }
    val appuye by interactions.collectIsPressedAsState()
    Column(
        modifier = modifier
            .clip(Formes.Grande)
            .background(
                when {
                    choisie -> Couleurs.Accent900
                    appuye -> Couleurs.Neutre800
                    else -> Color.Transparent
                },
            )
            .border(1.dp, if (choisie) Couleurs.Accent else Couleurs.Separateur, Formes.Grande)
            .selectable(choisie, interactions, indication = null, role = Role.RadioButton, onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(titre, style = Typo.DateCarte, color = Couleurs.Texte, modifier = Modifier.weight(1f))
            Radio(choisie)
        }
        Text(description, style = Typo.Libelle, color = if (choisie) Couleurs.Neutre300 else Couleurs.Neutre400)
    }
}

/** Bouton radio 18 dp : choisi, anneau accent, 4 dp d'accent-900, puis pastille accent ; sinon anneau neutre-600. */
@Composable
private fun Radio(choisi: Boolean) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .border(1.5.dp, if (choisi) Couleurs.Accent else Couleurs.Neutre600, CircleShape)
            .then(if (choisi) Modifier.background(Couleurs.Accent900, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (choisi) Box(Modifier.size(7.dp).background(Couleurs.Accent, CircleShape))
    }
}
