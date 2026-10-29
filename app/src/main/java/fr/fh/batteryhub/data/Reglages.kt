package fr.fh.batteryhub.data

import android.content.Context

class Reglages(context: Context) {

    private val p = context.getSharedPreferences("batteryhub", Context.MODE_PRIVATE)

    var frequenceMinutes: Int
        get() = Regles.frequenceValide(p.getInt(CLE_FREQ, Regles.FREQUENCE_PAR_DEFAUT))
        set(v) = p.edit().putInt(CLE_FREQ, Regles.frequenceValide(v)).apply()

    var seuil: Int
        get() = p.getInt(CLE_SEUIL, Regles.SEUIL_PAR_DEFAUT)
        set(v) = p.edit().putInt(CLE_SEUIL, v).apply()

    var lectureDeconnectes: Boolean
        get() = p.getBoolean(CLE_DECO, true)
        set(v) = p.edit().putBoolean(CLE_DECO, v).apply()

    var dernierScan: Long
        get() = p.getLong(CLE_SCAN, 0L)
        set(v) = p.edit().putLong(CLE_SCAN, v).apply()

    var widgetMode: String
        get() = p.getString(CLE_WIDGET, "4x2") ?: "4x2"
        set(v) = p.edit().putString(CLE_WIDGET, v).apply()

    /** Lecture automatique d'un appareil précis (les appareils connectés sont toujours lus). */
    fun lectureAuto(adresse: String): Boolean = p.getBoolean("auto_$adresse", true)

    fun setLectureAuto(adresse: String, actif: Boolean) {
        p.edit().putBoolean("auto_$adresse", actif).apply()
    }

    /** Instantané des appareils du dernier relevé, au format JSON compact. */
    var instantane: String
        get() = p.getString(CLE_INSTANTANE, "[]") ?: "[]"
        set(v) = p.edit().putString(CLE_INSTANTANE, v).apply()

    private companion object {
        const val CLE_FREQ = "frequence"
        const val CLE_SEUIL = "seuil"
        const val CLE_DECO = "lecture_deconnectes"
        const val CLE_SCAN = "dernier_scan"
        const val CLE_WIDGET = "widget_mode"
        const val CLE_INSTANTANE = "instantane"
    }
}
