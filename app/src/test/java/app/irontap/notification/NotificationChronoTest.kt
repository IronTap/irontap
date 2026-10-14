package app.irontap.notification

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.irontap.donnees.BaseIronTap
import app.irontap.donnees.EntrainementDao
import app.irontap.donnees.EntrainementDao.Suite
import app.irontap.donnees.EtatChrono
import app.irontap.donnees.EtatChrono.Etat
import app.irontap.donnees.Horloge
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/** Notification du chrono : affichage, actions et permission (spec §4.3.4), sur une base Room en mémoire. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificationChronoTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val gestionnaire = shadowOf(context.getSystemService(NotificationManager::class.java))
    private lateinit var base: BaseIronTap
    private lateinit var dao: EntrainementDao
    private lateinit var notification: NotificationChrono

    private val horloge = object : Horloge {
        var realtime = 100_000L
        override fun maintenant(): Instant = Instant.parse("2026-10-12T17:00:00Z")
        override fun realtime() = realtime
    }

    @Before
    fun ouvrir() {
        base = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), BaseIronTap::class.java)
            .build()
        dao = base.entrainements()
        notification = NotificationChrono(context, dao, horloge)
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun fermer() = base.close()

    private fun affichee(): Notification? = gestionnaire.getNotification(NotificationChrono.ID)

    private fun Notification.texte(cle: String) = extras.getCharSequence(cle)?.toString()

    /** Entraînement avec une série de tirage vertical : le chrono a été relancé à [realtime] = 40 s. */
    private suspend fun avecUneSerie(): Long {
        val id = checkNotNull(dao.creerEntrainement(LocalDate.of(2026, 10, 12), null, horloge.maintenant()))
        dao.enregistrerSerie(id, "Tirage vertical", 40.0, 15, Suite.NouvelleSerie, horloge.maintenant(), realtime = 40_000)
        return id
    }

    @Test
    fun `chrono en marche, chronometre systeme et actions Pause et Relancer`() = runTest {
        avecUneSerie()
        notification.actualiser()

        val n = checkNotNull(affichee())
        assertEquals(NotificationChrono.CANAL, n.channelId)
        assertEquals("Repos en cours", n.texte(Notification.EXTRA_TITLE))
        assertEquals("Tirage vertical · Série 2", n.texte(Notification.EXTRA_TEXT))
        assertEquals("Chrono de repos", n.texte(Notification.EXTRA_SUB_TEXT))
        assertTrue(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        // 60 s écoulées depuis la relance : le chronomètre système part de maintenant − 60 s.
        assertEquals(horloge.maintenant().toEpochMilli() - 60_000, n.`when`)
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(listOf("Pause", "Relancer"), n.actions.map { it.title.toString() })
    }

    @Test
    fun `Pause fige le temps, Reprendre et Relancer le font repartir`() = runTest {
        val id = avecUneSerie()

        notification.executer(ActionChrono.Pause, realtime = 112_000)
        assertEquals(EtatChrono(id, Etat.Pause, cumulMs = 72_000), dao.lireChrono(id))
        val enPause = checkNotNull(affichee())
        assertEquals("01:12 · En pause", enPause.texte(Notification.EXTRA_TITLE))
        assertEquals(false, enPause.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertEquals(listOf("Reprendre", "Relancer"), enPause.actions.map { it.title.toString() })

        notification.executer(ActionChrono.Reprendre, realtime = 200_000)
        assertEquals(EtatChrono(id, Etat.EnCours, demarreA = 200_000, cumulMs = 72_000), dao.lireChrono(id))

        notification.executer(ActionChrono.Relancer, realtime = 300_000)
        assertEquals(EtatChrono(id, Etat.EnCours, demarreA = 300_000), dao.lireChrono(id))
        assertEquals("Repos en cours", affichee()?.texte(Notification.EXTRA_TITLE))
    }

    @Test
    fun `fin de l'entrainement, la notification disparait et les actions sont sans effet`() = runTest {
        val id = avecUneSerie()
        notification.actualiser()
        assertNotEquals(null, affichee())

        dao.terminer(id, horloge.maintenant())
        notification.actualiser()
        assertNull(affichee())

        // Appui sur une action d'une notification restée affichée : rien ne se passe.
        notification.executer(ActionChrono.Reprendre, realtime = 200_000)
        assertNull(affichee())
        assertNull(dao.lireChrono(id))
    }

    @Test
    fun `chrono jamais lance, pas de notification`() = runTest {
        dao.creerEntrainement(LocalDate.of(2026, 10, 12), null, horloge.maintenant())
        notification.actualiser()
        assertNull(affichee())
    }

    @Test
    fun `sans permission, rien n'est affiche`() = runTest {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        avecUneSerie()
        notification.actualiser()
        assertNull(affichee())
    }
}
