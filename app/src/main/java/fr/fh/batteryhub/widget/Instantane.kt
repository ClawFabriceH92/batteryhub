package fr.fh.batteryhub.widget

import fr.fh.batteryhub.data.Appareil
import fr.fh.batteryhub.data.TypeAppareil
import org.json.JSONArray
import org.json.JSONObject

/** Sérialisation de l'instantané des appareils, pour le widget et l'écran d'accueil. */
object Instantane {

    fun versJson(appareils: List<Appareil>): String {
        val tableau = JSONArray()
        appareils.forEach { a ->
            tableau.put(
                JSONObject().apply {
                    put("adresse", a.adresse)
                    put("nom", a.nom)
                    put("type", a.type.name)
                    put("connecte", a.connecte)
                    put("niveau", a.niveau)
                    put("detail", a.detail)
                    put("horodatage", a.horodatage)
                    put("lectureAuto", a.lectureAuto)
                }
            )
        }
        return tableau.toString()
    }

    fun depuisJson(texte: String): List<Appareil> {
        val liste = ArrayList<Appareil>()
        try {
            val tableau = JSONArray(texte)
            for (i in 0 until tableau.length()) {
                val o = tableau.getJSONObject(i)
                liste.add(
                    Appareil(
                        adresse = o.optString("adresse"),
                        nom = o.optString("nom"),
                        type = runCatching { TypeAppareil.valueOf(o.optString("type")) }
                            .getOrDefault(TypeAppareil.AUTRE),
                        connecte = o.optBoolean("connecte", false),
                        niveau = o.optInt("niveau", -1),
                        detail = o.optString("detail"),
                        horodatage = o.optLong("horodatage", 0L),
                        lectureAuto = o.optBoolean("lectureAuto", true),
                    )
                )
            }
        } catch (_: Throwable) {
            // instantané illisible : on repart d'une liste vide
        }
        return liste
    }
}
