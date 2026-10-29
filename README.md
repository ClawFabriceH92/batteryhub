# BatteryHub

Application Android qui affiche l'état de batterie du téléphone et de tous ses périphériques Bluetooth appairés,
avec veille en arrière-plan, widget d'écran d'accueil et historique exportable en CSV.

## État

Cadrage validé le 29/10/2026. Maquette de validation livrée, en attente de validation avant développement.

- Spécification complète : [`docs/SPEC.md`](docs/SPEC.md)
- Maquette cliquable : [`maquette/batteryhub-maquette-v1.html`](maquette/batteryhub-maquette-v1.html)
- Captures : `docs/captures/`

## Vérifier la maquette

```bash
/root/.venvs/pw/bin/python tools/verifier_maquette.py
```

Le script contrôle la syntaxe de chaque bloc JavaScript, chaque écran, les interactions, le thème clair,
le flux vivant et l'absence d'erreur console. Dernier résultat : 31/31 contrôles OK.

## Décisions

1. Périmètre : téléphone + périphériques Bluetooth appairés.
2. Veille en arrière-plan, widget et historique, fréquence de récupération paramétrable.
3. Lecture automatique des appareils déconnectés, désactivable appareil par appareil.
4. Historique local uniquement, export CSV (séparateur `;`, BOM UTF-8).
5. Nom : BatteryHub, dépôt dédié, icône propre.
