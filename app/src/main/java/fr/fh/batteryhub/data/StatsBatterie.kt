package fr.fh.batteryhub.data

/**
 * Un relevé de la batterie interne, enrichi du courant et du secteur quand
 * l'appareil sait les donner. Les relevés d'avant la version 1.1.0 n'ont que
 * l'horodatage et le niveau : ils restent lisibles, ils ne comptent simplement
 * pas dans les mAh (on ne peut pas intégrer un courant qu'on n'a pas mesuré).
 */
data class Echantillon(
    val horodatage: Long,
    val niveau: Int,
    val courantMa: Int? = null,
    val secteur: Boolean = false,
    val temperatureC: Double? = null,
    val chargeRestanteMah: Int? = null,
)

/**
 * Statistiques de charge et de décharge sur une fenêtre glissante, calculées
 * à partir de nos propres relevés : l'intégrale du courant donne les mAh
 * réellement entrés et sortis, sans aucune donnée envoyée ailleurs.
 */
data class StatsChargeDecharge(
    val jours: Int = 7,
    val echantillons: Int = 0,
    val mesures: Int = 0,
    val mAhCharges: Int = 0,
    val mAhDecharges: Int = 0,
    val heuresEnCharge: Double = 0.0,
    val heuresHorsCharge: Double = 0.0,
    val vitesseChargeMa: Int? = null,
    val vitesseDechargeMa: Int? = null,
    val sessionsCharge: Int = 0,
    val dureeMoyenneSessionMin: Int? = null,
    val mAhParSession: Int? = null,
    val profondeurMoyenne: Int? = null,
    val temperatureMax: Double? = null,
    val temperatureMin: Double? = null,
    val cyclesEstimes: Double = 0.0,
    val capaciteMah: Int = 0,
    /** Heures de batterie restantes au rythme mesuré sur la fenêtre. */
    val autonomieEstimeeH: Double? = null,
    /** Minutes pour atteindre 100 % au rythme de charge mesuré. */
    val chargeRestanteMin: Int? = null,
) {
    /** Vrai dès qu'on a de quoi intégrer un courant, pas seulement des niveaux. */
    val exploitable: Boolean get() = mesures >= 2
}

object StatsBatterie {

    /** Au-delà de ce trou entre deux relevés, on n'intègre pas : ce serait faux. */
    const val TROU_MAX_MIN = 90

    const val CAPACITE_PAR_DEFAUT = 4500

    /**
     * @param echantillons relevés triés par date croissante
     * @param capaciteMah capacité nominale de la batterie, pour les cycles estimés
     */
    fun calculer(
        echantillons: List<Echantillon>,
        capaciteMah: Int = CAPACITE_PAR_DEFAUT,
        jours: Int = 7,
    ): StatsChargeDecharge {
        if (echantillons.size < 2) {
            return StatsChargeDecharge(jours = jours, echantillons = echantillons.size, capaciteMah = capaciteMah)
        }
        val tries = echantillons.sortedBy { it.horodatage }
        var mAhCharges = 0.0
        var mAhDecharges = 0.0
        var heuresCharge = 0.0
        var heuresDecharge = 0.0
        var mesures = 0

        for (i in 1 until tries.size) {
            val precedent = tries[i - 1]
            val courant = tries[i]
            val minutes = (courant.horodatage - precedent.horodatage) / 60_000.0
            if (minutes <= 0 || minutes > TROU_MAX_MIN) continue
            // L'intervalle où le secteur change mélange deux régimes : on ne
            // l'intègre pas, sinon la moitié de la charge compterait en décharge.
            if (precedent.secteur != courant.secteur) continue
            val intensite = intensite(precedent, courant) ?: continue
            mesures++
            val heures = minutes / 60.0
            if (intensite >= 0) {
                mAhCharges += intensite * heures
                heuresCharge += heures
            } else {
                mAhDecharges += -intensite * heures
                heuresDecharge += heures
            }
        }

        val sessions = sessionsDeCharge(tries)
        val dureeMoyenne = if (sessions.isNotEmpty()) {
            sessions.sumOf { it.second } / sessions.size
        } else null

        val temperatures = tries.mapNotNull { it.temperatureC }
        val dechargeMa = if (heuresDecharge > 0) (mAhDecharges / heuresDecharge) else null
        val chargeMa = if (heuresCharge > 0) (mAhCharges / heuresCharge) else null
        val dernierNiveau = tries.last().niveau

        return StatsChargeDecharge(
            jours = jours,
            echantillons = tries.size,
            mesures = mesures,
            mAhCharges = mAhCharges.toInt(),
            mAhDecharges = mAhDecharges.toInt(),
            heuresEnCharge = heuresCharge,
            heuresHorsCharge = heuresDecharge,
            vitesseChargeMa = chargeMa?.toInt(),
            vitesseDechargeMa = dechargeMa?.toInt(),
            sessionsCharge = sessions.size,
            dureeMoyenneSessionMin = dureeMoyenne?.toInt(),
            mAhParSession = if (sessions.isNotEmpty() && mAhCharges > 0) {
                (mAhCharges / sessions.size).toInt()
            } else null,
            profondeurMoyenne = profondeurMoyenne(tries),
            temperatureMax = temperatures.maxOrNull(),
            temperatureMin = temperatures.minOrNull(),
            cyclesEstimes = if (capaciteMah > 0) mAhDecharges / capaciteMah else 0.0,
            capaciteMah = capaciteMah,
            autonomieEstimeeH = dechargeMa?.takeIf { it > 0 }
                ?.let { dernierNiveau / 100.0 * capaciteMah / it },
            chargeRestanteMin = chargeMa?.takeIf { it > 0 }
                ?.let { ((100 - dernierNiveau) / 100.0 * capaciteMah / it * 60).toInt() },
        )
    }

    /**
     * Les constructeurs ne respectent pas tous le signe du courant : on le
     * normalise pour que le courant soit positif en charge, négatif en décharge.
     */
    fun normaliser(courantMa: Int, enCharge: Boolean): Int =
        if (enCharge) kotlin.math.abs(courantMa) else -kotlin.math.abs(courantMa)

    /**
     * Intensité de l'intervalle : la moyenne des deux mesures quand les deux
     * existent, sinon celle qu'on a. Nulle si aucune n'est disponible.
     */
    fun intensite(precedent: Echantillon, courant: Echantillon): Double? {
        val valeurs = listOfNotNull(precedent.courantMa, courant.courantMa)
        if (valeurs.isEmpty()) return null
        val moyenne = valeurs.average()
        // Un courant nul signifie « pas de mesure » sur la plupart des appareils.
        return if (moyenne == 0.0) null else moyenne
    }

    /**
     * Sessions de charge : de la première mesure secteur au dernier relevé
     * encore branché. Renvoie (début, durée en minutes).
     */
    fun sessionsDeCharge(tries: List<Echantillon>): List<Pair<Long, Int>> {
        val sessions = mutableListOf<Pair<Long, Int>>()
        var debut: Long? = null
        var dernier = 0L
        for (echantillon in tries) {
            if (echantillon.secteur) {
                if (debut == null) debut = echantillon.horodatage
                dernier = echantillon.horodatage
            } else if (debut != null) {
                sessions.add(debut!! to ((dernier - debut!!) / 60_000L).toInt())
                debut = null
            }
        }
        if (debut != null) sessions.add(debut!! to ((dernier - debut!!) / 60_000L).toInt())
        return sessions
    }

    /**
     * Profondeur de décharge : de combien le niveau est descendu avant chaque
     * remise sur secteur. On part du niveau le plus haut vu depuis la dernière
     * session de charge.
     */
    fun profondeurMoyenne(tries: List<Echantillon>): Int? {
        val chutes = mutableListOf<Int>()
        var haut = tries.firstOrNull()?.niveau ?: return null
        var surSecteur = tries.firstOrNull()?.secteur ?: false
        for (echantillon in tries) {
            if (echantillon.secteur) {
                if (!surSecteur) {
                    chutes.add((haut - echantillon.niveau).coerceAtLeast(0))
                    surSecteur = true
                }
                haut = echantillon.niveau
            } else {
                if (surSecteur) {
                    // Début d'une décharge : le niveau du moment est la référence.
                    haut = echantillon.niveau
                    surSecteur = false
                }
                haut = maxOf(haut, echantillon.niveau)
            }
        }
        return if (chutes.isEmpty()) null else chutes.average().toInt()
    }
}
