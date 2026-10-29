package fr.fh.batteryhub.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val recu = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (recu != AutoUpdater.dernierTelechargement(context)) return
        // si la permission d'installer des applications inconnues manque, l'écran Réglages
        // propose le bouton vers les autorisations : aucun échec silencieux
        AutoUpdater.installer(context)
    }
}
