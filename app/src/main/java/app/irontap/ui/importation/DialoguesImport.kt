package app.irontap.ui.importation

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.irontap.logique.formaterPeriode
import app.irontap.ui.composants.BoutonPrincipal
import app.irontap.ui.composants.BoutonTexte
import app.irontap.ui.composants.DialogueNocturne
import app.irontap.ui.theme.Couleurs
import app.irontap.ui.theme.Formes
import app.irontap.ui.theme.Typo

/** Erreurs listées dans le dialogue « Import impossible » ; les suivantes sont comptées. */
private const val ERREURS_AFFICHEES = 5

/**
 * Dialogues de l'import (DECISIONS.md « Import »), au style de 4b : confirmation, rien à importer, import impossible.
 * Une fois l'import fait, toast système « N entraînements importés » (l'accueil n'a pas de toast Nocturne).
 */
@Composable
fun DialoguesImport(etat: EtatImport, onConfirmer: () -> Unit, onFermer: () -> Unit) {
    when (etat) {
        EtatImport.Aucun, EtatImport.EnCours -> Unit
        is EtatImport.Confirmation -> DialogueImport(
            titre = "Importer ${pluriel(etat.entrainements.size)} ?",
            texte = formaterPeriode(etat.debut, etat.fin) + "." + when (etat.dejaPresents) {
                0 -> ""
                1 -> " 1 déjà présent sera ignoré."
                else -> " ${etat.dejaPresents} déjà présents seront ignorés."
            },
            action = "Importer",
            onAction = onConfirmer,
            onFermer = onFermer,
        )
        is EtatImport.RienAImporter -> DialogueImport(
            titre = "Rien à importer",
            texte = if (etat.total == 1) {
                "L’entraînement du fichier est déjà dans l’historique."
            } else {
                "Les ${etat.total} entraînements du fichier sont déjà dans l’historique."
            },
            onFermer = onFermer,
        )
        is EtatImport.Echec -> DialogueImport(
            titre = "Import impossible",
            texte = "Rien n’a été importé.",
            onFermer = onFermer,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                etat.erreurs.take(ERREURS_AFFICHEES).forEach { Text(it.toString(), style = Typo.Secondaire, color = Couleurs.Texte) }
                val autres = etat.erreurs.size - ERREURS_AFFICHEES
                if (autres > 0) {
                    Text("… et $autres autre${if (autres > 1) "s erreurs" else " erreur"}.", style = Typo.Secondaire, color = Couleurs.Neutre400)
                }
            }
        }
        is EtatImport.Termine -> {
            val context = LocalContext.current
            LaunchedEffect(etat) {
                val message = if (etat.nombre == 1) "1 entraînement importé" else "${etat.nombre} entraînements importés"
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                onFermer()
            }
        }
    }
}

private fun pluriel(nombre: Int) = if (nombre == 1) "1 entraînement" else "$nombre entraînements"

/** Dialogue au style de 4b : texte, détails éventuels, puis « Annuler » et [action], ou « OK » seul. */
@Composable
private fun DialogueImport(
    titre: String,
    texte: String,
    onFermer: () -> Unit,
    action: String? = null,
    onAction: () -> Unit = {},
    details: @Composable () -> Unit = {},
) {
    DialogueNocturne(titre = titre, onFermer = onFermer) {
        Text(texte, style = Typo.CorpsDialogue, color = Couleurs.Neutre300)
        details()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            if (action != null) BoutonTexte("Annuler", onClick = onFermer)
            BoutonPrincipal(
                texte = action ?: "OK",
                onClick = if (action != null) onAction else onFermer,
                hauteur = 48.dp,
                forme = Formes.Moyenne,
                style = Typo.Action,
                margeHorizontale = 22.dp,
            )
        }
    }
}
