#!/usr/bin/env python3
"""Génère des candidats d'icône BatteryHub avec ComfyUI (SDXL local sur .162).

Le workflow est construit ici, sans clés `_meta` / `_comment` : le serveur
ComfyUI renvoie HTTP 500 sur un workflow qui en contient.

Usage : python3 generer.py --n 4 --sortie brut
"""
import argparse
import json
import pathlib
import time
import urllib.parse
import urllib.request

HOTE = "http://192.168.0.162:8188"

POSITIF = (
    "mobile app icon, one single glossy 3D battery cell standing upright, "
    "rounded corners, emerald green energy fill, soft luminous glow, "
    "deep navy blue background, centered composition, minimal product render, "
    "studio lighting, ultra sharp, icon design, no text"
)
NEGATIF = (
    "text, letters, numbers, watermark, signature, blurry, multiple batteries, "
    "cluttered background, human hands, low contrast"
)


def workflow(prompt: str, negatif: str, seed: int, prefixe: str) -> dict:
    return {
        "3": {
            "class_type": "KSampler",
            "inputs": {
                "seed": seed,
                "steps": 32,
                "cfg": 7.0,
                "sampler_name": "dpmpp_2m",
                "scheduler": "karras",
                "denoise": 1.0,
                "model": ["4", 0],
                "positive": ["6", 0],
                "negative": ["7", 0],
                "latent_image": ["5", 0],
            },
        },
        "4": {
            "class_type": "CheckpointLoaderSimple",
            "inputs": {"ckpt_name": "sd_xl_base_1.0.safetensors"},
        },
        "5": {
            "class_type": "EmptyLatentImage",
            "inputs": {"width": 1024, "height": 1024, "batch_size": 1},
        },
        "6": {
            "class_type": "CLIPTextEncode",
            "inputs": {"text": prompt, "clip": ["4", 1]},
        },
        "7": {
            "class_type": "CLIPTextEncode",
            "inputs": {"text": negatif, "clip": ["4", 1]},
        },
        "8": {
            "class_type": "VAEDecode",
            "inputs": {"samples": ["3", 0], "vae": ["4", 2]},
        },
        "9": {
            "class_type": "SaveImage",
            "inputs": {"filename_prefix": prefixe, "images": ["8", 0]},
        },
    }


def poster(flux: dict) -> str:
    donnees = json.dumps({"prompt": flux}).encode()
    requete = urllib.request.Request(
        f"{HOTE}/prompt", data=donnees, headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(requete, timeout=30) as reponse:
        return json.load(reponse)["prompt_id"]


def attendre(prompt_id: str, limite_s: int = 300) -> dict:
    debut = time.time()
    while time.time() - debut < limite_s:
        with urllib.request.urlopen(f"{HOTE}/history/{prompt_id}", timeout=20) as reponse:
            historique = json.load(reponse)
        if prompt_id in historique:
            return historique[prompt_id]
        time.sleep(2)
    raise TimeoutError(f"aucun résultat après {limite_s} s pour {prompt_id}")


def telecharger(entree: dict, dossier: pathlib.Path, nom: str) -> pathlib.Path:
    for noeud in entree.get("outputs", {}).values():
        for image in noeud.get("images", []):
            url = (
                f"{HOTE}/view?filename={urllib.parse.quote(image['filename'])}"
                f"&subfolder={urllib.parse.quote(image.get('subfolder', ''))}&type={image['type']}"
            )
            cible = dossier / f"{nom}.png"
            with urllib.request.urlopen(url, timeout=60) as reponse:
                cible.write_bytes(reponse.read())
            return cible
    raise RuntimeError("aucune image dans la sortie du workflow")


def main() -> None:
    analyseur = argparse.ArgumentParser()
    analyseur.add_argument("--n", type=int, default=4)
    analyseur.add_argument("--sortie", default="brut")
    analyseur.add_argument("--prompt", default=POSITIF)
    analyseur.add_argument("--negatif", default=NEGATIF)
    analyseur.add_argument("--prefixe", default="batteryhub")
    arguments = analyseur.parse_args()

    dossier = pathlib.Path(__file__).parent / arguments.sortie
    dossier.mkdir(parents=True, exist_ok=True)

    for i in range(arguments.n):
        seed = int(time.time() * 1000) % 2**31 + i * 7919
        flux = workflow(arguments.prompt, arguments.negatif, seed, arguments.prefixe)
        identifiant = poster(flux)
        entree = attendre(identifiant)
        fichier = telecharger(entree, dossier, f"candidat-{i + 1}-seed{seed}")
        print(f"OK {fichier} (seed {seed})")


if __name__ == "__main__":
    main()
