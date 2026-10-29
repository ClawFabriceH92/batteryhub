package fr.fh.batteryhub.service

import android.content.Context
import fr.fh.batteryhub.bt.BatterieTelephone
import fr.fh.batteryhub.bt.BluetoothBatterie
import fr.fh.batteryhub.data.Appareil
import fr.fh.batteryhub.data.Echantillon
import fr.fh.batteryhub.data.Historique
import fr.fh.batteryhub.data.Reglages
import fr.fh.batteryhub.widget.Instantane
import fr.fh.batteryhub.widget.WidgetProvider

/** Relevé complet, partagé par le service de veille et par l'écran d'accueil. */
object Releveur {

    fun executer(context: Context): List<Appareil> {
        val reglages = Reglages(context)
        // Une seule lecture de la batterie interne : elle sert à la carte du
        // téléphone et à l'enregistrement des statistiques de charge.
        val etat = try {
            BatterieTelephone.lireEtat(context)
        } catch (_: Throwable) {
            null
        }
        val liste = BluetoothBatterie.relever(
            context = context,
            lectureDeconnectes = reglages.lectureDeconnectes,
            lectureAuto = { adresse -> reglages.lectureAuto(adresse) },
            timeoutGattMs = if (reglages.lectureDeconnectes) BluetoothBatterie.TIMEOUT_GATT_MS else 3_000L,
        )

        val historique = Historique(context)
        try {
            historique.purger()
            historique.enregistrer(
                liste.filter { it.niveau >= 0 },
                etat?.let { e ->
                    Echantillon(
                        horodatage = System.currentTimeMillis(),
                        niveau = e.niveau,
                        courantMa = e.courantMa,
                        secteur = e.secteur,
                        temperatureC = e.temperatureC,
                        chargeRestanteMah = e.chargeRestanteMah,
                    )
                },
            )
        } catch (_: Throwable) {
            // une base indisponible ne doit pas empêcher l'affichage
        } finally {
            historique.close()
        }

        reglages.dernierScan = System.currentTimeMillis()
        reglages.instantane = Instantane.versJson(liste)
        WidgetProvider.rafraichir(context)
        return liste
    }

    fun instantane(context: Context): List<Appareil> =
        Instantane.depuisJson(Reglages(context).instantane)
}
