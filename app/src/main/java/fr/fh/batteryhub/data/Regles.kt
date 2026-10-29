package fr.fh.batteryhub.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Règles métier pures, testables sans Android. */
object Regles {

    const val SEUIL_PAR_DEFAUT = 20
    const val FREQUENCE_PAR_DEFAUT = 30
    const val RETENTION_JOURS = 90

    val FREQUENCES: List<Int> = listOf(15, 30, 60, 180, 360)

    /** ok au-dessus de 30 %, warn de 16 à 30 %, ko à 15 % et moins. */
    fun categorie(niveau: Int): String = when {
        niveau <= 15 -> "ko"
        niveau <= 30 -> "warn"
        else -> "ok"
    }

    fun sousSeuil(niveau: Int, seuil: Int): Boolean = niveau <= seuil

    fun libelleFrequence(minutes: Int): String = when {
        minutes < 60 -> "$minutes min"
        minutes == 60 -> "1 h"
        else -> "${minutes / 60} h"
    }

    fun frequenceValide(minutes: Int): Int =
        if (minutes in FREQUENCES) minutes else FREQUENCE_PAR_DEFAUT

    fun nomFichierCsv(horodatage: Long, version: String): String =
        "batteryhub_" + jour(horodatage) + "_v" + version + ".csv"

    fun jour(horodatage: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date(horodatage))

    fun formatHorodatage(horodatage: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(horodatage))

    fun formatHeure(horodatage: Long): String =
        SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(horodatage))

    fun enteteCsv(): String = "adresse;nom;type;niveau;horodatage"

    fun ligneCsv(adresse: String, nom: String, type: String, niveau: Int, horodatage: Long): String {
        val nomNettoye = nom.replace(";", ",").replace("\n", " ")
        return "$adresse;$nomNettoye;$type;$niveau;${formatHorodatage(horodatage)}"
    }

    /** Un appareil muet n'a pas donné de niveau depuis au moins [jours] jours. */
    fun muet(horodatage: Long, maintenant: Long, jours: Int = 3): Boolean =
        maintenant - horodatage >= jours * 86_400_000L

    /** Estimation de tenue : minutes gagnées par point de batterie consommé. */
    fun tenueEstimee(minutesParPoint: Double, niveau: Int): String {
        val total = (minutesParPoint * niveau).toInt().coerceAtLeast(0)
        val h = total / 60
        val m = total % 60
        return if (h == 0) "$m min" else "$h h ${"%02d".format(m)}"
    }
}
