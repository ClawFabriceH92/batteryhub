package fr.fh.batteryhub

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import fr.fh.batteryhub.data.Appareil
import fr.fh.batteryhub.data.Reglages
import fr.fh.batteryhub.service.Releveur
import fr.fh.batteryhub.service.VeillePlanificateur
import fr.fh.batteryhub.service.VeilleService
import fr.fh.batteryhub.ui.EcranAlertes
import fr.fh.batteryhub.ui.EcranAppareils
import fr.fh.batteryhub.ui.EcranHistorique
import fr.fh.batteryhub.ui.EcranReglages
import fr.fh.batteryhub.ui.EcranWidget
import fr.fh.batteryhub.ui.ThemeBatteryHub
import fr.fh.batteryhub.update.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val demandeurPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UpdateManager.start(applicationContext)
        setContent {
            ThemeBatteryHub {
                AppBatteryHub(demanderPermissions = { demandeurPermissions.launch(permissionsRequises(this)) })
            }
        }
    }

    private fun permissionsRequises(context: Context): Array<String> {
        val liste = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                liste.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                liste.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                liste.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        return liste.toTypedArray()
    }
}

@Composable
private fun AppBatteryHub(demanderPermissions: () -> Unit) {

    val context = LocalContext.current
    val reglages = remember { Reglages(context) }
    val portee = rememberCoroutineScope()

    var onglet by remember { mutableIntStateOf(0) }
    var appareils by remember { mutableStateOf(Releveur.instantane(context)) }
    var enCours by remember { mutableStateOf(false) }
    var dernierScan by remember { mutableStateOf(reglages.dernierScan) }
    var frequence by remember { mutableStateOf(reglages.frequenceMinutes) }
    var seuil by remember { mutableStateOf(reglages.seuil) }
    var lectureDeconnectes by remember { mutableStateOf(reglages.lectureDeconnectes) }

    fun relever() {
        if (enCours) return
        enCours = true
        portee.launch {
            try {
                appareils = withContext(Dispatchers.IO) { Releveur.executer(context) }
                dernierScan = System.currentTimeMillis()
            } catch (_: Throwable) {
                // l'échec reste visible : la liste précédente est conservée
            } finally {
                enCours = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (permissionsManquantes(context)) demanderPermissions()
        if (appareils.isEmpty() ||
            System.currentTimeMillis() - reglages.dernierScan > frequence * 60_000L
        ) {
            relever()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val entrees = listOf(
                    Triple("Appareils", Icons.Filled.BatteryFull, 0),
                    Triple("Historique", Icons.Filled.History, 1),
                    Triple("Widget", Icons.Filled.Widgets, 2),
                    Triple("Alertes", Icons.Filled.Notifications, 3),
                    Triple("Réglages", Icons.Filled.Settings, 4),
                )
                entrees.forEach { (libelle, icone, index) ->
                    NavigationBarItem(
                        selected = onglet == index,
                        onClick = { onglet = index },
                        icon = { Icon(icone, contentDescription = libelle) },
                        label = { Text(libelle) },
                    )
                }
            }
        }
    ) { marges ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(marges)
        ) {
            when (onglet) {
                0 -> EcranAppareils(
                    appareils = appareils,
                    enCours = enCours,
                    dernierScan = dernierScan,
                    frequence = frequence,
                    seuil = seuil,
                    lectureDeconnectes = lectureDeconnectes,
                    onRelever = { relever() },
                    onChangerLectureAuto = { appareil, actif ->
                        reglages.setLectureAuto(appareil.adresse, actif)
                        appareils = appareils.map {
                            if (it.adresse == appareil.adresse) it.copy(lectureAuto = actif) else it
                        }
                    },
                )

                1 -> EcranHistorique(appareils = appareils)

                2 -> EcranWidget(
                    appareils = appareils,
                    mode = reglages.widgetMode,
                    onMode = { mode ->
                        reglages.widgetMode = mode
                        appareils = appareils.toList()
                    },
                )

                3 -> EcranAlertes(
                    appareils = appareils,
                    seuil = seuil,
                    onSeuil = { valeur ->
                        seuil = valeur
                        reglages.seuil = valeur
                        VeilleService.lancerManuel(context)
                    },
                )

                else -> EcranReglages(
                    frequence = frequence,
                    seuil = seuil,
                    lectureDeconnectes = lectureDeconnectes,
                    appareils = appareils,
                    onFrequence = { minutes ->
                        frequence = minutes
                        reglages.frequenceMinutes = minutes
                        VeillePlanificateur.planifier(context, minutes)
                    },
                    onLectureDeconnectes = { actif ->
                        lectureDeconnectes = actif
                        reglages.lectureDeconnectes = actif
                    },
                    onLectureAuto = { appareil, actif ->
                        reglages.setLectureAuto(appareil.adresse, actif)
                        appareils = appareils.map {
                            if (it.adresse == appareil.adresse) it.copy(lectureAuto = actif) else it
                        }
                    },
                    appareilsMaj = { liste: List<Appareil> -> appareils = liste },
                )
            }
        }
    }
}

private fun permissionsManquantes(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
        PackageManager.PERMISSION_GRANTED
}
