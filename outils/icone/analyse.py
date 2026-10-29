
import pathlib
from PIL import Image, ImageStat
d = pathlib.Path("/root/projects/batteryhub/outils/icone/brut2")
def moy(im): return ImageStat.Stat(im).mean
resultats = []
for f in sorted(d.glob("candidat-*.png")):
    im = Image.open(f).convert("RGB").resize((256,256))
    w,h = im.size
    coins = [im.crop((0,0,40,40)), im.crop((w-40,0,w,40)), im.crop((0,h-40,40,h)), im.crop((w-40,h-40,w,h))]
    fond = [moy(c) for c in coins]
    var_fond = sum(abs(fond[i][0]-fond[0][0])+abs(fond[i][1]-fond[0][1])+abs(fond[i][2]-fond[0][2]) for i in range(4))/4
    centre = moy(im.crop((w//3,h//4,2*w//3,3*h//4)))
    bord = sum(sum(c)/3 for c in fond)/4
    contraste = abs(sum(centre)/3 - bord)
    sat = moy(im.convert("HSV").split()[1])[0]
    # luminosite moyenne : une icone sombre sur fond sombre se perd
    lum = sum(moy(im))/3
    resultats.append((f.name, var_fond, contraste, sat, lum, [round(x) for x in fond[0]]))
for r in resultats:
    print("%-32s fond_var=%5.1f contraste=%5.1f sat=%5.1f lum=%5.1f fond=%s" % r)
