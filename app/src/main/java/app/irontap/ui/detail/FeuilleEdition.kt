package app.irontap.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.irontap.R
import app.irontap.donnees.Exercice
import app.irontap.donnees.Serie
import app.irontap.ui.composants.BoutonPrincipal
import app.irontap.ui.composants.BoutonTexte
import app.irontap.ui.composants.ChampPoids
import app.irontap.ui.composants.ChampRepetitions
import app.irontap.ui.composants.FeuilleNocturne
import app.irontap.ui.composants.Icone
import app.irontap.ui.composants.LibelleChamp
import app.irontap.ui.composants.MessageErreur
import app.irontap.ui.composants.RangeeAjouts
import app.irontap.ui.theme.Couleurs
import app.irontap.ui.theme.Typo

interface ActionsEdition {
    fun ajouterAuPoids(valeur: Int)
    fun ajouterAuxRepetitions(valeur: Int)
    fun enregistrer()
    fun supprimer(exercice: Exercice, serie: Serie)
    fun fermer()
}

private val AjoutsPoids = listOf(1, 5, 10, 20)
private val AjoutsRepetitions = listOf(1, 2, 5, 10)

/**
 * Écran 5 (maquette 7) : édition d'une série en feuille du bas, au-dessus du détail. Affichée tant que
 * [edition] n'est pas `null` ; quand elle le redevient (enregistrement, suppression), la feuille se
 * referme avec son animation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleEdition(
    edition: EditionSerie?,
    poids: TextFieldState,
    repetitions: TextFieldState,
    erreurRepetitions: Boolean,
    erreurEnregistrement: Boolean,
    actions: ActionsEdition,
) {
    // Dernière édition affichée, gardée le temps de l'animation de fermeture.
    var affichee by remember { mutableStateOf(edition) }
    if (edition != null) affichee = edition
    val contenu = affichee ?: return
    val etat = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(edition) {
        if (edition == null) {
            etat.hide()
            affichee = null
        }
    }
    FeuilleNocturne(onFermer = actions::fermer, etat = etat, margeHorizontale = 16.dp, espacePoignee = 16.dp) {
        val serie = contenu.serie
        Text(
            text = if (serie == null) "Nouvelle série" else "Modifier la série ${serie.ordre}",
            style = Typo.TitreEntete,
            color = Couleurs.Texte,
        )
        // Rappel du nom de l'exercice, non modifiable ici (spec §4.5).
        Row(
            modifier = Modifier.padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icone(R.drawable.ic_lock, taille = 14.dp, couleur = Couleurs.Neutre400)
            Text(contenu.exercice.nom, style = Typo.Secondaire, color = Couleurs.Neutre400)
        }
        LibelleChamp("Poids", Modifier.padding(top = 18.dp))
        ChampPoids(poids, fond = Couleurs.Fond)
        RangeeAjouts(AjoutsPoids, actions::ajouterAuPoids, fond = Couleurs.Fond)
        LibelleChamp("Nombre de répétitions", Modifier.padding(top = 14.dp))
        ChampRepetitions(repetitions, erreur = erreurRepetitions, fond = Couleurs.Fond)
        RangeeAjouts(AjoutsRepetitions, actions::ajouterAuxRepetitions, fond = Couleurs.Fond)
        if (erreurRepetitions) MessageErreur("Indique le nombre de répétitions", Modifier.padding(top = 6.dp))
        if (erreurEnregistrement) MessageErreur("Enregistrement impossible : stockage indisponible", Modifier.padding(top = 6.dp))
        BoutonPrincipal(
            texte = "Enregistrer",
            onClick = actions::enregistrer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            hauteur = 60.dp,
        )
        // « Supprimer » à gauche, loin de « Enregistrer » ; absent pour un ajout.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (serie != null) {
                BoutonTexte(
                    texte = "Supprimer",
                    onClick = { actions.supprimer(contenu.exercice, serie) },
                    icone = R.drawable.ic_trash,
                    couleur = Couleurs.Destructif,
                    tailleIcone = 16.dp,
                    margeHorizontale = 8.dp,
                )
            } else {
                Spacer(Modifier)
            }
            BoutonTexte("Annuler", onClick = actions::fermer, couleur = Couleurs.Neutre200, margeHorizontale = 16.dp)
        }
    }
}
