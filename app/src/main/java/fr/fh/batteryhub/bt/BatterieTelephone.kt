package fr.fh.batteryhub.bt

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import fr.fh.batteryhub.data.Adresses
import fr.fh.batteryhub.data.Appareil
import fr.fh.batteryhub.data.StatsBatterie
import fr.fh.batteryhub.data.TypeAppareil

/**
 * Lecture complète de la batterie interne : niveau, santé, mais aussi ce qui
 * permet de suivre la charge et la décharge (courant, tension, température,
 * charge restante, cycles).
 *
 * Le nombre de cycles n'est pas exposé par Android avant la version 14, et
 * certains constructeurs ne le publient jamais : [EtatBatterie.cyclesSysteme]
 * vaut alors null et l'écran affiche une estimation calculée sur l'historique.
 */
object BatterieTelephone {

    private const val SANTE_INCONNUE = "santé inconnue"

    /** Valeur renvoyée par un capteur absent : on la traite comme une absence. */
    private const val ABSENT = Int.MIN_VALUE

    data class EtatBatterie(
        val niveau: Int = 0,
        val courantMa: Int? = null,
        val courantMoyenMa: Int? = null,
        val tensionMv: Int? = null,
        val temperatureC: Double? = null,
        val chargeRestanteMah: Int? = null,
        val cyclesSysteme: Int? = null,
        val secteur: Boolean = false,
        val enCharge: Boolean = false,
        val plein: Boolean = false,
        val sante: String = SANTE_INCONNUE,
        val technologie: String? = null,
        /** Vrai quand l'appareil publie vraiment son nombre de cycles. */
        val cyclesFiables: Boolean = false,
    ) {
        /** Puissance instantanée en watts, positive en charge. */
        val puissanceW: Double?
            get() {
                val milliampieres = courantMa ?: return null
                val millivolts = tensionMv ?: return null
                return milliampieres / 1000.0 * millivolts / 1000.0
            }
    }

    fun lireEtat(context: Context): EtatBatterie {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val niveau = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }
            ?: (sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0)

        val statut = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val branche = (sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val enCharge = statut == BatteryManager.BATTERY_STATUS_CHARGING || branche
        val plein = statut == BatteryManager.BATTERY_STATUS_FULL

        val tensionMv = sticky?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it > 0 }
        val tempDixieme = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, ABSENT) ?: ABSENT
        val temperature = if (tempDixieme != ABSENT && tempDixieme > 0) tempDixieme / 10.0 else null

        // Les constructeurs ne respectent pas tous le signe : on normalise pour
        // que le courant soit positif en charge et négatif en décharge.
        val courant = bm?.propriete(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            ?.let { StatsBatterie.normaliser(it, enCharge) }
        val courantMoyen = bm?.propriete(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
            ?.let { StatsBatterie.normaliser(it, enCharge) }
        val chargeRestante = bm?.propriete(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            ?.takeIf { it > 0 }
            ?.div(1000)

        val cycles = cyclesDuSysteme(context, bm, sticky)

        val sante = when (sticky?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Bonne"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Surchauffe"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Batterie HS"
            BatteryManager.BATTERY_HEALTH_COLD -> "Trop froide"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Surtension"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Défaillance"
            else -> SANTE_INCONNUE.replaceFirstChar { it.uppercase() }
        }

        return EtatBatterie(
            niveau = niveau,
            courantMa = courant,
            courantMoyenMa = courantMoyen,
            tensionMv = tensionMv,
            temperatureC = temperature,
            chargeRestanteMah = chargeRestante,
            cyclesSysteme = cycles.first,
            secteur = branche,
            enCharge = enCharge,
            plein = plein,
            sante = sante,
            technologie = sticky?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY),
            cyclesFiables = cycles.second,
        )
    }

    /** Cycles lus dans le système, et si la valeur vient bien de l'appareil. */
    private fun cyclesDuSysteme(
        context: Context,
        bm: BatteryManager?,
        sticky: Intent?,
    ): Pair<Int?, Boolean> {
        // Android 14 publie le compteur dans l'état de la batterie.
        if (Build.VERSION.SDK_INT >= 34) {
            val extra = sticky?.getIntExtra("android.os.extra.CYCLE_COUNT", -1) ?: -1
            if (extra > 0) return extra to true
        }
        // Certains appareils exposent la propriété avant que le SDK ne la déclare :
        // lecture par réflexion, silencieuse si la constante n'existe pas.
        return try {
            val cle = BatteryManager::class.java.getField("BATTERY_PROPERTY_CYCLE_COUNT").getInt(null)
            val valeur = bm?.getIntProperty(cle) ?: ABSENT
            if (valeur in 1..100_000) valeur to true else null to false
        } catch (_: Throwable) {
            null to false
        }
    }

    /** Microampères renvoyés par [BatteryManager], signés et convertis en mA. */
    private fun BatteryManager.propriete(cle: Int): Int? {
        val micro = try {
            getIntProperty(cle)
        } catch (_: Throwable) {
            return null
        }
        if (micro == ABSENT || micro == 0) return null
        return micro / 1000
    }

    /** Ligne de détail de la carte « Ce téléphone ». */
    fun lire(context: Context): Appareil {
        val etat = lireEtat(context)
        val details = buildList {
            add(etat.sante)
            etat.cyclesSysteme?.let { add("$it cycles") }
            etat.temperatureC?.let { add(String.format("%.1f °C", it)) }
            etat.courantMa?.let { add("${if (it >= 0) "+" else ""}$it mA") }
        }.joinToString(" · ")

        return Appareil(
            adresse = Adresses.TELEPHONE,
            nom = "Ce téléphone",
            type = TypeAppareil.TELEPHONE,
            connecte = true,
            niveau = etat.niveau,
            detail = details.ifBlank { "Batterie interne" },
            horodatage = System.currentTimeMillis(),
            lectureAuto = true,
        )
    }
}
