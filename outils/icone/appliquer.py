#!/usr/bin/env python3
"""Transforme une image carrée en jeu d'icônes Android (adaptative + legacy).

Contrairement à generate_adaptive_icon.py (qui dessine un glyphe de police),
ce script part d'une IMAGE générée (ComfyUI, OpenAI, etc.).

Règles :
  * foreground = l'image entière en 108 dp, le launcher applique son masque ;
  * background = couleur unie échantillonnée dans les coins, pour que les bords
    de l'image se fondent dans le fond de l'icône ;
  * legacy ic_launcher.png et ic_launcher_round.png (masque circulaire) pour les
    lanceurs qui ignorent l'icône adaptative.

Usage : python3 appliquer.py --image brut/candidat-4.png --res ../../app/src/main/res
"""
import argparse
import pathlib
import re

from PIL import Image, ImageDraw

FOREGROUND_PX = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
LEGACY_PX = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def couleur_de_fond(image: Image.Image) -> tuple:
    """Couleur moyenne des quatre coins : c'est le fond de l'image générée."""
    w, h = image.size
    cote = max(8, w // 12)
    coins = [
        image.crop((0, 0, cote, cote)),
        image.crop((w - cote, 0, w, cote)),
        image.crop((0, h - cote, cote, h)),
        image.crop((w - cote, h - cote, w, h)),
    ]
    total = [0, 0, 0]
    for coin in coins:
        pixels = list(coin.convert("RGB").getdata())
        for p in pixels:
            total[0] += p[0]
            total[1] += p[1]
            total[2] += p[2]
    n = len(pixels) * len(coins)
    return (total[0] // n, total[1] // n, total[2] // n)


def cercle(image: Image.Image) -> Image.Image:
    masque = Image.new("L", image.size, 0)
    ImageDraw.Draw(masque).ellipse((0, 0, image.size[0] - 1, image.size[1] - 1), fill=255)
    sortie = Image.new("RGBA", image.size, (0, 0, 0, 0))
    sortie.paste(image.convert("RGBA"), (0, 0), masque)
    return sortie


def main() -> None:
    analyseur = argparse.ArgumentParser()
    analyseur.add_argument("--image", required=True)
    analyseur.add_argument("--res", required=True)
    arguments = analyseur.parse_args()

    source = Image.open(arguments.image).convert("RGB")
    res = pathlib.Path(arguments.res)
    fond = couleur_de_fond(source)
    hexa = "#%02X%02X%02X" % fond

    for densite, taille in FOREGROUND_PX.items():
        dossier = res / f"mipmap-{densite}"
        dossier.mkdir(parents=True, exist_ok=True)
        source.resize((taille, taille), Image.LANCZOS).save(
            dossier / "ic_launcher_foreground.png"
        )

    for densite, taille in LEGACY_PX.items():
        dossier = res / f"mipmap-{densite}"
        carre = Image.new("RGB", (taille, taille), fond)
        marge = int(taille * 0.04)
        carre.paste(source.resize((taille - 2 * marge, taille - 2 * marge), Image.LANCZOS), (marge, marge))
        carre.save(dossier / "ic_launcher.png")
        cercle(carre).save(dossier / "ic_launcher_round.png")

    anydpi = res / "mipmap-anydpi-v26"
    anydpi.mkdir(parents=True, exist_ok=True)
    for nom in ("ic_launcher.xml", "ic_launcher_round.xml"):
        (anydpi / nom).write_text(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@color/ic_launcher_background" />\n'
            '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n'
            '    <monochrome android:drawable="@mipmap/ic_launcher_foreground" />\n'
            '</adaptive-icon>\n',
            encoding="utf-8",
        )

    # colors.xml : on complète, on n'écrase jamais les autres couleurs de l'app
    couleurs = res / "values" / "colors.xml"
    existant = couleurs.read_text(encoding="utf-8") if couleurs.exists() else ""
    lignes = []
    for nom, valeur in (
        ("ic_launcher_background", hexa),
        ("batteryhub_fond", "#0D1117"),
        ("batteryhub_accent", "#4C9AFF"),
    ):
        if f'name="{nom}"' not in existant:
            lignes.append(f'    <color name="{nom}">{valeur}</color>')
        elif nom == "ic_launcher_background":
            # la première couleur doit suivre l'image : un fond d'icône différent
            # des bords de l'image laisse un carré visible autour du dessin
            existant = re.sub(
                r'<color name="ic_launcher_background">[^<]*</color>',
                f'<color name="ic_launcher_background">{valeur}</color>',
                existant,
            )
    if existant:
        contenu = existant
        if lignes:
            contenu = contenu.replace("</resources>", "\n".join(lignes) + "\n</resources>")
        couleurs.write_text(contenu, encoding="utf-8")
    else:
        contenu = (
            '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
            + "\n".join(lignes)
            + "\n</resources>\n"
        )
        couleurs.write_text(contenu, encoding="utf-8")

    print(f"icônes générées depuis {arguments.image} · fond {hexa}")


if __name__ == "__main__":
    main()
