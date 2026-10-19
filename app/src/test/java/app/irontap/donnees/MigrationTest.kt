package app.irontap.donnees

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.LocalDate

/**
 * Migrations v1 → v2 (séries dégressives) et v2 → v3 (supersets) : une base est créée avec le schéma exporté de sa
 * version (`schemas/…/N.json`), puis ouverte par Room, qui applique les migrations et vérifie que le résultat est
 * exactement le schéma courant.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fichier = context.getDatabasePath("migration.db")
    private var base: BaseIronTap? = null

    @After
    fun fermer() {
        base?.close()
        context.deleteDatabase(fichier.name)
    }

    /** Base v1 telle que Room l'aurait créée, remplie par [remplir]. */
    private fun creerV1(remplir: SQLiteDatabase.() -> Unit) = creer(1, remplir)

    /** Base de la [version] donnée telle que Room l'aurait créée, remplie par [remplir]. */
    private fun creer(version: Int, remplir: SQLiteDatabase.() -> Unit) {
        val schema = JSONObject(File("schemas/app.irontap.donnees.BaseIronTap/$version.json").readText()).getJSONObject("database")
        fichier.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(fichier, null).use { db ->
            val entites = schema.getJSONArray("entities")
            for (i in 0 until entites.length()) {
                val entite = entites.getJSONObject(i)
                val table = entite.getString("tableName")
                db.execSQL(entite.getString("createSql").replace("\${TABLE_NAME}", table))
                val index = entite.optJSONArray("indices") ?: continue
                for (j in 0 until index.length()) {
                    db.execSQL(index.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val initialisation = schema.getJSONArray("setupQueries")
            for (i in 0 until initialisation.length()) db.execSQL(initialisation.getString(i))
            db.version = version
            db.remplir()
        }
    }

    private fun ouvrir(): EntrainementDao =
        Room.databaseBuilder(context, BaseIronTap::class.java, fichier.name)
            .addMigrations(Migration1Vers2, Migration2Vers3)
            .allowMainThreadQueries()
            .build()
            .also { base = it }
            .entrainements()

    @Test
    fun `chaque serie existante devient une serie d'un palier`() = runTest {
        creerV1 {
            execSQL("INSERT INTO entrainement VALUES (1, '2026-10-12', 'Dos / biceps', 1000, 2000, 0)")
            execSQL("INSERT INTO entrainement VALUES (2, '2026-10-14', NULL, 3000, NULL, 1)")
            execSQL("INSERT INTO exercice VALUES (10, 1, 'Tirage vertical', 1)")
            execSQL("INSERT INTO exercice VALUES (11, 1, 'Tractions', 2)")
            execSQL("INSERT INTO exercice VALUES (12, 2, 'Squat', 1)")
            execSQL("INSERT INTO serie VALUES (100, 10, 1, 40.0, 15, 1100, NULL)")
            execSQL("INSERT INTO serie VALUES (101, 10, 2, 42.5, 12, 1200, 5000)")
            execSQL("INSERT INTO serie VALUES (102, 11, 1, NULL, 8, 1300, NULL)")
            execSQL("INSERT INTO serie VALUES (103, 12, 1, 100.0, 5, 3100, NULL)")
            execSQL("INSERT INTO etat_chrono VALUES (2, 'EnCours', 900, 0)")
        }
        val dao = ouvrir()

        val termine = checkNotNull(dao.lireComplet(1))
        assertEquals(LocalDate.of(2026, 10, 12), termine.entrainement.date)
        assertEquals(
            listOf(
                "Tirage vertical" to listOf(listOf(ValeursPalier(40.0, 15)), listOf(ValeursPalier(42.5, 12))),
                "Tractions" to listOf(listOf(ValeursPalier(null, 8))),
            ),
            termine.exercices.map { e -> e.exercice.nom to e.series.map { it.valeurs } },
        )
        val serie = termine.exercices[0].series[1]
        assertEquals(Serie(101, 10, 2, Instant.ofEpochMilli(1200), Instant.ofEpochMilli(5000)), serie.serie)
        assertEquals(listOf(1), serie.paliers.map { it.ordre })

        // L'entraînement en cours, son chrono et son état de saisie sont conservés.
        val enCours = checkNotNull(dao.observerEnCoursComplet().first())
        assertEquals(listOf(listOf(ValeursPalier(100.0, 5))), enCours.exercices.single().series.map { it.valeurs })
        assertEquals(EtatChrono(2, EtatChrono.Etat.EnCours, demarreA = 900), enCours.chrono)
        assertTrue(enCours.entrainement.nouvelExercice)
        assertTrue(enCours.paliersEnCours.isEmpty())

        // Les identifiants continuent après les anciens, et la suppression en cascade atteint les paliers.
        val ajout = dao.ajouterSerie(10, listOf(ValeursPalier(45.0, 10), ValeursPalier(30.0, 8)), Instant.ofEpochMilli(6000))
        assertTrue(ajout.serie.id > 103)
        dao.supprimerEntrainement(1)
        assertEquals(null, dao.lireComplet(1))
        assertEquals(listOf(103L), dao.lireHistoriqueExercice(99, "Squat").map { it.serieId })
    }

    @Test
    fun `base vide`() = runTest {
        creerV1 {}
        val dao = ouvrir()
        assertEquals(emptyList<Entrainement>(), dao.observerHistorique().first())
    }

    @Test
    fun `v2 vers v3, aucun exercice en superset et la degressive en attente conservee`() = runTest {
        creer(2) {
            execSQL("INSERT INTO entrainement VALUES (1, '2026-10-12', NULL, 1000, 2000, 0)")
            execSQL("INSERT INTO entrainement VALUES (2, '2026-10-14', NULL, 3000, NULL, 0)")
            execSQL("INSERT INTO exercice VALUES (10, 1, 'Tirage vertical', 1)")
            execSQL("INSERT INTO exercice VALUES (11, 2, 'Squat', 1)")
            execSQL("INSERT INTO serie VALUES (100, 10, 1, 1100, NULL)")
            execSQL("INSERT INTO palier VALUES (1, 100, 1, 45.0, 10)")
            execSQL("INSERT INTO palier VALUES (2, 100, 2, 30.0, 8)")
            execSQL("INSERT INTO palier_en_cours VALUES (1, 2, 1, 100.0, 5)")
            execSQL("INSERT INTO etat_chrono VALUES (2, 'EnCours', 900, 0)")
        }
        val dao = ouvrir()

        val termine = checkNotNull(dao.lireComplet(1))
        assertEquals(Exercice(10, 1, "Tirage vertical", 1), termine.exercices.single().exercice)
        assertEquals(listOf(ValeursPalier(45.0, 10), ValeursPalier(30.0, 8)), termine.exercices.single().series.single().valeurs)
        val enCours = checkNotNull(dao.observerEnCoursComplet().first())
        assertEquals(listOf(PalierEnCours(1, 2, 1, 100.0, 5, role = null)), enCours.paliersEnCours)
        assertEquals(null, enCours.superset)
        // Un superset peut ensuite commencer sur un nouvel exercice.
        dao.enregistrerSerie(2, "", null, 6, EntrainementDao.Suite.NouvelExercice, Instant.ofEpochMilli(4000), 1000)
        assertTrue(dao.commencerSuperset(2, "Curl", "s1") != null)
    }
}
