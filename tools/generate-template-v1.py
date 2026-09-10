"""Generate the v1 metric template, printable PDF, and independent image fixtures.

Uses reportlab, numpy, OpenCV 4.13, and pypdfium2. On this PC the OpenCV wheel is
SHA-256 verified from PyPI and unpacked into tmp/python-modules; other dependencies
come from the bundled Codex runtime. All geometry is defined below in millimetres.
"""
import json
import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tmp/python-modules"))
import cv2
import numpy as np
import pypdfium2
from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm

ASSETS = ROOT / "app/src/main/assets"
OUTPUT = ROOT / "output/pdf"
RADIUS = math.sqrt(600 / math.pi)
MARKERS = [{"id": 10 + row * 3 + col, "centerMm": [x, y], "sideMm": 10.0}
           for row, y in enumerate([-25.0, 25.0])
           for col, x in enumerate([-42.0, -18.0, 8.0])]
TEMPLATE = {
    "id": "LI6800-6-V1", "dictionary": "DICT_4X4_50", "dictionaryCode": 0,
    "apertureAreaMm2": 600.0, "apertureRadiusMm": RADIUS,
    "coordinateSystem": "origin at opening center, x right, y down, millimetres",
    "markerSideMm": 10.0, "markers": MARKERS,
    "minimumVisibleMarkers": 4, "originalFrontTipRadiusMm": 20.0,
    "protectedFrontArcDegrees": [-45, 45],
    "printScale": "Actual size / 100%, A4, no Fit to page",
}
DICTIONARY = cv2.aruco.getPredefinedDictionary(cv2.aruco.DICT_4X4_50)


def outline():
    # Preserve the original front 90-degree arc and furthest tip x=20 mm.
    points = [(-80, -34), (17, -34), (17, -19), (16, -16)]
    points += [(20 * math.cos(a), 20 * math.sin(a)) for a in np.linspace(-math.pi / 4, math.pi / 4, 65)]
    points += [(16, 16), (17, 19), (17, 34), (-80, 34)]
    return points


def draw_cutout(pdf, ox, oy):
    def point(x, y): return ((ox + x) * mm, A4[1] - (oy + y) * mm)
    pdf.setStrokeColorRGB(.45, .48, .48)
    pdf.setLineWidth(.35)
    path = pdf.beginPath()
    for i, (x, y) in enumerate(outline()):
        (path.moveTo if i == 0 else path.lineTo)(*point(x, y))
    path.close()
    pdf.drawPath(path)
    pdf.circle(*point(0, 0), RADIUS * mm, stroke=1, fill=0)
    pdf.setFillColorRGB(0, 0, 0)
    for marker in MARKERS:
        bits = cv2.aruco.generateImageMarker(DICTIONARY, marker["id"], 6)
        cx, cy = marker["centerMm"]
        for row in range(6):
            for col in range(6):
                if bits[row, col] == 0:
                    px, py = point(cx - 5 + col * 10 / 6, cy - 5 + (row + 1) * 10 / 6)
                    pdf.rect(px, py, 10 / 6 * mm, 10 / 6 * mm, stroke=0, fill=1)
    def label(x, y, text, size=8, bold=False):
        pdf.setFont("Helvetica-Bold" if bold else "Helvetica", size)
        pdf.drawString(*point(x, y), text)
    label(-74, -9, "LEAF AREA", 11, True)
    label(-74, -3, "LI6800-6-V1", 8)
    label(-74, 3, "6.000 cm2 opening", 8)
    label(-74, 9, "Keep all six markers.", 7)
    label(-74, 14, "Do not cover or fold.", 7)
    pdf.setFont("Helvetica", 7)
    pdf.drawCentredString(*point(0, -1), "CUT OUT")
    pdf.drawCentredString(*point(0, 3), "THIS CIRCLE")


def make_pdf():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    path = OUTPUT / "LI6800-6cm2-marker-cutout-v1.pdf"
    pdf = canvas.Canvas(str(path), pagesize=A4, pageCompression=1)
    pdf.setTitle("LI-6800 Leaf Area | 6 cm2 Marker Cutout | V1")
    pdf.setAuthor("LI-6800 Area app project")
    pdf.setViewerPreference("PrintScaling", "None")
    pdf.setFillColorRGB(.06, .17, .14)
    pdf.setFont("Helvetica-Bold", 20)
    pdf.drawString(20 * mm, A4[1] - 23 * mm, "Leaf area / 6 cm2")
    pdf.setFont("Helvetica", 10)
    pdf.drawString(20 * mm, A4[1] - 31 * mm, "PRINT AT ACTUAL SIZE (100%) ON A4. Disable Fit to page.")
    pdf.setFont("Helvetica", 9)
    pdf.drawString(20 * mm, A4[1] - 38 * mm, "Two identical cutouts. Cut on the outer line and remove the inner circle.")
    for y in [84, 165]: draw_cutout(pdf, 135, y)
    pdf.setFillColorRGB(.06, .17, .14)
    pdf.setFont("Helvetica-Bold", 10)
    pdf.drawString(20 * mm, A4[1] - 218 * mm, "Check this line measures exactly 50 mm after printing")
    pdf.setStrokeColorRGB(0, 0, 0)
    pdf.setLineWidth(.6)
    pdf.line(20 * mm, A4[1] - 229 * mm, 70 * mm, A4[1] - 229 * mm)
    for x in [20, 70]: pdf.line(x * mm, A4[1] - 227 * mm, x * mm, A4[1] - 231 * mm)
    pdf.setFont("Helvetica", 9)
    notes = [
        "Place the leaf flat on a white backing, with this cutout on top.",
        "Keep the six square markers visible. Use the matching LI6800-6-V1 app template.",
        "Only leaf tissue in the circle is measured. Check the colored overlay before saving.",
        "Opening diameter: 27.6395 mm. The front tip retains the original 20 mm radius.",
        "External imaging aid. Keep the same leaf region registered for LI-6800 measurements.",
    ]
    for i, line in enumerate(notes): pdf.drawString(20 * mm, A4[1] - (245 + i * 5) * mm, line)
    pdf.save()
    (ASSETS / "LI6800-6cm2-marker-cutout-v1.pdf").write_bytes(path.read_bytes())
    preview = ROOT / "tmp/pdfs/marker-cutout-v1.png"
    preview.parent.mkdir(parents=True, exist_ok=True)
    with pypdfium2.PdfDocument(path) as document:
        document[0].render(scale=2).to_pil().save(preview)
    return path


def make_fixtures():
    dest = ASSETS / "samples-v1"
    dest.mkdir(parents=True, exist_ok=True)
    q = 12
    width, height = 110 * q, 82 * q
    def px(x, y): return (round((x + 85) * q), round((y + 41) * q))
    yy, xx = np.mgrid[:height, :width]
    x, y = (xx + .5) / q - 85, (yy + .5) / q - 41
    aperture = x * x + y * y <= RADIUS * RADIUS
    report = {}
    for name in ["empty", "half", "full", "leaf-hole", "perspective", "missing-markers", "yellow"]:
        image = np.full((height, width, 3), 222, np.uint8)
        cv2.fillPoly(image, [np.array([px(*p) for p in outline()])], (255, 255, 255))
        tissue = np.zeros_like(aperture)
        if name in ["half", "perspective", "missing-markers"]: tissue = aperture & (x < 0)
        if name in ["full", "yellow"]: tissue = aperture.copy()
        if name == "leaf-hole":
            tissue = aperture & ((x / 9) ** 2 + (y / 19) ** 2 <= 1) & ((x - 2) ** 2 + (y + 2) ** 2 >= 2.5 ** 2)
        image[aperture] = (255, 255, 255)
        image[tissue] = (42, 112, 64) if name != "yellow" else (65, 190, 224)
        for marker in MARKERS:
            if name == "missing-markers" and marker["id"] >= 13: continue
            cx, cy = marker["centerMm"]
            mx, my = px(cx - 5, cy - 5)
            bits = cv2.aruco.generateImageMarker(DICTIONARY, marker["id"], 10 * q)
            image[my:my + 10 * q, mx:mx + 10 * q] = cv2.cvtColor(bits, cv2.COLOR_GRAY2BGR)
        if name == "perspective":
            source = np.float32([[0, 0], [width - 1, 0], [width - 1, height - 1], [0, height - 1]])
            target = np.float32([[80, 160], [width - 80, 0], [width - 1, height - 140], [0, height - 1]])
            image = cv2.warpPerspective(image, cv2.getPerspectiveTransform(source, target), (width, height), borderValue=(222, 222, 222))
        cv2.imwrite(str(dest / f"{name}.png"), image)
        report[name] = {"expectedAreaMm2": float(tissue.sum() / q ** 2), "valid": name != "missing-markers"}
    (dest / "expected.json").write_text(json.dumps(report, indent=2) + "\n")
    return report


def main():
    ASSETS.mkdir(parents=True, exist_ok=True)
    (ASSETS / "template-v1.json").write_text(json.dumps(TEMPLATE, indent=2) + "\n")
    pdf_path = make_pdf()
    report = make_fixtures()
    # Independently detect all printed markers from a rendered PDF page.
    with pypdfium2.PdfDocument(pdf_path) as document:
        rendered = np.array(document[0].render(scale=4).to_pil().convert("RGB"))
    detector = cv2.aruco.ArucoDetector(DICTIONARY)
    corners, ids, _ = detector.detectMarkers(rendered)
    assert ids is not None and len(ids) == 12, f"Expected 6 markers on each of 2 cutouts, found {ids}"
    assert all(list(ids.flatten()).count(m["id"]) == 2 for m in MARKERS)
    print(pdf_path)
    print("PDF marker read-back: 12/12; all 6 IDs occur twice.")
    print(json.dumps(report, indent=2))


if __name__ == "__main__": main()
