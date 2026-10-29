package app.irontap.export

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Fichier choisi pour l'import plus gros que [Importeur.TAILLE_MAX]. */
class FichierTropVolumineux : IOException()

/**
 * Lecture du fichier choisi pour l'import (`ACTION_OPEN_DOCUMENT`, DECISIONS.md « Import ») : aucune permission de
 * stockage, l'appli ne lit que le document que l'utilisateur a choisi. À appeler hors du fil principal.
 */
class Importeur(context: Context) {

    private val context = context.applicationContext

    /** Texte du document. */
    @Throws(IOException::class)
    fun lire(uri: Uri): String {
        val octets = try {
            val flux = context.contentResolver.openInputStream(uri) ?: throw IOException("Document inaccessible")
            flux.use(::lireAuPlus)
        } catch (e: SecurityException) {
            throw IOException(e)
        } catch (e: IllegalArgumentException) {
            throw IOException(e)
        }
        return decoder(octets)
    }

    private fun lireAuPlus(flux: InputStream): ByteArray {
        val sortie = ByteArrayOutputStream()
        val tampon = ByteArray(64 * 1024)
        while (true) {
            val lus = flux.read(tampon)
            if (lus < 0) return sortie.toByteArray()
            sortie.write(tampon, 0, lus)
            if (sortie.size() > TAILLE_MAX) throw FichierTropVolumineux()
        }
    }

    companion object {
        /** Bien au-delà d'un historique réel (environ 60 octets par palier). */
        const val TAILLE_MAX = 20 * 1024 * 1024

        /** UTF-8 (export d'IronTap), ou Windows-1252 s'il n'en est pas (CSV réenregistré par Excel). */
        internal fun decoder(octets: ByteArray): String = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(octets))
                .toString()
        } catch (e: CharacterCodingException) {
            String(octets, Charset.forName("windows-1252"))
        }
    }
}
