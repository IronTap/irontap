"""Génère les vector drawables Phosphor d'IronTap à partir de la géométrie SVG des maquettes (viewBox 256)."""
# Usage : python3 outils/generer_icones.py app/src/main/res/drawable
import re, sys, os

def rect(x, y, w, h, rx):
    return (f"M{x+rx},{y}h{w-2*rx}a{rx},{rx} 0,0 1,{rx},{rx}v{h-2*rx}a{rx},{rx} 0,0 1,{-rx},{rx}"
            f"h{-(w-2*rx)}a{rx},{rx} 0,0 1,{-rx},{-rx}v{-(h-2*rx)}a{rx},{rx} 0,0 1,{rx},{-rx}z")

def circle(cx, cy, r):
    return f"M{cx-r},{cy}a{r},{r} 0,1 1,{2*r},0a{r},{r} 0,1 1,{-2*r},0z"

def to_path(svg):
    out = []
    for tag, attrs in re.findall(r'<(\w+)([^>]*)/?>', svg):
        a = dict(re.findall(r'([\w-]+)="([^"]*)"', attrs)); n = lambda k: float(a[k])
        if tag == 'path': out.append(a['d'])
        elif tag == 'line': out.append(f"M{a['x1']},{a['y1']}L{a['x2']},{a['y2']}")
        elif tag == 'polyline':
            p = a['points'].split(); pts = [f"{p[i]},{p[i+1]}" for i in range(0, len(p), 2)]
            out.append('M' + 'L'.join(pts))
        elif tag == 'rect': out.append(rect(n('x'), n('y'), n('width'), n('height'), n('rx')))
        elif tag == 'circle': out.append(circle(n('cx'), n('cy'), n('r')))
    return re.sub(r'\.0(?=\D|$)', '', ' '.join(out))

# nom : (géométrie des maquettes, épaisseur de trait ou None si plein)
ICONS = {
    'x': ('<line x1="200" y1="56" x2="56" y2="200"/><line x1="200" y1="200" x2="56" y2="56"/>', 16),
    'arrow_left': ('<line x1="216" y1="128" x2="40" y2="128"/><polyline points="112 56 40 128 112 200"/>', 16),
    'caret_right': ('<polyline points="96 48 176 128 96 208"/>', 16),
    'plus': ('<line x1="40" y1="128" x2="216" y2="128"/><line x1="128" y1="40" x2="128" y2="216"/>', 20),
    'play': ('<path d="M72 39.9v176.2a8 8 0 0 0 12.2 6.8L228 132.9a8 8 0 0 0 0-13.8L84.2 33.1A8 8 0 0 0 72 39.9Z"/>', None),
    'pause': ('<rect x="56" y="40" width="52" height="176" rx="8"/><rect x="148" y="40" width="52" height="176" rx="8"/>', None),
    'arrow_counter_clockwise': ('<polyline points="24 56 24 104 72 104"/><path d="M67.6 192A88 88 0 1 0 65.8 65.8L24 104"/>', 18),
    'clock_counter_clockwise': ('<polyline points="128 80 128 128 168 152"/><polyline points="72 104 32 104 32 64"/><path d="M67.6 192a88 88 0 1 0-1.8-126.2C54 77.7 44.3 88.6 32 104"/>', 16),
    # Flèche des dégressives : trait 20 à 18 dp (bouton), 24 aux petites tailles (11–15 dp), comme les maquettes.
    'arrow_down': ('<line x1="128" y1="40" x2="128" y2="216"/><polyline points="56 144 128 216 200 144"/>', 20),
    'arrow_down_small': ('<line x1="128" y1="40" x2="128" y2="216"/><polyline points="56 144 128 216 200 144"/>', 24),
    'lock': ('<rect x="40" y="88" width="176" height="128" rx="8"/><path d="M92 88V52a36 36 0 0 1 72 0v36"/>', 16),
    'flag': ('<line x1="48" y1="224" x2="48" y2="48"/><path d="M48 168c64-56 96 56 160 0V48c-64 56-96-56-160 0"/>', 16),
    'calendar': ('<rect x="40" y="40" width="176" height="176" rx="8"/><line x1="176" y1="24" x2="176" y2="56"/><line x1="80" y1="24" x2="80" y2="56"/><line x1="40" y1="88" x2="216" y2="88"/>', 16),
    'export': ('<polyline points="86 58 128 16 170 58"/><line x1="128" y1="16" x2="128" y2="152"/><path d="M176 104h24a8 8 0 0 1 8 8v96a8 8 0 0 1-8 8H56a8 8 0 0 1-8-8v-96a8 8 0 0 1 8-8h24"/>', 16),
    'download_simple': ('<line x1="128" y1="144" x2="128" y2="32"/><polyline points="216 144 216 208 40 208 40 144"/><polyline points="168 104 128 144 88 104"/>', 16),
    'trash': ('<line x1="216" y1="56" x2="40" y2="56"/><path d="M200 56V208a8 8 0 0 1-8 8H64a8 8 0 0 1-8-8V56"/><path d="M168 56V40a16 16 0 0 0-16-16H104a16 16 0 0 0-16 16V56"/>', 16),
    'pencil_simple': ('<path d="M92.7 216H48a8 8 0 0 1-8-8v-44.7a8 8 0 0 1 2.3-5.6l120-120a8 8 0 0 1 11.4 0l44.6 44.6a8 8 0 0 1 0 11.4l-120 120a8 8 0 0 1-5.6 2.3Z"/><line x1="136" y1="64" x2="192" y2="120"/>', 16),
    'dots_three_vertical': ('<circle cx="128" cy="60" r="16"/><circle cx="128" cy="128" r="16"/><circle cx="128" cy="196" r="16"/>', None),
    'bell': ('<path d="M56 104a72 72 0 0 1 144 0c0 35.8 8.2 64.5 14.8 76A8 8 0 0 1 208 192H48a8 8 0 0 1-6.9-12C47.8 168.5 56 139.8 56 104Z"/><path d="M96 192a32 32 0 0 0 64 0"/>', 16),
    'check': ('<polyline points="40 144 96 200 224 72"/>', 20),
    'warning_circle': ('<circle cx="128" cy="128" r="96"/><line x1="128" y1="80" x2="128" y2="136"/><line x1="128" y1="172" x2="128" y2="174"/>', 18),
    # Haltère complet (état vide, 40 dp) et version compacte sans disques extérieurs (petites tailles, notification).
    'barbell': ('<rect x="56" y="56" width="40" height="144" rx="8"/><rect x="160" y="56" width="40" height="144" rx="8"/><line x1="96" y1="128" x2="160" y2="128"/><path d="M200 80h24a8 8 0 0 1 8 8v80a8 8 0 0 1-8 8h-24"/><path d="M56 176H32a8 8 0 0 1-8-8V88a8 8 0 0 1 8-8h24"/>', 12),
    'barbell_compact': ('<rect x="56" y="56" width="40" height="144" rx="8"/><rect x="160" y="56" width="40" height="144" rx="8"/><line x1="96" y1="128" x2="160" y2="128"/>', 20),
}

def drawable(path, sw):
    style = (f'android:fillColor="#FFFFFFFF"' if sw is None else
             f'android:strokeColor="#FFFFFFFF"\n        android:strokeWidth="{sw}"\n'
             f'        android:strokeLineCap="round"\n        android:strokeLineJoin="round"')
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Phosphor (MIT), géométrie reprise des maquettes. Généré par outils/generer_icones.py : ne pas modifier à la main. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="256"
    android:viewportHeight="256">
    <path
        {style}
        android:pathData="{path}" />
</vector>
'''

out = sys.argv[1]
for name, (svg, sw) in ICONS.items():
    open(os.path.join(out, f'ic_{name}.xml'), 'w').write(drawable(to_path(svg), sw))

# Icône du lanceur : haltère complet en accent, ramené dans la zone sûre (66 dp centrés sur 108).
barbell = to_path(ICONS['barbell'][0])
open(os.path.join(out, 'ic_launcher_foreground.xml'), 'w').write(f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Haltère Phosphor (MIT) en accent, dans la zone sûre de l'icône adaptative. Généré par outils/generer_icones.py. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group
        android:scaleX="0.2578125"
        android:scaleY="0.2578125"
        android:translateX="21"
        android:translateY="21">
        <path
            android:strokeColor="@color/nocturne_accent"
            android:strokeWidth="12"
            android:strokeLineCap="round"
            android:strokeLineJoin="round"
            android:pathData="{barbell}" />
    </group>
</vector>
''')
print(len(ICONS), 'icônes')
