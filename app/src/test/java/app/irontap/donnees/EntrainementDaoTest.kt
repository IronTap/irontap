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

    // --- Supersets (spec §4.3.10) ---

    private fun onglet(nom: String, poids: Double?, reps: Int?) = SaisieOnglet(nom, poids, reps)

    /** Exercices sous la forme « ordre.nom[rôle]: tour/poids×reps, … ». */
    private suspend fun resumeSuperset(id: Long) = exercices(id).map { (exercice, series) ->
        "${exercice.ordre}.${exercice.nom}[${exercice.supersetRole ?: "-"}]: " +
            series.joinToString { (serie, paliers) -> "${serie.ordre}/" + paliers.joinToString("↓") { "${it.poidsKg}×${it.repetitions}" } }
    }

    @Test
    fun `superset, tours A et B ensemble, exercices crees au premier tour a la suite`() = runTest {
        val id = creer()
        serie(id, nom = "Développé militaire", poids = 30.0, reps = 10)
        // L'exercice courant a déjà une série : pas de superset.
        assertNull(dao.commencerSuperset(id, "Développé militaire", "x"))
        serie(id, poids = 30.0, reps = 10, suite = Suite.NouvelExercice)

        val superset = checkNotNull(dao.commencerSuperset(id, "Élévations latérales", "s1"))
        assertEquals(SupersetEnCours(id, "s1", nomA = "Élévations latérales"), superset)
        assertNull(dao.commencerSuperset(id, "Autre", "s2"))

        val premier = checkNotNull(dao.enregistrerTour(id, onglet("Élévations latérales", 8.0, 15), onglet(" Curl marteau ", 12.0, 12), false, t0, 5_000))
        assertEquals(1, premier.tour)
        assertEquals(setOf(RoleSuperset.A, RoleSuperset.B), premier.series.keys)
        assertEquals(EtatChrono(id, Etat.EnCours, demarreA = 5_000), dao.lireChrono(id))
        val deuxieme = checkNotNull(dao.enregistrerTour(id, onglet("ignoré", 8.0, 12), onglet("ignoré", 12.0, 10), false, t0, 6_000))
        assertEquals(2, deuxieme.tour)

        assertEquals(
            listOf(
                "1.Développé militaire[-]: 1/30.0×10, 2/30.0×10",
                "2.Élévations latérales[A]: 1/8.0×15, 2/8.0×12",
                "3.Curl marteau[B]: 1/12.0×12, 2/12.0×10",
            ),
            resumeSuperset(id),
        )
        val enCours = checkNotNull(dao.lireEnCoursComplet())
        assertEquals(setOf("s1"), enCours.exercices.mapNotNull { it.exercice.supersetId }.toSet())
        // Valeurs saisies effacées, onglet A actif, noms gardés.
        assertEquals(SupersetEnCours(id, "s1", nomA = "Élévations latérales"), enCours.superset)
    }

    @Test
    fun `superset, tour refuse sans rien ecrire si un nom manque`() = runTest {
        val id = creer()
        dao.commencerSuperset(id, "Élévations", "s1")
        assertNull(dao.enregistrerTour(id, onglet("Élévations", 8.0, 15), onglet("  ", 12.0, 12), false, t0, 5_000))
        assertNull(dao.enregistrerTour(id, null, null, false, t0, 5_000))
        assertTrue(exercices(id).isEmpty())
        assertEquals(EtatChrono(id), dao.lireChrono(id))
    }

    @Test
    fun `superset, degressive sur l'onglet B puis tour, palier garde par onglet`() = runTest {
        val id = creer()
        dao.commencerSuperset(id, "", "s1")
        // Le nom de B se verrouille, son exercice n'est créé qu'avec le tour, après A. Sans nom pour A, rien n'est gardé.
        assertNotNull(dao.ajouterPalierEnCours(id, "Curl", 14.0, 10, RoleSuperset.B))
        assertNull(dao.ajouterPalierEnCours(id, " ", 14.0, 10, RoleSuperset.A))
        val enCours = checkNotNull(dao.lireEnCoursComplet())
        assertTrue(enCours.exercices.isEmpty())
        assertEquals("Curl", enCours.superset?.nomB)
        assertEquals(listOf(RoleSuperset.B), enCours.paliersEnCours.map { it.role })

        dao.enregistrerTour(id, onglet("Élévations", 8.0, 15), onglet("", 10.0, 8), false, t0, 5_000)
        assertEquals(listOf("1.Élévations[A]: 1/8.0×15", "2.Curl[B]: 1/14.0×10↓10.0×8"), resumeSuperset(id))
        assertTrue(checkNotNull(dao.lireEnCoursComplet()).paliersEnCours.isEmpty())
    }

    @Test
    fun `nouvel exercice termine le superset, onglet vide ignore, exercice seul redevenu ordinaire`() = runTest {
        val id = creer()
        dao.commencerSuperset(id, "Élévations", "s1")
        dao.enregistrerTour(id, onglet("Élévations", 8.0, 15), onglet("Curl", 12.0, 12), false, t0, 5_000)
        // Fin sur A seul : le superset reste un superset (B a une série au tour 1).
        val fin = checkNotNull(dao.enregistrerTour(id, onglet("", 8.0, 12), null, true, t0, 6_000))
        assertEquals(2 to setOf(RoleSuperset.A), fin.tour to fin.series.keys)
        val apres = checkNotNull(dao.lireEnCoursComplet())
        assertNull(apres.superset)
        assertTrue(apres.entrainement.nouvelExercice)
        assertEquals(listOf("1.Élévations[A]: 1/8.0×15, 2/8.0×12", "2.Curl[B]: 1/12.0×12"), resumeSuperset(id))

        // Premier tour avec A seul : A redevient un exercice ordinaire.
        dao.commencerSuperset(id, "Fentes", "s2")
        dao.enregistrerTour(id, onglet("Fentes", 20.0, 10), null, true, t0, 7_000)
        assertEquals("3.Fentes[-]: 1/20.0×10", resumeSuperset(id).last())
        // Les deux onglets vides : rien d'enregistré, le superset se termine.
        dao.commencerSuperset(id, "Gainage", "s3")
        val vide = checkNotNull(dao.enregistrerTour(id, null, null, true, t0, 8_000))
        assertTrue(vide.series.isEmpty())
        assertNull(checkNotNull(dao.lireEnCoursComplet()).superset)
        assertEquals(3, exercices(id).size)
    }

    @Test
    fun `terminer pendant un superset, avec ou sans le tour`() = runTest {
        val id = creer()
        serie(id, nom = "Squat", suite = Suite.NouvelExercice)
        dao.commencerSuperset(id, "Élévations", "s1")
        dao.ajouterPalierEnCours(id, "Élévations", 8.0, 15, RoleSuperset.A)
        dao.enregistrerTourEtTerminer(id, onglet("", 6.0, 12), null, t0, 0)
        assertEquals(listOf("1.Squat[-]: 1/40.0×10", "2.Élévations[-]: 1/8.0×15↓6.0×12"), resumeSuperset(id))

        val autre = creer()
        serie(autre, nom = "Squat", suite = Suite.NouvelExercice)
        dao.commencerSuperset(autre, "Élévations", "s2")
        dao.ajouterPalierEnCours(autre, "Élévations", 8.0, 15, RoleSuperset.A)
        dao.terminer(autre, t0)
        assertEquals(listOf("1.Squat[-]: 1/40.0×10"), resumeSuperset(autre))
        assertNull(dao.lireEnCoursComplet())
    }

    /** Superset terminé de trois tours : A 8×15, 8×12, 6×12 et B 12×12, 12×10, 10×10. */
    private suspend fun supersetTermine(): Triple<Long, Long, Long> {
        val id = creer()
        dao.commencerSuperset(id, "Élévations", "s1")
        dao.enregistrerTour(id, onglet("Élévations", 8.0, 15), onglet("Curl", 12.0, 12), false, t0, 0)
        dao.enregistrerTour(id, onglet("", 8.0, 12), onglet("", 12.0, 10), false, t0, 0)
        dao.enregistrerTour(id, onglet("", 6.0, 12), onglet("", 10.0, 10), true, t0, 0)
        dao.terminer(id, t0)
        val (a, b) = exercices(id).map { it.exercice.id }
        return Triple(id, a, b)
    }

    private suspend fun serieDuTour(exerciceId: Long, tour: Int, id: Long) =
        exercices(id).first { it.exercice.id == exerciceId }.series.first { it.serie.ordre == tour }.serie.id

    @Test
    fun `supprimer dans un superset, les tours ne sont renumerotes que vides`() = runTest {
        val (id, a, b) = supersetTermine()
        // A du tour 2 : B reste, rien n'est renuméroté.
        dao.supprimerSeries(listOf(serieDuTour(a, 2, id)))
        assertEquals(listOf("1.Élévations[A]: 1/8.0×15, 3/6.0×12", "2.Curl[B]: 1/12.0×12, 2/12.0×10, 3/10.0×10"), resumeSuperset(id))
        // B du tour 2 : le tour est vide, le tour 3 devient le 2 pour A et B.
        dao.supprimerSeries(listOf(serieDuTour(b, 2, id)))
        assertEquals(listOf("1.Élévations[A]: 1/8.0×15, 2/6.0×12", "2.Curl[B]: 1/12.0×12, 2/10.0×10"), resumeSuperset(id))
        // Les deux d'un coup.
        dao.supprimerSeries(listOf(serieDuTour(a, 1, id), serieDuTour(b, 1, id)))
        assertEquals(listOf("1.Élévations[A]: 1/6.0×12", "2.Curl[B]: 1/10.0×10"), resumeSuperset(id))
        // Plus de série pour A : l'exercice disparaît, B redevient ordinaire.
        dao.supprimerSeries(listOf(serieDuTour(a, 1, id)))
        assertEquals(listOf("1.Curl[-]: 1/10.0×10"), resumeSuperset(id))
    }

    @Test
    fun `exercice d'un superset laisse seul, ses series sont renumerotees a la suite`() = runTest {
        val (id, a, b) = supersetTermine()
        dao.supprimerSeries(listOf(serieDuTour(b, 2, id)))
        dao.supprimerSeries(listOf(serieDuTour(b, 1, id), serieDuTour(b, 3, id)))
        assertEquals(listOf("1.Élévations[-]: 1/8.0×15, 2/8.0×12, 3/6.0×12"), resumeSuperset(id))
        assertNotNull(a)
    }

    @Test
    fun `ajouter un tour, completer un tour, modifier un tour`() = runTest {
        val (id, a, b) = supersetTermine()
        dao.supprimerSeries(listOf(serieDuTour(b, 3, id)))
        assertEquals(4, dao.ecrireTour(a, b, null, listOf(ValeursPalier(6.0, 10)), null, t0))
        // Case « — » du tour 3 complétée.
        dao.ajouterSerie(b, listOf(ValeursPalier(10.0, 8)), t0, ordre = 3)
        assertEquals(2, dao.ecrireTour(a, b, 2, listOf(ValeursPalier(9.0, 12)), listOf(ValeursPalier(12.0, 9), ValeursPalier(8.0, 6)), t0))
        assertEquals(
            listOf(
                "1.Élévations[A]: 1/8.0×15, 2/9.0×12, 3/6.0×12, 4/6.0×10",
                "2.Curl[B]: 1/12.0×12, 2/12.0×9↓8.0×6, 3/10.0×8",
            ),
            resumeSuperset(id),
        )
    }

    @Test
    fun `historique d'un exercice fait en superset, avec son partenaire`() = runTest {
        supersetTermine()
        val seul = creer(lundi.minusDays(3))
        serie(seul, nom = "Curl")
        dao.terminer(seul, t0)
        val enCours = creer()
        val lignes = dao.lireHistoriqueExercice(enCours, "curl")
        assertEquals(listOf("Élévations", null), lignes.map { it.partenaire }.distinct())
        assertEquals(listOf("Curl"), dao.lireHistoriqueExercice(enCours, "Élévations").map { it.partenaire }.distinct())
    }

    @Test
    fun `memoriser un superset termine ou remplace est sans effet`() = runTest {
        val id = creer()
        val superset = checkNotNull(dao.commencerSuperset(id, "A", "s1"))
        dao.memoriserSuperset(superset.copy(nomB = "B", actif = RoleSuperset.B, poidsB = "12", repetitionsB = "10"))
        assertEquals(superset.copy(nomB = "B", actif = RoleSuperset.B, poidsB = "12", repetitionsB = "10"), dao.lireEnCoursComplet()?.superset)
        dao.enregistrerTour(id, null, null, true, t0, 0)
        dao.memoriserSuperset(superset)
        assertNull(dao.lireEnCoursComplet()?.superset)
        dao.commencerSuperset(id, "C", "s2")
        dao.memoriserSuperset(superset.copy(nomA = "périmé"))
        assertEquals("C", dao.lireEnCoursComplet()?.superset?.nomA)
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
