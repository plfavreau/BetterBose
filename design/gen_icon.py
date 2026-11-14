"""Generates the "Quiet b" launcher icon + notification icon as vector drawables.

Design units are the 100x100 SVG (variant 12) in design/logo-variants-2.html; the adaptive icon
shows the middle 72dp of its 108dp canvas, so design unit u maps to 18 + 0.72u dp.
Run from the repo root: python3 design/gen_icon.py
"""
RES = "app/src/main/res"
S = 0.72
STEM_X, STEM_TOP, STEM_BOTTOM = 36, 24, 74
BOWL_X, BOWL_Y, BOWL_R, CORE_R = 52, 58, 16, 5
STROKE = 7
HEAD = '<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"'


def circ(cx, cy, r):
    return f"M{cx:.2f},{cy - r:.2f} a{r:.2f},{r:.2f} 0 1,1 0,{2 * r:.2f} a{r:.2f},{r:.2f} 0 1,1 0,{-2 * r:.2f}"


def glyph(scale, offset):
    p = lambda u: offset + u * scale
    w = STROKE * scale
    return "\n".join([
        f'    <path android:pathData="M{p(STEM_X):.2f},{p(STEM_TOP):.2f} L{p(STEM_X):.2f},{p(STEM_BOTTOM):.2f}" '
        f'android:strokeColor="#FFFFFF" android:strokeWidth="{w:.2f}" android:strokeLineCap="round" android:fillColor="#00000000"/>',
        f'    <path android:pathData="{circ(p(BOWL_X), p(BOWL_Y), BOWL_R * scale)}" '
        f'android:strokeColor="#FFFFFF" android:strokeWidth="{w:.2f}" android:fillColor="#00000000"/>',
        f'    <path android:pathData="{circ(p(BOWL_X), p(BOWL_Y), CORE_R * scale)}" android:fillColor="#FFFFFF"/>',
    ])


def vector(size, body, extra=""):
    return (f'{HEAD}{extra}\n    android:width="{size}dp" android:height="{size}dp" '
            f'android:viewportWidth="{size}" android:viewportHeight="{size}">\n{body}\n</vector>\n')


def write(name, text):
    with open(f"{RES}/drawable/{name}.xml", "w") as f:
        f.write(text)


write("ic_launcher_fg", vector(108, glyph(S, 18)))
write("ic_launcher_mono", vector(108, glyph(S, 18)))
write("ic_launcher_bg", vector(108, """    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="radial" android:centerX="54" android:centerY="46.8" android:gradientRadius="54"
                android:startColor="#3A3A3E" android:endColor="#0C0C0E"/>
        </aapt:attr>
    </path>""", '\n    xmlns:aapt="http://schemas.android.com/aapt"'))

# Notification icon: glyph spans ~u 20..77, map it onto 24dp with a 1.5dp margin.
k = 21 / 58
write("ic_notify", vector(24, glyph(k, 12 - 49 * k)))
