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
import app.irontap.ui.apropos.ComposantLicence
import app.irontap.ui.apropos.EcranAPropos
import app.irontap.ui.apropos.EcranLicence
import app.irontap.ui.detail.DetailViewModel
import app.irontap.ui.detail.EcranDetail
import app.irontap.ui.detail.NavigationDetail
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

@Serializable
data class Detail(val id: Long)

@Serializable
data object APropos

/** [composant] : nom d'un [ComposantLicence]. */
@Serializable
data class Licence(val composant: String)

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

                        override fun ouvrir(entrainement: Entrainement) = navController.navigate(Detail(entrainement.id)) {
                            launchSingleTop = true
                        }

                        override fun aPropos() = navController.navigate(APropos) { launchSingleTop = true }
                    },
                )
            }
            composable<APropos> {
                EcranAPropos(
                    onRetour = { navController.popBackStack<APropos>(inclusive = true) },
                    onLicence = { navController.navigate(Licence(it.name)) { launchSingleTop = true } },
                )
            }
            composable<Licence> { entree ->
                val route = entree.toRoute<Licence>()
                EcranLicence(ComposantLicence.valueOf(route.composant), onRetour = { navController.popBackStack<Licence>(inclusive = true) })
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
            composable<Detail> { entree ->
                val route = entree.toRoute<Detail>()
                EcranDetail(
                    vm = viewModel(factory = DetailViewModel.fabrique(route.id)),
                    navigation = object : NavigationDetail {
                        override fun retour() {
                            navController.popBackStack<Detail>(inclusive = true)
                        }

                        override fun modifierEntrainement() = navController.navigate(ModifierEntrainement(route.id)) {
                            launchSingleTop = true
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
            if (demandesSaisie > 0) navController.ouvrirSaisie()
        }
    }
}

/** Saisie de l'entraînement en cours, au-dessus de l'accueil : Retour y ramène toujours (spec §3). */
private fun NavController.ouvrirSaisie() = navigate(Saisie) {
    popUpTo<Accueil>()
    launchSingleTop = true
}
