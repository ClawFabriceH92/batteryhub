# BatteryHub

Application Android qui affiche l'état de batterie du téléphone et de tous ses périphériques Bluetooth appairés,
avec veille en arrière-plan, widget d'écran d'accueil, historique local et export CSV.

Tout reste sur le téléphone : aucun compte, aucun serveur, aucune donnée envoyée (hors vérification de mise à jour GitHub).

## État

v1.0.0 : application compilée, signée et publiée en release GitHub.

- Spécification et décisions de cadrage : [`docs/SPEC.md`](docs/SPEC.md)
- Maquette de validation : [`maquette/batteryhub-maquette-v1.html`](maquette/batteryhub-maquette-v1.html)
- Captures : `docs/captures/`

## Fonctionnalités

- Relevé du téléphone : niveau, santé, cycles, température.
- **Écran « Ma batterie » (v1.1.0)** : cycles (compteur du système quand Android 14+ le publie,
  sinon estimation), état en direct (courant en mA, puissance en W, tension, température, charge
  restante en mAh, temps de charge ou autonomie restante) et statistiques de charge/décharge sur
  7 jours calculées à partir de nos propres relevés : mAh chargés et déchargés, nombre de sessions
  de charge, durée et mAh moyens par session, vitesses moyennes, profondeur de décharge,
  températures extrêmes, cycles estimés (mAh déchargés ÷ capacité nominale).
  La capacité nominale se renseigne sur l'écran (4500 mAh par défaut).
- Relevé des appareils Bluetooth appairés : niveau système quand il est exposé, sinon connexion GATT au service Battery (0x180F).
- Lecture automatique des appareils hors de portée, désactivable appareil par appareil.
- Veille en arrière-plan à la fréquence choisie (15 min, 30 min, 1 h, 3 h, 6 h), relancée après redémarrage.
- Widget d'écran d'accueil 4 × 2 (les 4 appareils les plus bas) ou 2 × 2 (le plus bas).
- Alertes sous le seuil réglable (15, 20, 25, 30 %) et rappel pour un appareil muet depuis 3 jours.
- Historique local 90 jours, export CSV séparateur `;` avec BOM UTF-8.
- Relevés enrichis (courant, secteur, température, charge restante) en base depuis la v1.1.0 :
  migration non destructive, les anciens relevés restent lisibles et ne faussent pas les mAh.
- Limite assumée : Android ne donne le nombre de cycles qu'à partir d'Android 14 et seulement si
  le constructeur le publie ; le système de fichiers de la batterie (`/sys/class/power_supply`)
  est refusé aux applications sans root. D'où l'estimation, affichée comme telle.
- Mise à jour depuis les releases GitHub (téléchargement + installation).

## Compiler

```bash
export ANDROID_HOME=/opt/android-sdk JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
set -a && . /root/.secrets/keystores-android/batteryhub-keystore.env && set +a
./gradlew --no-daemon testDebugUnitTest assembleRelease
```

L'APK signé sort dans `app/build/outputs/apk/release/app-release.apk`.

## Vérifier la maquette

```bash
/root/.venvs/pw/bin/python tools/verifier_maquette.py
```

31 contrôles Playwright : syntaxe JavaScript, chaque écran, interactions, thème clair, flux vivant, zéro erreur console.

## Décisions

1. Périmètre : téléphone + périphériques Bluetooth appairés.
2. Veille en arrière-plan, widget et historique, fréquence de récupération paramétrable.
3. Lecture automatique des appareils déconnectés, désactivable appareil par appareil.
4. Historique local uniquement, export CSV (séparateur `;`, BOM UTF-8).
5. Nom : BatteryHub, dépôt dédié, icône propre.
