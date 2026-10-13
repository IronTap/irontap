package app.irontap.logique

import java.text.Normalizer

/** Suggestion de nom d'exercice ; [debut] et [fin] délimitent dans [nom] la partie correspondant à la saisie. */
data class Suggestion(val nom: String, val debut: Int, val fin: Int)

/**
 * Suggestions pour la saisie d'un nom d'exercice (spec §4.3.2) : insensibles à la casse et aux accents,
 * correspondance sur n'importe quelle partie du nom, sans doublons, dans l'ordre de [noms]
 * (du plus récemment utilisé au plus ancien). Un nom identique à la saisie n'est pas proposé.
 */
fun suggerer(saisie: String, noms: List<String>, max: Int = 4): List<Suggestion> {
    val recherche = normaliser(saisie.trim()).texte
    if (recherche.isEmpty()) return emptyList()
    val vus = HashSet<String>()
    val suggestions = ArrayList<Suggestion>(max)
    for (nom in noms) {
        val normalise = normaliser(nom)
        if (!vus.add(normalise.texte) || normalise.texte == recherche) continue
        val position = normalise.texte.indexOf(recherche)
        if (position < 0) continue
        val fin = normalise.origine[position + recherche.length - 1] + 1
        suggestions += Suggestion(nom, debut = normalise.origine[position], fin = fin)
        if (suggestions.size == max) break
    }
    return suggestions
}

/** Texte en minuscules sans accents ; [origine] donne, pour chaque caractère, sa position dans le texte d'origine. */
private class TexteNormalise(val texte: String, val origine: IntArray)

private fun normaliser(texte: String): TexteNormalise {
    val resultat = StringBuilder(texte.length)
    val origine = ArrayList<Int>(texte.length)
    texte.forEachIndexed { index, caractere ->
        // Chaque caractère est décomposé (é → e + accent) puis privé de ses marques diacritiques.
        Normalizer.normalize(caractere.toString(), Normalizer.Form.NFD).forEach { decompose ->
            if (Character.getType(decompose) != Character.NON_SPACING_MARK.toInt()) {
                resultat.append(decompose.lowercaseChar())
                origine += index
            }
        }
    }
    return TexteNormalise(resultat.toString(), origine.toIntArray())
}
