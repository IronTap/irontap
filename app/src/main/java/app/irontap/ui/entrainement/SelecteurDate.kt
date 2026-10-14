package app.irontap.ui.entrainement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import app.irontap.logique.formaterDateCourte
import app.irontap.ui.composants.BoutonTexte
import app.irontap.ui.composants.FenetreDialogue
import app.irontap.ui.theme.Couleurs
import app.irontap.ui.theme.Typo
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Largeur minimale du `DatePicker` Material. */
private val LargeurCalendrier = 360.dp

/**
 * Sélecteur de date (2b) : calendrier Material dans la fenêtre des dialogues Nocturne (voile, marge 20 si la place le permet,
 * rayon 24, élévation lg), jour sélectionné en cercle accent-700 / texte accent-100, boutons texte accent-300.
 * Le DatePicker compte en millisecondes UTC.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelecteurDate(date: LocalDate, onChoisir: (LocalDate) -> Unit, onFermer: () -> Unit) {
    val etat = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    val choisie = etat.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
    val couleurs = DatePickerDefaults.colors(
        containerColor = Couleurs.Surface,
        titleContentColor = Couleurs.Neutre400,
        headlineContentColor = Couleurs.Texte,
        weekdayContentColor = Couleurs.Neutre500,
        subheadContentColor = Couleurs.Texte,
        navigationContentColor = Couleurs.Neutre300,
        yearContentColor = Couleurs.Neutre200,
        currentYearContentColor = Couleurs.Accent300,
        selectedYearContentColor = Couleurs.Accent100,
        selectedYearContainerColor = Couleurs.Accent700,
        dayContentColor = Couleurs.Neutre200,
        selectedDayContentColor = Couleurs.Accent100,
        selectedDayContainerColor = Couleurs.Accent700,
        todayContentColor = Couleurs.Accent300,
        todayDateBorderColor = Couleurs.Accent,
        dividerColor = Couleurs.Separateur,
    )
    // Le calendrier Material ne descend pas sous 360 dp : sur un écran étroit, la marge se réduit.
    val largeurEcran = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val marge = ((largeurEcran - LargeurCalendrier) / 2).coerceIn(0.dp, 20.dp)
    FenetreDialogue(onFermer = onFermer, forme = RoundedCornerShape(24.dp), marge = marge) {
        Column(Modifier.padding(bottom = 12.dp)) {
            DatePicker(
                state = etat,
                colors = couleurs,
                showModeToggle = false,
                title = {
                    Text(
                        "Sélectionner une date",
                        style = Typo.Libelle,
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 20.dp),
                    )
                },
                headline = {
                    Text(
                        choisie?.let(::formaterDateCourte).orEmpty(),
                        style = Typo.TitreDate,
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                    )
                },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            ) {
                BoutonTexte("Annuler", onClick = onFermer, margeHorizontale = 16.dp)
                BoutonTexte(
                    texte = "OK",
                    onClick = {
                        choisie?.let(onChoisir)
                        onFermer()
                    },
                    margeHorizontale = 16.dp,
                )
            }
        }
    }
}
