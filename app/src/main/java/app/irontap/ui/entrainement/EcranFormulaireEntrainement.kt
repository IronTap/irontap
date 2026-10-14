package app.irontap.ui.entrainement

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.irontap.R
import app.irontap.logique.formaterDate
import app.irontap.ui.composants.BoutonContour
import app.irontap.ui.composants.BoutonPrincipal
import app.irontap.ui.composants.BoutonRond
import app.irontap.ui.composants.ChampTexte
import app.irontap.ui.composants.Icone
import app.irontap.ui.composants.LibelleChamp
import app.irontap.ui.composants.MessageErreur
import app.irontap.ui.composants.cadreChamp
import app.irontap.ui.theme.Couleurs
import app.irontap.ui.theme.Formes
import app.irontap.ui.theme.Typo
import java.time.LocalDate

/**
 * Écran 2 : nouvel entraînement (2a) ou modification d'un entraînement passé (2c).
 * [onLance] ouvre la saisie après la création ; [onFermer] revient à l'écran précédent.
 */
@Composable
fun EcranFormulaireEntrainement(vm: FormulaireEntrainementViewModel, onLance: () -> Unit, onFermer: () -> Unit) {
    val etat by vm.etat.collectAsStateWithLifecycle()
    LaunchedEffect(etat.fin) {
        when (etat.fin) {
            FinFormulaire.Lance -> onLance()
            FinFormulaire.Enregistre, FinFormulaire.Introuvable -> onFermer()
            null -> Unit
        }
    }
    ContenuFormulaire(
        etat = etat,
        aujourdhui = vm.aujourdhui,
        libelle = vm.libelle,
        onDate = vm::choisirDate,
        onValider = vm::valider,
        onFermer = onFermer,
    )
}

@Composable
fun ContenuFormulaire(
    etat: EtatFormulaire,
    aujourdhui: LocalDate,
    libelle: TextFieldState,
    onDate: (LocalDate) -> Unit,
    onValider: () -> Unit,
    onFermer: () -> Unit,
    selecteurOuvert: Boolean = false,
) {
    var choisirDate by rememberSaveable { mutableStateOf(selecteurOuvert) }
    Column(
        Modifier
            .fillMaxSize()
            .background(Couleurs.Fond)
            .safeDrawingPadding(),
    ) {
        EnTete(if (etat.modification) "Modifier l’entraînement" else "Nouvel entraînement", onFermer)
        BoxWithConstraints(Modifier.weight(1f)) {
            val hauteurVisible = maxHeight
            // Comme la saisie : les boutons sont en bas, et tout défile sans être écrasé quand le clavier est ouvert.
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Column(
                    Modifier
                        .heightIn(min = hauteurVisible)
                        .padding(start = 16.dp, top = 16.dp, end = 16.dp),
                ) {
                    LibelleChamp("Date")
                    if (etat.date != null) ChampDate(etat.date) { choisirDate = true }
                    if (!etat.modification && etat.date == aujourdhui) {
                        Text("Aujourd’hui", style = Typo.Libelle, color = Couleurs.Neutre500, modifier = Modifier.padding(top = 6.dp))
                    }
                    Text(
                        text = buildAnnotatedString {
                            append("Libellé ")
                            withStyle(SpanStyle(color = Couleurs.Neutre500)) { append("(facultatif)") }
                        },
                        style = Typo.Libelle,
                        color = Couleurs.Neutre300,
                        modifier = Modifier.padding(top = 22.dp, bottom = 6.dp),
                    )
                    ChampTexte(etat = libelle, placeholder = "ex. Jambes", hauteur = 62.dp, longueurMax = 40)
                    if (etat.erreur) {
                        MessageErreur("Enregistrement impossible : stockage indisponible", Modifier.padding(top = 6.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Spacer(Modifier.weight(1f))
                    BoutonPrincipal(
                        texte = if (etat.modification) "Enregistrer" else "Lancer l’entraînement",
                        onClick = onValider,
                        modifier = Modifier.fillMaxWidth(),
                        icone = if (etat.modification) null else R.drawable.ic_play,
                        tailleIcone = 16.dp,
                        actif = etat.date != null,
                    )
                    BoutonContour("Annuler", onFermer, Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
        }
    }
    if (choisirDate && etat.date != null) {
        SelecteurDate(
            date = etat.date,
            onChoisir = onDate,
            onFermer = { choisirDate = false },
        )
    }
}

/** En-tête 64 dp : ✕ (48) et titre 20 / 500. */
@Composable
private fun EnTete(titre: String, onFermer: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BoutonRond(
            onClick = onFermer,
            taille = 48.dp,
            fond = Color.Transparent,
            fondAppuye = Couleurs.Neutre800,
            bordure = Color.Transparent,
            description = "Fermer",
        ) { Icone(R.drawable.ic_x, couleur = Couleurs.Neutre300) }
        Text(titre, style = Typo.TitreEntete, color = Couleurs.Texte)
    }
}

/** Champ date de 62 dp : la date en 22 / 500 et un calendrier accent ; un appui ouvre le sélecteur (2b). */
@Composable
private fun ChampDate(date: LocalDate, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .cadreChamp()
            .clip(Formes.Grande)
            .clickable(role = Role.Button, onClickLabel = "Changer la date", onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(formaterDate(date), style = Typo.Champ, color = Couleurs.Texte, modifier = Modifier.weight(1f))
        Icone(R.drawable.ic_calendar, couleur = Couleurs.Accent)
    }
}

