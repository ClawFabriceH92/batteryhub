package fr.fh.batteryhub.update

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/** Télécharge l'APK de mise à jour et lance l'installation. */
object AutoUpdater {

    private const val PREFS = "batteryhub_update"
    private const val CLE_DOWNLOAD_ID = "download_id"
    private const val NOM_APK = "batteryhub-update.apk"

    fun fichierApk(context: Context): File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), NOM_APK)

    fun dernierTelechargement(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(CLE_DOWNLOAD_ID, -1L)

    fun peutInstaller(context: Context): Boolean =
        try { context.packageManager.canRequestPackageInstalls() } catch (_: Throwable) { false }

    fun ouvrirReglagesInstallation(context: Context) {
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Throwable) {
                // ignore
            }
        }
    }

    fun telecharger(context: Context, url: String): Boolean = try {
        val gestionnaire = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (gestionnaire == null) {
            false
        } else {
            fichierApk(context).delete()
            val requete = DownloadManager.Request(Uri.parse(url))
                .setTitle("BatteryHub : mise à jour")
                .setDescription("Téléchargement de la nouvelle version…")
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationUri(Uri.fromFile(fichierApk(context)))
                .setAllowedOverMetered(true)
            val id = gestionnaire.enqueue(requete)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putLong(CLE_DOWNLOAD_ID, id).apply()
            true
        }
    } catch (_: Throwable) {
        false
    }

    fun installer(context: Context): Boolean {
        if (!peutInstaller(context)) return false
        val fichier = fichierApk(context)
        if (!fichier.exists() || fichier.length() == 0L) return false
        return try {
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", fichier
            )
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (_: Throwable) {
            false
        }
    }
}
