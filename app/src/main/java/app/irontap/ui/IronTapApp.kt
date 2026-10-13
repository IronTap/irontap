package app.irontap.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable

/** Routes typées de l'application. Les autres écrans s'ajouteront ici. */
@Serializable
data object Accueil

@Composable
fun IronTapApp() {
    // Thème provisoire, remplacé à l'étape suivante par le thème Nocturne.
    MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF161826), surface = Color(0xFF161826))) {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = Accueil) {
            composable<Accueil> { EcranProvisoire() }
        }
    }
}

@Composable
private fun EcranProvisoire() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center) {
            Text("IronTap", style = MaterialTheme.typography.headlineMedium)
        }
    }
}
