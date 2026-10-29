package fr.fh.batteryhub.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** Déclenche la lecture planifiée. */
class VeilleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(Intent(context, VeilleService::class.java))
            } else {
                context.startService(Intent(context, VeilleService::class.java))
            }
        } catch (_: Throwable) {
            // arrière-plan restreint : replanification au prochain passage
            VeillePlanificateur.planifier(context, 30)
        }
    }
}
