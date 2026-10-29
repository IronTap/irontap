package app.irontap.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.irontap.R
import app.irontap.donnees.Exercice
import app.irontap.donnees.SerieAvecPaliers
import app.irontap.ui.composants.BoutonPrincipal
import app.irontap.ui.composants.BoutonRond
import app.irontap.ui.composants.BoutonTexte
import app.irontap.ui.composants.ChampPalier
import app.irontap.ui.composants.ChampPoids
import app.irontap.ui.composants.ChampRepetitions
import app.irontap.ui.composants.FeuilleNocturne
import app.irontap.ui.composants.Icone
import app.irontap.ui.composants.LibelleChamp
import app.irontap.ui.composants.MessageErreur
import app.irontap.ui.composants.RangeeAjouts
import app.irontap.ui.theme.Couleurs
import app.irontap.ui.theme.Formes
import app.irontap.ui.theme.Typo

interface ActionsEdition {
    fun ajouterAuPoids(valeur: Int)
    fun ajouterAuxRepetitions(valeur: Int)

    /** « + Ajouter un palier » : retourne la clé de la nouvelle ligne, qui prend le focus. */
    fun ajouterPalier(): Long
    fun retirerPalier(cle: Long)
    fun enregistrer()
    fun supprimer(exercice: Exercice, serie: SerieAvecPaliers)
    fun fermer()
}

private val AjoutsPoids = listOf(1, 2, 5, 10, 20)
private val AjoutsRepetitions = listOf(1, 2, 5, 10)

/**
 * Édition d'une série en feuille du bas, au-dessus du détail : écran 7 pour une série simple (champs et
 * boutons +X), tableau des paliers 7·D pour une dégressive (spec §4.5.1). Affichée tant que [edition]
 * n'est pas `null` ; quand elle le redevient (enregistrement, suppression), la feuille se referme avec son
 * animation. [paliersEnErreur] : clés des paliers dont les répétitions manquent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleEdition(
    edition: EditionSerie?,
    paliers: List<ChampsPalier>,
    paliersEnErreur: Set<Long>,
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
            text = if (serie == null) "Nouvelle série" else "Modifier la série ${serie.serie.ordre}",
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
            Text(
                text = if (contenu.tableau) "${contenu.exercice.nom} · dégressive" else contenu.exercice.nom,
                style = Typo.Secondaire,
                color = Couleurs.Neutre400,
            )
        }
        val enErreur = paliers.any { it.cle in paliersEnErreur }
        // Palier ajouté qui doit prendre le focus, une fois sa ligne affichée.
        var aFocaliser by remember { mutableStateOf<Long?>(null) }
        if (contenu.tableau) {
            TableauPaliers(
                paliers = paliers,
                paliersEnErreur = paliersEnErreur,
                aFocaliser = aFocaliser,
                onFocalise = { aFocaliser = null },
                onAjouter = { aFocaliser = actions.ajouterPalier() },
                onRetirer = actions::retirerPalier,
            )
            Text(
                "Appuie sur une valeur pour la modifier au clavier.",
                style = Typo.Libelle,
                color = Couleurs.Neutre500,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (enErreur) MessageErreur("Indique le nombre de répétitions de chaque palier", Modifier.padding(top = 6.dp))
        } else {
            val palier = paliers.firstOrNull() ?: return@FeuilleNocturne
            LibelleChamp("Poids", Modifier.padding(top = 18.dp))
            ChampPoids(palier.poids, fond = Couleurs.Fond)
            RangeeAjouts(AjoutsPoids, actions::ajouterAuPoids, fond = Couleurs.Fond)
            LibelleChamp("Nombre de répétitions", Modifier.padding(top = 14.dp))
            ChampRepetitions(palier.repetitions, erreur = enErreur, fond = Couleurs.Fond)
            RangeeAjouts(AjoutsRepetitions, actions::ajouterAuxRepetitions, fond = Couleurs.Fond)
            if (enErreur) MessageErreur("Indique le nombre de répétitions", Modifier.padding(top = 6.dp))
            // Passage en dégressive : la feuille bascule vers le tableau (spec §4.5.1).
            BoutonTexte(
                texte = "Ajouter un palier",
                onClick = { aFocaliser = actions.ajouterPalier() },
                modifier = Modifier.padding(top = 6.dp),
                icone = R.drawable.ic_plus,
                tailleIcone = 16.dp,
                margeHorizontale = 8.dp,
            )
        }
        if (erreurEnregistrement) MessageErreur("Enregistrement impossible : stockage indisponible", Modifier.padding(top = 6.dp))
        BoutonPrincipal(
            texte = "Enregistrer",
            onClick = actions::enregistrer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (contenu.tableau) 18.dp else 14.dp),
            hauteur = 60.dp,
        )
        // « Supprimer » à gauche, loin de « Enregistrer » ; absent pour un ajout.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (contenu.tableau) 4.dp else 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (serie != null) {
                BoutonTexte(
                    texte = if (contenu.tableau) "Supprimer la série" else "Supprimer",
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

/**
 * 7·D : une ligne de 64 dp par palier sur fond `bg`, rayon 14, filets couleur surface ; repère « 1 » ou ↓,
 * poids × répétitions au clavier numérique, ✕ sur les paliers dégressifs ; « + Ajouter un palier » en bas.
 * « Suivant » du clavier enchaîne poids → répétitions → poids du palier suivant.
 */
@Composable
private fun TableauPaliers(
    paliers: List<ChampsPalier>,
    paliersEnErreur: Set<Long>,
    aFocaliser: Long?,
    onFocalise: () -> Unit,
    onAjouter: () -> Unit,
    onRetirer: (Long) -> Unit,
) {
    val focus = remember { HashMap<Long, Pair<FocusRequester, FocusRequester>>() }
    fun demandes(cle: Long) = focus.getOrPut(cle) { FocusRequester() to FocusRequester() }
    LaunchedEffect(aFocaliser) {
        if (aFocaliser != null) {
            demandes(aFocaliser).first.requestFocus()
            onFocalise()
        }
    }
    val focusManager = LocalFocusManager.current
    Column(
        Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .clip(Formes.Grande)
            .background(Couleurs.Fond),
    ) {
        paliers.forEachIndexed { index, palier ->
            key(palier.cle) {
                val (poids, repetitions) = demandes(palier.cle)
                val suivant = paliers.getOrNull(index + 1)
                LignePalier(
                    numero = index + 1,
                    palier = palier,
                    erreur = palier.cle in paliersEnErreur,
                    focusPoids = poids,
                    focusRepetitions = repetitions,
                    dernier = suivant == null,
                    versRepetitions = { repetitions.requestFocus() },
                    versSuivant = { if (suivant != null) demandes(suivant.cle).first.requestFocus() else focusManager.clearFocus() },
                    onRetirer = { onRetirer(palier.cle) },
                )
            }
        }
        LigneAjoutPalier(onAjouter)
    }
}

@Composable
private fun LignePalier(
    numero: Int,
    palier: ChampsPalier,
    erreur: Boolean,
    focusPoids: FocusRequester,
    focusRepetitions: FocusRequester,
    dernier: Boolean,
    versRepetitions: () -> Unit,
    versSuivant: () -> Unit,
    onRetirer: () -> Unit,
) {
    val principal = numero == 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .drawBehind {
                val trait = 1.dp.toPx()
                drawLine(Couleurs.Surface, Offset(0f, size.height - trait / 2), Offset(size.width, size.height - trait / 2), trait)
            }
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
            if (principal) {
                Text("1", style = Typo.Corps, color = Couleurs.Neutre500, textAlign = TextAlign.Center)
            } else {
                Icone(R.drawable.ic_arrow_down_small, taille = 15.dp, couleur = Couleurs.Accent300)
            }
        }
        ChampPalier(
            etat = palier.poids,
            poids = true,
            largeur = 100.dp,
            description = "Poids du palier $numero",
            modifier = Modifier.focusRequester(focusPoids),
            onSuivant = KeyboardActionHandler { versRepetitions() },
        )
        Text("×", style = Typo.Suggestion, color = Couleurs.Neutre500)
        ChampPalier(
            etat = palier.repetitions,
            poids = false,
            largeur = 68.dp,
            description = "Répétitions du palier $numero",
            modifier = Modifier.focusRequester(focusRepetitions),
            erreur = erreur,
            imeAction = if (dernier) ImeAction.Done else ImeAction.Next,
            onSuivant = KeyboardActionHandler { versSuivant() },
        )
        Spacer(Modifier.weight(1f))
        if (principal) {
            // Le palier principal ne se retire pas : « Supprimer la série » retire tout.
            Spacer(Modifier.size(48.dp))
        } else {
            BoutonRond(
                onClick = onRetirer,
                taille = 48.dp,
                fond = Color.Transparent,
                fondAppuye = Couleurs.Neutre800,
                bordure = Color.Transparent,
                description = "Retirer ce palier",
            ) { Icone(R.drawable.ic_x, taille = 18.dp, couleur = Couleurs.Neutre500) }
        }
    }
}

/** « + Ajouter un palier », 52 dp, 15 / 500 accent-300. */
@Composable
private fun LigneAjoutPalier(onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val appuye by interactions.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(if (appuye) Couleurs.Neutre800 else Color.Transparent)
            .clickable(interactions, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icone(R.drawable.ic_plus, taille = 16.dp, couleur = Couleurs.Accent300)
        Text("Ajouter un palier", style = Typo.Action, color = Couleurs.Accent300)
    }
}
