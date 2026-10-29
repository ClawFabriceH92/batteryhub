package fr.fh.batteryhub.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/** Les deux passages de relais système dont le flux de mise à jour a besoin. */
object AutoUpdater {

    const val AUTORISATION_MANQUANTE = "autorisation d'installation manquante"

    /**
     * Ouvre l'installeur sur l'APK téléchargé.
     * @return null en cas de succès, sinon la raison de l'échec à afficher.
     */
    fun install(context: Context, file: File): String? {
        if (!file.exists() || file.length() == 0L) return "fichier de mise à jour absent"
        if (!peutInstaller(context)) return AUTORISATION_MANQUANTE
        return try {
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            null
        } catch (e: Exception) {
            e.message ?: "ouverture de l'installeur impossible"
        }
    }

    fun peutInstaller(context: Context): Boolean = runCatching {
        context.packageManager.canRequestPackageInstalls()
    }.getOrDefault(false)

    fun openInstallSettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
                // ignore
            }
        }
    }
}
