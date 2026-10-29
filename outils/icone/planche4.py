
from PIL import Image, ImageDraw, ImageStat
import pathlib
d = pathlib.Path("/root/projects/batteryhub/outils/icone/brut4")
fs = sorted(d.glob("candidat-*.png"))
t = 320
planche = Image.new("RGB", (t*3 + 40, (t+30)*2 + 20), (20,22,28))
dr = ImageDraw.Draw(planche)
def moy(im): return ImageStat.Stat(im).mean
for i, f in enumerate(fs):
    im = Image.open(f).convert("RGB")
    planche.paste(im.resize((t,t), Image.LANCZOS), (10 + (i%3)*(t+10), 10 + (i//3)*(t+30)))
    dr.text((11 + (i%3)*(t+10), 16 + (i//3)*(t+30) + t - 4), f"candidat {i+1}", fill=(255,255,255))
    p = im.resize((256,256)); w,h = p.size
    coins = [p.crop((0,0,40,40)), p.crop((w-40,0,w,40)), p.crop((0,h-40,40,h)), p.crop((w-40,h-40,w,h))]
    fond = [moy(c) for c in coins]
    var = sum(abs(fond[k][0]-fond[0][0])+abs(fond[k][1]-fond[0][1])+abs(fond[k][2]-fond[0][2]) for k in range(4))/4
    centre = moy(p.crop((w//3,h//4,2*w//3,3*h//4)))
    bord = sum(sum(c)/3 for c in fond)/4
    print("candidat %d  fond_var=%5.1f contraste=%5.1f sat=%5.1f fond=%s" % (i+1, var, abs(sum(centre)/3-bord), moy(p.convert("HSV").split()[1])[0], [round(x) for x in fond[0]]))
planche.save("/root/projects/batteryhub/docs/captures/icone-hero-candidats.png")
print("planche ok")
