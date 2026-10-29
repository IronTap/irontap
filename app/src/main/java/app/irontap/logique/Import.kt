package app.irontap.logique

import app.irontap.donnees.EntrainementComplet
import app.irontap.donnees.RoleSuperset
import app.irontap.donnees.ValeursPalier
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Entraînement lu dans un export CSV (DECISIONS.md « Import »), prêt à être ajouté à l'historique. */
data class EntrainementImporte(val date: LocalDate, val libelle: String?, val exercices: List<ExerciceImporte>)

/**
 * Exercice importé, dans l'ordre de l'entraînement. [superset] : numéro du superset dans l'entraînement (1, 2… dans
 * l'ordre des exercices), avec le [role] de l'exercice ; A est immédiatement suivi de B. Les séries d'un exercice
 * ordinaire sont numérotées 1, 2… ; celles d'un superset gardent le numéro de leur tour, qui peut sauter (A : 1, 3).
 */
data class ExerciceImporte(
    val nom: String,
    val series: List<SerieImportee>,
    val superset: Int? = null,
    val role: RoleSuperset? = null,
)

/** Série importée : son numéro et ses paliers, le principal d'abord. */
data class SerieImportee(val ordre: Int, val paliers: List<ValeursPalier>)

/** Erreur de lecture : [ligne] du fichier (1 = en-tête), `null` quand elle concerne le fichier entier. */
data class ErreurImport(val ligne: Int?, val message: String) {
    override fun toString() = if (ligne != null) "Ligne $ligne : $message" else message
}

sealed interface LectureImport {
    data class Valide(val entrainements: List<EntrainementImporte>) : LectureImport

    /** Fichier refusé en entier : rien n'est importé. Erreurs dans l'ordre des lignes. */
    data class Erreurs(val erreurs: List<ErreurImport>) : LectureImport
}

/** Colonnes indispensables : celles de la spec §5. `entrainement`, `superset` et `role_superset` sont facultatives. */
private val Obligatoires = EnteteCsv.take(8)

/**
 * Lit un export CSV d'IronTap (DECISIONS.md « Import ») et reconstruit ses entraînements, dans l'ordre du fichier.
 * - Format : marque d'ordre d'octets facultative, séparateur `;` ou `,` (celui de l'en-tête), champs entre guillemets
 *   (RFC 4180), fins de ligne CRLF ou LF, colonnes repérées par leur nom, lignes vides ignorées. L'apostrophe ajoutée
 *   par l'export devant un libellé ou un nom est retirée.
 * - Entraînements : regroupés par la colonne `entrainement` ; sans elle (anciens exports), une ligne commence un
 *   nouvel entraînement quand la date ou le libellé change, ou qu'elle ne suit pas la précédente (exercice, série,
 *   palier croissants, même nom pour un même exercice).
 * - Valeurs : poids au point ou à la virgule, arrondi au 0,25 kg comme à la saisie, vide ou nul = poids du corps ;
 *   répétitions de 1 à 999. Exercices, séries et paliers renumérotés 1, 2… dans leur ordre, sauf les tours d'un
 *   superset, gardés tels quels (les tours vides sont retirés). Un exercice seul de son superset devient ordinaire,
 *   comme dans l'appli ; sans colonnes de superset (anciens exports), A et B reviennent en deux exercices ordinaires.
 * Toute erreur refuse le fichier entier, avec chaque ligne fautive.
 */
fun lireExportCsv(contenu: String): LectureImport {
    val texte = contenu.removePrefix("\uFEFF")
    val premiereLigne = texte.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
    val separateur = if (';' !in premiereLigne && ',' in premiereLigne) ',' else ';'
    val enregistrements = try {
        decouper(texte, separateur).filter { e -> e.champs.any { it.isNotBlank() } }
    } catch (e: GuillemetNonFerme) {
        return LectureImport.Erreurs(listOf(ErreurImport(e.ligne, "guillemet ouvert et jamais fermé")))
    }
    val entete = enregistrements.firstOrNull() ?: return refus("Le fichier est vide.")
    val colonnes = entete.champs.withIndex().associate { (index, nom) -> nom.trim().lowercase() to index }
    val manquantes = Obligatoires.filter { it !in colonnes }
    if (manquantes.size == Obligatoires.size) {
        return refus("Ce fichier n’est pas un export CSV d’IronTap. Seul le format CSV s’importe : exporte de nouveau en choisissant CSV.")
    }
    if (manquantes.isNotEmpty()) return refus("Ce fichier n’est pas un export CSV d’IronTap complet : colonnes manquantes (${manquantes.joinToString(", ")}).")
    if (("superset" in colonnes) != ("role_superset" in colonnes)) {
        return refus("Colonne manquante : ${if ("superset" in colonnes) "role_superset" else "superset"}.")
    }
    val erreurs = ArrayList<ErreurImport>()
    val lignes = enregistrements.drop(1).mapNotNull { lireLigne(it, colonnes, entete.champs.size, erreurs) }
    if (erreurs.isEmpty() && lignes.isEmpty()) return refus("Le fichier ne contient aucune série.")
    val entrainements = regrouper(lignes, numerotes = "entrainement" in colonnes, erreurs).map { construire(it, erreurs) }
    return if (erreurs.isEmpty()) {
        LectureImport.Valide(entrainements)
    } else {
        LectureImport.Erreurs(erreurs.sortedBy { it.ligne ?: 0 })
    }
}

private fun refus(message: String) = LectureImport.Erreurs(listOf(ErreurImport(null, message)))

/** Champ libre tel qu'avant l'export : sans l'apostrophe ajoutée devant une formule ou une apostrophe (cf. [champTexteCsv]). */
internal fun lireChampTexte(champ: String): String =
    if (champ.length > 1 && champ[0] == '\'' && (champ[1] in DebutsFormule || champ[1] == '\'')) champ.substring(1) else champ

// --- Découpage RFC 4180 ---

/** Un enregistrement CSV : ses champs et la ligne du fichier où il commence. */
private class Enregistrement(val ligne: Int, val champs: List<String>)

private class GuillemetNonFerme(val ligne: Int) : Exception()

private fun decouper(texte: String, separateur: Char): List<Enregistrement> {
    val enregistrements = ArrayList<Enregistrement>()
    val champs = ArrayList<String>()
    val champ = StringBuilder()
    var ligne = 1
    var debut = 1
    var entreGuillemets = false
    // Le champ en cours a commencé par un guillemet : un guillemet qui suit le guillemet fermant est du texte.
    var cite = false
    fun finChamp() {
        champs += champ.toString()
        champ.setLength(0)
        cite = false
    }
    fun finEnregistrement() {
        finChamp()
        enregistrements += Enregistrement(debut, champs.toList())
        champs.clear()
    }
    var i = 0
    while (i < texte.length) {
        val c = texte[i]
        if (entreGuillemets) {
            when {
                c == '"' && texte.getOrNull(i + 1) == '"' -> {
                    champ.append('"')
                    i++
                }
                c == '"' -> entreGuillemets = false
                else -> {
                    if (c == '\n') ligne++
                    champ.append(c)
                }
            }
        } else {
            when {
                c == '"' && champ.isEmpty() && !cite -> {
                    entreGuillemets = true
                    cite = true
                }
                c == separateur -> finChamp()
                c == '\r' || c == '\n' -> {
                    if (c == '\r' && texte.getOrNull(i + 1) == '\n') i++
                    finEnregistrement()
                    ligne++
                    debut = ligne
                }
                else -> champ.append(c)
            }
        }
        i++
    }
    if (entreGuillemets) throw GuillemetNonFerme(debut)
    if (champ.isNotEmpty() || champs.isNotEmpty() || cite) finEnregistrement()
    return enregistrements
}

// --- Lignes ---

/** Une ligne valide : un palier, et l'entraînement, l'exercice et la série auxquels il appartient. */
private class LigneLue(
    val ligne: Int,
    val date: LocalDate,
    val libelle: String?,
    val nom: String,
    val exercice: Int,
    val serie: Int,
    val palier: Int,
    val valeurs: ValeursPalier,
    val entrainement: Int?,
    val superset: Int?,
    val role: RoleSuperset?,
) {
    /** Suit [precedente] dans un même entraînement, dans l'ordre de l'export. */
    fun suit(precedente: LigneLue): Boolean {
        val ordre = compareValuesBy(this, precedente, { it.exercice }, { it.serie }, { it.palier })
        return ordre > 0 && (exercice != precedente.exercice || nom == precedente.nom)
    }
}

/** Ligne lue, ou `null` après avoir ajouté ses erreurs à [erreurs]. */
private fun lireLigne(enregistrement: Enregistrement, colonnes: Map<String, Int>, largeur: Int, erreurs: MutableList<ErreurImport>): LigneLue? {
    val ligne = enregistrement.ligne
    val nombreErreurs = erreurs.size
    fun erreur(message: String) {
        erreurs += ErreurImport(ligne, message)
    }
    fun champ(nom: String) = colonnes[nom]?.let { enregistrement.champs.getOrNull(it) }?.trim().orEmpty()
    if (enregistrement.champs.size > largeur) erreur("${enregistrement.champs.size} colonnes au lieu de $largeur")

    val date = champ("date").let { texte ->
        try {
            LocalDate.parse(texte)
        } catch (e: DateTimeParseException) {
            erreur(if (texte.isEmpty()) "date manquante" else "date « $texte » invalide (attendu AAAA-MM-JJ)")
            null
        }
    }
    val libelle = lireChampTexte(champ("libelle_entrainement")).trim().ifEmpty { null }
    val nom = lireChampTexte(champ("exercice")).trim()
    if (nom.isEmpty()) erreur("nom d’exercice manquant")
    fun entier(colonne: String, libelleColonne: String): Int? {
        val texte = champ(colonne)
        val valeur = texte.toIntOrNull()?.takeIf { it > 0 }
        if (valeur == null) erreur(if (texte.isEmpty()) "$libelleColonne manquant" else "$libelleColonne « $texte » invalide")
        return valeur
    }
    val exercice = entier("ordre_exercice", "ordre_exercice")
    val serie = entier("ordre_serie", "ordre_serie")
    val palier = entier("palier", "palier")
    val poids = champ("poids_kg").let { texte ->
        if (texte.isEmpty()) return@let null
        val valeur = texte.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 && it <= POIDS_MAX_KG }
        if (valeur == null) erreur("poids « $texte » invalide")
        valeur?.let(::arrondirPoids)?.takeIf { it > 0 }
    }
    val repetitions = champ("repetitions").let { texte ->
        val valeur = texte.toIntOrNull()?.takeIf { it in 1..REPETITIONS_MAX }
        if (valeur == null) erreur(if (texte.isEmpty()) "répétitions manquantes" else "répétitions « $texte » invalides (de 1 à $REPETITIONS_MAX)")
        valeur
    }
    val entrainement = if ("entrainement" in colonnes) entier("entrainement", "numéro d’entraînement") else null
    val superset = champ("superset")
    val roleTexte = champ("role_superset")
    val numeroSuperset = superset.takeIf { it.isNotEmpty() }?.let { texte ->
        texte.toIntOrNull()?.takeIf { it > 0 }.also { if (it == null) erreur("superset « $texte » invalide") }
    }
    val role = roleTexte.takeIf { it.isNotEmpty() }?.let { texte ->
        RoleSuperset.entries.firstOrNull { it.name.equals(texte, ignoreCase = true) }.also {
            if (it == null) erreur("rôle de superset « $texte » invalide (A ou B)")
        }
    }
    if (superset.isEmpty() && roleTexte.isNotEmpty()) erreur("rôle de superset sans numéro de superset")
    if (superset.isNotEmpty() && roleTexte.isEmpty()) erreur("superset sans rôle (A ou B)")

    if (erreurs.size > nombreErreurs) return null
    return LigneLue(
        ligne, checkNotNull(date), libelle, nom, checkNotNull(exercice), checkNotNull(serie), checkNotNull(palier),
        ValeursPalier(poids, checkNotNull(repetitions)), entrainement, numeroSuperset, role,
    )
}

// --- Entraînements ---

/** Lignes regroupées par entraînement, dans l'ordre de leur première ligne (cf. [lireExportCsv]). */
private fun regrouper(lignes: List<LigneLue>, numerotes: Boolean, erreurs: MutableList<ErreurImport>): List<List<LigneLue>> {
    val groupes = LinkedHashMap<Int, MutableList<LigneLue>>()
    var numero = 0
    var precedente: LigneLue? = null
    for (ligne in lignes) {
        val cle = if (numerotes) {
            checkNotNull(ligne.entrainement)
        } else {
            val p = precedente
            if (p == null || p.date != ligne.date || p.libelle != ligne.libelle || !ligne.suit(p)) numero++
            numero
        }
        val groupe = groupes.getOrPut(cle) { ArrayList() }
        val premiere = groupe.firstOrNull()
        if (premiere != null && (premiere.date != ligne.date || premiere.libelle != ligne.libelle)) {
            erreurs += ErreurImport(ligne.ligne, "entraînement $cle avec une autre date ou un autre libellé qu’à la ligne ${premiere.ligne}")
        } else {
            groupe += ligne
        }
        precedente = ligne
    }
    return groupes.values.toList()
}

/** Exercices, séries et paliers d'un entraînement, vérifiés et renumérotés (cf. [lireExportCsv]). */
private fun construire(lignes: List<LigneLue>, erreurs: MutableList<ErreurImport>): EntrainementImporte {
    val premiere = lignes.first()
    // Exercice → ses lignes, la première fixant le nom et le superset.
    val exercices = lignes.groupBy { it.exercice }.toSortedMap().values.map { lignesExercice ->
        val reference = lignesExercice.first()
        for (ligne in lignesExercice.drop(1)) {
            when {
                ligne.nom != reference.nom ->
                    erreurs += ErreurImport(ligne.ligne, "exercice ${ligne.exercice} nommé « ${ligne.nom} » ici et « ${reference.nom} » à la ligne ${reference.ligne}")
                ligne.superset != reference.superset || ligne.role != reference.role ->
                    erreurs += ErreurImport(ligne.ligne, "exercice ${ligne.exercice} avec un autre superset qu’à la ligne ${reference.ligne}")
            }
        }
        val series = lignesExercice.groupBy { it.serie }.toSortedMap().map { (ordre, lignesSerie) ->
            val vus = HashMap<Int, LigneLue>()
            for (ligne in lignesSerie) {
                vus.putIfAbsent(ligne.palier, ligne)?.let {
                    erreurs += ErreurImport(ligne.ligne, "palier ${ligne.palier} de la série $ordre de « ${ligne.nom} » déjà présent à la ligne ${it.ligne}")
                }
            }
            SerieImportee(ordre, lignesSerie.sortedBy { it.palier }.distinctBy { it.palier }.map { it.valeurs })
        }
        reference to series
    }
    // Supersets : un A et un B, A immédiatement suivi de B ; un exercice seul redevient ordinaire.
    val membres = exercices.withIndex().filter { it.value.first.superset != null }.groupBy { it.value.first.superset }
    val complets = HashSet<Int>()
    for ((numero, liste) in membres) {
        if (liste.size == 1) continue
        val (indexA, a) = liste.firstOrNull { it.value.first.role == RoleSuperset.A } ?: liste.first()
        val b = liste.firstOrNull { it.value.first.role == RoleSuperset.B }
        when {
            liste.size > 2 || b == null || a.first.role != RoleSuperset.A ->
                erreurs += ErreurImport(liste[1].value.first.ligne, "superset $numero : il faut un exercice A et un exercice B, ni plus ni moins")
            b.index != indexA + 1 ->
                erreurs += ErreurImport(b.value.first.ligne, "superset $numero : l’exercice B doit suivre immédiatement l’exercice A")
            else -> complets += checkNotNull(numero)
        }
    }
    val numeros = exercices.mapNotNull { it.first.superset?.takeIf { n -> n in complets } }.distinct()
        .withIndex().associate { (index, numero) -> numero to index + 1 }
    val importes = exercices.map { (reference, series) ->
        val numero = reference.superset?.let { numeros[it] }
        if (numero == null) {
            ExerciceImporte(reference.nom, series.mapIndexed { index, serie -> serie.copy(ordre = index + 1) })
        } else {
            ExerciceImporte(reference.nom, series, numero, reference.role)
        }
    }
    return EntrainementImporte(premiere.date, premiere.libelle, renumeroterTours(importes))
}

/** Tours de chaque superset renumérotés 1, 2… pour A et B ensemble, sans tour vide. */
private fun renumeroterTours(exercices: List<ExerciceImporte>): List<ExerciceImporte> {
    val tours = exercices.filter { it.superset != null }.groupBy { it.superset }.mapValues { (_, membres) ->
        membres.flatMap { it.series }.map { it.ordre }.distinct().sorted().withIndex().associate { (index, tour) -> tour to index + 1 }
    }
    return exercices.map { exercice ->
        val renumerotation = tours[exercice.superset] ?: return@map exercice
        exercice.copy(series = exercice.series.map { it.copy(ordre = renumerotation.getValue(it.ordre)) })
    }
}

// --- Doublons ---

/** Entraînement de l'historique sous la forme d'un import, pour reconnaître un entraînement déjà présent. */
fun EntrainementComplet.versImport(): EntrainementImporte {
    val supersets = numerosSupersets(exercices)
    return EntrainementImporte(
        entrainement.date,
        entrainement.libelle,
        exercices.map { exercice ->
            val numero = exercice.exercice.supersetId?.let { supersets[it] }
            ExerciceImporte(
                nom = exercice.exercice.nom,
                series = exercice.series.mapIndexed { index, serie -> SerieImportee(if (numero != null) serie.serie.ordre else index + 1, serie.valeurs) },
                superset = numero,
                role = exercice.exercice.supersetRole.takeIf { numero != null },
            )
        },
    )
}

/**
 * Sépare les entraînements [importes] de ceux déjà présents dans [existants] : même date, même libellé, mêmes
 * exercices, séries, paliers et supersets. Un pour un : un fichier qui contient deux fois le même entraînement en
 * ajoute un si l'historique en a déjà un. Retourne les entraînements à ajouter, dans l'ordre, et le nombre d'ignorés.
 */
fun ecarterDoublons(importes: List<EntrainementImporte>, existants: List<EntrainementImporte>): Pair<List<EntrainementImporte>, Int> {
    val restants = existants.groupingBy { it }.eachCount().toMutableMap()
    val nouveaux = importes.filter { entrainement ->
        val n = restants[entrainement] ?: 0
        if (n > 0) restants[entrainement] = n - 1
        n == 0
    }
    return nouveaux to importes.size - nouveaux.size
}
