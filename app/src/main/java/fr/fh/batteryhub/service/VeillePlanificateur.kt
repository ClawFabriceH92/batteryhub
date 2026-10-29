package fr.fh.batteryhub.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import fr.fh.batteryhub.data.Regles

/** Planifie la prochaine lecture à la fréquence choisie. */
object VeillePlanificateur {

    private const val REQUEST_CODE = 4211

    fun planifier(context: Context, minutes: Int) {
        val gestionnaire = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intention = Intent(context, VeilleReceiver::class.java)
        val enAttente = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intention,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val delai = Regles.frequenceValide(minutes) * 60_000L
        val echeance = System.currentTimeMillis() + delai
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                gestionnaire.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, echeance, enAttente)
            } else {
                gestionnaire.set(AlarmManager.RTC_WAKEUP, echeance, enAttente)
            }
        } catch (_: SecurityException) {
            try {
                gestionnaire.set(AlarmManager.RTC, echeance, enAttente)
            } catch (_: Throwable) {
                // aucune alarme possible : la veille reprendra à la prochaine ouverture
            }
        } catch (_: Throwable) {
            // ignore
        }
    }

    fun annuler(context: Context) {
        val gestionnaire = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intention = Intent(context, VeilleReceiver::class.java)
        val enAttente = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intention,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            gestionnaire.cancel(enAttente)
        } catch (_: Throwable) {
            // ignore
        }
    }
}
