package app.irontap.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import app.irontap.logique.FormatExport
import java.io.File
import java.io.IOException

/**
 * Écritures de l'export (spec §5, DECISIONS.md « Export ») : aucune permission de stockage. Le partage
 * passe par un fichier du cache exposé en lecture seule par [FileProvider] ; l'enregistrement écrit dans
 * le document que l'utilisateur a choisi (`ACTION_CREATE_DOCUMENT`). À appeler hors du fil principal.
 */
class Exporteur(context: Context) {

    private val context = context.applicationContext

    /** Seul dossier exposé par le FileProvider (`res/xml/chemins_export.xml`). */
    private val dossier get() = File(context.cacheDir, DOSSIER)

    /**
     * Intention `ACTION_SEND` de l'export. Un texte court part tel quel (messagerie, bloc-notes) ; un texte long
     * ou un CSV part en pièce jointe, écrite dans le cache après en avoir effacé les exports précédents.
     */
    @Throws(IOException::class)
    fun intentionPartage(format: FormatExport, nomFichier: String, contenu: String, sujet: String): Intent {
        val intention = Intent(Intent.ACTION_SEND)
            .setType(format.mime)
            .putExtra(Intent.EXTRA_SUBJECT, sujet)
        if (format == FormatExport.Texte && contenu.length <= TEXTE_DIRECT_MAX) {
            return intention.putExtra(Intent.EXTRA_TEXT, contenu)
        }
        val fichier = ecrireCache(nomFichier, format.fichier(contenu))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}$SUFFIXE_AUTORITE", fichier)
        return intention
            .putExtra(Intent.EXTRA_STREAM, uri)
            // ClipData : la permission de lecture suit l'intention jusqu'à l'appli choisie dans le sélecteur.
            .apply { clipData = ClipData.newRawUri(nomFichier, uri) }
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /**
     * Écrit l'export dans le document choisi (que le sélecteur vient de créer), en remplaçant son contenu.
     * En cas d'échec, le document est supprimé pour ne pas laisser de fichier vide ou tronqué.
     */
    @Throws(IOException::class)
    fun enregistrer(uri: Uri, format: FormatExport, contenu: String) {
        try {
            val flux = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Document inaccessible")
            flux.use { it.write(format.fichier(contenu).toByteArray(Charsets.UTF_8)) }
        } catch (e: IOException) {
            supprimer(uri)
            throw e
        } catch (e: SecurityException) {
            supprimer(uri)
            throw IOException(e)
        } catch (e: IllegalArgumentException) {
            supprimer(uri)
            throw IOException(e)
        }
    }

    private fun supprimer(uri: Uri) {
        try {
            if (DocumentsContract.isDocumentUri(context, uri)) DocumentsContract.deleteDocument(context.contentResolver, uri)
        } catch (e: Exception) {
            // Au mieux : suppression impossible (fournisseur qui la refuse…) : l'erreur d'écriture reste affichée.
        }
    }

    private fun ecrireCache(nomFichier: String, contenu: String): File {
        val dossier = dossier
        dossier.listFiles()?.forEach { it.delete() }
        if (!dossier.isDirectory && !dossier.mkdirs()) throw IOException("Dossier d'export indisponible")
        // Le nom vient de nomFichierExport : pas de séparateur, il reste dans le dossier.
        return File(dossier, File(nomFichier).name).apply { writeText(contenu, Charsets.UTF_8) }
    }

    companion object {
        private const val DOSSIER = "exports"
        const val SUFFIXE_AUTORITE = ".exports"

        /** Au-delà, le texte passe en pièce jointe : une intention trop lourde ferait échouer le partage. */
        const val TEXTE_DIRECT_MAX = 50_000
    }
}
