package fr.fh.batteryhub.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fr.fh.batteryhub.data.Reglages

/** Relance la veille après un redémarrage du téléphone. */
class DemarrageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        try {
            val reglages = Reglages(context)
            VeillePlanificateur.planifier(context, reglages.frequenceMinutes)
        } catch (_: Throwable) {
            // ignore
        }
    }
}
