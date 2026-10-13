package app.irontap.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.irontap.ui.theme.IronTapTheme
import kotlinx.serialization.Serializable

/** Routes typées de l'application. Les autres écrans s'ajouteront ici. */
@Serializable
data object Accueil

@Composable
fun IronTapApp() {
    IronTapTheme {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = Accueil) {
            // Provisoire : la planche du thème tient lieu d'accueil jusqu'à l'écran 1.
            composable<Accueil> { Nuancier() }
        }
    }
}
