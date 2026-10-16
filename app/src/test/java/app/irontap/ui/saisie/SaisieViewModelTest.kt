package app.irontap.ui.saisie

import android.content.Context
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.irontap.donnees.BaseIronTap
import app.irontap.donnees.EntrainementDao
import app.irontap.donnees.EntrainementDao.Suite
import app.irontap.donnees.EtatChrono
import app.irontap.donnees.Horloge
import app.irontap.donnees.ValeursPalier
import app.irontap.logique.HistoriqueExercice
import app.irontap.logique.LigneHistorique
import app.irontap.logique.SeanceHistorique
import app.irontap.ui.ViewModelsDeTest
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Règles de l'écran Saisie série (spec §4.3), sur une base Room en mémoire. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SaisieViewModelTest {

    private val vms = ViewModelsDeTest()
    private lateinit var base: BaseIronTap
    private lateinit var dao: EntrainementDao
    private val brouillon = BrouillonSaisie()

    private val horloge = object : Horloge {
        var realtime = 10_000L
        override fun maintenant(): Instant = Instant.parse("2026-10-12T17:00:00Z")
        override fun realtime() = realtime
    }

    @Before
    fun ouvrir() {
        base = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), BaseIronTap::class.java)
            .build()
        dao = base.entrainements()
    }

    @After
    fun fermer() {
        vms.vider()
        base.close()
        Dispatchers.resetMain()
    }

    /** Toasts affichés : le temps virtuel du test les efface aussitôt, on les relève au passage. */
    private val toasts = CopyOnWriteArrayList<String>()

    /** ViewModel abonné comme par l'écran ; les coroutines du ViewModel s'exécutent sur le dispatcher du test. */
    private fun TestScope.ecran(brouillon: BrouillonSaisie = this@SaisieViewModelTest.brouillon): SaisieViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        return vms.garder(SaisieViewModel(dao, horloge, brouillon)).also { vm ->
            backgroundScope.launch { vm.etat.collect {} }
            backgroundScope.launch { vm.suggestions.collect {} }
            backgroundScope.launch { vm.formulaire.collect { f -> f.toast?.let { if (toasts.lastOrNull() != it) toasts += it } } }
        }
    }

    private suspend fun dernierToast(): String = withContext(Dispatchers.Default) {
        withTimeout(5_000) {
            while (toasts.isEmpty()) delay(10)
            toasts.last()
        }
    }

    /** Attend (en temps réel : Room écrit sur ses propres fils) que [condition] soit vraie. */
    private suspend fun <T> StateFlow<T>.attendre(condition: (T) -> Boolean): T =
        withContext(Dispatchers.Default) { withTimeout(5_000) { first(condition) } }

    private suspend fun SaisieViewModel.enCours() = etat.attendre { it is EtatSaisie.EnCours } as EtatSaisie.EnCours

    private fun SaisieViewModel.saisir(nom: String? = null, poids: String? = null, repetitions: String? = null) {
        nom?.let(this.nom::setTextAndPlaceCursorAtEnd)
        poids?.let(this.poids::setTextAndPlaceCursorAtEnd)
        repetitions?.let(this.repetitions::setTextAndPlaceCursorAtEnd)
        // Hors composition, les `snapshotFlow` ne voient les modifications qu'une fois notifiées.
        Snapshot.sendApplyNotifications()
    }

    private suspend fun creer() = checkNotNull(dao.creerEntrainement(LocalDate.of(2026, 10, 12), "Dos", horloge.maintenant()))

    @Test
    fun `sans nom ni repetitions, rien n'est enregistre et les champs sont signales`() = runTest {
        val id = creer()
        val vm = ecran()
        vm.enCours()

        vm.saisir(poids = "40")
        vm.enregistrer(Suite.NouvelleSerie)

        assertEquals(Formulaire(erreurNom = true, erreurRepetitions = true), vm.formulaire.value)
        assertTrue(dao.lireComplet(id)!!.exercices.isEmpty())
        assertEquals(EtatChrono(id), dao.lireChrono(id))

        // L'erreur disparaît dès que le champ est modifié.
        vm.saisir(nom = "S")
        assertFalse(vm.formulaire.value.erreurNom)
        assertTrue(vm.formulaire.value.erreurRepetitions)
        vm.ajouterAuxRepetitions(5)
        Snapshot.sendApplyNotifications()
        assertFalse(vm.formulaire.value.erreurRepetitions)
    }

    @Test
    fun `nouvelle serie, puis nouvel exercice`() = runTest {
        val id = creer()
        val vm = ecran()
        vm.enCours()

        vm.saisir(nom = "Squat", poids = "41,3", repetitions = "8")
        horloge.realtime = 50_000
        vm.enregistrer(Suite.NouvelleSerie)

        val apresSerie = vm.etat.attendre { (it as? EtatSaisie.EnCours)?.verrouille == true } as EtatSaisie.EnCours
        assertEquals("Squat", apresSerie.nomVerrouille)
        assertEquals(1 to 2, apresSerie.numeroExercice to apresSerie.numeroSerie)
        assertEquals(EtatChrono(id, EtatChrono.Etat.EnCours, demarreA = 50_000), apresSerie.chrono)
        assertEquals("Série 1 enregistrée · 41,25 kg × 8", dernierToast())
        assertEquals("" to "", vm.poids.text.toString() to vm.repetitions.text.toString())
        assertTrue("pas de suggestions pendant le verrouillage", vm.suggestions.value.isEmpty())

        toasts.clear()
        vm.saisir(repetitions = "6")
        vm.enregistrer(Suite.NouvelExercice)

        val apresExercice = vm.etat.attendre { (it as? EtatSaisie.EnCours)?.numeroExercice == 2 } as EtatSaisie.EnCours
        assertFalse(apresExercice.verrouille)
        assertEquals(1, apresExercice.numeroSerie)
        assertEquals("Série 2 enregistrée · PDC × 6", dernierToast())
        assertEquals("", vm.nom.text.toString())
    }

    @Test
    fun `un double appui n'enregistre qu'une serie`() = runTest {
        val id = creer()
        val vm = ecran()
        vm.enCours()

        vm.saisir(nom = "Squat", repetitions = "8")
        vm.enregistrer(Suite.NouvelleSerie)
        vm.enregistrer(Suite.NouvelleSerie)
        vm.etat.attendre { (it as? EtatSaisie.EnCours)?.verrouille == true }

        assertEquals(1, dao.lireComplet(id)!!.exercices.single().series.size)
    }

    @Test
    fun `terminer enregistre la serie affichee dans le dialogue 4a`() = runTest {
        val id = creer()
        val vm = ecran()
        vm.enCours()

        vm.demanderFin()
        assertEquals(DialogueFin.Simple, vm.formulaire.attendre { it.dialogue != null }.dialogue)
        vm.annulerFin()

        vm.saisir(nom = "Squat", poids = "100", repetitions = "5")
        vm.demanderFin()
        val dialogue = vm.formulaire.attendre { it.dialogue != null }.dialogue
        assertEquals(DialogueFin.AvecSerie(SerieSaisie("Squat", emptyList(), ValeursPalier(100.0, 5))), dialogue)
        assertEquals("100 kg × 5", (dialogue as DialogueFin.AvecSerie).serie.description)

        vm.terminer(avecSerie = true)

        vm.etat.attendre { it == EtatSaisie.Aucun }
        val termine = dao.lireComplet(id)!!
        assertEquals(horloge.maintenant(), termine.entrainement.termineLe)
        assertEquals(listOf(100.0 to 5), termine.exercices.single().series.map { it.paliers.single().let { p -> p.poidsKg to p.repetitions } })
        assertEquals("", vm.nom.text.toString())
    }

    @Test
    fun `terminer sans serie supprime l'entrainement`() = runTest {
        val id = creer()
        val vm = ecran()
        vm.enCours()

        vm.demanderFin()
        vm.formulaire.attendre { it.dialogue != null }
        vm.terminer(avecSerie = false)

        vm.etat.attendre { it == EtatSaisie.Aucun }
        assertNull(dao.lireComplet(id))
    }

    @Test
    fun `la saisie est retrouvee apres Retour puis Reprendre, pas sur un nouvel entrainement`() = runTest {
        val id = creer()
        val vm = ecran()
        vm.enCours()
        vm.saisir(nom = "Curl", poids = "12,5")

        // Retour : le ViewModel de l'écran disparaît ; « Reprendre » en crée un nouveau.
        val repris = ecran()
        repris.enCours()
        assertEquals("Curl" to "12,5", repris.nom.text.toString() to repris.poids.text.toString())

        dao.terminer(id, horloge.maintenant())
        creer()
        val nouveau = ecran()
        nouveau.etat.attendre { it is EtatSaisie.EnCours && it.entrainement.id != id }
        assertEquals("" to "", nouveau.nom.text.toString() to nouveau.poids.text.toString())
    }

    /** Entraînement terminé le [date], avec les [series] (poids, répétitions) d'un exercice [nom]. */
    private suspend fun passe(date: LocalDate, libelle: String?, nom: String, vararg series: Pair<Double?, Int>) {
        val id = checkNotNull(dao.creerEntrainement(date, libelle, horloge.maintenant()))
        series.forEach { (poids, reps) -> dao.enregistrerSerie(id, nom, poids, reps, Suite.NouvelleSerie, horloge.maintenant(), 0) }
        dao.terminer(id, horloge.maintenant())
    }

    @Test
    fun `bouton historique, nom d'un autre entrainement, casse et accents ignores, aussi une fois verrouille`() = runTest {
        passe(LocalDate.of(2026, 10, 5), "Dos / biceps", "Tirage vertical", 40.0 to 15)
        val id = creer()
        dao.enregistrerSerie(id, "Développé couché", 50.0, 10, Suite.NouvelExercice, horloge.maintenant(), 0)
        val vm = ecran()
        backgroundScope.launch { vm.historiqueDisponible.collect {} }
        vm.enCours()
        assertFalse(vm.historiqueDisponible.value)

        vm.saisir(nom = "tirage VERTICAL ")
        vm.historiqueDisponible.attendre { it }
        vm.saisir(nom = "Tirage")
        assertFalse(vm.historiqueDisponible.value)
        // Exercice présent seulement dans l'entraînement en cours.
        vm.saisir(nom = "developpe couche")
        assertFalse(vm.historiqueDisponible.value)
        vm.saisir(nom = "")
        assertFalse(vm.historiqueDisponible.value)

    }

    @Test
    fun `bouton historique sur le nom verrouille, champ vide`() = runTest {
        passe(LocalDate.of(2026, 10, 5), "Dos / biceps", "Tirage vertical", 40.0 to 15)
        val id = creer()
        dao.enregistrerSerie(id, "tirage vertical", 40.0, 12, Suite.NouvelleSerie, horloge.maintenant(), 0)
        val vm = ecran()
        backgroundScope.launch { vm.historiqueDisponible.collect {} }

        // Le champ reste vide : seul le nom verrouillé peut faire apparaître le bouton.
        assertEquals("tirage vertical", vm.enCours().nomVerrouille)
        assertEquals("", vm.nom.text.toString())
        vm.historiqueDisponible.attendre { it }
    }

    @Test
    fun `popup historique, seances precedentes, sans toucher a la saisie ni au chrono`() = runTest {
        passe(LocalDate.of(2026, 9, 24), null, "tirage vertical", 37.5 to 12, 40.0 to 10)
        passe(LocalDate.of(2026, 10, 12), "Dos / biceps", "Tirage vertical", 40.0 to 15, 45.0 to 12)
        val id = creer()
        // Série de l'entraînement en cours (chrono relancé) : absente de la popup.
        dao.enregistrerSerie(id, "Tirage vertical", 50.0, 3, Suite.NouvelExercice, horloge.maintenant(), 5_000)
        val vm = ecran()
        vm.enCours()
        vm.saisir(nom = "TIRAGE vertical", poids = "45", repetitions = "10")
        val chrono = checkNotNull(dao.lireChrono(id))
        assertEquals(EtatChrono.Etat.EnCours, chrono.etat)
        val suggestions = vm.suggestions.value

        vm.ouvrirHistorique()
        val historique = vm.formulaire.attendre { it.historique != null }.historique
        assertEquals(
            HistoriqueExercice(
                "Tirage vertical",
                listOf(
                    SeanceHistorique("Dos / biceps — 12/10/2026", listOf(LigneHistorique("15 × 40 kg"), LigneHistorique("12 × 45 kg"))),
                    SeanceHistorique("24/09/2026", listOf(LigneHistorique("12 × 37,5 kg"), LigneHistorique("10 × 40 kg"))),
                ),
            ),
            historique,
        )

        vm.fermerHistorique()
        assertNull(vm.formulaire.value.historique)
        assertEquals(listOf("TIRAGE vertical", "45", "10"), listOf(vm.nom, vm.poids, vm.repetitions).map { it.text.toString() })
        assertEquals(chrono, dao.lireChrono(id))
        assertEquals(suggestions, vm.suggestions.value)
        assertEquals(1, dao.lireComplet(id)!!.exercices.single().series.size)
    }

    // --- Séries dégressives (spec §4.3.9) ---

    private suspend fun SaisieViewModel.paliersGardes(nombre: Int) =
        etat.attendre { (it as? EtatSaisie.EnCours)?.paliersEnCours?.size == nombre } as EtatSaisie.EnCours

    @Test
    fun `50 x 10, degressive, 30 x 8, degressive, 20 x 6, nouvelle serie donne une seule serie a 3 paliers`() = runTest {
        val id = creer()
        val vm = ecran()
        vm.enCours()
        horloge.realtime = 5_000
        vm.basculerChrono()
        val chrono = EtatChrono(id, EtatChrono.Etat.EnCours, demarreA = 5_000)
        vm.etat.attendre { (it as? EtatSaisie.EnCours)?.chrono == chrono }

        vm.saisir(nom = "Tirage vertical", poids = "50", repetitions = "10")
        horloge.realtime = 20_000
        vm.degressive()
        val a2 = vm.paliersGardes(1)
        // Nom verrouillé, champs vidés, même numéro de série, chrono intact.
        assertEquals("Tirage vertical", a2.nomVerrouille)
        assertEquals(1 to 1, a2.numeroExercice to a2.numeroSerie)
        assertEquals("50 kg × 10 ↓ …", a2.chaineDegressive)
        assertEquals("" to "", vm.poids.text.toString() to vm.repetitions.text.toString())
        assertEquals(chrono, dao.lireChrono(id))

        vm.saisir(poids = "30", repetitions = "8")
        vm.degressive()
        val a3 = vm.paliersGardes(2)
        assertEquals("50 kg × 10 ↓ 30 kg × 8 ↓ …", a3.chaineDegressive)
        assertEquals(1, a3.numeroSerie)
        assertEquals(chrono, dao.lireChrono(id))
        assertTrue(toasts.isEmpty())

        vm.saisir(poids = "20", repetitions = "6")
        horloge.realtime = 30_000
        vm.enregistrer(Suite.NouvelleSerie)
        val apres = vm.etat.attendre { (it as? EtatSaisie.EnCours)?.numeroSerie == 2 } as EtatSaisie.EnCours
        assertTrue(apres.paliersEnCours.isEmpty())
        assertNull(apres.chaineDegressive)
        assertEquals("Série 1 enregistrée · 50 kg × 10 ↓ 30 kg × 8 ↓ 20 kg × 6", dernierToast())
        assertEquals(
            listOf(listOf(ValeursPalier(50.0, 10), ValeursPalier(30.0, 8), ValeursPalier(20.0, 6))),
            dao.lireComplet(id)!!.exercices.single().series.map { it.valeurs },
        )
        assertEquals(EtatChrono(id, EtatChrono.Etat.EnCours, demarreA = 30_000), dao.lireChrono(id))
    }

    @Test
    fun `degressive, meme validation que nouvelle serie`() = runTest {
        val id = creer()
        val vm = ecran()
        vm.enCours()
        vm.saisir(poids = "50")
        vm.degressive()
        assertEquals(Formulaire(erreurNom = true, erreurRepetitions = true), vm.formulaire.value)
        assertTrue(checkNotNull(dao.lireEnCoursComplet()).paliersEnCours.isEmpty())
        assertEquals("50", vm.poids.text.toString())
        assertEquals(EtatChrono(id), dao.lireChrono(id))
    }

    @Test
    fun `appli tuee au milieu d'une degressive, les paliers gardes sont restaures`() = runTest {
        val id = creer()
        val vm = ecran()
        vm.enCours()
        vm.saisir(nom = "Tirage vertical", poids = "50", repetitions = "10")
        vm.degressive()
        vm.paliersGardes(1)
        vm.saisir(poids = "30", repetitions = "8")
        vm.degressive()
        vm.paliersGardes(2)

        // Processus tué : ViewModel et brouillon perdus, la base reste.
        vms.vider()
        val reprise = ecran(BrouillonSaisie())
        val etat = reprise.enCours()
        assertEquals("50 kg × 10 ↓ 30 kg × 8 ↓ …", etat.chaineDegressive)
        assertEquals("Tirage vertical", etat.nomVerrouille)
        assertEquals(1, etat.numeroSerie)

        reprise.saisir(poids = "20", repetitions = "6")
        reprise.enregistrer(Suite.NouvelExercice)
        reprise.etat.attendre { (it as? EtatSaisie.EnCours)?.numeroExercice == 2 }
        assertEquals("Série 1 enregistrée · 50 kg × 10 ↓ 30 kg × 8 ↓ 20 kg × 6", dernierToast())
        assertEquals(3, dao.lireComplet(id)!!.exercices.single().series.single().paliers.size)
    }

    @Test
    fun `terminer avec des paliers gardes propose la serie complete`() = runTest {
        val id = creer()
        val vm = ecran()
        vm.enCours()
        vm.saisir(nom = "Tirage vertical", poids = "50", repetitions = "10")
        vm.degressive()
        vm.paliersGardes(1)

        // Champs vides : la série ne garde que les paliers déjà saisis.
        vm.demanderFin()
        val seule = vm.formulaire.attendre { it.dialogue != null }.dialogue as DialogueFin.AvecSerie
        assertEquals(SerieSaisie("Tirage vertical", listOf(ValeursPalier(50.0, 10)), null), seule.serie)
        assertEquals("50 kg × 10", seule.serie.description)
        vm.annulerFin()

        // Palier courant valide : il complète la série.
        vm.saisir(poids = "30", repetitions = "8")
        vm.demanderFin()
        val complete = vm.formulaire.attendre { it.dialogue != null }.dialogue as DialogueFin.AvecSerie
        assertEquals("50 kg × 10 ↓ 30 kg × 8", complete.serie.description)
        vm.terminer(avecSerie = true)

        vm.etat.attendre { it == EtatSaisie.Aucun }
        assertEquals(
            listOf(listOf(ValeursPalier(50.0, 10), ValeursPalier(30.0, 8))),
            dao.lireComplet(id)!!.exercices.single().series.map { it.valeurs },
        )
    }

    @Test
    fun `terminer aussitot apres degressive propose quand meme la serie`() = runTest {
        creer()
        val vm = ecran()
        vm.enCours()
        vm.saisir(nom = "Tirage vertical", poids = "50", repetitions = "10")
        // Sans attendre que l'écran voie le palier gardé. Le dialogue relit la base : un état d'écran en retard
        // proposerait 4b et abandonnerait le palier (course trop brève pour être reproduite ici à coup sûr).
        vm.degressive()
        vm.demanderFin()
        val dialogue = vm.formulaire.attendre { it.dialogue != null }.dialogue
        assertEquals("50 kg × 10", (dialogue as DialogueFin.AvecSerie).serie.description)
    }
}
