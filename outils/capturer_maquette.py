"""Capture une maquette HTML du handoff avec Chromium headless (Playwright), pour comparer au rendu Compose.

Usage :
  LD_LIBRARY_PATH=~/opt/libs-navigateur/racine/usr/lib/x86_64-linux-gnu \
  ~/opt/venv-outils/bin/python outils/capturer_maquette.py ../irontap-specs/design_handoff_irontap/design SaisieSerie.dc.html sortie.png 360 800

Prérequis (hors dépôt, sans sudo) : venv ~/opt/venv-outils avec `playwright` + `playwright install chromium-headless-shell`,
et libnss3 / libnspr4 / libasound2t64 extraites des .deb Ubuntu dans ~/opt/libs-navigateur/racine.
La maquette charge Inter depuis Google Fonts : la capture nécessite le réseau (outil de dev, pas l'appli).
"""
import functools
import http.server
import sys
import threading

from playwright.sync_api import sync_playwright


class Silencieux(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *args):
        pass


racine, page_html, sortie = sys.argv[1], sys.argv[2], sys.argv[3]
largeur, hauteur = int(sys.argv[4]), int(sys.argv[5])

serveur = http.server.ThreadingHTTPServer(("127.0.0.1", 0), functools.partial(Silencieux, directory=racine))
threading.Thread(target=serveur.serve_forever, daemon=True).start()
with sync_playwright() as p:
    navigateur = p.chromium.launch(chromium_sandbox=True)
    page = navigateur.new_page(viewport={"width": largeur, "height": hauteur}, device_scale_factor=3)
    page.on("console", lambda m: print("console:", m.text) if m.type == "error" else None)
    page.goto(f"http://127.0.0.1:{serveur.server_port}/{page_html}", wait_until="networkidle")
    page.wait_for_timeout(1500)
    page.screenshot(path=sortie, full_page=True)
    navigateur.close()
serveur.shutdown()
