"""Read original inputs without altering them; record PDF geometry in millimetres.

Requires pdfplumber (available in this PC's bundled Codex Python runtime).
Run from any working directory. Output: docs/reference-geometry.json.
"""
import hashlib
import json
import math
from pathlib import Path

import pdfplumber

ROOT = Path(__file__).resolve().parents[1]
MM_PER_POINT = 25.4 / 72


def fingerprint(path):
    return {
        "filename": path.name,
        "bytes": path.stat().st_size,
        "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
    }


def inspect_mask():
    path = ROOT / "LI-6800-mask.pdf"
    with pdfplumber.open(path) as document:
        assert len(document.pages) == 1
        page = document.pages[0]
        assert page.page_obj.attrs.get("UserUnit", 1) == 1
        # Each outline has coincident fill and stroke paths: count strokes only.
        circles = [
            curve for curve in page.curves
            if curve["stroke"]
            and abs(curve["width"] - curve["height"]) < 0.01
            and 70 < curve["width"] < 90
        ]
        assert len(circles) == 4, "Reference geometry changed; inspect it before reuse."
        apertures = []
        for curve in sorted(circles, key=lambda c: (c["top"], c["x0"])):
            width = curve["width"] * MM_PER_POINT
            height = curve["height"] * MM_PER_POINT
            apertures.append({
                "center_from_page_top_left_mm": [
                    (curve["x0"] + curve["x1"]) / 2 * MM_PER_POINT,
                    (curve["top"] + curve["bottom"]) / 2 * MM_PER_POINT,
                ],
                "diameter_x_mm": width,
                "diameter_y_mm": height,
                "ideal_ellipse_area_from_bounds_cm2": math.pi * width * height / 400,
                "note": "Bounds of the circle's cubic Bezier path, not the cut paper or hardware bore.",
            })
        return {
            **fingerprint(path),
            "page_size_mm": [page.width * MM_PER_POINT, page.height * MM_PER_POINT],
            "apertures": apertures,
        }


def main():
    evidence = {
        "inspection_date": "2026-09-10",
        "print_contract": "User specifies actual size / 100%, with no printer scaling.",
        "application_target": {
            "aperture_area_cm2": 6.0,
            "aperture_area_mm2": 600.0,
            "ideal_circle_diameter_mm": 2 * math.sqrt(600 / math.pi),
            "authority": "User clarification: It should be 6 cm^2.",
            "note": "Target for the new template; original PDF remains unchanged.",
        },
        "mask_pdf": inspect_mask(),
        "calibration_pdf": fingerprint(ROOT / "calibration_pad_pro.pdf"),
        "photo": fingerprint(ROOT / "2026-09-10 12.40.27.jpg"),
    }
    output = ROOT / "docs" / "reference-geometry.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
    print(output)
    print(json.dumps(evidence["application_target"], indent=2))
    print("Original first circle:", evidence["mask_pdf"]["apertures"][0])


if __name__ == "__main__":
    main()
