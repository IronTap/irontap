package app.irontap.ui.apropos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.irontap.logique.BlocLicence
import app.irontap.ui.theme.Couleurs
import app.irontap.ui.theme.Typo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Écran 10b : texte intégral de la licence de [composant], lu dans les assets (hors ligne). */
@Composable
fun EcranLicence(composant: ComposantLicence, onRetour: () -> Unit) {
    val assets = LocalContext.current.assets
    val blocs by produceState<List<BlocLicence>?>(null, composant) {
        value = withContext(Dispatchers.IO) {
            try {
                lireLicence(assets, composant)
            } catch (e: IOException) {
                listOf(BlocLicence.Paragraphe("Texte de la licence indisponible."))
            }
        }
    }
    ContenuLicence(composant, blocs.orEmpty(), onRetour)
}

/**
 * En-tête de 68 dp (← , nom 19 / 500, licence 13 sp neutre-400), puis le texte défilant à 20 dp des bords :
 * copyright 14 sp, titres de section 13 / 500 neutre-200, paragraphes 13 sp neutre-300 interligne 1,6.
 */
@Composable
fun ContenuLicence(composant: ComposantLicence, blocs: List<BlocLicence>, onRetour: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Couleurs.Fond)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        EnTeteRetour(onRetour, hauteur = 68) {
            Column {
                Text(composant.nom, style = Typo.TitreDetail, color = Couleurs.Texte, modifier = Modifier.semantics { heading() })
                Text(composant.licence, style = Typo.Libelle, color = Couleurs.Neutre400)
            }
        }
        val bas = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 24.dp + bas),
        ) {
            itemsIndexed(blocs) { _, bloc ->
                when (bloc) {
                    is BlocLicence.Copyright ->
                        Text(bloc.texte, style = Typo.Secondaire, color = Couleurs.Texte, modifier = Modifier.padding(bottom = 14.dp))
                    is BlocLicence.Titre ->
                        Text(bloc.texte, style = Typo.EnteteSeance, color = Couleurs.Neutre200, modifier = Modifier.padding(bottom = 8.dp))
                    is BlocLicence.Paragraphe ->
                        Text(bloc.texte, style = Typo.TexteLicence, color = Couleurs.Neutre300, modifier = Modifier.padding(bottom = 12.dp))
                }
            }
        }
    }
}
