package app.irontap.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.KeyboardActionHandler
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.irontap.R
import app.irontap.donnees.RoleSuperset
import app.irontap.logique.formaterPoids
import app.irontap.ui.composants.ApparenceChampPalier
import app.irontap.ui.composants.BadgeLettre
import app.irontap.ui.composants.BoutonPrincipal
import app.irontap.ui.composants.BoutonTexte
import app.irontap.ui.composants.ChampPalier
import app.irontap.ui.composants.FeuilleNocturne
import app.irontap.ui.composants.Icone
import app.irontap.ui.composants.MessageErreur
import app.irontap.ui.theme.Couleurs
import app.irontap.ui.theme.Typo

/**
 * « Ajouter un tour » / « Modifier le tour N » d'un superset (maquette 3·S+ T1) : blocs A puis B, poids × répétitions
 * au clavier numérique (« Suivant » : A poids → A reps → B poids → B reps), valeurs du tour précédent en indication.
 * Un bloc vide est ignoré ; les deux vides désactivent « Enregistrer le tour ». Affichée tant que [edition] n'est pas
 * `null`, refermée avec son animation ensuite.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleTour(
    edition: EditionTour?,
    champs: Map<RoleSuperset, ChampsPalier>,
    paliersEnErreur: Set<Long>,
    erreurEnregistrement: Boolean,
    enregistrable: Boolean,
    onEnregistrer: () -> Unit,
    onSupprimer: (EditionTour) -> Unit,
    onFermer: () -> Unit,
) {
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
    val focusManager = LocalFocusManager.current
    // Ordre de saisie : A poids, A reps, B poids, B reps.
    val focus = remember { List(4) { FocusRequester() } }
    FeuilleNocturne(onFermer = onFermer, etat = etat, margeHorizontale = 16.dp, espacePoignee = 16.dp) {
        Text(
            text = if (contenu.existant) "Modifier le tour ${contenu.tour}" else "Ajouter un tour",
            style = Typo.TitreEntete,
            color = Couleurs.Texte,
        )
        Row(
            modifier = Modifier.padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icone(R.drawable.ic_link_simple, taille = 14.dp, couleur = Couleurs.Neutre400)
            Text("Superset · tour ${contenu.tour}", style = Typo.Secondaire, color = Couleurs.Neutre400)
        }
        RoleSuperset.entries.forEachIndexed { index, role ->
            val champ = champs.getValue(role)
            val indication = contenu.indications[role]
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BadgeLettre(role)
                    Text(contenu.exercice(role).nom, style = Typo.TitreCarte, color = Couleurs.Texte)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChampPalier(
                        etat = champ.poids,
                        poids = true,
                        largeur = 0.dp,
                        description = "Poids de l’exercice $role",
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focus[index * 2]),
                        onSuivant = KeyboardActionHandler { focus[index * 2 + 1].requestFocus() },
                        apparence = ApparenceChampPalier.Tour,
                        indication = indication?.poidsKg?.let(::formaterPoids) ?: "0",
                        remplir = true,
                    )
                    Text("×", style = Typo.SigneTour, color = Couleurs.Neutre500)
                    val dernier = index == RoleSuperset.entries.lastIndex
                    ChampPalier(
                        etat = champ.repetitions,
                        poids = false,
                        largeur = 96.dp,
                        description = "Répétitions de l’exercice $role",
                        modifier = Modifier.focusRequester(focus[index * 2 + 1]),
                        erreur = champ.cle in paliersEnErreur,
                        imeAction = if (dernier) ImeAction.Done else ImeAction.Next,
                        onSuivant = KeyboardActionHandler { if (dernier) focusManager.clearFocus() else focus[index * 2 + 2].requestFocus() },
                        apparence = ApparenceChampPalier.Tour,
                        indication = indication?.repetitions?.toString() ?: "0",
                    )
                }
            }
        }
        if (paliersEnErreur.any { cle -> champs.values.any { it.cle == cle } }) {
            MessageErreur("Indique le nombre de répétitions", Modifier.padding(top = 8.dp))
        }
        val aide = if (contenu.existant) {
            "Laisse un bloc vide pour ne pas changer sa série."
        } else {
            listOfNotNull(contenu.precedent?.let { "Valeurs du tour $it en indication." }, "Laisse B vide pour ajouter A seul.").joinToString(" ")
        }
        Text(aide, style = Typo.Libelle, color = Couleurs.Neutre500, modifier = Modifier.padding(top = 10.dp))
        if (erreurEnregistrement) MessageErreur("Enregistrement impossible : stockage indisponible", Modifier.padding(top = 6.dp))
        BoutonPrincipal(
            texte = "Enregistrer le tour",
            onClick = onEnregistrer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
            hauteur = 60.dp,
            actif = enregistrable,
        )
        if (contenu.existant) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BoutonTexte(
                    texte = "Supprimer le tour",
                    onClick = { onSupprimer(contenu) },
                    icone = R.drawable.ic_trash,
                    couleur = Couleurs.Destructif,
                    tailleIcone = 16.dp,
                    margeHorizontale = 8.dp,
                )
                BoutonTexte("Annuler", onClick = onFermer, couleur = Couleurs.Neutre200, margeHorizontale = 16.dp)
            }
        } else {
            BoutonTexte(
                "Annuler",
                onClick = onFermer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                couleur = Couleurs.Neutre200,
            )
        }
        Spacer(Modifier.padding(bottom = 4.dp))
    }
}
