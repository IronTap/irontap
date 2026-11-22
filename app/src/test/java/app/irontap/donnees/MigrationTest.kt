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
 * Migrations v1 → v2 (séries dégressives), v2 → v3 (supersets), v3 → v4 (marqueur « à l'échec »), v4 → v5 (RIR par
 * palier) et v5 → v6 (plage de RIR) : une base est créée avec le schéma exporté de sa version (`schemas/…/N.json`), puis ouverte par Room, qui
 * applique les migrations et vérifie que le résultat est exactement le schéma courant.
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
            .addMigrations(Migration1Vers2, Migration2Vers3, Migration3Vers4, Migration4Vers5, Migration5Vers6)
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

    @Test
    fun `v3 vers v6, aucun RIR et le superset en cours conserve`() = runTest {
        creer(3) {
            execSQL("INSERT INTO entrainement VALUES (1, '2026-10-12', NULL, 1000, 2000, 0)")
            execSQL("INSERT INTO entrainement VALUES (2, '2026-10-14', NULL, 3000, NULL, 0)")
            execSQL("INSERT INTO exercice VALUES (10, 1, 'Tirage vertical', 1, NULL, NULL)")
            execSQL("INSERT INTO serie VALUES (100, 10, 1, 1100, NULL)")
            execSQL("INSERT INTO palier VALUES (1, 100, 1, 45.0, 10)")
            execSQL("INSERT INTO superset_en_cours VALUES (2, 's1', 'Élévations', 'Curl', 'B', '8', '15', '12', '')")
            execSQL("INSERT INTO etat_chrono VALUES (2, 'EnCours', 900, 0)")
        }
        val dao = ouvrir()

        val serie = checkNotNull(dao.lireComplet(1)).exercices.single().series.single()
        assertEquals(Serie(100, 10, 1, Instant.ofEpochMilli(1100), null), serie.serie)
        assertEquals(listOf(Palier(1, 100, 1, 45.0, 10, rirBas = null)), serie.paliers)
        val superset = checkNotNull(dao.observerEnCoursComplet().first()?.superset)
        assertEquals(SupersetEnCours(2, "s1", "Élévations", "Curl", RoleSuperset.B, "8", "15", "12", ""), superset)
        // Le RIR s'écrit ensuite normalement.
        dao.modifierSerie(100, listOf(ValeursPalier(45.0, 10, Rir(2))), Instant.ofEpochMilli(4000))
        assertEquals(listOf(ValeursPalier(45.0, 10, Rir(2))), checkNotNull(dao.lireComplet(1)).exercices.single().series.single().valeurs)
        dao.memoriserSuperset(superset.copy(rirBBas = 3))
        assertEquals(Rir(3), dao.observerEnCoursComplet().first()?.superset?.rirB)
    }

    @Test
    fun `v4 vers v6, une serie a l'echec passe a RIR 0 sur chaque palier`() = runTest {
        creer(4) {
            execSQL("INSERT INTO entrainement VALUES (1, '2026-10-12', NULL, 1000, 2000, 0)")
            execSQL("INSERT INTO entrainement VALUES (2, '2026-10-14', NULL, 3000, NULL, 0)")
            execSQL("INSERT INTO exercice VALUES (10, 1, 'Tirage vertical', 1, NULL, NULL)")
            execSQL("INSERT INTO serie VALUES (100, 10, 1, 1100, NULL, 0)")
            execSQL("INSERT INTO serie VALUES (101, 10, 2, 1200, 1500, 1)")
            // Série supprimée avant la mise à jour : son identifiant ne doit pas resservir.
            execSQL("INSERT INTO serie VALUES (105, 10, 3, 1300, NULL, 0)")
            execSQL("DELETE FROM serie WHERE id = 105")
            execSQL("INSERT INTO palier VALUES (1, 100, 1, 40.0, 12)")
            execSQL("INSERT INTO palier VALUES (2, 101, 1, 45.0, 10)")
            execSQL("INSERT INTO palier VALUES (3, 101, 2, 30.0, 8)")
            // Superset en cours : case cochée sur A, ses paliers gardés passent aussi à RIR 0.
            execSQL("INSERT INTO palier_en_cours VALUES (1, 2, 1, 10.0, 12, 'A')")
            execSQL("INSERT INTO palier_en_cours VALUES (2, 2, 1, 12.0, 10, 'B')")
            execSQL("INSERT INTO superset_en_cours VALUES (2, 's1', 'Élévations', 'Curl', 'A', '8', '15', '', '', 1, 0)")
            execSQL("INSERT INTO etat_chrono VALUES (2, 'EnCours', 900, 0)")
        }
        val dao = ouvrir()

        val series = checkNotNull(dao.lireComplet(1)).exercices.single().series
        assertEquals(listOf(Serie(100, 10, 1, Instant.ofEpochMilli(1100), null), Serie(101, 10, 2, Instant.ofEpochMilli(1200), Instant.ofEpochMilli(1500))), series.map { it.serie })
        assertEquals(
            listOf(listOf(Palier(1, 100, 1, 40.0, 12, rirBas = null)), listOf(Palier(2, 101, 1, 45.0, 10, rirBas = 0), Palier(3, 101, 2, 30.0, 8, rirBas = 0))),
            series.map { it.paliers },
        )
        val enCours = checkNotNull(dao.observerEnCoursComplet().first())
        assertEquals(
            listOf(PalierEnCours(1, 2, 1, 10.0, 12, RoleSuperset.A, rirBas = 0), PalierEnCours(2, 2, 1, 12.0, 10, RoleSuperset.B, rirBas = null)),
            enCours.paliersEnCours,
        )
        assertEquals(SupersetEnCours(2, "s1", "Élévations", "Curl", RoleSuperset.A, "8", "15", "", "", rirABas = 0, rirBBas = null), enCours.superset)

        // Compteur d'identifiants conservé, et suppressions toujours en cascade jusqu'aux paliers.
        assertEquals(106L, dao.ajouterSerie(10, listOf(ValeursPalier(40.0, 8, Rir(1))), Instant.ofEpochMilli(4000)).serie.id)
        dao.supprimerEntrainement(1)
        val restants = checkNotNull(base).openHelper.readableDatabase.query("SELECT COUNT(*) FROM palier").use { it.moveToFirst(); it.getInt(0) }
        assertEquals(0, restants)
    }

    @Test
    fun `v5 vers v6, les RIR notes restent des valeurs seules et une plage s'enregistre`() = runTest {
        creer(5) {
            execSQL("INSERT INTO entrainement VALUES (1, '2026-11-17', NULL, 1000, 2000, 0)")
            execSQL("INSERT INTO entrainement VALUES (2, '2026-11-20', NULL, 3000, NULL, 0)")
            execSQL("INSERT INTO exercice VALUES (10, 1, 'Tirage vertical', 1, NULL, NULL)")
            execSQL("INSERT INTO serie VALUES (100, 10, 1, 1100, NULL)")
            execSQL("INSERT INTO palier VALUES (1, 100, 1, 45.0, 10, 2)")
            execSQL("INSERT INTO palier VALUES (2, 100, 2, 30.0, 8, NULL)")
            execSQL("INSERT INTO palier_en_cours VALUES (1, 2, 1, 10.0, 12, 'A', 1)")
            execSQL("INSERT INTO superset_en_cours VALUES (2, 's1', 'Élévations', 'Curl', 'B', '8', '15', '12', '', 0, 5)")
            execSQL("INSERT INTO etat_chrono VALUES (2, 'EnCours', 900, 0)")
        }
        val dao = ouvrir()

        val serie = checkNotNull(dao.lireComplet(1)).exercices.single().series.single()
        assertEquals(listOf(Rir(2), null), serie.paliers.map { it.rir })
        val enCours = checkNotNull(dao.observerEnCoursComplet().first())
        assertEquals(listOf(Rir(1)), enCours.paliersEnCours.map { it.rir })
        assertEquals(Rir(0) to Rir(5), enCours.superset?.rirA to enCours.superset?.rirB)

        dao.modifierSerie(100, listOf(ValeursPalier(45.0, 10, Rir(2, 3)), ValeursPalier(30.0, 8, Rir(4, 5))), Instant.ofEpochMilli(4000))
        assertEquals(listOf(Rir(2, 3), Rir(4, 5)), checkNotNull(dao.lireComplet(1)).exercices.single().series.single().paliers.map { it.rir })
    }
}
