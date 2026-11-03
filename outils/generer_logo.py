"""Génère les images du logo qui ne sont pas dans l'APK, à partir du symbole IronTap (irontap-symbol.svg) :
- fastlane/metadata/android/en-US/images/icon.png : icône de la fiche F-Droid (512 px), dessinée comme l'icône du
  lanceur (symbole de 54 dp sur les 72 dp visibles, fond nocturne_bg, coins arrondis comme l'ancienne icône) ;
- avec le dossier du site en argument : favicon.svg (symbole sur un disque bg, lisible sur un onglet clair ou sombre,
  aussi logo de la barre de navigation) et img/apple-touch-icon.png (180 px, opaque : iOS arrondit lui-même).

Les dessins Android (lanceur, À propos, notification) sont faits par generer_icones.py.

Usage :
  LD_LIBRARY_PATH=~/opt/libs-navigateur/racine/usr/lib/x86_64-linux-gnu \
  ~/opt/venv-outils/bin/python outils/generer_logo.py [../irontap-website]

Prérequis : ceux de capturer_maquette.py (Playwright, Chromium headless), plus Pillow dans le même venv.
"""
import io
import os
import re
import sys

from PIL import Image
from playwright.sync_api import sync_playwright

RACINE = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
FOND = '#161826'  # nocturne_bg, fond de l'icône du lanceur et --bg du site

source = open(os.path.join(RACINE, 'irontap-symbol.svg')).read()
trace = re.search(r'\sd="([^"]+)"', source).group(1)
teinte = re.search(r'fill="(#[0-9a-fA-F]{6})"', source).group(1).lower()
symbole = f'<path fill="{teinte}" fill-rule="evenodd" d="{trace}"/>'
# Boîte du tracé : x de -0,85 à 510,29, y de 7,68 à 503,47 (le cercle est un peu plus large que haut).
CX, CY, LARGEUR = 254.72, 255.58, 511.14


def icone(cote, rayon_coins):
    """Fond carré (coins arrondis ou non) et symbole de 54/72 du côté, centré, comme l'icône du lanceur."""
    e = cote * 54 / 72 / LARGEUR
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{cote}" height="{cote}" viewBox="0 0 {cote} {cote}">'
            f'<rect width="{cote}" height="{cote}" rx="{rayon_coins}" fill="{FOND}"/>'
            f'<g transform="translate({cote / 2 - CX * e:.3f} {cote / 2 - CY * e:.3f}) scale({e:.6f})">{symbole}</g></svg>')


def png(navigateur, svg, cote, sortie):
    page = navigateur.new_page(viewport={'width': cote, 'height': cote})
    page.set_content(f'<!doctype html><body style="margin:0">{svg}</body>')
    image = Image.open(io.BytesIO(page.screenshot(omit_background=True)))
    page.close()
    image.save(sortie, optimize=True)
    print(sortie)


site = sys.argv[1] if len(sys.argv) > 1 else None
with sync_playwright() as p:
    navigateur = p.chromium.launch()
    png(navigateur, icone(512, 110), 512, os.path.join(RACINE, 'fastlane/metadata/android/en-US/images/icon.png'))
    if site:
        png(navigateur, icone(180, 0), 180, os.path.join(site, 'img/apple-touch-icon.png'))
    navigateur.close()

if site:
    # Disque elliptique sous l'anneau (rayons entre ceux du bord intérieur et du bord extérieur), viewBox recentré.
    favicon = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="-1.5 0 512 512">'
               f'<ellipse cx="{CX}" cy="{CY}" rx="236" ry="230" fill="{FOND}"/>{symbole}</svg>\n')
    open(os.path.join(site, 'favicon.svg'), 'w').write(favicon)
    print(os.path.join(site, 'favicon.svg'))
