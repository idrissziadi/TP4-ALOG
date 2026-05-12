import requests
import os
import sys

SERVER = "http://localhost:8080/HISSAB/api/hissab/calculer"
DIR    = os.path.dirname(os.path.abspath(__file__))
TOL    = 0.001

TESTS = [
    ("3x4.png",          "3 × 4",             12.0),
    ("5+2x6-3.png",      "5 + 2 × 6 - 3",     14.0),
    ("12div4+3.png",     "12 / 4 + 3",          6.0),
    ("7x8-10.png",       "7 × 8 - 10",         46.0),
    ("6+2x5.png",        "(6 + 2) × 5",        40.0),
    ("100div4x3.png",    "100 / 4 × 3",        75.0),
    ("9-3x2.png",        "9 - 3 × 2",           3.0),
    ("2x8+12div3.png",   "2 × 8 + 12 / 3",     20.0),
]

W = 62
print("=" * W)
print("  HISSAB -- Test OCR diagnostique")
print("=" * W)

passed = 0
failed = 0

for i, (fname, expr_attendue, res_attendu) in enumerate(TESTS, 1):
    path = os.path.join(DIR, fname)
    print(f"\n[{i}/{len(TESTS)}] {fname}")
    print(f"  Expr. attendue  : {expr_attendue}  (= {res_attendu})")

    if not os.path.exists(path):
        print(f"  ✗ Fichier introuvable")
        failed += 1
        continue

    try:
        with open(path, "rb") as f:
            resp = requests.post(SERVER, files={"fichier": (fname, f, "image/png")}, timeout=40)
        data = resp.json()
    except Exception as e:
        print(f"  ✗ Erreur réseau : {e}")
        failed += 1
        continue

    if not data.get("succes"):
        print(f"  Expr. extraite  : —")
        print(f"  Résultat obtenu : —")
        print(f"  Message erreur  : {data.get('message', '?')}")
        print(f"  → FAIL ✗")
        failed += 1
        continue

    expr_ocr = data.get("expression", "?")
    res_obtenu = data.get("resultat", None)

    print(f"  Expr. extraite  : {expr_ocr}")
    print(f"  Résultat obtenu : {res_obtenu}")

    if res_obtenu is not None and abs(float(res_obtenu) - res_attendu) < TOL:
        print(f"  → PASS ✓")
        passed += 1
    else:
        print(f"  → FAIL ✗  (attendu {res_attendu})")
        failed += 1

print()
print("=" * W)
print(f"  Resume : {passed} PASS / {failed} FAIL  ({len(TESTS)} images)")
print("=" * W)
sys.exit(0 if failed == 0 else 1)
