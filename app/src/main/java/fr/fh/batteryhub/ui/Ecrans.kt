package fr.fh.batteryhub.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.fh.batteryhub.data.Appareil
import fr.fh.batteryhub.data.Historique
import fr.fh.batteryhub.data.Regles
import fr.fh.batteryhub.data.TypeAppareil
import fr.fh.batteryhub.export.ExportCsv
import fr.fh.batteryhub.update.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun iconeDe(type: TypeAppareil) = when (type) {
    TypeAppareil.TELEPHONE -> Icons.Filled.Smartphone
    TypeAppareil.ECOUTEURS -> Icons.Filled.Headphones
    TypeAppareil.MONTRE -> Icons.Filled.Watch
    TypeAppareil.SOURIS -> Icons.Filled.Mouse
    TypeAppareil.CLAVIER -> Icons.Filled.Keyboard
    TypeAppareil.ENCEINTE -> Icons.Filled.Speaker
    TypeAppareil.AUTRE -> Icons.Filled.Watch
}

@Composable
private fun Titre(texte: String, sousTitre: String? = null) {
    Text(
        texte,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
    )
    if (sousTitre != null) {
        Text(
            sousTitre,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun BarreNiveau(niveau: Int) {
    val categorie = Regles.categorie(niveau)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(niveau.coerceIn(0, 100) / 100f)
                .height(6.dp)
                .background(couleurNiveau(categorie), RoundedCornerShape(4.dp))
        )
    }
}

@Composable
private fun CarteAppareil(
    appareil: Appareil,
    onLectureAuto: ((Appareil, Boolean) -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    iconeDe(appareil.type),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        appareil.nom,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        appareil.detail,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (appareil.niveau >= 0) "${appareil.niveau} %" else "n/d",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (appareil.niveau >= 0)
                        couleurNiveau(Regles.categorie(appareil.niveau))
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (appareil.niveau >= 0) {
                Spacer(Modifier.height(10.dp))
                BarreNiveau(appareil.niveau)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (appareil.connecte) "Connecté" else "Hors de portée",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (!appareil.connecte && onLectureAuto != null) {
                    Text(
                        "lecture auto",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Switch(
                        checked = appareil.lectureAuto,
                        onCheckedChange = { actif -> onLectureAuto(appareil, actif) },
                    )
                }
            }
        }
    }
}

@Composable
fun EcranAppareils(
    appareils: List<Appareil>,
    enCours: Boolean,
    dernierScan: Long,
    frequence: Int,
    seuil: Int,
    lectureDeconnectes: Boolean,
    onRelever: () -> Unit,
    onChangerLectureAuto: (Appareil, Boolean) -> Unit,
) {
    val connectes = appareils.filter { it.connecte }
    val horsPortee = appareils.filterNot { it.connecte }
    val sousSeuil = appareils.count { it.niveau >= 0 && Regles.sousSeuil(it.niveau, seuil) }

    LazyColumn(modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
        item {
            Titre(
                "Batteries",
                if (dernierScan > 0)
                    "Relevé à ${Regles.formatHeure(dernierScan)} · toutes les ${Regles.libelleFrequence(frequence)}"
                else "Aucun relevé pour le moment",
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Indicateur("Appareils", appareils.size.toString(), Modifier.weight(1f))
                Indicateur("Sous $seuil %", sousSeuil.toString(), Modifier.weight(1f))
                Indicateur("Hors portée", horsPortee.size.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            FilledTonalButton(onClick = onRelever, enabled = !enCours) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (enCours) "Relevé en cours…" else "Relever maintenant")
            }
            Spacer(Modifier.height(16.dp))
            if (appareils.isEmpty()) {
                Text(
                    "Aucun appareil relevé pour l'instant. Appuyez sur « Relever maintenant ».",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (connectes.isNotEmpty()) {
            item { Etiquette("Connectés · ${connectes.size}") }
            items(connectes) { appareil ->
                CarteAppareil(appareil, onChangerLectureAuto)
            }
        }
        if (horsPortee.isNotEmpty()) {
            item { Etiquette("Hors de portée · ${horsPortee.size}") }
            items(horsPortee) { appareil ->
                CarteAppareil(appareil, onChangerLectureAuto)
            }
        }
        if (!lectureDeconnectes && horsPortee.isNotEmpty()) {
            item {
                Text(
                    "La lecture des appareils déconnectés est désactivée dans les réglages.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun Etiquette(texte: String) {
    Text(
        texte.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
    )
}

@Composable
private fun Indicateur(libelle: String, valeur: String, modifier: Modifier = Modifier) {
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
fun EcranHistorique(appareils: List<Appareil>) {
    val context = LocalContext.current
    val selectionnables = remember(appareils) { appareils.filter { it.niveau >= 0 } }
    var selection by remember(appareils) { mutableStateOf(selectionnables.firstOrNull()?.adresse) }
    var points by remember { mutableStateOf(emptyList<Pair<Long, Int>>()) }
    var nombreReleves by remember { mutableIntStateOf(0) }

    LaunchedEffect(selection) {
        val adresse = selection
        if (adresse == null) {
            points = emptyList()
            nombreReleves = 0
            return@LaunchedEffect
        }
        val (serie, total) = withContext(Dispatchers.IO) {
            try {
                val historique = Historique(context)
                try {
                    val depuis = System.currentTimeMillis() - 7 * 86_400_000L
                    historique.serie(adresse, depuis) to historique.compter(adresse)
                } finally {
                    historique.close()
                }
            } catch (_: Throwable) {
                emptyList<Pair<Long, Int>>() to 0
            }
        }
        points = serie
        nombreReleves = total
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Titre("Historique", "7 derniers jours, un relevé par cycle de veille")
        Spacer(Modifier.height(12.dp))

        if (selectionnables.isEmpty()) {
            Text(
                "Aucun relevé enregistré pour l'instant.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Graphe(points = points)
                Text(
                    "${points.size} point(s) sur 7 jours · $nombreReleves relevé(s) conservés",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            selectionnables.take(3).forEach { appareil ->
                OutlinedButton(onClick = { selection = appareil.adresse }) {
                    Text(
                        appareil.nom.split(" ").first(),
                        fontSize = 12.sp,
                        fontWeight = if (selection == appareil.adresse) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            selectionnables.drop(3).take(3).forEach { appareil ->
                OutlinedButton(onClick = { selection = appareil.adresse }) {
                    Text(
                        appareil.nom.split(" ").first(),
                        fontSize = 12.sp,
                        fontWeight = if (selection == appareil.adresse) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        val selectionne = selectionnables.firstOrNull { it.adresse == selection }
        if (selectionne != null && points.size >= 2) {
            val premier = points.first().second
            val dernier = points.last().second
            val baisse = (premier - dernier).coerceAtLeast(0)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Indicateur("Niveau actuel", "$dernier %", Modifier.weight(1f))
                Indicateur("Écart 7 jours", "$baisse pts", Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Graphe(points: List<Pair<Long, Int>>) {
    val couleur = MaterialTheme.colorScheme.primary
    val fondGrille = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
    ) {
        if (points.isEmpty()) return@Canvas
        val marge = 8f
        val largeur = size.width - marge * 2
        val hauteur = size.height - marge * 2
        for (i in 1..3) {
            val y = marge + hauteur * i / 4f
            drawLine(fondGrille, Offset(marge, y), Offset(size.width - marge, y), strokeWidth = 1f)
        }
        if (points.size == 1) {
            drawCircle(couleur, radius = 4f, center = Offset(size.width / 2f, size.height / 2f))
            return@Canvas
        }
        val chemin = Path()
        points.forEachIndexed { index, (_, niveau) ->
            val x = marge + largeur * index / (points.size - 1).toFloat()
            val y = marge + hauteur * (1f - niveau.coerceIn(0, 100) / 100f)
            if (index == 0) chemin.moveTo(x, y) else chemin.lineTo(x, y)
        }
        drawPath(chemin, couleur, style = Stroke(width = 3f, cap = StrokeCap.Round))
    }
}

@Composable
fun EcranWidget(appareils: List<Appareil>, mode: String, onMode: (String) -> Unit) {
    val tries = appareils.filter { it.niveau >= 0 }.sortedBy { it.niveau }
    val affiches = if (mode == "2x2") tries.take(1) else tries.take(4)

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Titre("Widget", "Aperçu de l'écran d'accueil")
        Spacer(Modifier.height(14.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "BATTERYHUB",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (appareils.isNotEmpty()) Regles.formatHeure(System.currentTimeMillis()) else "jamais",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (affiches.isEmpty()) {
                    Text(
                        "Aucun relevé",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    affiches.forEach { appareil ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 3.dp),
                        ) {
                            Text(
                                appareil.nom,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            Text(
                                "${appareil.niveau} %",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = couleurNiveau(Regles.categorie(appareil.niveau)),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onMode("4x2") }) {
                Text("4 × 2 · 4 appareils", fontSize = 12.sp)
            }
            OutlinedButton(onClick = { onMode("2x2") }) {
                Text("2 × 2 · le plus bas", fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    "Sur l'écran d'accueil",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Le widget affiche les appareils les plus bas en premier. " +
                        "Un appui ouvre l'application. Il se rafraîchit à chaque relevé de la veille.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun EcranAlertes(appareils: List<Appareil>, seuil: Int, onSeuil: (Int) -> Unit) {
    val bas = appareils.filter { it.niveau >= 0 && Regles.sousSeuil(it.niveau, seuil) }
    val maintenant = System.currentTimeMillis()

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Titre("Alertes", "Notification dès qu'un appareil passe sous le seuil")
        Spacer(Modifier.height(14.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    "SEUIL D'ALERTE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "$seuil %",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 20, 25, 30).forEach { valeur ->
                        if (valeur == seuil) {
                            FilledTonalButton(onClick = { onSeuil(valeur) }) { Text("$valeur %") }
                        } else {
                            OutlinedButton(onClick = { onSeuil(valeur) }) { Text("$valeur %") }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Etiquette("Appareils sous le seuil")
        if (bas.isEmpty()) {
            Text(
                "Aucun appareil sous $seuil %.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            bas.forEach { appareil ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "${appareil.nom} : ${appareil.niveau} %",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = couleurNiveau(Regles.categorie(appareil.niveau)),
                        )
                        Text(
                            "Relevé à ${Regles.formatHeure(appareil.horodatage)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Etiquette("Appareils muets")
        val muets = appareils.filter { it.horodatage > 0 && Regles.muet(it.horodatage, maintenant) }
        if (muets.isEmpty()) {
            Text(
                "Aucun appareil sans relevé depuis 3 jours.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            muets.forEach { appareil ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            appareil.nom,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "Dernier relevé : ${Regles.formatHorodatage(appareil.horodatage)} · " +
                                "désactivez sa lecture automatique si l'appareil ne sert plus",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun EcranReglages(
    frequence: Int,
    seuil: Int,
    lectureDeconnectes: Boolean,
    appareils: List<Appareil>,
    onFrequence: (Int) -> Unit,
    onLectureDeconnectes: (Boolean) -> Unit,
    onLectureAuto: (Appareil, Boolean) -> Unit,
    appareilsMaj: (List<Appareil>) -> Unit,
    etatMaj: UpdateManager.State = UpdateManager.State.Idle,
    onVerifierMaj: () -> Unit = {},
    onTelechargerMaj: () -> Unit = {},
    onInstallerMaj: () -> Unit = {},
    onAutoriserInstallation: () -> Unit = {},
) {
    val context = LocalContext.current
    val portee = rememberCoroutineScope()
    var messageExport by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Titre("Réglages", "La veille tourne en arrière-plan, sans compte ni réseau")
        Spacer(Modifier.height(14.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    "FRÉQUENCE DE RÉCUPÉRATION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Regles.FREQUENCES.take(3).forEach { minutes ->
                        if (minutes == frequence) {
                            FilledTonalButton(onClick = { onFrequence(minutes) }) {
                                Text(Regles.libelleFrequence(minutes), fontSize = 12.sp)
                            }
                        } else {
                            OutlinedButton(onClick = { onFrequence(minutes) }) {
                                Text(Regles.libelleFrequence(minutes), fontSize = 12.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Regles.FREQUENCES.drop(3).forEach { minutes ->
                        if (minutes == frequence) {
                            FilledTonalButton(onClick = { onFrequence(minutes) }) {
                                Text(Regles.libelleFrequence(minutes), fontSize = 12.sp)
                            }
                        } else {
                            OutlinedButton(onClick = { onFrequence(minutes) }) {
                                Text(Regles.libelleFrequence(minutes), fontSize = 12.sp)
                            }
                        }
                    }
                }
                Text(
                    "Valeur actuelle : ${Regles.libelleFrequence(frequence)}. " +
                        "Les appareils connectés sont lus à chaque cycle.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Lire les appareils déconnectés",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Ouvre une connexion pour relever le niveau réel",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = lectureDeconnectes, onCheckedChange = onLectureDeconnectes)
            }
        }

        val horsPortee = appareils.filterNot { it.connecte }
        if (horsPortee.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "APPAREIL PAR APPAREIL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    horsPortee.forEach { appareil ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    appareil.nom,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    if (appareil.lectureAuto) "lecture auto activée"
                                    else "lecture auto désactivée",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = appareil.lectureAuto,
                                onCheckedChange = { actif -> onLectureAuto(appareil, actif) },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        CarteMiseAJour(
            etat = etatMaj,
            versionInstallee = BuildConfigVersion(),
            onVerifier = onVerifierMaj,
            onTelecharger = onTelechargerMaj,
            onInstaller = onInstallerMaj,
            onAutoriser = onAutoriserInstallation,
        )

        Spacer(Modifier.height(12.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    "DONNÉES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Historique local, conservé ${Regles.RETENTION_JOURS} jours. " +
                        "Séparateur point-virgule, BOM UTF-8 pour Excel.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = {
                        portee.launch {
                            val fichier = withContext(Dispatchers.IO) { ExportCsv.ecrire(context) }
                            if (fichier == null) {
                                messageExport = "Export impossible"
                            } else {
                                messageExport = fichier.name
                                ExportCsv.partager(context, fichier)
                            }
                        }
                    }
                ) {
                    Text("Exporter en CSV")
                }
                messageExport?.let { nom ->
                    Text(
                        nom,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { appareilsMaj(appareils) }) {
            Text("Version ${BuildConfigVersion()}", fontSize = 11.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun BuildConfigVersion(): String = fr.fh.batteryhub.BuildConfig.VERSION_NAME

@Composable
private fun CarteMiseAJour(
    etat: UpdateManager.State,
    versionInstallee: String,
    onVerifier: () -> Unit,
    onTelecharger: () -> Unit,
    onInstaller: () -> Unit,
    onAutoriser: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "MISE À JOUR",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            when (etat) {
                is UpdateManager.State.Available -> {
                    Text(
                        "Version ${etat.version} disponible",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (etat.notes.isNotBlank()) {
                        Text(
                            etat.notes.take(240),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(onClick = onTelecharger) { Text("Télécharger") }
                }

                is UpdateManager.State.Downloading -> {
                    Text(
                        "Téléchargement de la version ${etat.version}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { etat.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${(etat.progress * 100).toInt()} %",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                is UpdateManager.State.ReadyToInstall -> {
                    Text(
                        "Version ${etat.version} prête",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "L'installation remplace l'application, les réglages et l'historique sont conservés.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(onClick = onInstaller) { Text("Installer") }
                    TextButton(onClick = onAutoriser) {
                        Text("Autoriser l'installation d'applications inconnues", fontSize = 11.sp)
                    }
                }

                is UpdateManager.State.UpToDate -> {
                    Text(
                        "À jour (version ${etat.version})",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onVerifier) { Text("Vérifier à nouveau") }
                }

                is UpdateManager.State.Failed -> {
                    Text(
                        "Vérification impossible",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    if (etat.detail != null) {
                        Text(
                            etat.detail,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onVerifier) { Text("Réessayer") }
                        OutlinedButton(onClick = onAutoriser) { Text("Autoriser l'installation") }
                    }
                }

                UpdateManager.State.Idle -> {
                    Text(
                        "Version installée : $versionInstallee",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onVerifier) { Text("Vérifier les mises à jour") }
                }
            }
        }
    }
}

@Suppress("unused")
private fun contexteNom(context: Context): String = context.packageName
