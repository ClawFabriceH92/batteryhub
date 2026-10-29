# BatteryHub · spécification

Application Android qui centralise l'état de batterie du téléphone et de ses périphériques Bluetooth appairés.
Le téléphone est la seule source de données : aucun serveur, aucun compte, aucun envoi réseau.

## Décisions de cadrage (validées le 29/10/2026)

| # | Question | Décision | Conséquence technique |
|---|----------|----------|----------------------|
| 1 | Périmètre du premier livrable | Téléphone + périphériques Bluetooth **appairés** (écouteurs, montre, souris, clavier, enceinte) | Lecture native Android : `BluetoothDevice.getBatteryLevel()` quand disponible, sinon service GATT `0x180F` ; aucun matériel à ajouter |
| 2 | Surveillance | Veille en arrière-plan + widget d'écran d'accueil + historique, **fréquence de récupération paramétrable** | `ForegroundService` (type `connectedDevice`) + `AlarmManager`/`WorkManager` selon la fréquence ; `GlanceAppWidget` ; persistance Room |
| 3 | Appareils non connectés | Lecture **automatique** de tous les appareils, avec un interrupteur par appareil pour désactiver | Connexion GATT courte sur les appareils appairés hors de portée, délai maximal 8 s, résultat mémorisé avec horodatage |
| 4 | Historique | **Local seulement** + export CSV à la demande (séparateur `;`, BOM UTF-8) | Room sur l'appareil, purge à 90 jours, export via `FileProvider` et partage Android |
| 5 | Nom | **BatteryHub** | Dépôt dédié `ClawFabriceH92/batteryhub`, `applicationId` `fr.fh.batteryhub`, icône propre |

## Écrans

1. **Appareils** : téléphone en premier, puis connectés, puis hors de portée. Pour chaque appareil : nom, détail, niveau en pourcentage coloré (vert > 30 %, orange 16 à 30 %, rouge ≤ 15 %), barre de charge, dernier relevé. Bouton « Forcer la lecture » et interrupteur de lecture auto sur les appareils hors de portée.
2. **Historique** : courbe 7 jours par appareil, sélecteur d'appareil, tenue estimée, décharge moyenne par jour, nombre de relevés conservés.
3. **Widget** : aperçu 4 × 2 (4 appareils les plus bas) et 2 × 2 (le plus bas seul). Tap pour ouvrir l'appareil, appui long pour une lecture immédiate.
4. **Alertes** : seuil réglable (15 / 20 / 25 / 30 %), liste des alertes récentes, rappel pour un appareil muet depuis 3 jours.
5. **Réglages** : fréquence de récupération (15 min, 30 min, 1 h, 3 h, 6 h), lecture globale des appareils déconnectés, réglage appareil par appareil, export CSV.

## Règles de calcul

- Couleur : `≤ 15 %` rouge, `16 à 30 %` orange, `> 30 %` vert.
- Fréquence par défaut : 30 minutes.
- Seuil d'alerte par défaut : 20 %.
- Rétention de l'historique : 90 jours, purge au démarrage du service.
- Un appareil « muet » depuis 3 jours déclenche une alerte et propose la désactivation de sa lecture automatique.

## Sources de lecture Android

| Cas | Méthode | Remarque |
|-----|---------|----------|
| Écouteurs, casques, certains claviers et souris | `BluetoothDevice.getBatteryLevel()` (API 33+) ou `BluetoothAdapter.ACTION_BATTERY_CHANGED` réfléchi | Lecture sans connexion, ne perturbe pas l'audio |
| Appareils exposant le service GATT `0x180F` (Battery Service), caractéristique `0x2A19` | `BluetoothGatt` | Connexion brève puis déconnexion ; sur les appareils audio, peut couper le son une seconde |
| Téléphone | `BatteryManager` : niveau, santé, cycles (`BATTERY_PROPERTY_CYCLE_COUNT`), température, tension | |

Limites assumées : un appareil purement filaire ne remonte rien ; un appareil éteint depuis longtemps conserve son dernier niveau connu avec son horodatage ; certains appareils propriétaires n'exposent aucun service batterie.

## Permissions

- `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` (Android 12+).
- `POST_NOTIFICATIONS` pour les alertes et la notification du service de veille.
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`.
- `RECEIVE_BOOT_COMPLETED` pour relancer la veille après redémarrage.

Aucune permission réseau : l'app fonctionne entièrement hors ligne.

## Livrables

- APK signé `BatteryHub_<date>_v<x>.apk` publié en release GitHub.
- Maquette de validation : `maquette/batteryhub-maquette-v1.html` (vérifiée 31/31 par `tools/verifier_maquette.py`).
- Icône propre à l'app (aucune reprise d'une autre app).

## Historique des questions

- 29/10/2026 : les 5 questions de cadrage ont été posées une par une (périmètre, surveillance, appareils déconnectés, historique, nom).
