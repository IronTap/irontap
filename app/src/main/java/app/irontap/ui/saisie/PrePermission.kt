package app.irontap.ui.saisie

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.irontap.IronTapApplication
import app.irontap.R
import app.irontap.ui.composants.BoutonPrincipal
import app.irontap.ui.composants.BoutonTexte
import app.irontap.ui.composants.FeuilleNocturne
import app.irontap.ui.composants.Icone
import app.irontap.ui.theme.Couleurs
import app.irontap.ui.theme.Typo
import kotlinx.coroutines.launch

/**
 * Écran 9 (spec §4.3.4) : la première fois que le chrono démarre (Play ou série enregistrée), explique
 * l'intérêt des notifications avant la demande système. Montré une seule fois, et seulement sur
 * Android 13+ quand la permission n'est pas encore accordée.
 */
@Composable
fun PrePermissionNotifications(chronoEnCours: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val conteneur = (LocalContext.current.applicationContext as IronTapApplication).container
    val notification = conteneur.notificationChrono
    val montree by conteneur.reglages.prePermissionMontree.collectAsStateWithLifecycle(initialValue = null)
    var visible by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val demande = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { accordee ->
        // Le chrono tourne déjà : sa notification apparaît tout de suite.
        if (accordee) scope.launch { notification.actualiser() }
    }
    LaunchedEffect(chronoEnCours, montree) {
        if (chronoEnCours && montree == false && !notification.autorisee()) {
            visible = true
            conteneur.reglages.marquerPrePermissionMontree()
        }
    }
    if (visible) {
        FeuillePrePermission(
            onAutoriser = {
                visible = false
                demande.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onPlusTard = { visible = false },
        )
    }
}

/** Contenu de l'écran 9 : cloche dans un cercle 56, titre 22, texte 15, « Autoriser » 56 dp, « Plus tard » en texte. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuillePrePermission(onAutoriser: () -> Unit, onPlusTard: () -> Unit) {
    val etat = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Referme la feuille avec son animation avant de passer la main.
    fun fermerPuis(suite: () -> Unit) {
        scope.launch { etat.hide() }.invokeOnCompletion { suite() }
    }
    FeuilleNocturne(onFermer = onPlusTard, etat = etat) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(Couleurs.Accent900, CircleShape)
                .border(1.dp, Couleurs.Accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icone(R.drawable.ic_bell, taille = 26.dp, couleur = Couleurs.Accent300) }
        Spacer(Modifier.height(16.dp))
        Text("Garde un œil sur ton chrono", style = Typo.GrandTitre, color = Couleurs.Texte)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Autorise les notifications pour voir ton chrono même quand l’appli est en arrière-plan.",
            style = Typo.CorpsDialogue,
            color = Couleurs.Neutre300,
        )
        Spacer(Modifier.height(22.dp))
        BoutonPrincipal(
            texte = "Autoriser les notifications",
            onClick = { fermerPuis(onAutoriser) },
            modifier = Modifier.fillMaxWidth(),
            hauteur = 56.dp,
            style = Typo.ActionFeuille,
        )
        BoutonTexte(
            texte = "Plus tard",
            onClick = { fermerPuis(onPlusTard) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            couleur = Couleurs.Neutre300,
        )
    }
}
