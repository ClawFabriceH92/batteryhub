
from PIL import Image, ImageDraw
import pathlib
d = pathlib.Path("/root/projects/batteryhub/outils/icone/brut2")
fs = sorted(d.glob("candidat-*.png"))
t = 320
planche = Image.new("RGB", (t*3 + 40, (t+30)*2 + 20), (20,22,28))
dr = ImageDraw.Draw(planche)
for i, f in enumerate(fs):
    im = Image.open(f).convert("RGB").resize((t,t), Image.LANCZOS)
    x = 10 + (i%3)*(t+10); y = 10 + (i//3)*(t+30)
    planche.paste(im, (x,y))
    dr.text((x+6, y+t+6), f"candidat {i+1}", fill=(230,230,240))
p = "/root/projects/batteryhub/docs/captures/icone-candidats.png"
planche.save(p); print(p, planche.size)
