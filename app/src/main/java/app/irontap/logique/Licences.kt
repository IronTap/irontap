package app.irontap.logique

/** Bloc d'un texte de licence affiché par l'écran 10b. */
sealed interface BlocLicence {
    /** Ligne de copyright : 14 sp, texte normal. */
    data class Copyright(val texte: String) : BlocLicence

    /** Titre de section de la licence (« PREAMBLE ») : 13 / 500 neutre-200. */
    data class Titre(val texte: String) : BlocLicence

    /** Paragraphe : 13 sp, interligne 1,6, neutre-300. */
    data class Paragraphe(val texte: String) : BlocLicence
}

/**
 * Découpe le texte brut d'une licence (coupé à ~70 colonnes) en blocs à afficher : paragraphes séparés par des
 * lignes vides, lignes d'un paragraphe réunies, filets de tirets ignorés. Titre : une ligne seule en majuscules
 * (« TERMS AND CONDITIONS »), ou une ligne courte en majuscules suivie de son texte sans ligne vide (« PREAMBLE »
 * de l'OFL) ; une ligne en majuscules longue est le début d'un paragraphe (« THE SOFTWARE IS PROVIDED… »).
 */
fun decouperLicence(texte: String): List<BlocLicence> =
    texte.replace("\r\n", "\n").split(Regex("\n[ \t]*\n")).flatMap { paragraphe ->
        val brutes = paragraphe.lines().filter { it.isNotBlank() && !it.trim().all { c -> c == '-' || c == '=' } }
        val lignes = brutes.map { it.trim() }
        when {
            lignes.isEmpty() -> emptyList()
            lignes.size == 1 && (enMajuscules(lignes.single(), 70) || centree(brutes.single()) || Numerotee.matches(lignes.single())) ->
                listOf(BlocLicence.Titre(lignes.single()))
            lignes.size > 1 && (enMajuscules(lignes.first(), 40) || centree(brutes.first())) ->
                listOf(BlocLicence.Titre(lignes.first()), BlocLicence.Paragraphe(lignes.drop(1).joinToString(" ")))
            else -> listOf(BlocLicence.Paragraphe(lignes.joinToString(" ")))
        }
    }

private fun enMajuscules(ligne: String, longueurMax: Int) =
    ligne.length <= longueurMax && ligne.any { it.isLetter() } && ligne == ligne.uppercase()

/** Ligne centrée dans le texte brut (« Preamble » de la GPL, « Apache License ») : 10 espaces en tête au moins. */
private fun centree(ligne: String) = ligne.takeWhile { it == ' ' }.length >= 10 && ligne.trim().length <= 70

/** Intitulé numéroté seul sur sa ligne : « 0. Definitions. », « 15. Disclaimer of Warranty. ». */
private val Numerotee = Regex("""\d+\.\s+\S.{0,60}\.""")

/**
 * Sépare la ligne de copyright du fichier (« Copyright (c) 2023 Phosphor Icons », parmi les premiers paragraphes)
 * du reste du texte : elle s'affiche en tête, « (c) » écrit « © ». Sans ligne de copyright, le texte est inchangé.
 */
fun extraireCopyright(blocs: List<BlocLicence>): List<BlocLicence> {
    val index = blocs.take(3).indexOfFirst { it is BlocLicence.Paragraphe && it.texte.startsWith("Copyright", ignoreCase = true) }
    if (index < 0) return blocs
    val ligne = (blocs[index] as BlocLicence.Paragraphe).texte.replace(Regex("\\((c|C)\\)"), "©")
    return listOf(BlocLicence.Copyright(ligne)) + blocs.filterIndexed { i, _ -> i != index }
}

/**
 * Bibliothèque embarquée (liste générée à la compilation, `assets/licences/bibliotheques.txt`). [licence] :
 * identifiant SPDX (« Apache-2.0 », « BSD-3-Clause »).
 */
data class Bibliotheque(val licence: String, val titulaire: String, val module: String, val version: String)

/** Lignes « licence⇥titulaire⇥groupe:module⇥version » ; une ligne mal formée est ignorée. */
fun lireBibliotheques(texte: String): List<Bibliotheque> = texte.lines().mapNotNull { ligne ->
    ligne.split('\t').takeIf { it.size == 4 }?.let { (licence, titulaire, module, version) -> Bibliotheque(licence, titulaire, module, version) }
}

/** Nom affiché d'une licence SPDX des bibliothèques. */
fun nomLicence(spdx: String) = when (spdx) {
    "Apache-2.0" -> "Apache 2.0"
    "BSD-3-Clause" -> "BSD 3 clauses"
    else -> spdx
}

/**
 * « Bibliothèques Android » (10b), licence par licence, Apache 2.0 d'abord : par titulaire, sa ligne de copyright
 * puis ses bibliothèques (« module version », une par ligne), puis le texte de la licence, une seule fois ([textes] :
 * texte de chaque licence SPDX).
 */
fun blocsBibliotheques(bibliotheques: List<Bibliotheque>, textes: Map<String, String>): List<BlocLicence> =
    bibliotheques.groupBy { it.licence }.entries.sortedBy { if (it.key == "Apache-2.0") 0 else 1 }.flatMap { (licence, liste) ->
        val parTitulaire = liste.groupBy { it.titulaire }.flatMap { (titulaire, modules) ->
            listOf(
                BlocLicence.Copyright("Copyright © $titulaire"),
                BlocLicence.Paragraphe(modules.joinToString("\n") { "${it.module} ${it.version}" }),
            )
        }
        // Le texte est repris tel quel : la mention « Copyright 2008 Google Inc. » de la BSD doit être reproduite.
        val texte = textes[licence]?.let { decouperLicence(it) }.orEmpty()
        val annonce = if (liste.size > 1) "Ces bibliothèques sont distribuées" else "Cette bibliothèque est distribuée"
        parTitulaire + BlocLicence.Paragraphe("$annonce sous la licence ${nomLicence(licence)}, dont le texte suit.") + texte
    }
