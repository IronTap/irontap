package app.irontap.logique

import app.irontap.donnees.Entrainement
import app.irontap.donnees.EntrainementComplet
import app.irontap.donnees.Exercice
import app.irontap.donnees.ExerciceAvecSeries
import app.irontap.donnees.RoleSuperset
import app.irontap.donnees.serieDeTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ExportTest {

    private val t0 = Instant.parse("2026-10-12T17:00:00Z")

    private fun degressive(ordre: Int, nom: String, vararg paliers: Pair<Double?, Int>) = ExerciceAvecSeries(
        Exercice(ordre.toLong(), 1, nom, ordre),
        listOf(serieDeTest(1, ordre.toLong(), 1, paliers[0].first, paliers[0].second, t0, *paliers.drop(1).toTypedArray())),
    )

    private fun exercice(ordre: Int, nom: String, vararg series: Pair<Double?, Int>) = ExerciceAvecSeries(
        Exercice(ordre.toLong(), 1, nom, ordre),
        series.mapIndexed { i, (poids, reps) -> serieDeTest(0, ordre.toLong(), i + 1, poids, reps, t0) },
    )

    private fun entrainement(date: LocalDate, libelle: String?, vararg exercices: ExerciceAvecSeries) =
        EntrainementComplet(Entrainement(1, date, libelle, t0, termineLe = t0), exercices.toList())

    private val dos = entrainement(
        LocalDate.of(2026, 10, 12),
        "Dos / biceps",
        exercice(1, "Tirage vertical", 40.0 to 15, 45.0 to 12),
        exercice(2, "Curl haltères", 12.5 to 12),
    )

    @Test
    fun `texte comme l'exemple de la spec`() {
        assertEquals(
            """
            Lun. 12 oct. 2026 — Dos / biceps

            Tirage vertical
              40 kg × 15
              45 kg × 12

            Curl haltères
              12,5 kg × 12

            """.trimIndent(),
            exporterTexte(listOf(dos)),
        )
    }

    @Test
    fun `texte sans libelle, poids du corps, deux lignes vides entre les entrainements`() {
        val tractions = entrainement(LocalDate.of(2026, 10, 9), null, exercice(1, "Tractions", null to 8))
        assertEquals(
            "Lun. 12 oct. 2026 — Dos / biceps\n\nTirage vertical\n  40 kg × 15\n  45 kg × 12\n\nCurl haltères\n  12,5 kg × 12\n" +
                "\n\n" +
                "Ven. 9 oct. 2026\n\nTractions\n  PDC × 8\n",
            exporterTexte(listOf(dos, tractions)),
        )
        assertEquals("", exporterTexte(emptyList()))
    }

    @Test
    fun `csv une ligne par palier, point decimal, poids vide au poids du corps`() {
        val tractions = entrainement(LocalDate.of(2026, 10, 9), null, exercice(1, "Tractions", null to 8))
        assertEquals(
            "date;libelle_entrainement;exercice;ordre_exercice;ordre_serie;palier;poids_kg;repetitions;entrainement;superset;role_superset\r\n" +
                "2026-10-12;Dos / biceps;Tirage vertical;1;1;1;40;15;1;;\r\n" +
                "2026-10-12;Dos / biceps;Tirage vertical;1;2;1;45;12;1;;\r\n" +
                "2026-10-12;Dos / biceps;Curl haltères;2;1;1;12.5;12;1;;\r\n" +
                "2026-10-09;;Tractions;1;1;1;;8;2;;\r\n",
            exporterCsv(listOf(dos, tractions)),
        )
    }

    @Test
    fun `degressive, paliers sur la meme ligne en texte, une ligne par palier en csv`() {
        val seance = entrainement(LocalDate.of(2026, 10, 12), null, degressive(1, "Tirage vertical", 45.0 to 10, 30.0 to 8, null to 6))
        assertEquals("Lun. 12 oct. 2026\n\nTirage vertical\n  45 kg × 10 ↓ 30 kg × 8 ↓ PDC × 6\n", exporterTexte(listOf(seance)))
        assertEquals(
            "date;libelle_entrainement;exercice;ordre_exercice;ordre_serie;palier;poids_kg;repetitions;entrainement;superset;role_superset\r\n" +
                "2026-10-12;;Tirage vertical;1;1;1;45;10;1;;\r\n" +
                "2026-10-12;;Tirage vertical;1;1;2;30;8;1;;\r\n" +
                "2026-10-12;;Tirage vertical;1;1;3;;6;1;;\r\n",
            exporterCsv(listOf(seance)),
        )
    }

    @Test
    fun `csv champs echappes selon la RFC 4180`() {
        assertEquals("Dos / biceps", champTexteCsv("Dos / biceps"))
        assertEquals("\"Haut; bas\"", champTexteCsv("Haut; bas"))
        assertEquals("\"Curl \"\"marteau\"\"\"", champTexteCsv("Curl \"marteau\""))
        assertEquals("\"Ligne 1\nLigne 2\"", champTexteCsv("Ligne 1\nLigne 2"))
    }

    @Test
    fun `csv formules neutralisees`() {
        assertEquals("'=HYPERLINK(\"x\")".let { "\"" + it.replace("\"", "\"\"") + "\"" }, champTexteCsv("=HYPERLINK(\"x\")"))
        assertEquals("'+5 kg", champTexteCsv("+5 kg"))
        assertEquals("'-1", champTexteCsv("-1"))
        assertEquals("'@SUM(A1)", champTexteCsv("@SUM(A1)"))
        assertEquals("'\tTab", champTexteCsv("\tTab"))
        assertEquals("Développé", champTexteCsv("Développé"))
    }

    @Test
    fun `csv apostrophe en tete doublee, l'import retire celle ajoutee`() {
        assertEquals("''Tirage", champTexteCsv("'Tirage"))
        assertEquals("''=1", champTexteCsv("'=1"))
    }

    /** Superset de trois tours, B absent au tour 2 (A : 1, 2, 3 ; B : 1, 3), avec une dégressive sur A au tour 3. */
    private val epaules = entrainement(
        LocalDate.of(2026, 10, 15),
        "Épaules",
        exercice(1, "Développé militaire", 30.0 to 10),
        ExerciceAvecSeries(
            Exercice(2, 1, "Élévations latérales", 2, "uuid-1", RoleSuperset.A),
            listOf(serieDeTest(21, 2, 1, 8.0, 15, t0), serieDeTest(22, 2, 2, 8.0, 12, t0), serieDeTest(23, 2, 3, 6.0, 12, t0, 4.0 to 10)),
        ),
        ExerciceAvecSeries(
            Exercice(3, 1, "Curl marteau", 3, "uuid-1", RoleSuperset.B),
            listOf(serieDeTest(31, 3, 1, 12.0, 12, t0), serieDeTest(33, 3, 3, 12.0, 10, t0)),
        ),
        exercice(4, "Oiseau", 6.0 to 12),
    )

    @Test
    fun `superset en texte, une ligne par tour, tiret pour une serie absente`() {
        assertEquals(
            """
            Jeu. 15 oct. 2026 — Épaules

            Développé militaire
              30 kg × 10

            Superset : Élévations latérales + Curl marteau
              A 8 kg × 15 · B 12 kg × 12
              A 8 kg × 12 · B —
              A 6 kg × 12 ↓ 4 kg × 10 · B 12 kg × 10

            Oiseau
              6 kg × 12

            """.trimIndent(),
            exporterTexte(listOf(epaules)),
        )
    }

    @Test
    fun `superset en csv, numero du superset et role, ordre_serie au numero du tour`() {
        assertEquals(
            "date;libelle_entrainement;exercice;ordre_exercice;ordre_serie;palier;poids_kg;repetitions;entrainement;superset;role_superset\r\n" +
                "2026-10-12;Dos / biceps;Tirage vertical;1;1;1;40;15;1;;\r\n" +
                "2026-10-12;Dos / biceps;Tirage vertical;1;2;1;45;12;1;;\r\n" +
                "2026-10-12;Dos / biceps;Curl haltères;2;1;1;12.5;12;1;;\r\n" +
                "2026-10-15;Épaules;Développé militaire;1;1;1;30;10;2;;\r\n" +
                "2026-10-15;Épaules;Élévations latérales;2;1;1;8;15;2;1;A\r\n" +
                "2026-10-15;Épaules;Élévations latérales;2;2;1;8;12;2;1;A\r\n" +
                "2026-10-15;Épaules;Élévations latérales;2;3;1;6;12;2;1;A\r\n" +
                "2026-10-15;Épaules;Élévations latérales;2;3;2;4;10;2;1;A\r\n" +
                "2026-10-15;Épaules;Curl marteau;3;1;1;12;12;2;1;B\r\n" +
                "2026-10-15;Épaules;Curl marteau;3;3;1;12;10;2;1;B\r\n" +
                "2026-10-15;Épaules;Oiseau;4;1;1;6;12;2;;\r\n",
            exporterCsv(listOf(dos, epaules)),
        )
    }

    @Test
    fun `exercice reste seul de son superset exporte comme ordinaire`() {
        val seul = entrainement(
            LocalDate.of(2026, 10, 15),
            null,
            ExerciceAvecSeries(Exercice(2, 1, "Élévations", 1, "uuid-2", RoleSuperset.A), listOf(serieDeTest(1, 2, 1, 8.0, 15, t0))),
        )
        assertEquals("Jeu. 15 oct. 2026\n\nÉlévations\n  8 kg × 15\n", exporterTexte(listOf(seul)))
        assertTrue(exporterCsv(listOf(seul)).endsWith(";8;15;1;;\r\n"))
    }

    @Test
    fun `seul le fichier CSV commence par une marque d'ordre d'octets`() {
        assertEquals("\uFEFFa;b\r\n", FormatExport.Csv.fichier("a;b\r\n"))
        assertEquals("texte\n", FormatExport.Texte.fichier("texte\n"))
    }

    @Test
    fun `apercu des premieres lignes`() {
        assertEquals(
            "Lun. 12 oct. 2026 — Dos / biceps\n\nTirage vertical\n  40 kg × 15\n  45 kg × 12",
            apercuExport(exporterTexte(listOf(dos))),
        )
        assertEquals(
            "date;libelle_entrainement;exercice;ordre_exercice;ordre_serie;palier;poids_kg;repetitions;entrainement;superset;role_superset\n" +
                "2026-10-12;Dos / biceps;Tirage vertical;1;1;1;40;15;1;;",
            apercuExport(exporterCsv(listOf(dos)), lignes = 2),
        )
        // Pas de ligne vide finale.
        assertEquals("Lun. 12 oct. 2026", apercuExport("Lun. 12 oct. 2026\n\n\n"))
    }

    @Test
    fun `nom du fichier`() {
        val aujourdhui = LocalDate.of(2026, 10, 14)
        assertEquals("irontap-2026-10-12.txt", nomFichierExport(FormatExport.Texte, LocalDate.of(2026, 10, 12), aujourdhui))
        assertEquals("irontap-historique-2026-10-14.csv", nomFichierExport(FormatExport.Csv, null, aujourdhui))
    }
}
