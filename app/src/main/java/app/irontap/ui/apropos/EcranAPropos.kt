package app.irontap.ui.apropos

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.irontap.BuildConfig
import app.irontap.R
import app.irontap.ui.composants.BoutonRond
import app.irontap.ui.composants.Icone
import app.irontap.ui.theme.Couleurs
import app.irontap.ui.theme.Formes
import app.irontap.ui.theme.Typo

/** « Version 1.0.0 (1) », lue dans `BuildConfig`. */
val VersionAppli = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

/**
 * Écran 10a : À propos (spec §4.6). « Code source » ouvre le dépôt dans le navigateur (`ACTION_VIEW`) : IronTap n'a
 * toujours pas la permission Internet, c'est le navigateur qui se connecte.
 */
@Composable
fun EcranAPropos(onRetour: () -> Unit, onLicence: (ComposantLicence) -> Unit) {
    val context = LocalContext.current
    ContenuAPropos(
        version = VersionAppli,
        onRetour = onRetour,
        onCodeSource = {
            val intention = Intent(Intent.ACTION_VIEW, UrlCodeSource.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
            try {
                context.startActivity(intention)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "Aucune application pour ouvrir ce lien", Toast.LENGTH_SHORT).show()
            }
        },
        onLicence = onLicence,
    )
}

@Composable
fun ContenuAPropos(version: String, onRetour: () -> Unit, onCodeSource: () -> Unit, onLicence: (ComposantLicence) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Couleurs.Fond)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        EnTeteRetour(onRetour) { Text("À propos", style = Typo.TitreEntete, color = Couleurs.Texte) }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            BlocAppli(version)
            Carte {
                LigneAPropos(
                    titre = "Code source",
                    sousTitre = UrlCodeSource.removePrefix("https://"),
                    icone = R.drawable.ic_code,
                    finale = R.drawable.ic_arrow_square_out,
                    premiere = true,
                    uneLigne = true,
                    onClick = onCodeSource,
                )
                LigneAPropos(
                    titre = "Licence d’IronTap",
                    sousTitre = ComposantLicence.IronTap.ligne,
                    icone = R.drawable.ic_file_text,
                    onClick = { onLicence(ComposantLicence.IronTap) },
                )
            }
            Text(
                "LICENCES TIERCES",
                style = Typo.SurTitre,
                color = Couleurs.Neutre500,
                modifier = Modifier.padding(start = 4.dp, top = 22.dp, end = 4.dp, bottom = 8.dp),
            )
            Carte {
                ComposantLicence.Tierces.forEachIndexed { index, composant ->
                    LigneAPropos(
                        titre = composant.nom,
                        sousTitre = composant.ligne,
                        premiere = index == 0,
                        onClick = { onLicence(composant) },
                    )
                }
            }
        }
    }
}

/** En-tête : ← (48 dp) puis [titre]. */
@Composable
internal fun EnTeteRetour(onRetour: () -> Unit, hauteur: Int = 64, titre: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = hauteur.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (hauteur == 64) 6.dp else 4.dp),
    ) {
        BoutonRond(
            onClick = onRetour,
            taille = 48.dp,
            fond = Color.Transparent,
            fondAppuye = Couleurs.Neutre800,
            bordure = Color.Transparent,
            description = "Retour",
        ) { Icone(R.drawable.ic_arrow_left, couleur = Couleurs.Neutre300) }
        titre()
    }
}

/** Icône de l'appli (60 dp, rayon 16, accent-900 bordé d'accent, haltère accent-300), nom et version. */
@Composable
private fun BlocAppli(version: String) {
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, end = 4.dp, bottom = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val forme = RoundedCornerShape(16.dp)
        Box(
            Modifier
                .size(60.dp)
                .background(Couleurs.Accent900, forme)
                .border(1.dp, Couleurs.Accent, forme),
            contentAlignment = Alignment.Center,
        ) { Icone(R.drawable.ic_barbell_logo, taille = 30.dp, couleur = Couleurs.Accent300) }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("IronTap", style = Typo.GrandTitre, color = Couleurs.Texte)
            Text(version, style = Typo.Secondaire, color = Couleurs.Neutre400)
        }
    }
}

/** Carte surface, rayon 14 : ses lignes sont séparées par un filet couleur `bg`. */
@Composable
private fun Carte(contenu: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Formes.Grande)
            .background(Couleurs.Surface),
    ) { contenu() }
}

/**
 * Ligne de 64 dp au moins (padding 10/12/10/16, gap 14) : icône 20 dp accent-300 facultative, titre 16 sp, sous-titre
 * 13 sp neutre-400 (sur une ligne avec ellipsis si [uneLigne], l'URL du dépôt), puis [finale] 16 dp neutre-500
 * (chevron par défaut).
 */
@Composable
private fun LigneAPropos(
    titre: String,
    sousTitre: String,
    onClick: () -> Unit,
    icone: Int? = null,
    finale: Int = R.drawable.ic_caret_right,
    premiere: Boolean = false,
    uneLigne: Boolean = false,
) {
    val interactions = remember { MutableInteractionSource() }
    val appuye by interactions.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .drawBehind {
                if (!premiere) drawLine(Couleurs.Fond, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx() * 2)
            }
            .background(if (appuye) Couleurs.Neutre800 else Color.Transparent)
            .clickable(interactions, indication = null, role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icone != null) Icone(icone, taille = 20.dp, couleur = Couleurs.Accent300)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titre, style = Typo.LigneAPropos, color = Couleurs.Texte)
            Text(
                sousTitre,
                style = Typo.Libelle,
                color = Couleurs.Neutre400,
                maxLines = if (uneLigne) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icone(finale, taille = 16.dp, couleur = Couleurs.Neutre500)
    }
}
