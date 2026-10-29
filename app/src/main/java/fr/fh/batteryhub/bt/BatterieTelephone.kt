package fr.fh.batteryhub.bt

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import fr.fh.batteryhub.data.Adresses
import fr.fh.batteryhub.data.Appareil
import fr.fh.batteryhub.data.TypeAppareil

object BatterieTelephone {

    private const val SANTE_INCONNUE = "santé inconnue"

    fun lire(context: Context): Appareil {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val niveau = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0

        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val sante = when (sticky?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "santé bonne"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "surchauffe"
            BatteryManager.BATTERY_HEALTH_DEAD -> "batterie HS"
            BatteryManager.BATTERY_HEALTH_COLD -> "trop froide"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "surtension"
            else -> SANTE_INCONNUE
        }
        val tempDixieme = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val temperature = if (tempDixieme > 0) String.format("%.1f °C", tempDixieme / 10.0) else null

        // BATTERY_PROPERTY_CYCLE_COUNT n'est pas exposé par toutes les versions du SDK :
        // lecture par réflexion, silencieuse si la constante est absente de l'appareil.
        val cycles = try {
            val champ = BatteryManager::class.java.getField("BATTERY_PROPERTY_CYCLE_COUNT")
            val cle = champ.getInt(null)
            bm?.getIntProperty(cle)?.takeIf { it > 0 }
        } catch (_: Throwable) {
            null
        }

        val details = buildList {
            add(sante.replaceFirstChar { it.uppercase() })
            if (cycles != null) add("$cycles cycles")
            if (temperature != null) add(temperature)
        }.joinToString(" · ")

        return Appareil(
            adresse = Adresses.TELEPHONE,
            nom = "Ce téléphone",
            type = TypeAppareil.TELEPHONE,
            connecte = true,
            niveau = niveau,
            detail = details.ifBlank { "Batterie interne" },
            horodatage = System.currentTimeMillis(),
            lectureAuto = true,
        )
    }
}
