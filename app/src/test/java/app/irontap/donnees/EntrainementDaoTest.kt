package app.irontap.donnees

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.irontap.donnees.EntrainementDao.Suite
import app.irontap.donnees.EtatChrono.Etat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EntrainementDaoTest {

    private lateinit var base: BaseIronTap
    private lateinit var dao: EntrainementDao

    private val lundi = LocalDate.of(2026, 10, 12)
    private val t0 = Instant.parse("2026-10-12T17:00:00Z")

    @Before
    fun ouvrir() {
        base = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), BaseIronTap::class.java)
            .build()
        dao = base.entrainements()
    }

    @After
    fun fermer() = base.close()

    private suspend fun serie(
        id: Long,
        nom: String = "",
        poids: Double? = 40.0,
        reps: Int = 10,
        suite: Suite = Suite.NouvelleSerie,
        realtime: Long = 0,
    ) = dao.enregistrerSerie(id, nom, poids, reps, suite, t0, realtime)

    private suspend fun exercices(id: Long) = dao.observerExercices(id).first()

    /** `assertThrows` pour du code suspendu (le bloc est copié dans la coroutine du test). */
    private inline fun <reified T : Throwable> echoue(bloc: () -> Unit) {
        try {
            bloc()
        } catch (e: Throwable) {
            if (e is T) return
            throw e
        }
        throw AssertionError("${T::class.simpleName} attendue")
    }

    /** Exercices sous la forme « ordre.nom: ordre/poids×reps, … » pour comparer d'un coup d'œil. */
    private suspend fun resume(id: Long) = exercices(id).map { (exercice, series) ->
        "${exercice.ordre}.${exercice.nom}: " + series.joinToString { "${it.ordre}/${it.poidsKg}×${it.repetitions}" }
    }

    @Test
    fun `creer un entraînement le met en cours avec un chrono arrete`() = runTest {
        val id = dao.creerEntrainement(lundi, "  Dos / biceps ", t0)

        val enCours = dao.observerEnCours().first()
        assertEquals(Entrainement(id, lundi, "Dos / biceps", t0), enCours)
        assertEquals(EtatChrono(id), dao.observerChrono(id).first())
        assertTrue(dao.observerHistorique().first().isEmpty())
    }

    @Test
    fun `un libelle vide est enregistre comme absent`() = runTest {
        val id = dao.creerEntrainement(lundi, "   ", t0)
        assertNull(dao.lireEnCours()!!.libelle)

        dao.modifierEntrainement(id, lundi.minusDays(1), " Jambes ")
        assertEquals("Jambes", dao.lireEnCours()!!.libelle)
        dao.modifierEntrainement(id, lundi, "")
        assertNull(dao.lireEnCours()!!.libelle)
    }

    @Test
    fun `un seul entraînement en cours a la fois`() = runTest {
        dao.creerEntrainement(lundi, null, t0)
        echoue<IllegalStateException> { dao.creerEntrainement(lundi, null, t0) }
    }

    @Test
    fun `nouvelle serie reste sur l'exercice, nouvel exercice en ouvre un autre`() = runTest {
        val id = dao.creerEntrainement(lundi, null, t0)

        val s1 = serie(id, nom = "  Tirage vertical ", poids = 40.0, reps = 15)
        assertEquals(1, s1?.ordre)
        // Nom verrouillé : celui passé est ignoré.
        serie(id, nom = "ignoré", poids = 45.0, reps = 12, suite = Suite.NouvelExercice)
        assertTrue(dao.lireEnCours()!!.nouvelExercice)
        serie(id, nom = "Curl haltères", poids = 12.5, reps = 12)
        assertEquals(false, dao.lireEnCours()!!.nouvelExercice)
        val s4 = serie(id, poids = null, reps = 8)
        assertEquals(2, s4?.ordre)

        assertEquals(
            listOf("1.Tirage vertical: 1/40.0×15, 2/45.0×12", "2.Curl haltères: 1/12.5×12, 2/null×8"),
            resume(id),
        )
    }

    @Test
    fun `un nouvel exercice sans nom n'enregistre rien`() = runTest {
        val id = dao.creerEntrainement(lundi, null, t0)
        assertNull(serie(id, nom = "  "))
        assertTrue(exercices(id).isEmpty())
        assertEquals(EtatChrono(id), dao.lireChrono(id))
    }

    @Test
    fun `enregistrer une serie relance le chrono, meme en pause`() = runTest {
        val id = dao.creerEntrainement(lundi, null, t0)
        dao.modifierChrono(id) { it.demarre(1_000).enPause(9_000) }
        assertEquals(EtatChrono(id, Etat.Pause, null, 8_000), dao.lireChrono(id))

        serie(id, nom = "Squat", realtime = 20_000)

        assertEquals(EtatChrono(id, Etat.EnCours, demarreA = 20_000, cumulMs = 0), dao.observerChrono(id).first())
    }

    @Test
    fun `terminer un entraînement le range dans l'historique et supprime son chrono`() = runTest {
        val ancien = dao.creerEntrainement(lundi.minusDays(7), null, t0)
        serie(ancien, nom = "Squat")
        dao.terminer(ancien, t0)
        val id = dao.creerEntrainement(lundi, "Dos", t0)
        serie(id, nom = "Tirage", suite = Suite.NouvelExercice)
        val fin = t0.plusSeconds(3600)

        dao.terminer(id, fin)

        assertNull(dao.observerEnCours().first())
        assertNull(dao.lireChrono(id))
        val historique = dao.observerHistorique().first()
        assertEquals(listOf(id, ancien), historique.map { it.id })
        assertEquals(fin, historique.first().termineLe)
        assertEquals(false, historique.first().nouvelExercice)
    }

    @Test
    fun `terminer un entraînement sans serie le supprime`() = runTest {
        val id = dao.creerEntrainement(lundi, null, t0)
        dao.terminer(id, t0)
        assertNull(dao.observer(id).first())
        assertNull(dao.lireChrono(id))
    }

    @Test
    fun `supprimer une serie renumerote les suivantes`() = runTest {
        val id = dao.creerEntrainement(lundi, null, t0)
        serie(id, nom = "Tirage", reps = 15)
        val s2 = serie(id, reps = 12)
        serie(id, reps = 10)

        dao.supprimerSerie(s2!!.id)

        assertEquals(listOf("1.Tirage: 1/40.0×15, 2/40.0×10"), resume(id))
    }

    @Test
    fun `supprimer la derniere serie d'un exercice supprime l'exercice et renumerote les suivants`() = runTest {
        val id = dao.creerEntrainement(lundi, null, t0)
        serie(id, nom = "Tirage", suite = Suite.NouvelExercice)
        val seule = serie(id, nom = "Curl", suite = Suite.NouvelExercice)
        serie(id, nom = "Dips")
        dao.terminer(id, t0)

        dao.supprimerSerie(seule!!.id)

        assertEquals(listOf("1.Tirage: 1/40.0×10", "2.Dips: 1/40.0×10"), resume(id))
        // Un entraînement sans exercice reste dans l'historique.
        dao.supprimerSerie(exercices(id).first().series.single().id)
        dao.supprimerSerie(exercices(id).first().series.single().id)
        assertNotNull(dao.observer(id).first())
        assertTrue(exercices(id).isEmpty())
    }

    @Test
    fun `edition de l'historique`() = runTest {
        val id = dao.creerEntrainement(lundi, null, t0)
        val s1 = serie(id, nom = "Tirage", poids = 40.0, reps = 15)
        dao.terminer(id, t0)
        val exercice = exercices(id).single().exercice
        val plusTard = t0.plusSeconds(86_400)

        dao.renommerExercice(exercice.id, "  Tirage vertical ")
        dao.modifierSerie(s1!!.id, 41.25, 14, plusTard)
        val ajout = dao.ajouterSerie(exercice.id, null, 20, plusTard)

        assertEquals(2, ajout.ordre)
        assertEquals(listOf("1.Tirage vertical: 1/41.25×14, 2/null×20"), resume(id))
        assertEquals(plusTard, exercices(id).single().series.first().modifieLe)
    }

    @Test
    fun `supprimer un entraînement supprime ses exercices et series`() = runTest {
        val id = dao.creerEntrainement(lundi, null, t0)
        serie(id, nom = "Squat")
        dao.supprimerEntrainement(id)

        assertNull(dao.observer(id).first())
        assertTrue(exercices(id).isEmpty())
        assertTrue(dao.observerNomsExercices().first().isEmpty())
    }

    @Test
    fun `noms d'exercices du plus recemment utilise au plus ancien, sans doublon`() = runTest {
        val ancien = dao.creerEntrainement(lundi.minusDays(7), null, t0)
        serie(ancien, nom = "Squat", suite = Suite.NouvelExercice)
        serie(ancien, nom = "Tirage", suite = Suite.NouvelExercice)
        serie(ancien, nom = "Curl")
        dao.terminer(ancien, t0)
        val recent = dao.creerEntrainement(lundi, null, t0)
        serie(recent, nom = "Squat")

        assertEquals(listOf("Squat", "Curl", "Tirage"), dao.observerNomsExercices().first())
    }

    @Test
    fun `lecture complete triee pour le detail et l'export`() = runTest {
        val id = dao.creerEntrainement(lundi, "Dos", t0)
        serie(id, nom = "Tirage", reps = 15)
        serie(id, reps = 12, suite = Suite.NouvelExercice)
        serie(id, nom = "Curl", reps = 12)
        dao.terminer(id, t0)
        val enCours = dao.creerEntrainement(lundi, null, t0)
        serie(enCours, nom = "Squat")

        val complet = dao.lireComplet(id)!!
        assertEquals(listOf("Tirage", "Curl"), complet.exercices.map { it.exercice.nom })
        assertEquals(listOf(15, 12), complet.exercices.first().series.map { it.repetitions })
        assertEquals(listOf(id), dao.lireHistoriqueComplet().map { it.entrainement.id })
    }
}
