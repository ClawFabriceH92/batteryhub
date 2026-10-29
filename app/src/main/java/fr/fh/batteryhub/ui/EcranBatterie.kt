package fr.fh.batteryhub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.fh.batteryhub.bt.BatterieTelephone
import fr.fh.batteryhub.data.Regles
import fr.fh.batteryhub.data.StatsChargeDecharge
import kotlin.math.abs

/**
 * Écran « Ma batterie » : ce que le téléphone veut bien dire (niveau, santé,
 * courant, température, cycles quand le système les publie) et ce qu'on calcule
 * nous-mêmes à partir des relevés (mAh entrés et sortis, sessions de charge,
 * vitesses, cycles estimés).
 */
@Composable
fun EcranBatterie(
    etat: BatterieTelephone.EtatBatterie?,
    stats: StatsChargeDecharge,
    capaciteMah: Int,
    dernierScan: Long,
    onCapacite: (Int) -> Unit,
    onRafraichir: () -> Unit,
) {
    var dialogueCapacite by remember { mutableStateOf(false) }
    var saisie by remember(capaciteMah) { mutableStateOf(capaciteMah.toString()) }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            "Ma batterie",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            if (dernierScan > 0) {
                "Relevé à ${Regles.formatHeure(dernierScan)} · mesure à chaque cycle de veille"
            } else {
                "Aucun relevé pour le moment"
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(14.dp))

        val cycles = etat?.cyclesSysteme
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tuile("Niveau", "${etat?.niveau ?: 0} %", Modifier.weight(1f))
            Tuile("Cycles", if (cycles != null) cycles.toString() else "est.", Modifier.weight(1f))
            Tuile("Santé", etat?.sante ?: "—", Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))

        FilledTonalButton(onClick = onRafraichir) { Text("Relever maintenant") }
        Spacer(Modifier.height(16.dp))

        // ------------------------------------------------------------- en direct
        Carte("Maintenant") {
            Ligne("État", when {
                etat == null -> "—"
                etat.plein -> "Pleine"
                etat.enCharge -> "En charge"
                else -> "Sur batterie"
            })
            Ligne("Courant", etat?.courantMa?.let { "${if (it >= 0) "+" else ""}$it mA" } ?: "non mesuré")
            Ligne(
                "Puissance",
                etat?.puissanceW?.let { String.format("%.1f W", abs(it)) } ?: "non mesurée",
            )
            Ligne("Tension", etat?.tensionMv?.let { String.format("%.3f V", it / 1000.0) } ?: "—")
            Ligne("Température", etat?.temperatureC?.let { String.format("%.1f °C", it) } ?: "—")
            Ligne(
                "Charge restante",
                etat?.chargeRestanteMah?.let { "$it mAh" } ?: "non fournie par le téléphone",
            )
            Ligne("Technologie", etat?.technologie ?: "—")
            val restant = when {
                etat == null -> null
                etat.enCharge || etat.secteur -> stats.chargeRestanteMin
                else -> stats.autonomieEstimeeH?.let { (it * 60).toInt() }
            }
            Ligne(
                if (etat?.enCharge == true || etat?.secteur == true) "Charge restante estimée" else "Autonomie estimée",
                restant?.let { duree(it) } ?: "en attente de mesures",
            )
        }

        Spacer(Modifier.height(12.dp))

        // -------------------------------------------------------- cycles et santé
        Carte("Cycles et usure") {
            if (cycles != null && etat?.cyclesFiables == true) {
                Ligne("Cycles comptés par le téléphone", "$cycles")
            } else {
                Ligne("Cycles comptés par le téléphone", "non publié par ce modèle")
                Ligne(
                    "Cycles estimés (${stats.jours} j)",
                    String.format("%.1f", stats.cyclesEstimes),
                )
                Text(
                    "Android ne donne le compteur de cycles qu'à partir d'Android 14, et " +
                        "seulement si le constructeur le publie. L'estimation divise les mAh " +
                        "déchargés depuis l'installation par la capacité nominale.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Ligne("Capacité nominale", "$capaciteMah mAh")
            Row {
                TextButton(onClick = { saisie = capaciteMah.toString(); dialogueCapacite = true }) {
                    Text(if (cycles != null) "Modifier la capacité" else "Renseigner la capacité")
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // --------------------------------------------------- charge et décharge
        Carte("Charge et décharge · ${stats.jours} derniers jours") {
            if (!stats.exploitable) {
                Text(
                    "L'intégration du courant commence maintenant : il faut au moins deux " +
                        "mesures avec courant pour calculer les mAh. Laissez la veille tourner, " +
                        "les chiffres apparaîtront aux prochains relevés.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Ligne("Énergie chargée", "${stats.mAhCharges} mAh")
                Ligne("Énergie déchargée", "${stats.mAhDecharges} mAh")
                Ligne(
                    "Sessions de charge",
                    if (stats.sessionsCharge == 0) "aucune" else "${stats.sessionsCharge}",
                )
                Ligne("Durée moyenne d'une charge", stats.dureeMoyenneSessionMin?.let { duree(it) } ?: "—")
                Ligne("mAh par session", stats.mAhParSession?.let { "$it mAh" } ?: "—")
                Ligne(
                    "Vitesse de charge",
                    stats.vitesseChargeMa?.let { "$it mA en moyenne" } ?: "—",
                )
                Ligne(
                    "Vitesse de décharge",
                    stats.vitesseDechargeMa?.let { "$it mA en moyenne" } ?: "—",
                )
                Ligne("Profondeur de décharge", stats.profondeurMoyenne?.let { "$it %" } ?: "—")
                Ligne("Température max", stats.temperatureMax?.let { String.format("%.1f °C", it) } ?: "—")
                Ligne("Température min", stats.temperatureMin?.let { String.format("%.1f °C", it) } ?: "—")
                Ligne("Mesures exploitées", "${stats.mesures} sur ${stats.echantillons} relevés")
            }
        }

        Spacer(Modifier.height(20.dp))
    }

    if (dialogueCapacite) {
        AlertDialog(
            onDismissRequest = { dialogueCapacite = false },
            title = { Text("Capacité de la batterie") },
            text = {
                Column {
                    Text(
                        "Capacité annoncée par le constructeur, en mAh (par exemple 4500). " +
                            "Elle sert aux cycles estimés et à l'autonomie.",
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = saisie,
                        onValueChange = { saisie = it.filter { c -> c.isDigit() }.take(5) },
                        label = { Text("Capacité (mAh)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    saisie.toIntOrNull()?.let(onCapacite)
                    dialogueCapacite = false
                }) { Text("Enregistrer") }
            },
            dismissButton = {
                TextButton(onClick = { dialogueCapacite = false }) { Text("Annuler") }
            },
        )
    }
}

@Composable
private fun Tuile(libelle: String, valeur: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                libelle.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                valeur,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun Carte(titre: String, contenu: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                titre.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            contenu()
        }
    }
}

@Composable
private fun Ligne(libelle: String, valeur: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            libelle,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            valeur,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 95 minutes → « 1 h 35 », 45 → « 45 min ». */
internal fun duree(minutes: Int): String {
    if (minutes < 60) return "$minutes min"
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0) "$h h" else "$h h ${m.toString().padStart(2, '0')}"
}
