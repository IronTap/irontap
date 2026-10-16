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

    private suspend fun creer(date: LocalDate = lundi, libelle: String? = null) =
        checkNotNull(dao.creerEntrainement(date, libelle, t0))

    /** Exercices sous la forme « ordre.nom: ordre/poids×reps, … » pour comparer d'un coup d'œil. */
    private suspend fun resume(id: Long) = exercices(id).map { (exercice, series) ->
        "${exercice.ordre}.${exercice.nom}: " + series.joinToString { (serie, paliers) -> "${serie.ordre}/" + paliers.joinToString("↓") { "${it.poidsKg}×${it.repetitions}" } }
    }

    @Test
    fun `creer un entraînement le met en cours avec un chrono arrete`() = runTest {
        val id = creer(lundi, "  Dos / biceps ")

        val enCours = dao.observerEnCours().first()
        assertEquals(Entrainement(id, lundi, "Dos / biceps", t0), enCours)
        assertEquals(EtatChrono(id), dao.observerChrono(id).first())
        assertTrue(dao.observerHistorique().first().isEmpty())
    }

    @Test
    fun `un libelle vide est enregistre comme absent`() = runTest {
        val id = creer(lundi, "   ")
        assertNull(dao.lireEnCours()!!.libelle)

        dao.modifierEntrainement(id, lundi.minusDays(1), " Jambes ")
        assertEquals("Jambes", dao.lireEnCours()!!.libelle)
        dao.modifierEntrainement(id, lundi, "")
        assertNull(dao.lireEnCours()!!.libelle)
    }

    @Test
    fun `un seul entraînement en cours a la fois`() = runTest {
        val id = creer()
        assertNull(dao.creerEntrainement(lundi, "Autre", t0))
        assertEquals(id, dao.lireEnCours()!!.id)
    }

    @Test
    fun `nouvelle serie reste sur l'exercice, nouvel exercice en ouvre un autre`() = runTest {
        val id = creer()

        val s1 = serie(id, nom = "  Tirage vertical ", poids = 40.0, reps = 15)
        assertEquals(1, s1?.serie?.ordre)
        // Nom verrouillé : celui passé est ignoré.
        serie(id, nom = "ignoré", poids = 45.0, reps = 12, suite = Suite.NouvelExercice)
        assertTrue(dao.lireEnCours()!!.nouvelExercice)
        serie(id, nom = "Curl haltères", poids = 12.5, reps = 12)
        assertEquals(false, dao.lireEnCours()!!.nouvelExercice)
        val s4 = serie(id, poids = null, reps = 8)
        assertEquals(2, s4?.serie?.ordre)

        assertEquals(
            listOf("1.Tirage vertical: 1/40.0×15, 2/45.0×12", "2.Curl haltères: 1/12.5×12, 2/null×8"),
            resume(id),
        )
    }

    @Test
    fun `un nouvel exercice sans nom n'enregistre rien`() = runTest {
        val id = creer()
        assertNull(serie(id, nom = "  "))
        assertTrue(exercices(id).isEmpty())
        assertEquals(EtatChrono(id), dao.lireChrono(id))
    }

    @Test
    fun `enregistrer une serie relance le chrono, meme en pause`() = runTest {
        val id = creer()
        dao.modifierChrono(id) { it.demarre(1_000).enPause(9_000) }
        assertEquals(EtatChrono(id, Etat.Pause, null, 8_000), dao.lireChrono(id))

        serie(id, nom = "Squat", realtime = 20_000)

        assertEquals(EtatChrono(id, Etat.EnCours, demarreA = 20_000, cumulMs = 0), dao.observerChrono(id).first())
    }

    @Test
    fun `terminer un entraînement le range dans l'historique et supprime son chrono`() = runTest {
        val ancien = creer(lundi.minusDays(7))
        serie(ancien, nom = "Squat")
        dao.terminer(ancien, t0)
        val id = creer(lundi, "Dos")
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
        val id = creer()
        dao.terminer(id, t0)
        assertNull(dao.observer(id).first())
        assertNull(dao.lireChrono(id))
    }

    @Test
    fun `supprimer une serie renumerote les suivantes`() = runTest {
        val id = creer()
        serie(id, nom = "Tirage", reps = 15)
        val s2 = serie(id, reps = 12)
        serie(id, reps = 10)

        dao.supprimerSerie(s2!!.serie.id)

        assertEquals(listOf("1.Tirage: 1/40.0×15, 2/40.0×10"), resume(id))
    }

    @Test
    fun `supprimer la derniere serie d'un exercice supprime l'exercice et renumerote les suivants`() = runTest {
        val id = creer()
        serie(id, nom = "Tirage", suite = Suite.NouvelExercice)
        val seule = serie(id, nom = "Curl", suite = Suite.NouvelExercice)
        serie(id, nom = "Dips")
        dao.terminer(id, t0)

        dao.supprimerSerie(seule!!.serie.id)

        assertEquals(listOf("1.Tirage: 1/40.0×10", "2.Dips: 1/40.0×10"), resume(id))
        // Un entraînement sans exercice reste dans l'historique.
        dao.supprimerSerie(exercices(id).first().series.single().serie.id)
        dao.supprimerSerie(exercices(id).first().series.single().serie.id)
        assertNotNull(dao.observer(id).first())
        assertTrue(exercices(id).isEmpty())
    }

    @Test
    fun `edition de l'historique`() = runTest {
        val id = creer()
        val s1 = serie(id, nom = "Tirage", poids = 40.0, reps = 15)
        dao.terminer(id, t0)
        val exercice = exercices(id).single().exercice
        val plusTard = t0.plusSeconds(86_400)

        dao.renommerExercice(exercice.id, "  Tirage vertical ")
        dao.modifierSerie(s1!!.serie.id, listOf(ValeursPalier(41.25, 14)), plusTard)
        val ajout = dao.ajouterSerie(exercice.id, listOf(ValeursPalier(null, 20)), plusTard)

        assertEquals(2, ajout.serie.ordre)
        assertEquals(listOf("1.Tirage vertical: 1/41.25×14, 2/null×20"), resume(id))
        assertEquals(plusTard, exercices(id).single().series.first().serie.modifieLe)
    }

    @Test
    fun `supprimer un entraînement supprime ses exercices et series`() = runTest {
        val id = creer()
        serie(id, nom = "Squat")
        dao.supprimerEntrainement(id)

        assertNull(dao.observer(id).first())
        assertTrue(exercices(id).isEmpty())
        assertTrue(dao.observerNomsExercices().first().isEmpty())
    }

    @Test
    fun `noms d'exercices du plus recemment utilise au plus ancien, sans doublon`() = runTest {
        val ancien = creer(lundi.minusDays(7))
        serie(ancien, nom = "Squat", suite = Suite.NouvelExercice)
        serie(ancien, nom = "Tirage", suite = Suite.NouvelExercice)
        serie(ancien, nom = "Curl")
        dao.terminer(ancien, t0)
        val recent = creer()
        serie(recent, nom = "Squat")

        assertEquals(listOf("Squat", "Curl", "Tirage"), dao.observerNomsExercices().first())
    }

    @Test
    fun `historique d'un exercice, autres entrainements, casse et accents ignores, du plus recent au plus ancien`() = runTest {
        val ancien = creer(lundi.minusDays(14), "Pecs")
        serie(ancien, nom = "Développé couché", poids = 50.0, reps = 10)
        serie(ancien, poids = 55.0, reps = 8)
        dao.terminer(ancien, t0)
        // Deux séances le même jour : la plus récemment créée (cree_le) d'abord, même saisie avant l'autre.
        val soir = checkNotNull(dao.creerEntrainement(lundi.minusDays(7), "Soir", t0.plusSeconds(3_600)))
        serie(soir, nom = "Développé couché", poids = 52.5, reps = 9)
        dao.terminer(soir, t0)
        // Exercice fait deux fois dans la séance : séries à la suite.
        val matin = checkNotNull(dao.creerEntrainement(lundi.minusDays(7), null, t0))
        serie(matin, nom = "developpe COUCHE", poids = null, reps = 12, suite = Suite.NouvelExercice)
        serie(matin, nom = "Squat", suite = Suite.NouvelExercice)
        serie(matin, nom = "Développé couché", poids = 60.0, reps = 6)
        dao.terminer(matin, t0)
        // Séance saisie après coup, datée avant les autres : en dernier.
        val rattrapage = creer(lundi.minusDays(21))
        serie(rattrapage, nom = "Développé couché", poids = 45.0, reps = 12)
        dao.terminer(rattrapage, t0)
        val enCours = creer()
        serie(enCours, nom = "Développé couché", poids = 70.0, reps = 3, suite = Suite.NouvelExercice)
        serie(enCours, nom = "Fentes")

        val series = dao.lireHistoriqueExercice(enCours, " DEVELOPPE couché ")
        assertEquals(listOf(soir, matin, matin, ancien, ancien, rattrapage), series.map { it.entrainementId })
        assertEquals(listOf(9, 12, 6, 10, 8, 12), series.map { it.repetitions })
        assertEquals(listOf(52.5, null, 60.0, 50.0, 55.0, 45.0), series.map { it.poidsKg })
        assertEquals(SerieHistorique(soir, lundi.minusDays(7), "Soir", "Développé couché", series.first().serieId, 52.5, 9), series.first())
        assertEquals("developpe COUCHE", series[1].nom)

        // Exercice de l'entraînement en cours seulement, nom inconnu ou vide : rien.
        assertTrue(dao.lireHistoriqueExercice(enCours, "Fentes").isEmpty())
        assertTrue(dao.lireHistoriqueExercice(enCours, "Rowing").isEmpty())
        assertTrue(dao.lireHistoriqueExercice(enCours, " ").isEmpty())
        assertEquals(
            setOf("Développé couché", "developpe COUCHE", "Squat"),
            dao.observerNomsExercicesHors(enCours).first().toSet(),
        )
    }

    @Test
    fun `degressive, paliers gardes sans toucher au chrono, puis une seule serie`() = runTest {
        val id = creer()
        serie(id, nom = "Squat", poids = 100.0, reps = 5, suite = Suite.NouvelExercice, realtime = 2_000)
        val chrono = dao.lireChrono(id)

        // Premier palier d'un nouvel exercice : l'exercice est ouvert (nom verrouillé), sans série.
        assertNotNull(dao.ajouterPalierEnCours(id, " Tirage vertical ", 50.0, 10))
        assertNotNull(dao.ajouterPalierEnCours(id, "ignoré", 30.0, 8))
        val enCours = checkNotNull(dao.lireEnCoursComplet())
        assertEquals(listOf("Squat", "Tirage vertical"), enCours.exercices.map { it.exercice.nom })
        assertTrue(enCours.exercices[1].series.isEmpty())
        assertEquals(false, enCours.entrainement.nouvelExercice)
        assertEquals(listOf(ValeursPalier(50.0, 10), ValeursPalier(30.0, 8)), enCours.paliersEnCours.map { it.valeurs })
        assertEquals(listOf(1, 2), enCours.paliersEnCours.map { it.ordre })
        assertEquals(chrono, dao.lireChrono(id))

        val enregistree = checkNotNull(dao.enregistrerSerie(id, "", 20.0, 6, Suite.NouvelleSerie, t0, 9_000))
        assertEquals(1, enregistree.serie.ordre)
        assertEquals(listOf(ValeursPalier(50.0, 10), ValeursPalier(30.0, 8), ValeursPalier(20.0, 6)), enregistree.valeurs)
        assertEquals(listOf("1.Squat: 1/100.0×5", "2.Tirage vertical: 1/50.0×10↓30.0×8↓20.0×6"), resume(id))
        assertTrue(checkNotNull(dao.lireEnCoursComplet()).paliersEnCours.isEmpty())
        assertEquals(EtatChrono(id, Etat.EnCours, demarreA = 9_000), dao.lireChrono(id))
    }

    @Test
    fun `degressive sans nom pour un nouvel exercice, rien n'est garde`() = runTest {
        val id = creer()
        assertNull(dao.ajouterPalierEnCours(id, "  ", 50.0, 10))
        assertTrue(checkNotNull(dao.lireEnCoursComplet()).let { it.exercices.isEmpty() && it.paliersEnCours.isEmpty() })
        // Ni sur un entraînement terminé.
        serie(id, nom = "Squat")
        dao.terminer(id, t0)
        assertNull(dao.ajouterPalierEnCours(id, "Squat", 50.0, 10))
    }

    @Test
    fun `4a avec seulement des paliers gardes, et fin qui abandonne une degressive`() = runTest {
        val id = creer()
        serie(id, nom = "Squat", suite = Suite.NouvelExercice)
        dao.ajouterPalierEnCours(id, "Tirage", 50.0, 10)
        dao.enregistrerSerieEtTerminer(id, "", null, null, t0, 0)
        assertEquals(listOf("1.Squat: 1/40.0×10", "2.Tirage: 1/50.0×10"), resume(id))

        // Terminer sans enregistrer : paliers gardés et exercice ouvert par le premier palier abandonnés.
        val autre = creer()
        serie(autre, nom = "Squat", suite = Suite.NouvelExercice)
        dao.ajouterPalierEnCours(autre, "Tirage", 50.0, 10)
        dao.terminer(autre, t0)
        assertEquals(listOf("1.Squat: 1/40.0×10"), resume(autre))
        // Seulement une dégressive commencée : l'entraînement sans série est supprimé.
        val vide = creer()
        dao.ajouterPalierEnCours(vide, "Tirage", 50.0, 10)
        dao.terminer(vide, t0)
        assertNull(dao.lireComplet(vide))
    }

    @Test
    fun `modifier une serie remplace tous ses paliers`() = runTest {
        val id = creer()
        val simple = checkNotNull(serie(id, nom = "Tirage", poids = 45.0, reps = 10))
        dao.terminer(id, t0)
        val plusTard = t0.plusSeconds(60)

        dao.modifierSerie(simple.serie.id, listOf(ValeursPalier(45.0, 10), ValeursPalier(30.0, 8), ValeursPalier(null, 6)), plusTard)
        assertEquals(listOf("1.Tirage: 1/45.0×10↓30.0×8↓null×6"), resume(id))
        assertEquals(plusTard, exercices(id).single().series.single().serie.modifieLe)

        dao.modifierSerie(simple.serie.id, listOf(ValeursPalier(40.0, 12)), plusTard)
        assertEquals(listOf("1.Tirage: 1/40.0×12"), resume(id))
        assertEquals(listOf(1), exercices(id).single().series.single().paliers.map { it.ordre })

        // Supprimer la série emporte ses paliers ; l'exercice vide disparaît.
        dao.supprimerSerie(simple.serie.id)
        assertTrue(exercices(id).isEmpty())
    }

    @Test
    fun `historique d'un exercice, une ligne par palier dans l'ordre`() = runTest {
        val passe = creer(lundi.minusDays(7))
        serie(passe, nom = "Tirage", poids = 40.0, reps = 15)
        dao.ajouterPalierEnCours(passe, "Tirage", 45.0, 10)
        serie(passe, poids = 30.0, reps = 8)
        dao.terminer(passe, t0)
        val enCours = creer()

        val lignes = dao.lireHistoriqueExercice(enCours, "tirage")
        assertEquals(listOf(15, 10, 8), lignes.map { it.repetitions })
        val (simple, degressive) = exercices(passe).single().series.map { it.serie.id }
        assertEquals(listOf(simple, degressive, degressive), lignes.map { it.serieId })
    }

    @Test
    fun `lecture complete triee pour le detail et l'export`() = runTest {
        val id = creer(lundi, "Dos")
        serie(id, nom = "Tirage", reps = 15)
        serie(id, reps = 12, suite = Suite.NouvelExercice)
        serie(id, nom = "Curl", reps = 12)
        dao.terminer(id, t0)
        val enCours = creer()
        serie(enCours, nom = "Squat")

        val complet = dao.lireComplet(id)!!
        assertEquals(listOf("Tirage", "Curl"), complet.exercices.map { it.exercice.nom })
        assertEquals(listOf(15, 12), complet.exercices.first().series.map { it.paliers.single().repetitions })
        assertEquals(listOf(id), dao.observerHistoriqueComplet().first().map { it.entrainement.id })
    }

    @Test
    fun `apres nouvel exercice, un nom vide n'enregistre rien et laisse le chrono intact`() = runTest {
        val id = creer()
        serie(id, nom = "Squat", suite = Suite.NouvelExercice, realtime = 5_000)
        val chrono = dao.lireChrono(id)

        assertNull(serie(id, nom = " ", realtime = 9_000))

        assertEquals(listOf("1.Squat: 1/40.0×10"), resume(id))
        assertEquals(chrono, dao.lireChrono(id))
        assertTrue(dao.lireEnCours()!!.nouvelExercice)
    }

    @Test
    fun `aucune serie n'est enregistree sur un entrainement termine ou supprime`() = runTest {
        val id = creer()
        serie(id, nom = "Squat")
        dao.terminer(id, t0)
        assertNull(serie(id, nom = "Squat"))
        assertEquals(listOf("1.Squat: 1/40.0×10"), resume(id))
        assertNull(dao.lireChrono(id))

        val vide = creer()
        dao.terminer(vide, t0)
        assertNull(serie(vide, nom = "Squat"))
    }

    @Test
    fun `l'entrainement en cours complet et son chrono`() = runTest {
        assertNull(dao.observerEnCoursComplet().first())
        val id = creer(libelle = "Dos")
        serie(id, nom = "Tirage", reps = 15)
        serie(id, reps = 12, suite = Suite.NouvelExercice, realtime = 3_000)
        serie(id, nom = "Curl", reps = 12, realtime = 4_000)

        val enCours = dao.observerEnCoursComplet().first()!!
        assertEquals(id, enCours.entrainement.id)
        assertEquals(listOf("Tirage", "Curl"), enCours.exercices.map { it.exercice.nom })
        assertEquals(listOf(listOf(15, 12), listOf(12)), enCours.exercices.map { e -> e.series.map { it.paliers.single().repetitions } })
        assertEquals(EtatChrono(id, Etat.EnCours, demarreA = 4_000), enCours.chrono)

        dao.terminer(id, t0)
        assertNull(dao.observerEnCoursComplet().first())
    }
}
