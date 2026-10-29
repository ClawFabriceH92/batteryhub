package fr.fh.batteryhub.bt

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import fr.fh.batteryhub.data.Appareil
import fr.fh.batteryhub.data.TypeAppareil
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Lecture des niveaux de batterie des appareils Bluetooth appairés.
 *
 * 1. niveau système (API 33+) : instantané, aucune connexion, ne coupe pas l'audio ;
 * 2. sinon service GATT Battery (0x180F / 0x2A19) : connexion brève, utile sur les appareils
 *    hors de portée qui exposent leur niveau (souris, clavier, enceinte).
 */
object BluetoothBatterie {

    private val SERVICE_BATTERIE: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    private val CARACTERISTIQUE_NIVEAU: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    const val TIMEOUT_GATT_MS = 8_000L

    fun autorisationAccordee(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    fun bluetoothActif(context: Context): Boolean {
        val gestionnaire = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return gestionnaire?.adapter?.isEnabled == true
    }

    fun appareilsAppaires(context: Context): List<BluetoothDevice> {
        if (!autorisationAccordee(context)) return emptyList()
        return try {
            val gestionnaire = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            gestionnaire?.adapter?.bondedDevices?.toList().orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun nomDe(device: BluetoothDevice): String = try {
        device.name?.takeIf { it.isNotBlank() } ?: device.address
    } catch (_: SecurityException) {
        device.address
    }

    fun connecteDe(context: Context, device: BluetoothDevice): Boolean = try {
        val gestionnaire = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val profil = gestionnaire?.getConnectionState(device, BluetoothProfile.GATT)
        profil == BluetoothProfile.STATE_CONNECTED
    } catch (_: Throwable) {
        false
    }

    fun typeDe(device: BluetoothDevice): TypeAppareil {
        val nom = nomDe(device).lowercase()
        val classe = try {
            device.bluetoothClass?.majorDeviceClass ?: -1
        } catch (_: Throwable) {
            -1
        }
        return when {
            nom.contains("buds") || nom.contains("airpod") || nom.contains("casque") ||
                nom.contains("headphone") || nom.contains("écouteur") -> TypeAppareil.ECOUTEURS
            nom.contains("watch") || nom.contains("montre") || nom.contains("band") -> TypeAppareil.MONTRE
            nom.contains("mouse") || nom.contains("souris") || nom.contains("mx master") -> TypeAppareil.SOURIS
            nom.contains("keyboard") || nom.contains("clavier") || nom.contains("mx keys") -> TypeAppareil.CLAVIER
            nom.contains("jbl") || nom.contains("speaker") || nom.contains("enceinte") ||
                nom.contains("flip") || nom.contains("boom") -> TypeAppareil.ENCEINTE
            classe == 0x05 -> TypeAppareil.AUTRE
            else -> TypeAppareil.AUTRE
        }
    }

    /** Niveau système sans connexion (API 33+), ou par réflexion sur les versions antérieures. */
    fun niveauSysteme(device: BluetoothDevice): Int? {
        try {
            // méthode absente de l'API publique selon les constructeurs : réflexion prudente
            val m = BluetoothDevice::class.java.getMethod("getBatteryLevel")
            val v = m.invoke(device) as? Int
            if (v != null && v in 0..100) return v
        } catch (_: Throwable) {
            // méthode absente ou non autorisée : on passera par le GATT
        }
        return null
    }

    /** Connexion GATT brève pour lire la caractéristique Battery Level. */
    fun niveauGatt(context: Context, device: BluetoothDevice, timeoutMs: Long = TIMEOUT_GATT_MS): Int? {
        val verrou = CountDownLatch(1)
        var resultat: Int? = null
        var gatt: BluetoothGatt? = null

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                try {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        g.discoverServices()
                    } else {
                        g.close()
                        verrou.countDown()
                    }
                } catch (_: Throwable) {
                    verrou.countDown()
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                try {
                    val service = g.getService(SERVICE_BATTERIE)
                    val carac = service?.getCharacteristic(CARACTERISTIQUE_NIVEAU)
                    if (carac == null) {
                        g.disconnect(); g.close(); verrou.countDown()
                    } else if (!g.readCharacteristic(carac)) {
                        g.disconnect(); g.close(); verrou.countDown()
                    }
                } catch (_: Throwable) {
                    verrou.countDown()
                }
            }

            @Deprecated("API < 33")
            override fun onCharacteristicRead(
                g: BluetoothGatt,
                carac: BluetoothGattCharacteristic,
                status: Int,
            ) {
                try {
                    if (carac.uuid == CARACTERISTIQUE_NIVEAU && status == BluetoothGatt.GATT_SUCCESS) {
                        val v = carac.value?.firstOrNull()?.toInt()?.and(0xFF)
                        if (v != null && v in 0..100) resultat = v
                    }
                } catch (_: Throwable) {
                    // ignore
                } finally {
                    try { g.disconnect() } catch (_: Throwable) {}
                    try { g.close() } catch (_: Throwable) {}
                    verrou.countDown()
                }
            }

            override fun onCharacteristicRead(
                g: BluetoothGatt,
                carac: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int,
            ) {
                try {
                    if (carac.uuid == CARACTERISTIQUE_NIVEAU && status == BluetoothGatt.GATT_SUCCESS) {
                        val v = value.firstOrNull()?.toInt()?.and(0xFF)
                        if (v != null && v in 0..100) resultat = v
                    }
                } catch (_: Throwable) {
                    // ignore
                } finally {
                    try { g.disconnect() } catch (_: Throwable) {}
                    try { g.close() } catch (_: Throwable) {}
                    verrou.countDown()
                }
            }
        }

        return try {
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            if (gatt == null) {
                null
            } else {
                verrou.await(timeoutMs, TimeUnit.MILLISECONDS)
                resultat
            }
        } catch (_: SecurityException) {
            null
        } catch (_: Throwable) {
            null
        } finally {
            if (verrou.count == 1L) {
                try { gatt?.disconnect() } catch (_: Throwable) {}
                try { gatt?.close() } catch (_: Throwable) {}
            }
        }
    }

    /**
     * Relevé complet : téléphone puis appareils appairés.
     * Les appareils hors de portée ne sont lus que si [lectureDeconnectes] et la lecture auto
     * de l'appareil sont activées.
     */
    fun relever(
        context: Context,
        lectureDeconnectes: Boolean,
        lectureAuto: (String) -> Boolean,
        timeoutGattMs: Long = TIMEOUT_GATT_MS,
    ): List<Appareil> {
        val maintenant = System.currentTimeMillis()
        val liste = ArrayList<Appareil>()
        liste.add(BatterieTelephone.lire(context))

        for (device in appareilsAppaires(context)) {
            val adresse = try { device.address } catch (_: Throwable) { continue }
            val nom = nomDe(device)
            val connecte = connecteDe(context, device)
            val niveauSysteme = niveauSysteme(device)
            var niveau = niveauSysteme
            var methode = "système"

            if (niveau == null && (connecte || (lectureDeconnectes && lectureAuto(adresse)))) {
                niveau = niveauGatt(context, device, timeoutGattMs)
                methode = "GATT"
            }

            if (niveau == null) {
                liste.add(
                    Appareil(
                        adresse = adresse,
                        nom = nom,
                        type = typeDe(device),
                        connecte = connecte,
                        niveau = -1,
                        detail = if (connecte) "Niveau indisponible" else "Aucune lecture possible",
                        horodatage = maintenant,
                        lectureAuto = lectureAuto(adresse),
                    )
                )
                continue
            }

            liste.add(
                Appareil(
                    adresse = adresse,
                    nom = nom,
                    type = typeDe(device),
                    connecte = connecte,
                    niveau = niveau,
                    detail = if (connecte) "Connecté · lecture $methode" else "Lu par $methode",
                    horodatage = maintenant,
                    lectureAuto = lectureAuto(adresse),
                )
            )
        }
        return liste
    }
}
