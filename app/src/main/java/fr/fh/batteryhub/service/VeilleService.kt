package fr.fh.batteryhub.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import fr.fh.batteryhub.MainActivity
import fr.fh.batteryhub.R
import fr.fh.batteryhub.data.Appareil
import fr.fh.batteryhub.data.Reglages
import fr.fh.batteryhub.data.Regles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Service de veille : lit les niveaux, enregistre l'historique, met le widget à jour,
 * notifie les appareils sous le seuil, puis replanifie la lecture suivante.
 */
class VeilleService : Service() {

    private val portee = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        creerCanaux()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        demarrerAuPremierPlan()
        val manuel = intent?.getBooleanExtra(EXTRA_MANUEL, false) == true
        portee.launch {
            try {
                val resultat = Releveur.executer(this@VeilleService)
                notifierSeuils(this@VeilleService, Reglages(this@VeilleService), resultat)
                if (manuel) notifierResultatManuel(resultat)
            } catch (t: Throwable) {
                // une erreur de lecture ne doit jamais tuer le service ni l'application
                notifierErreur()
            } finally {
                VeillePlanificateur.planifier(this@VeilleService, Reglages(this@VeilleService).frequenceMinutes)
                if (!manuel) arreter()
            }
        }
        return START_STICKY
    }

    private fun arreter() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
        } catch (_: Throwable) {
            // ignore
        }
    }

    private fun demarrerAuPremierPlan() {
        val notif = notificationVeille("Relevé des batteries en cours…")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(ID_NOTIFICATION_VEILLE, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(ID_NOTIFICATION_VEILLE, notif)
            }
        } catch (_: Throwable) {
            // si le système refuse le service de premier plan, la lecture continue
        }
    }

    private fun notificationVeille(texte: String): Notification {
        val ouverture = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CANAL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle("BatteryHub")
            .setContentText(texte)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(ouverture)
            .build()
    }

    private fun notifierSeuils(context: Context, reglages: Reglages, liste: List<Appareil>) {
        val bas = liste.filter { it.niveau >= 0 && Regles.sousSeuil(it.niveau, reglages.seuil) }
        if (bas.isEmpty()) return
        val titre = if (bas.size == 1) "${bas[0].nom} : ${bas[0].niveau} %"
        else "${bas.size} appareils sous ${reglages.seuil} %"
        val texte = bas.joinToString(" · ") { "${it.nom} ${it.niveau} %" }
        val ouverture = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CANAL_ALERTES)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle(titre)
            .setContentText(texte)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texte))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(ouverture)
            .build()
        try {
            val gestionnaire = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            gestionnaire.notify(ID_NOTIFICATION_ALERTE, notif)
        } catch (_: Throwable) {
            // notification refusée : sans conséquence sur le relevé
        }
    }

    private fun notifierResultatManuel(liste: List<Appareil>) {
        val lus = liste.count { it.niveau >= 0 }
        val texte = "$lus appareil(s) relevé(s) · ${Regles.formatHeure(System.currentTimeMillis())}"
        try {
            val gestionnaire = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            gestionnaire.notify(
                ID_NOTIFICATION_ALERTE,
                NotificationCompat.Builder(this, CANAL_ALERTES)
                    .setSmallIcon(R.drawable.ic_stat_battery)
                    .setContentTitle("Relevé terminé")
                    .setContentText(texte)
                    .setAutoCancel(true)
                    .build()
            )
        } catch (_: Throwable) {
            // ignore
        }
    }

    private fun notifierErreur() {
        try {
            val gestionnaire = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            gestionnaire.notify(
                ID_NOTIFICATION_ALERTE,
                NotificationCompat.Builder(this, CANAL_ALERTES)
                    .setSmallIcon(R.drawable.ic_stat_battery)
                    .setContentTitle("Relevé impossible")
                    .setContentText("Vérifiez l'autorisation Bluetooth et la batterie de l'appareil.")
                    .setAutoCancel(true)
                    .build()
            )
        } catch (_: Throwable) {
            // ignore
        }
    }

    private fun creerCanaux() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val gestionnaire = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        gestionnaire.createNotificationChannel(
            NotificationChannel(CANAL_SERVICE, "Veille des batteries", NotificationManager.IMPORTANCE_LOW)
        )
        gestionnaire.createNotificationChannel(
            NotificationChannel(CANAL_ALERTES, "Alertes de batterie", NotificationManager.IMPORTANCE_HIGH)
        )
        gestionnaire.createNotificationChannel(
            NotificationChannel(CANAL_MAJ, "Mises à jour", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    companion object {
        const val CANAL_SERVICE = "batteryhub_veille"
        const val CANAL_ALERTES = "batteryhub_alertes"
        const val CANAL_MAJ = "batteryhub_maj"
        const val ID_NOTIFICATION_VEILLE = 1001
        const val ID_NOTIFICATION_ALERTE = 1002
        const val EXTRA_MANUEL = "manuel"

        /** Lance un relevé ponctuel, sans planification (bouton « relever maintenant »). */
        fun lancerManuel(context: Context) {
            val intent = Intent(context, VeilleService::class.java).putExtra(EXTRA_MANUEL, true)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Throwable) {
                // contexte restreint : le relevé repartira au prochain cycle
            }
        }
    }
}
