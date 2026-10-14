package app.irontap.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.irontap.donnees.Entrainement
import app.irontap.ui.accueil.ActionsAccueil
import app.irontap.ui.accueil.EcranAccueil
import app.irontap.ui.entrainement.EcranFormulaireEntrainement
import app.irontap.ui.entrainement.FormulaireEntrainementViewModel
import app.irontap.ui.saisie.EcranSaisie
import app.irontap.ui.theme.IronTapTheme
import kotlinx.serialization.Serializable

/** Routes typées de l'application (parcours : spec §3). */
@Serializable
data object Accueil

@Serializable
data object NouvelEntrainement

@Serializable
data class ModifierEntrainement(val id: Long)

@Serializable
data object Saisie

/** [demandesSaisie] : compteur d'appuis sur la notification du chrono, qui ramènent à l'écran de saisie. */
@Composable
fun IronTapApp(demandesSaisie: Int = 0) {
    IronTapTheme {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = Accueil) {
            composable<Accueil> {
                EcranAccueil(
                    actions = object : ActionsAccueil {
                        override fun nouvelEntrainement() = navController.navigate(NouvelEntrainement) { launchSingleTop = true }
                        override fun reprendre() = navController.ouvrirSaisie()

                        // Détail (écran 4) et export (§5) : étapes suivantes.
                        override fun ouvrir(entrainement: Entrainement) = Unit
                        override fun exporter() = Unit
                    },
                )
            }
            composable<NouvelEntrainement> {
                EcranFormulaireEntrainement(
                    vm = viewModel(factory = FormulaireEntrainementViewModel.fabrique(id = null)),
                    onLance = navController::ouvrirSaisie,
                    onFermer = { navController.popBackStack<NouvelEntrainement>(inclusive = true) },
                )
            }
            composable<ModifierEntrainement> { entree ->
                val route = entree.toRoute<ModifierEntrainement>()
                EcranFormulaireEntrainement(
                    vm = viewModel(factory = FormulaireEntrainementViewModel.fabrique(route.id)),
                    onLance = {},
                    onFermer = { navController.popBackStack<ModifierEntrainement>(inclusive = true) },
                )
            }
            composable<Saisie> {
                EcranSaisie(onQuitter = { navController.popBackStack<Saisie>(inclusive = true) })
            }
        }
        // Après le NavHost : le graphe est défini quand l'effet s'exécute.
        LaunchedEffect(demandesSaisie) {
            if (demandesSaisie > 0) navController.ouvrirSaisie()
        }
    }
}

/** Saisie de l'entraînement en cours, au-dessus de l'accueil : Retour y ramène toujours (spec §3). */
private fun NavController.ouvrirSaisie() = navigate(Saisie) {
    popUpTo<Accueil>()
    launchSingleTop = true
}
