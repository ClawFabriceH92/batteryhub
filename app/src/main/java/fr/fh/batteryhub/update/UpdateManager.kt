package fr.fh.batteryhub.update

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import fr.fh.batteryhub.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Flux de mise à jour piloté depuis l'application.
 *
 * Le téléchargement se fait ici et non par DownloadManager : GitHub renvoie l'APK
 * vers un CDN par une chaîne de redirections que DownloadManager ne suit pas
 * toujours, ce qui produisait des fichiers tronqués refusés à l'installation.
 */
object UpdateManager {

    sealed interface State {
        data object Idle : State
        data class Available(val version: String, val notes: String, val apkUrl: String) : State
        data class Downloading(val version: String, val progress: Float) : State
        data class ReadyToInstall(val version: String, val file: File) : State
        data class UpToDate(val version: String) : State
        data class Failed(val detail: String?) : State
    }

    private const val DIR = "updates"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var dernierControle = UpdateSchedule.NEVER

    fun checkAtLaunch(context: Context?) {
        controle(context, manual = false)
    }

    fun checkNow(context: Context? = null) = controle(context, manual = true)

    private fun controle(context: Context?, manual: Boolean) {
        if (job?.isActive == true) return
        val maintenant = System.currentTimeMillis()
        if (!UpdateSchedule.isDue(manual, dernierControle, maintenant)) return
        dernierControle = maintenant
        job = scope.launch {
            when (val resultat = UpdateChecker.check()) {
                is UpdateChecker.CheckResult.Available -> {
                    val info = resultat.info
                    _state.value = State.Available(info.version, info.notes, info.apkUrl)
                    // une nouvelle version ne mérite un dialogue que si l'installer coûte
                    // un appui : sur un réseau non facturé l'APK est déjà téléchargé
                    if (!manual && context != null) preparerEnSilence(context, info.version, info.apkUrl)
                }

                UpdateChecker.CheckResult.UpToDate -> {
                    _state.value = if (manual) State.UpToDate(BuildConfig.VERSION_NAME) else State.Idle
                }

                is UpdateChecker.CheckResult.Unreachable -> {
                    // un contrôle raté ne consomme pas le créneau horaire
                    dernierControle = UpdateSchedule.NEVER
                    _state.value = if (manual) State.Failed(resultat.reason) else State.Idle
                }
            }
        }
    }

    /**
     * Télécharge sans le demander, mais uniquement sur un réseau non facturé :
     * tirer 10 Mo de données mobiles sans prévenir n'est pas un service.
     */
    private suspend fun preparerEnSilence(context: Context, version: String, url: String) {
        val dejaLa = fichierValide(context, version)
        if (dejaLa != null) {
            _state.value = State.ReadyToInstall(version, dejaLa)
            return
        }
        if (!reseauNonFacture(context)) return
        _state.value = State.Downloading(version, 0f)
        _state.value = runCatching { fetch(context, version, url) }.fold(
            onSuccess = { State.ReadyToInstall(version, it) },
            onFailure = { State.Available(version, "", url) },
        )
    }

    private fun reseauNonFacture(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val reseau = cm.activeNetwork ?: return false
        val capacites = cm.getNetworkCapabilities(reseau) ?: return false
        capacites.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capacites.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }.getOrDefault(false)

    /** L'APK déjà sur le disque pour [version], s'il est réel. */
    private fun fichierValide(context: Context, version: String): File? {
        val fichier = File(File(context.filesDir, DIR), "batteryhub-v$version.apk")
        if (!fichier.exists() || fichier.length() == 0L) return null
        val entete = ByteArray(2)
        runCatching { fichier.inputStream().use { it.read(entete) } }.getOrNull()
        return if (String(entete) == "PK") fichier else null
    }

    fun download(context: Context) {
        val disponible = _state.value as? State.Available ?: return
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = State.Downloading(disponible.version, 0f)
            _state.value = runCatching {
                fetch(context, disponible.version, disponible.apkUrl)
            }.fold(
                onSuccess = { State.ReadyToInstall(disponible.version, it) },
                onFailure = { State.Failed(it.message) },
            )
        }
    }

    private suspend fun fetch(context: Context, version: String, url: String): File =
        withContext(Dispatchers.IO) {
            val dossier = File(context.filesDir, DIR).apply { mkdirs() }
            dossier.listFiles()?.forEach { it.delete() }
            val cible = File(dossier, "batteryhub-v$version.apk")

            val requete = Request.Builder().url(url).get().build()
            http.newCall(requete).execute().use { reponse ->
                if (!reponse.isSuccessful) error("HTTP ${reponse.code}")
                val corps = reponse.body ?: error("Corps vide")
                val total = corps.contentLength()
                var ecrit = 0L
                corps.byteStream().use { entree ->
                    cible.outputStream().use { sortie ->
                        val tampon = ByteArray(64 * 1024)
                        var lu = entree.read(tampon)
                        while (lu != -1) {
                            sortie.write(tampon, 0, lu)
                            ecrit += lu
                            if (total > 0) {
                                _state.value = State.Downloading(
                                    version, (ecrit.toFloat() / total).coerceIn(0f, 1f)
                                )
                            }
                            lu = entree.read(tampon)
                        }
                    }
                }
            }
            if (cible.length() == 0L) error("Téléchargement vide")
            // un fichier qui ne commence pas par « PK » est une page web, pas un APK
            val entete = ByteArray(2)
            cible.inputStream().use { it.read(entete) }
            if (String(entete) != "PK") {
                cible.delete()
                error("fichier téléchargé invalide (ce n'est pas un APK)")
            }
            cible
        }

    fun install(context: Context) {
        val pret = _state.value as? State.ReadyToInstall ?: return
        AutoUpdater.install(context, pret.file)
    }

    fun canInstall(context: Context): Boolean = runCatching {
        context.packageManager.canRequestPackageInstalls()
    }.getOrDefault(false)

    fun openInstallSettings(context: Context) = AutoUpdater.openInstallSettings(context)

    fun dismiss() {
        _state.value = State.Idle
    }

    /** Vérification immédiate au démarrage, puis contrôle horaire. */
    fun start(context: Context) {
        checkAtLaunch(context.applicationContext)
    }
}
