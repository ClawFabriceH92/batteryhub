package fr.fh.batteryhub.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import fr.fh.batteryhub.BuildConfig
import fr.fh.batteryhub.data.Historique
import fr.fh.batteryhub.data.Regles
import java.io.File

/** Export CSV de l'historique complet, séparateur point-virgule, BOM UTF-8. */
object ExportCsv {

    fun ecrire(context: Context): File? = try {
        val historique = Historique(context)
        val contenu = try {
            historique.toutExporterCsv()
        } finally {
            historique.close()
        }
        val dossier = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: File(context.filesDir, "exports")
        dossier.mkdirs()
        val fichier = File(
            dossier,
            Regles.nomFichierCsv(System.currentTimeMillis(), BuildConfig.VERSION_NAME)
        )
        fichier.writeText(contenu, Charsets.UTF_8)
        fichier
    } catch (_: Throwable) {
        null
    }

    /** Ouvre le partage Android sur le fichier exporté. */
    fun partager(context: Context, fichier: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", fichier
            )
            val intention = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fichier.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(intention, "Exporter l'historique")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Throwable) {
            // aucun application de partage : le fichier reste sur l'appareil
        }
    }

    /** Chemin du dernier export, pour l'afficher à l'écran. */
    fun cheminLisible(context: Context): String {
        val dossier = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        return dossier?.absolutePath ?: context.filesDir.absolutePath
    }
}
