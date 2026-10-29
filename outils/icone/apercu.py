
from PIL import Image, ImageDraw
import pathlib
src = Image.open("/root/projects/batteryhub/outils/icone/brut4/candidat-2-seed133397500.png").convert("RGB")
fond = (0x37, 0x3B, 0x53)
taille = 512
base = src.resize((taille, taille), Image.LANCZOS)

def masque(forme):
    m = Image.new("L", (taille, taille), 0)
    d = ImageDraw.Draw(m)
    if forme == "rond":
        d.ellipse((0,0,taille-1,taille-1), fill=255)
    elif forme == "squircle":
        r = int(taille*0.26)
        d.rounded_rectangle((0,0,taille-1,taille-1), radius=r, fill=255)
    else:
        r = int(taille*0.16)
        d.rounded_rectangle((0,0,taille-1,taille-1), radius=r, fill=255)
    return m

planche = Image.new("RGB", (taille*3 + 80, taille + 120), (24, 26, 34))
for i, forme in enumerate(["rond", "squircle", "arrondi"]):
    icone = Image.new("RGB", (taille, taille), fond)
    icone.paste(base, (0,0), masque(forme))
    planche.paste(icone, (20 + i*(taille+20), 40))
# rendu réel 48 px
petite = base.resize((48,48), Image.LANCZOS)
planche.paste(petite, (20, taille+60))
m48 = masque("rond").resize((48,48), Image.LANCZOS)
f48 = Image.new("RGB",(48,48), fond); f48.paste(petite,(0,0),m48)
planche.paste(f48, (90, taille+60))
p = "/root/projects/batteryhub/docs/captures/icone-apercu.png"
planche.save(p)
print(p, planche.size)
