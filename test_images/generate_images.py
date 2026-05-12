from PIL import Image, ImageDraw, ImageFont
import os, shutil

OUT_DIR = os.path.dirname(os.path.abspath(__file__))

TESTS = [
    ("3x4.png",          "3 × 4"),
    ("5+2x6-3.png",      "5 + 2 × 6 - 3"),
    ("12div4+3.png",     "12 / 4 + 3"),
    ("7x8-10.png",       "7 × 8 - 10"),
    ("6+2x5.png",        "(6 + 2) × 5"),
    ("100div4x3.png",    "100 / 4 × 3"),
    ("9-3x2.png",        "9 - 3 × 2"),
    ("2x8+12div3.png",   "2 × 8 + 12 / 3"),
]

try:
    font = ImageFont.truetype("arial.ttf", 80)
except Exception:
    try:
        font = ImageFont.truetype("cambria.ttc", 80)
    except Exception:
        font = ImageFont.load_default()

PAD_X, PAD_Y = 80, 50   # marge autour du texte

for fname, expr in TESTS:
    # Mesurer d'abord le texte pour adapter le canvas
    tmp  = ImageDraw.Draw(Image.new("RGB", (1, 1)))
    bbox = tmp.textbbox((0, 0), expr, font=font)
    tw   = bbox[2] - bbox[0]
    th   = bbox[3] - bbox[1]

    W = tw + PAD_X * 2
    H = th + PAD_Y * 2

    img = Image.new("RGB", (W, H), color=(245, 245, 245))   # fond gris très clair
    d   = ImageDraw.Draw(img)

    # Cadre fin — aide l'OCR à délimiter la région de texte
    d.rectangle([(3, 3), (W - 4, H - 4)], outline=(80, 80, 80), width=2)

    x = (W - tw) // 2
    y = (H - th) // 2
    d.text((x, y), expr, font=font, fill=(0, 0, 0))

    path = os.path.join(OUT_DIR, fname)
    img.save(path)
    print("OK ->", fname)

print(f"\n{len(TESTS)} images regenerees dans", OUT_DIR)
