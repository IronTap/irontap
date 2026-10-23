package app.irontap.ui.apropos

import android.content.res.AssetManager
import app.irontap.logique.BlocLicence
import app.irontap.logique.blocsBibliotheques
import app.irontap.logique.decouperLicence
import app.irontap.logique.extraireCopyright
import app.irontap.logique.lireBibliotheques

/** Dépôt du code source (« Code source », 10a). */
const val UrlCodeSource = "https://github.com/IronTap/irontap"

/**
 * Composant dont l'écran 10b affiche la licence : [nom] et [ligne] (10a), [licence] (sous-titre de 10b). Les textes
 * sont embarqués dans `assets/licences/` à la compilation (tâche `preparerLicences`), lus hors ligne.
 */
enum class ComposantLicence(val nom: String, val ligne: String, val licence: String) {
    IronTap("IronTap", "GPL-3.0 · logiciel libre", "GNU GPL 3.0 ou ultérieure"),
    Inter("Inter", "Police · SIL Open Font License 1.1", "SIL Open Font License 1.1"),
    Phosphor("Phosphor Icons", "Icônes · MIT", "MIT"),
    Bibliotheques("Bibliothèques Android", "Kotlin, Jetpack Compose, Room… · Apache 2.0, BSD", "Apache 2.0 · BSD 3 clauses (protobuf)"),
    ;

    companion object {
        /** Lignes de la carte « Licences tierces ». */
        val Tierces = listOf(Inter, Phosphor, Bibliotheques)
    }
}

/** Texte de la licence de [composant], découpé pour l'affichage. À appeler hors du fil principal. */
fun lireLicence(assets: AssetManager, composant: ComposantLicence): List<BlocLicence> {
    fun lire(fichier: String) = assets.open("licences/$fichier").bufferedReader().use { it.readText() }
    return when (composant) {
        ComposantLicence.IronTap -> listOf(
            BlocLicence.Copyright("Copyright © 2026 IronTap Dev"),
            BlocLicence.Paragraphe(
                "IronTap est un logiciel libre : vous pouvez le redistribuer et le modifier selon les termes de la licence " +
                    "GNU GPL, version 3 ou toute version ultérieure. Il est distribué sans aucune garantie. Le texte de la " +
                    "licence suit.",
            ),
        ) + decouperLicence(lire("irontap-LICENSE.txt"))
        ComposantLicence.Inter -> extraireCopyright(decouperLicence(lire("inter-OFL.txt")))
        ComposantLicence.Phosphor -> extraireCopyright(decouperLicence(lire("phosphor-MIT.txt")))
        ComposantLicence.Bibliotheques -> blocsBibliotheques(
            lireBibliotheques(lire("bibliotheques.txt")),
            mapOf("Apache-2.0" to lire("apache-2.0.txt"), "BSD-3-Clause" to lire("protobuf-BSD-3-Clause.txt")),
        )
    }
}
