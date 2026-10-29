package app.irontap.logique

import app.irontap.donnees.Entrainement
import app.irontap.donnees.EntrainementComplet
import app.irontap.donnees.Exercice
import app.irontap.donnees.ExerciceAvecSeries
import app.irontap.donnees.RoleSuperset
import app.irontap.donnees.ValeursPalier
import app.irontap.donnees.serieDeTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Lecture d'un export CSV (DECISIONS.md « Import »). */
class ImportTest {

    private val t0 = Instant.parse("2026-10-12T17:00:00Z")

    private fun ressource(nom: String) = checkNotNull(javaClass.getResource("/import/$nom")).readText()

    private fun valide(contenu: String) = (lireExportCsv(contenu) as LectureImport.Valide).entrainements

    private fun erreurs(contenu: String) = (lireExportCsv(contenu) as LectureImport.Erreurs).erreurs.map { it.toString() }

    private fun s(ordre: Int, vararg paliers: Pair<Double?, Int>) = SerieImportee(ordre, paliers.map { (p, r) -> ValeursPalier(p, r) })

    /** Deux séances le même jour au même libellé ; superset dont B saute le tour 2, dégressive sur A ; champs à échapper. */
    private val historique = listOf(
        EntrainementComplet(
            Entrainement(1, LocalDate.of(2026, 10, 15), "Épaules ; bras", t0, termineLe = t0),
            listOf(
                ExerciceAvecSeries(Exercice(1, 1, "=Développé \"militaire\"", 1), listOf(serieDeTest(1, 1, 1, 32.5, 10, t0))),
                ExerciceAvecSeries(
                    Exercice(2, 1, "Élévations latérales", 2, "s1", RoleSuperset.A),
                    listOf(serieDeTest(2, 2, 1, 8.0, 15, t0), serieDeTest(3, 2, 2, 8.0, 12, t0), serieDeTest(4, 2, 3, 6.0, 12, t0, 4.0 to 10)),
                ),
                ExerciceAvecSeries(
                    Exercice(3, 1, "'Curl' marteau", 3, "s1", RoleSuperset.B),
                    listOf(serieDeTest(5, 3, 1, 12.0, 12, t0), serieDeTest(6, 3, 3, 12.0, 10, t0)),
                ),
                ExerciceAvecSeries(Exercice(4, 1, "Tractions", 4), listOf(serieDeTest(7, 4, 1, null, 8, t0), serieDeTest(8, 4, 2, 41.25, 6, t0))),
            ),
        ),
        EntrainementComplet(
            Entrainement(2, LocalDate.of(2026, 10, 12), null, t0, termineLe = t0),
            listOf(ExerciceAvecSeries(Exercice(5, 2, "Squat", 1), listOf(serieDeTest(9, 5, 1, 100.0, 5, t0)))),
        ),
        EntrainementComplet(
            Entrainement(3, LocalDate.of(2026, 10, 12), null, t0, termineLe = t0),
            listOf(ExerciceAvecSeries(Exercice(6, 3, "Squat", 1), listOf(serieDeTest(10, 6, 1, 100.0, 5, t0)))),
        ),
    )

    @Test
    fun `aller-retour exact avec l'export, marque d'ordre d'octets comprise`() {
        val fichier = FormatExport.Csv.fichier(exporterCsv(historique))
        assertEquals(historique.map { it.versImport() }, valide(fichier))
    }

    @Test
    fun `superset reconstruit, tours gardes, dans l'exemple de test`() {
        val entrainements = valide(ressource("exemple-import.csv"))
        assertEquals(listOf("Test import · Épaules / bras", "Test import · Dos", "Test import · Dos", "Test import ; jambes"), entrainements.map { it.libelle })
        val epaules = entrainements[0]
        assertEquals(
            listOf(
                ExerciceImporte("Développé militaire", listOf(s(1, 30.0 to 10), s(2, 32.5 to 8))),
                ExerciceImporte("Élévations latérales", listOf(s(1, 8.0 to 15), s(2, 8.0 to 12), s(3, 6.0 to 12, 4.0 to 10)), 1, RoleSuperset.A),
                ExerciceImporte("Curl marteau", listOf(s(1, 12.0 to 12), s(3, 12.0 to 10)), 1, RoleSuperset.B),
                ExerciceImporte("Oiseau", listOf(s(1, 6.0 to 12), s(2, 6.0 to 10))),
            ),
            epaules.exercices,
        )
        assertEquals(listOf("Tractions", "Tirage vertical", "Curl \"pupitre\"", "=Gainage"), entrainements[1].exercices.map { it.nom })
        assertEquals(listOf(s(1, null to 8), s(2, null to 6)), entrainements[1].exercices[0].series)
        assertEquals(listOf("Rowing haltère"), entrainements[2].exercices.map { it.nom })
        // A commence au tour 2.
        assertEquals(listOf(s(2, 50.0 to 12)), entrainements[3].exercices[2].series)
        assertEquals(listOf(1, 2), entrainements[3].exercices[3].series.map { it.ordre })
    }

    @Test
    fun `ancien format sans colonnes de superset, exercices ordinaires renumerotes`() {
        val (entrainement) = valide(ressource("exemple-import-ancien.csv"))
        assertEquals(LocalDate.of(2026, 9, 16), entrainement.date)
        assertEquals(listOf("Développé couché", "Écarté poulie", "Dips", "Pompes"), entrainement.exercices.map { it.nom })
        // Dips : séries 1 et 3 dans l'export (B d'un superset), 1 et 2 ici.
        assertEquals(listOf(s(1, null to 10), s(2, null to 8)), entrainement.exercices[2].series)
        assertTrue(entrainement.exercices.all { it.superset == null && it.role == null })
    }

    @Test
    fun `ancien format, deux seances le meme jour au meme libelle separees`() {
        // Sans champ entre guillemets : les 8 premières colonnes se découpent simplement.
        val ancien = exporterCsv(historique.drop(1)).lines().joinToString("\r\n") { ligne -> ligne.split(";").take(8).joinToString(";") }
        assertEquals(historique.drop(1).map { it.versImport() }, valide(ancien))
    }

    @Test
    fun `virgule comme separateur, fins de ligne LF, poids a la virgule arrondi, colonnes dans le desordre`() {
        val contenu = "exercice,date,ordre_exercice,ordre_serie,palier,poids_kg,repetitions,libelle_entrainement\n" +
            "Curl,2026-10-12,1,1,1,\"12,6\",10,\n\n" +
            "Curl,2026-10-12,1,2,1,0,8,\n"
        assertEquals(
            listOf(EntrainementImporte(LocalDate.of(2026, 10, 12), null, listOf(ExerciceImporte("Curl", listOf(s(1, 12.5 to 10), s(2, null to 8)))))),
            valide(contenu),
        )
    }

    @Test
    fun `erreurs avec leur numero de ligne, fichier refuse en entier`() {
        assertEquals(
            listOf(
                "Ligne 3 : répétitions manquantes",
                "Ligne 4 : date « 14/09/2026 » invalide (attendu AAAA-MM-JJ)",
                "Ligne 5 : poids « vingt » invalide",
                "Ligne 7 : palier 1 de la série 1 de « Fentes » déjà présent à la ligne 6",
            ),
            erreurs(ressource("exemple-import-erreurs.csv")),
        )
    }

    @Test
    fun `superset incoherent ou incomplet`() {
        val entete = EnteteCsv.joinToString(";")
        assertEquals(
            listOf("Ligne 3 : superset 1 : il faut un exercice A et un exercice B, ni plus ni moins"),
            erreurs("$entete\n2026-10-12;;A1;1;1;1;8;10;1;1;A\n2026-10-12;;A2;2;1;1;8;10;1;1;A\n"),
        )
        assertEquals(
            listOf("Ligne 4 : superset 1 : l’exercice B doit suivre immédiatement l’exercice A"),
            erreurs("$entete\n2026-10-12;;A;1;1;1;8;10;1;1;A\n2026-10-12;;X;2;1;1;8;10;1;;\n2026-10-12;;B;3;1;1;8;10;1;1;B\n"),
        )
        assertEquals(listOf("Ligne 2 : superset sans rôle (A ou B)"), erreurs("$entete\n2026-10-12;;A;1;1;1;8;10;1;1;\n"))
        assertEquals(listOf("Ligne 2 : rôle de superset « C » invalide (A ou B)"), erreurs("$entete\n2026-10-12;;A;1;1;1;8;10;1;1;C\n"))
        // Un exercice seul de son superset redevient ordinaire, ses séries renumérotées.
        val (seul) = valide("$entete\n2026-10-12;;A;1;3;1;8;10;1;1;A\n")
        assertEquals(listOf(ExerciceImporte("A", listOf(s(1, 8.0 to 10)))), seul.exercices)
    }

    @Test
    fun `exercice nomme differemment et entrainement a deux dates`() {
        val entete = EnteteCsv.joinToString(";")
        assertEquals(
            listOf(
                "Ligne 3 : exercice 1 nommé « Fente » ici et « Squat » à la ligne 2",
                "Ligne 4 : entraînement 1 avec une autre date ou un autre libellé qu’à la ligne 2",
            ),
            erreurs("$entete\n2026-10-12;;Squat;1;1;1;8;10;1;;\n2026-10-12;;Fente;1;2;1;8;10;1;;\n2026-10-13;;Squat;1;3;1;8;10;1;;\n"),
        )
    }

    @Test
    fun `fichier qui n'est pas un export CSV`() {
        assertEquals(
            listOf("Ce fichier n’est pas un export CSV d’IronTap. Seul le format CSV s’importe : exporte de nouveau en choisissant CSV."),
            erreurs(exporterTexte(historique)),
        )
        assertEquals(
            listOf("Ce fichier n’est pas un export CSV d’IronTap complet : colonnes manquantes (palier, poids_kg)."),
            erreurs("date;libelle_entrainement;exercice;ordre_exercice;ordre_serie;repetitions\n"),
        )
        assertEquals(listOf("Le fichier est vide."), erreurs("\uFEFF\r\n"))
        assertEquals(listOf("Le fichier ne contient aucune série."), erreurs(EnteteCsv.joinToString(";") + "\r\n"))
        assertEquals(listOf("Ligne 2 : guillemet ouvert et jamais fermé"), erreurs(EnteteCsv.joinToString(";") + "\n2026-10-12;\"Dos;Curl\n"))
    }

    @Test
    fun `apostrophe ajoutee par l'export retiree`() {
        assertEquals("=1", lireChampTexte("'=1"))
        assertEquals("'Curl", lireChampTexte("''Curl"))
        assertEquals("'Curl", lireChampTexte("'Curl"))
        assertEquals("'", lireChampTexte("'"))
    }

    @Test
    fun `doublons ecartes un pour un`() {
        val a = EntrainementImporte(LocalDate.of(2026, 10, 12), null, listOf(ExerciceImporte("Squat", listOf(s(1, 100.0 to 5)))))
        val b = a.copy(libelle = "Jambes")
        assertEquals(listOf(b) to 1, ecarterDoublons(listOf(a, b), listOf(a)))
        assertEquals(listOf(a) to 1, ecarterDoublons(listOf(a, a), listOf(a)))
        assertEquals(emptyList<EntrainementImporte>() to 2, ecarterDoublons(listOf(a, a), listOf(a, a)))
    }
}
