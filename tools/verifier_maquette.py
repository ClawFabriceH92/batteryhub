#!/usr/bin/env python3
"""Vérifie la maquette BatteryHub : syntaxe JS, chaque écran, interactions, zéro erreur console.

Usage : /root/.venvs/pw/bin/python tools/verifier_maquette.py
Sortie : docs/captures/*.png + RÉSULTAT n/n
"""
import pathlib, re, subprocess, sys

RACINE = pathlib.Path(__file__).resolve().parent.parent
MAQUETTE = RACINE / "maquette" / "batteryhub-maquette-v1.html"
CAPTURES = RACINE / "docs" / "captures"
CAPTURES.mkdir(parents=True, exist_ok=True)

controles = []


def check(nom, condition, detail=""):
    controles.append((nom, bool(condition), detail))
    print(("OK    " if condition else "ÉCHEC ") + nom + ((" · " + str(detail)) if detail else ""))


# ---------- 1. syntaxe de chaque bloc <script> inline ----------
html = MAQUETTE.read_text(encoding="utf-8")
blocs = re.findall(r"<script>(.*?)</script>", html, re.S)
check("bloc script trouvé", len(blocs) == 1, f"{len(blocs)} bloc(s)")
tmp = RACINE / "tools" / "_bloc.js"
for i, b in enumerate(blocs):
    tmp.write_text(b, encoding="utf-8")
    r = subprocess.run(["node", "--check", str(tmp)], capture_output=True, text=True)
    check(f"node --check bloc {i}", r.returncode == 0, r.stderr.strip()[:200])
tmp.unlink(missing_ok=True)

# ---------- 2. navigateur ----------
from playwright.sync_api import sync_playwright

erreurs = []


def capturer(page, nom):
    page.locator("#phone").screenshot(path=str(CAPTURES / nom))
    print("      capture", nom)


with sync_playwright() as p:
    nav = p.chromium.launch()
    page = nav.new_page(viewport={"width": 1180, "height": 980}, device_scale_factor=2)
    page.on("console", lambda m: erreurs.append(("console", m.type, m.text)) if m.type == "error" else None)
    page.on("pageerror", lambda e: erreurs.append(("pageerror", str(e))))
    page.goto(MAQUETTE.as_uri())
    page.wait_for_timeout(700)

    def txt():
        return page.locator("#content").inner_text().replace("\u202f", " ").replace("\u00a0", " ")

    def contient(*morceaux):
        bas = txt().lower()
        return all(m.lower() in bas for m in morceaux)

    def nav_vers(ecran):
        page.click(f"[data-nav='{ecran}']")
        page.wait_for_timeout(350)
        return txt()

    # écran Appareils
    t = txt()
    check("écran Appareils : titre", contient("Batteries"))
    check("écran Appareils : 6 cartes", page.locator(".card[id^='card-']").count() == 6,
          page.locator(".card[id^='card-']").count())
    check("écran Appareils : téléphone listé", contient("Ce téléphone"))
    check("écran Appareils : écouteurs listés", contient("Buds2 Pro"))
    check("écran Appareils : hors portée séparés", contient("Hors de portée"))
    check("écran Appareils : bouton forcer la lecture", page.locator("[data-go]").count() == 2,
          page.locator("[data-go]").count())
    check("écran Appareils : KPI sous 30 %", contient("Sous 30 %"))
    page.wait_for_timeout(500)
    capturer(page, "01-appareils-sombre.png")

    # forcer la lecture
    page.locator("[data-go]").first.click()
    page.wait_for_timeout(1400)
    t = txt()
    check("forçage : lecture effectuée", contient("Lecture forcée") or contient("lecture réussie"), t[:160])
    capturer(page, "02-appareils-apres-lecture.png")

    # écran Historique
    t = nav_vers("historique")
    check("Historique : titre", contient("Historique"))
    check("Historique : courbe tracée", page.locator("#content svg path").count() >= 2,
          page.locator("#content svg path").count())
    check("Historique : sélecteur d'appareil", page.locator("[data-hist]").count() == 6,
          page.locator("[data-hist]").count())
    check("Historique : relevés comptés", contient("84"), [l for l in t.split("\n") if "84" in l])
    capturer(page, "03-historique.png")

    # changement d'appareil dans l'historique
    page.locator("[data-hist='watch']").click()
    page.wait_for_timeout(400)
    check("Historique : bascule sur la montre", contient("Montre") and contient("Tenue estimée"))
    page.locator("[data-hist='buds']").click()
    page.wait_for_timeout(300)

    # écran Widget
    t = nav_vers("widget")
    check("Widget : aperçu présent", page.locator(".widget").count() == 1)
    check("Widget : 4 lignes d'appareils", page.locator(".widget .wrow").count() == 4,
          page.locator(".widget .wrow").count())
    check("Widget : styles proposés", page.locator("[data-wid]").count() == 2)
    capturer(page, "04-widget.png")

    # écran Alertes
    t = nav_vers("alertes")
    check("Alertes : seuil affiché", contient("Seuil d'alerte") and contient("20"))
    check("Alertes : notifications listées", page.locator(".dot").count() >= 3,
          page.locator(".dot").count())
    page.locator("[data-seuil='25']").click()
    page.wait_for_timeout(400)
    check("Alertes : seuil modifiable à 25 %", contient("25 %"))
    capturer(page, "05-alertes.png")
    page.locator("[data-seuil='20']").click()
    page.wait_for_timeout(300)

    # écran Réglages
    t = nav_vers("reglages")
    check("Réglages : fréquence présente", contient("Fréquence de récupération"))
    check("Réglages : 5 fréquences", page.locator("[data-freq]").count() == 5,
          page.locator("[data-freq]").count())
    check("Réglages : interrupteur global", page.locator("[data-sw='global']").count() == 1)
    check("Réglages : export CSV", page.locator("[data-exp='csv']").count() == 1)
    page.locator("[data-freq='1 h']").click()
    page.wait_for_timeout(400)
    check("Réglages : fréquence changée en 1 h", contient("Valeur actuelle : 1 h"))
    capturer(page, "06-reglages.png")

    # bascule de thème + capture clair
    page.click("#btnTheme")
    page.wait_for_timeout(500)
    t = nav_vers("appareils")
    check("thème clair : appliqué", page.get_attribute("html", "data-theme") == "light",
          page.get_attribute("html", "data-theme"))
    check("thème clair : contenu rendu", contient("Batteries"))
    page.wait_for_timeout(600)
    capturer(page, "07-appareils-clair.png")

    # flux vivant : le setInterval doit provoquer de nouveaux rendus
    avant = page.evaluate("window.__bh.rendus()")
    page.wait_for_timeout(5200)
    apres = page.evaluate("window.__bh.rendus()")
    check("flux vivant : rendus successifs", apres - avant >= 1, f"{avant} -> {apres}")
    heure = page.locator("#sbHeure").inner_text()
    check("hélium".replace("hélium", "horloge au format HH:MM"), len(heure) == 5 and ":" in heure, heure)

    nav.close()

# ---------- 3. zéro erreur console ----------
check("aucune erreur console", len(erreurs) == 0, erreurs[:3])

ok = sum(1 for _, c, _ in controles if c)
total = len(controles)
print("\nRÉSULTAT : %d/%d contrôles OK" % (ok, total))
if ok != total:
    for nom, c, d in controles:
        if not c:
            print("  à corriger :", nom, d)
    sys.exit(1)
