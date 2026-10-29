package fr.fh.batteryhub

import fr.fh.batteryhub.data.Echantillon
import fr.fh.batteryhub.data.StatsBatterie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsBatterieTest {

    private val minute = 60_000L

    private fun echantillon(
        minutes: Long,
        niveau: Int,
        courant: Int? = null,
        secteur: Boolean = false,
        temperature: Double? = null,
    ) = Echantillon(
        horodatage = minutes * minute,
        niveau = niveau,
        courantMa = courant,
        secteur = secteur,
        temperatureC = temperature,
    )

    @Test
    fun `decharge de 1 A pendant 2 h donne 2000 mAh`() {
        // Un relevé toutes les 30 min, courant constant de -1000 mA.
        val echantillons = (0..4).map { echantillon(it * 30L, 100 - it * 5, -1000) }
        val stats = StatsBatterie.calculer(echantillons, capaciteMah = 4000, jours = 7)

        assertEquals(2000, stats.mAhDecharges)
        assertEquals(0, stats.mAhCharges)
        assertEquals(1000, stats.vitesseDechargeMa)
        assertTrue(stats.exploitable)
    }

    @Test
    fun `charge et decharge sont separees par le signe du courant`() {
        val echantillons = listOf(
            echantillon(0, 50, -800),
            echantillon(30, 60, -800),
            echantillon(60, 70, 1500, secteur = true),
            echantillon(90, 80, 1500, secteur = true),
        )
        val stats = StatsBatterie.calculer(echantillons, capaciteMah = 4000)

        assertEquals(400, stats.mAhDecharges)
        assertEquals(750, stats.mAhCharges)
        assertEquals(1500, stats.vitesseChargeMa)
    }

    @Test
    fun `un trou de plus de 90 minutes n est pas integre`() {
        val echantillons = listOf(
            echantillon(0, 80, -1000),
            echantillon(30, 79, -1000),
            // 5 heures sans relevé : intégrer donnerait des mAh inventés.
            echantillon(330, 70, -1000),
            echantillon(360, 69, -1000),
        )
        val stats = StatsBatterie.calculer(echantillons, capaciteMah = 4000)
        assertEquals(1000, stats.mAhDecharges) // deux intervalles de 30 min seulement
    }

    @Test
    fun `les releves sans courant ne comptent pas dans les mAh`() {
        val echantillons = listOf(
            echantillon(0, 90),
            echantillon(30, 88),
            echantillon(60, 86),
        )
        val stats = StatsBatterie.calculer(echantillons, capaciteMah = 4000)
        assertEquals(0, stats.mAhDecharges)
        assertEquals(0, stats.mesures)
        assertTrue(!stats.exploitable)
    }

    @Test
    fun `les sessions de charge sont comptees avec leur duree moyenne`() {
        val echantillons = listOf(
            echantillon(0, 50, -800),
            echantillon(30, 45, 1800, secteur = true),
            echantillon(60, 55, 1800, secteur = true),
            echantillon(90, 65, -700),
            echantillon(120, 60, -700),
            echantillon(150, 55, 1200, secteur = true),
            echantillon(180, 65, 1200, secteur = true),
        )
        val stats = StatsBatterie.calculer(echantillons, capaciteMah = 4500)

        assertEquals(2, stats.sessionsCharge)
        // Sessions de 30 min chacune (premier au dernier relevé branché).
        assertEquals(30, stats.dureeMoyenneSessionMin)
    }

    @Test
    fun `les cycles estimes divisent les mAh decharges par la capacite`() {
        val echantillons = (0..8).map { echantillon(it * 30L, 100 - it * 10, -1000) }
        val stats = StatsBatterie.calculer(echantillons, capaciteMah = 2000)

        // 4 h à 1 A = 4000 mAh déchargés, pour une batterie de 2000 mAh.
        assertEquals(4000, stats.mAhDecharges)
        assertEquals(2.0, stats.cyclesEstimes, 0.01)
    }

    @Test
    fun `la profondeur de decharge moyenne suit les passages sur secteur`() {
        val echantillons = listOf(
            echantillon(0, 100, -500),
            echantillon(60, 70, -500),
            echantillon(120, 70, 1500, secteur = true),
            echantillon(180, 100, -500),
            echantillon(240, 60, -500),
            echantillon(300, 60, 1500, secteur = true),
        )
        val stats = StatsBatterie.calculer(echantillons, capaciteMah = 4500)
        // 100 → 70 puis 100 → 60 : 35 % en moyenne.
        assertEquals(35, stats.profondeurMoyenne)
    }

    @Test
    fun `temperatures extremes et valeurs absentes`() {
        val echantillons = listOf(
            echantillon(0, 80, -500, temperature = 31.5),
            echantillon(30, 78, -500, temperature = 36.2),
            echantillon(60, 76, -500, temperature = 29.0),
        )
        val stats = StatsBatterie.calculer(echantillons, capaciteMah = 4500)
        assertEquals(36.2, stats.temperatureMax!!, 0.01)
        assertEquals(29.0, stats.temperatureMin!!, 0.01)

        val vide = StatsBatterie.calculer(emptyList())
        assertNull(vide.temperatureMax)
        assertEquals(0, vide.mAhDecharges)
    }

    @Test
    fun `le signe du courant est normalise selon la charge`() {
        assertEquals(-1200, StatsBatterie.normaliser(1200, enCharge = false))
        assertEquals(-1200, StatsBatterie.normaliser(-1200, enCharge = false))
        assertEquals(1200, StatsBatterie.normaliser(-1200, enCharge = true))
        assertEquals(1200, StatsBatterie.normaliser(1200, enCharge = true))
    }
}
