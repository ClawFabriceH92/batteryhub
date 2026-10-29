package fr.fh.batteryhub.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import fr.fh.batteryhub.MainActivity
import fr.fh.batteryhub.R
import fr.fh.batteryhub.data.Appareil
import fr.fh.batteryhub.data.Reglages
import fr.fh.batteryhub.data.Regles

/** Widget d'écran d'accueil : les quatre appareils les plus bas. */
class WidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id ->
            try {
                appWidgetManager.updateAppWidget(id, construire(context))
            } catch (_: Throwable) {
                // widget retiré entre-temps
            }
        }
    }

    companion object {

        private val IDS_LIGNES = intArrayOf(
            R.id.ligne1, R.id.ligne2, R.id.ligne3, R.id.ligne4
        )

        fun rafraichir(context: Context) {
            try {
                val gestionnaire = AppWidgetManager.getInstance(context) ?: return
                val composant = ComponentName(context, WidgetProvider::class.java)
                val ids = gestionnaire.getAppWidgetIds(composant)
                if (ids.isEmpty()) return
                val vues = construire(context)
                ids.forEach { gestionnaire.updateAppWidget(it, vues) }
            } catch (_: Throwable) {
                // aucun widget posé
            }
        }

        fun construire(context: Context): RemoteViews {
            val vues = RemoteViews(context.packageName, R.layout.widget_batteryhub)
            val reglages = Reglages(context)
            val appareils = Instantane.depuisJson(reglages.instantane)
            val mode = reglages.widgetMode

            val aAfficher: List<Appareil> = if (mode == "2x2") {
                appareils.filter { it.niveau >= 0 }.minByOrNull { it.niveau }?.let { listOf(it) } ?: emptyList()
            } else {
                appareils.filter { it.niveau >= 0 }.sortedBy { it.niveau }.take(4)
            }

            val ouverture = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            vues.setOnClickPendingIntent(R.id.widget_root, ouverture)

            val scan = reglages.dernierScan
            vues.setTextViewText(
                R.id.widget_maj,
                if (scan > 0) Regles.formatHeure(scan) else "jamais"
            )

            if (aAfficher.isEmpty()) {
                vues.setViewVisibility(R.id.widget_vide, View.VISIBLE)
                IDS_LIGNES.forEach { vues.setViewVisibility(it, View.GONE) }
                return vues
            }

            vues.setViewVisibility(R.id.widget_vide, View.GONE)
            IDS_LIGNES.forEachIndexed { index, idLigne ->
                val a = aAfficher.getOrNull(index)
                if (a == null) {
                    vues.setViewVisibility(idLigne, View.GONE)
                } else {
                    vues.setViewVisibility(idLigne, View.VISIBLE)
                    vues.setTextViewText(idLigne, "${tronquer(a.nom)}  ${a.niveau} %")
                    vues.setTextColor(idLigne, couleur(Regles.categorie(a.niveau)))
                }
            }
            return vues
        }

        private fun tronquer(nom: String): String =
            if (nom.length <= 18) nom else nom.take(17) + "…"

        private fun couleur(categorie: String): Int = when (categorie) {
            "ko" -> Color.parseColor("#E5534B")
            "warn" -> Color.parseColor("#F0A63A")
            else -> Color.parseColor("#2ECC8F")
        }
    }
}
