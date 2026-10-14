package app.irontap.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.irontap.IronTapApplication
import app.irontap.ui.saisie.EcranSaisie
import app.irontap.ui.theme.IronTapTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Routes typées de l'application. Les autres écrans s'ajouteront ici. */
@Serializable
data object Accueil

@Serializable
data object Saisie

/** [demandesSaisie] : compteur d'appuis sur la notification du chrono, qui ramènent à l'écran de saisie. */
@Composable
fun IronTapApp(demandesSaisie: Int = 0) {
    IronTapTheme {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = Accueil) {
            // Provisoire : la planche du thème tient lieu d'accueil jusqu'à l'écran 1. Son bouton reprend
            // l'entraînement en cours, ou en crée un (date du jour, sans libellé) à la place de l'écran 2.
            composable<Accueil> {
                val conteneur = (LocalContext.current.applicationContext as IronTapApplication).container
                val scope = rememberCoroutineScope()
                Nuancier(
                    onSaisie = {
                        scope.launch {
                            val dao = conteneur.base.entrainements()
                            if (dao.lireEnCours() == null) {
                                dao.creerEntrainement(LocalDate.now(), null, conteneur.horloge.maintenant())
                            }
                            navController.navigate(Saisie) { launchSingleTop = true }
                        }
                    },
                )
            }
            composable<Saisie> {
                EcranSaisie(onQuitter = { navController.popBackStack<Saisie>(inclusive = true) })
            }
        }
        // Après le NavHost : le graphe est défini quand l'effet s'exécute.
        LaunchedEffect(demandesSaisie) {
            if (demandesSaisie > 0) {
                // Retour depuis la saisie : l'accueil, quel que soit l'écran d'où l'on vient (spec §3).
                navController.navigate(Saisie) {
                    popUpTo<Accueil>()
                    launchSingleTop = true
                }
            }
        }
    }
}
