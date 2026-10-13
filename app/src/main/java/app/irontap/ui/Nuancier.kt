package app.irontap.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.irontap.R
import app.irontap.ui.composants.BoutonContour
import app.irontap.ui.composants.BoutonPrincipal
import app.irontap.ui.composants.BoutonSecondaire
import app.irontap.ui.composants.EtiquetteLibelle
import app.irontap.ui.composants.Icone
import app.irontap.ui.theme.Couleurs
import app.irontap.ui.theme.Elevation
import app.irontap.ui.theme.Formes
import app.irontap.ui.theme.Typo
import app.irontap.ui.theme.elevation

/**
 * Planche provisoire du thème Nocturne : couleurs, typographie, icônes, boutons, élévations.
 * Sert d'écran d'accueil le temps de construire les vrais écrans, pour vérifier le rendu sur le téléphone.
 */
@Composable
fun Nuancier(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Couleurs.Fond)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Entraînements", style = Typo.TitreAccueil)
        SurTitre("Historique · 24")
        CarteHistorique()
        BandeauChrono()

        SurTitre("Typographie")
        Text("41,25", style = Typo.Valeur)
        Text("Tirage vertical", style = Typo.Champ)
        Text("Terminer l'entraînement ?", style = Typo.TitreDialogue)
        Text("Nouvelle\nsérie", style = Typo.GrandeAction)
        Text(
            "La série en cours (45 kg × 10) n'est pas encore enregistrée.",
            style = Typo.CorpsDialogue,
            color = Couleurs.Neutre300,
        )
        Text("Poids", style = Typo.Libelle, color = Couleurs.Neutre300)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icone(R.drawable.ic_warning_circle, taille = 15.dp, couleur = Couleurs.ErreurTexte)
            Text("Indique le nombre de répétitions", style = Typo.Libelle, color = Couleurs.ErreurTexte)
        }

        SurTitre("Icônes")
        GrilleIcones()

        SurTitre("Boutons")
        BoutonPrincipal("Nouvel entraînement", onClick = {}, Modifier.fillMaxWidth(), icone = R.drawable.ic_plus)
        BoutonSecondaire("Nouvel entraînement", onClick = {}, Modifier.fillMaxWidth(), icone = R.drawable.ic_plus)
        BoutonContour("Annuler", onClick = {}, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BoutonPrincipal("Nouvelle\nsérie", onClick = {}, Modifier.weight(1f), hauteur = 72.dp)
            BoutonSecondaire(
                "Nouvel\nexercice", onClick = {}, Modifier.weight(1f),
                hauteur = 72.dp, style = Typo.GrandeAction, couleurTexte = Couleurs.Texte,
            )
        }

        SurTitre("Élévations")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Elevation.entries.forEach { niveau ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .elevation(niveau, Formes.Grande)
                        .background(Couleurs.Surface, Formes.Grande),
                    contentAlignment = Alignment.Center,
                ) { Text(niveau.name.lowercase(), style = Typo.Discret, color = Couleurs.Neutre300) }
            }
        }

        SurTitre("Couleurs")
        Palette("Neutres", listOf(Couleurs.Neutre100, Couleurs.Neutre200, Couleurs.Neutre300, Couleurs.Neutre400, Couleurs.Neutre500, Couleurs.Neutre600, Couleurs.Neutre700, Couleurs.Neutre800, Couleurs.Neutre900))
        Palette("Accent", listOf(Couleurs.Accent100, Couleurs.Accent200, Couleurs.Accent300, Couleurs.Accent400, Couleurs.Accent500, Couleurs.Accent600, Couleurs.Accent700, Couleurs.Accent800, Couleurs.Accent900))
        Palette("Erreur", listOf(Couleurs.ErreurBordure, Couleurs.ErreurTexte, Couleurs.Destructif, Couleurs.DestructifDialogue, Couleurs.FondSuppression, Couleurs.TexteSuppression))
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SurTitre(texte: String) {
    Text(texte.uppercase(), style = Typo.SurTitre, color = Couleurs.Neutre500)
}

@Composable
private fun CarteHistorique() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Formes.Grande)
            .background(Couleurs.Surface)
            .padding(start = 16.dp, top = 13.dp, end = 12.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Lun. 12 oct. 2026", style = Typo.DateCarte)
            EtiquetteLibelle("Dos / biceps")
        }
        Icone(R.drawable.ic_caret_right, taille = 18.dp, couleur = Couleurs.Neutre500)
    }
}

@Composable
private fun BandeauChrono() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(Formes.Grande)
            .background(Couleurs.Surface)
            .padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Couleurs.Accent400))
                Text("REPOS · EN COURS", style = Typo.SurTitre, color = Couleurs.Accent300)
            }
            Text("01:12", style = Typo.Chrono, color = Couleurs.Accent100)
        }
        BoutonRond(R.drawable.ic_pause, Couleurs.Accent900, Couleurs.Accent, Couleurs.Accent300)
        BoutonRond(R.drawable.ic_arrow_counter_clockwise, Couleurs.Fond, Couleurs.Separateur, Couleurs.Texte)
    }
}

@Composable
private fun BoutonRond(@DrawableRes icone: Int, fond: Color, bordure: Color, couleur: Color) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(fond)
            .border(1.dp, bordure, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icone(icone, taille = 22.dp, couleur = couleur) }
}

private val Icones = listOf(
    R.drawable.ic_x, R.drawable.ic_arrow_left, R.drawable.ic_caret_right, R.drawable.ic_plus,
    R.drawable.ic_play, R.drawable.ic_pause, R.drawable.ic_arrow_counter_clockwise, R.drawable.ic_lock,
    R.drawable.ic_flag, R.drawable.ic_calendar, R.drawable.ic_export, R.drawable.ic_download_simple,
    R.drawable.ic_trash, R.drawable.ic_pencil_simple, R.drawable.ic_dots_three_vertical, R.drawable.ic_bell,
    R.drawable.ic_check, R.drawable.ic_warning_circle, R.drawable.ic_barbell, R.drawable.ic_barbell_compact,
)

@Composable
private fun GrilleIcones() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icones.chunked(7).forEach { ligne ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                ligne.forEach { Icone(it, taille = 22.dp, couleur = Couleurs.Neutre200) }
            }
        }
    }
}

@Composable
private fun Palette(nom: String, couleurs: List<Color>) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(nom, style = Typo.Libelle, color = Couleurs.Neutre400, modifier = Modifier.width(56.dp))
        couleurs.forEach { Box(Modifier.size(24.dp).clip(Formes.Petite).background(it)) }
    }
}
