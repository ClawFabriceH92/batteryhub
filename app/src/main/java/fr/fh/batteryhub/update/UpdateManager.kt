package fr.fh.batteryhub.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import fr.fh.batteryhub.BuildConfig
import fr.fh.batteryhub.R
import fr.fh.batteryhub.service.VeilleService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

/** Vérification des mises à jour au lancement puis chaque jour à 14 h 00. */
object UpdateManager {

    private const val ID_NOTIFICATION_MAJ = 1003
    private var boucleDemarree = false

    fun start(context: Context) {
        if (boucleDemarree) return
        boucleDemarree = true
        val application = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                verifier(application)
                while (isActive) {
                    val maintenant = Calendar.getInstance()
                    if (maintenant.get(Calendar.HOUR_OF_DAY) == 14 &&
                        maintenant.get(Calendar.MINUTE) == 0
                    ) {
                        verifier(application)
                        delay(61_000)
                    } else {
                        delay(30_000)
                    }
                }
            } catch (_: Throwable) {
                // la vérification de mise à jour ne doit jamais faire tomber l'application
            }
        }
    }

    fun verifier(context: Context) {
        val info = UpdateChecker.latestWithApk() ?: return
        if (!UpdateChecker.miseAJourDisponible(BuildConfig.VERSION_NAME, info)) return
        if (AutoUpdater.peutInstaller(context)) {
            AutoUpdater.telecharger(context, info.downloadUrl)
        } else {
            notifierPermissionManquante(context, info.versionName)
        }
    }

    private fun notifierPermissionManquante(context: Context, version: String) {
        try {
            creerCanal(context)
            val versReglages = PendingIntent.getActivity(
                context, 20,
                Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    android.net.Uri.parse("package:${context.packageName}")
                ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, VeilleService.CANAL_MAJ)
                .setSmallIcon(R.drawable.ic_stat_battery)
                .setContentTitle("BatteryHub $version disponible")
                .setContentText("Autorisez l'installation pour mettre à jour l'application.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(versReglages)
                .build()
            val gestionnaire =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            gestionnaire.notify(ID_NOTIFICATION_MAJ, notification)
        } catch (_: Throwable) {
            // notification impossible : pas bloquant
        }
    }

    private fun creerCanal(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val gestionnaire =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        gestionnaire.createNotificationChannel(
            NotificationChannel(
                VeilleService.CANAL_MAJ, "Mises à jour", NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }
}
